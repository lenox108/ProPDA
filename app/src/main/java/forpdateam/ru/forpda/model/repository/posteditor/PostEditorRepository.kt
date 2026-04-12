package forpdateam.ru.forpda.model.repository.posteditor

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCache
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi
import forpdateam.ru.forpda.model.repository.BaseRepository
import io.reactivex.Completable
import io.reactivex.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by radiationx on 01.01.18.
 */
class PostEditorRepository(
        private val schedulers: SchedulersProvider,
        private val editPostApi: EditPostApi,
        private val attachmentsApi: AttachmentsApi,
        private val forumUsersCache: ForumUsersCache
) : BaseRepository(schedulers) {

    private val warmCache = ConcurrentHashMap<Int, EditPostWarmEntry>()
    /** Один активный сетевой Single на postId — префетч и открытие редактора не дублируют TCP. */
    private val inflightForm = ConcurrentHashMap<Int, Single<EditPostForm>>()
    private val inflightAttach = ConcurrentHashMap<Int, Single<List<AttachmentItem>>>()
    /** [bumpEditPrefetchGeneration] при новой загрузке темы; префетч сравнивает снимок с [get]. */
    private val prefetchGeneration = AtomicInteger(0)

    fun bumpEditPrefetchGeneration() {
        prefetchGeneration.incrementAndGet()
    }

    fun invalidateEditCache(postId: Int) {
        if (postId > 0) warmCache.remove(postId)
    }

    /**
     * Снимок кэша для мгновенного UI (в т.ч. после TTL): BBCode и вложения, если они уже были в кэше.
     * [attachments] == null — список attach init ещё не кэшировали, догрузит [loadEditAttachments].
     */
    fun snapshotWarmEdit(postId: Int): EditPostWarmSnapshot? {
        if (postId <= 0) return null
        val hit = warmCache[postId] ?: return null
        val f = hit.form ?: return null
        if (f.errorCode != EditPostForm.ERROR_NONE || !isEditFormContentPresent(f)) return null
        val attCopy = hit.attachments?.let { deepCopyAttachmentList(it) }
        return EditPostWarmSnapshot(form = f.deepCopyForCache(), attachments = attCopy)
    }

    /**
     * Старт загрузки формы + attach до открытия экрана (делит inflight с редактором).
     */
    fun kickWarmNetworkLoad(postId: Int) {
        if (postId <= 0) return
        // Сначала форма (BBCode), потом attach init — меньше параллельной нагрузки на лимиты сервера.
        Completable.fromAction {
            try {
                loadFormBody(postId).blockingGet()
                loadAttachBody(postId).blockingGet()
            } catch (_: Exception) {
            }
        }
                .subscribeOn(schedulers.io())
                .subscribe({}, {})
    }

    /**
     * После загрузки темы — префетч форм редактирования (до [PREFETCH_MAX_PARALLEL] запросов параллельно).
     */
    fun prefetchEditForPosts(postIds: Iterable<Int>) {
        val ids = postIds.distinct().filter { it > 0 }
        if (ids.isEmpty()) return
        val gen = prefetchGeneration.get()
        val sem = Semaphore(PREFETCH_MAX_PARALLEL)
        for (postId in ids) {
            Completable.fromAction {
                sem.acquireUninterruptibly()
                try {
                    prefetchEditOnePost(postId, gen)
                } finally {
                    sem.release()
                }
            }
                    .subscribeOn(schedulers.io())
                    .subscribe({}, {})
        }
    }

    private fun prefetchEditOnePost(postId: Int, gen: Int) {
        if (prefetchGeneration.get() != gen) return
        try {
            val cached = warmCache[postId]
            if (cached != null && cached.form != null && cached.attachments != null && cached.isFresh(CACHE_TTL_MS)) {
                return
            }
            if (prefetchGeneration.get() != gen) return
            val form = loadFormBody(postId).blockingGet()
            if (form.errorCode != EditPostForm.ERROR_NONE) return
            if (!isEditFormContentPresent(form)) return
            if (prefetchGeneration.get() != gen) return
            val att = loadAttachBody(postId).blockingGet()
            putWarmEntry(
                    postId,
                    EditPostWarmEntry(
                            form = form.deepCopyForCache(),
                            attachments = deepCopyAttachmentList(att),
                            storedAtMillis = System.currentTimeMillis(),
                    )
            )
        } catch (_: Exception) {
            // тихий prefetch
        }
    }

    private fun putWarmEntry(postId: Int, entry: EditPostWarmEntry) {
        pruneStaleWarmCache()
        evictWarmCacheIfOverLimit(postId)
        warmCache[postId] = entry
    }

    private fun evictWarmCacheIfOverLimit(incomingPostId: Int) {
        while (warmCache.size >= MAX_WARM_ENTRIES && !warmCache.containsKey(incomingPostId)) {
            val staleKey = warmCache.entries
                    .filter { !it.value.isFresh(CACHE_TTL_MS) }
                    .minByOrNull { it.value.storedAtMillis }
                    ?.key
            if (staleKey != null) {
                warmCache.remove(staleKey)
                continue
            }
            val oldestKey = warmCache.minByOrNull { it.value.storedAtMillis }?.key ?: break
            warmCache.remove(oldestKey)
        }
    }

    private fun pruneStaleWarmCache() {
        val deadline = System.currentTimeMillis() - CACHE_TTL_MS * 2
        val it = warmCache.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.storedAtMillis < deadline) {
                it.remove()
            }
        }
    }

    /**
     * UI: результат на main. Внутри — [loadFormBody] + observeOn(ui).
     * Rx-таймаут с запасом к OkHttp (connect/read/write в [Client] по 45 с).
     */
    fun loadForm(postId: Int): Single<EditPostForm> =
            loadFormBody(postId).observeOn(schedulers.ui())

    /**
     * Цепочка без observeOn(main): для префетча с io и для single-flight.
     */
    private fun loadFormBody(postId: Int): Single<EditPostForm> = Single.defer {
        val hit = warmCache[postId]
        val cachedForm = hit?.form
        if (cachedForm != null && hit.isFresh(CACHE_TTL_MS) && isEditFormContentPresent(cachedForm)) {
            return@defer Single.fromCallable { cachedForm.deepCopyForCache() }
                    .subscribeOn(schedulers.io())
        }
        sharedNetworkLoadForm(postId)
    }

    private fun sharedNetworkLoadForm(postId: Int): Single<EditPostForm> =
            inflightForm.computeIfAbsent(postId) {
                Single.fromCallable { editPostApi.loadForm(postId) }
                        .timeout(NETWORK_FORM_TIMEOUT_SEC, TimeUnit.SECONDS, schedulers.io())
                        .subscribeOn(schedulers.io())
                        .withNetworkRetry(maxAttempts = 1, initialDelayMs = 500L)
                        .doOnSuccess { form ->
                            if (form.errorCode == EditPostForm.ERROR_NONE && isEditFormContentPresent(form)) {
                                mergeWarmCacheAfterForm(postId, form)
                            }
                        }
                        .doFinally { inflightForm.remove(postId) }
                        .cache()
            }

    fun loadEditAttachments(postId: Int): Single<List<AttachmentItem>> =
            loadAttachBody(postId).observeOn(schedulers.ui())

    private fun loadAttachBody(postId: Int): Single<List<AttachmentItem>> = Single.defer {
        val hit = warmCache[postId]
        if (hit != null && hit.attachments != null && hit.isFresh(CACHE_TTL_MS)) {
            return@defer Single.fromCallable { deepCopyAttachmentList(hit.attachments!!).toMutableList() }
                    .subscribeOn(schedulers.io())
        }
        sharedNetworkLoadAttach(postId)
    }

    private fun sharedNetworkLoadAttach(postId: Int): Single<List<AttachmentItem>> =
            inflightAttach.computeIfAbsent(postId) {
                Single.fromCallable { editPostApi.loadEditAttachments(postId) }
                        .timeout(ATTACH_NETWORK_TIMEOUT_SEC, TimeUnit.SECONDS, schedulers.io())
                        .subscribeOn(schedulers.io())
                        .withNetworkRetry(maxAttempts = 1, initialDelayMs = 400L)
                        .doOnSuccess { list -> mergeWarmCacheAfterAttachments(postId, list) }
                        .onErrorReturn { emptyList() }
                        .doFinally { inflightAttach.remove(postId) }
                        .cache()
            }

    private fun mergeWarmCacheAfterForm(postId: Int, form: EditPostForm) {
        val prev = warmCache[postId]
        val keepAtt = prev?.attachments
                ?.takeIf { prev.isFresh(CACHE_TTL_MS) }
                ?.let { deepCopyAttachmentList(it) }
        putWarmEntry(
                postId,
                EditPostWarmEntry(
                        form = form.deepCopyForCache(),
                        attachments = keepAtt,
                        storedAtMillis = System.currentTimeMillis(),
                )
        )
    }

    private fun mergeWarmCacheAfterAttachments(postId: Int, list: List<AttachmentItem>) {
        val prev = warmCache[postId]
        val keepForm = prev?.form?.takeIf { prev.isFresh(CACHE_TTL_MS) }?.deepCopyForCache()
        putWarmEntry(
                postId,
                EditPostWarmEntry(
                        form = keepForm,
                        attachments = deepCopyAttachmentList(list),
                        storedAtMillis = System.currentTimeMillis(),
                )
        )
    }

    fun uploadFiles(id: Int, files: List<RequestFile>, pending: List<AttachmentItem>): Single<List<AttachmentItem>> = Single
            .fromCallable { attachmentsApi.uploadTopicFiles(id, files, pending) }
            .runInIoToUi()

    fun deleteFiles(id: Int, items: List<AttachmentItem>): Single<List<AttachmentItem>> = Single
            .fromCallable { attachmentsApi.deleteTopicFiles(id, items) }
            .runInIoToUi()

    fun sendPost(form: EditPostForm, scrollTraceId: String? = null): Single<ThemePage> = Single
            .fromCallable { editPostApi.sendPost(form, scrollTraceId) }
            .doOnSuccess { saveUsers(it) }
            .runInIoToUi()

    private fun saveUsers(page: ThemePage) {
        val forumUsers = page.posts.map { post ->
            ForumUser().apply {
                id = post.userId
                nick = post.nick
                avatar = post.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        /**
         * Выше OkHttp 45 с × возможный ретрай: иначе Rx обрывает раньше сети и показывается «60 seconds».
         */
        private const val NETWORK_FORM_TIMEOUT_SEC = 120L
        private const val ATTACH_NETWORK_TIMEOUT_SEC = 45L
        private const val MAX_WARM_ENTRIES = 32
        private const val PREFETCH_MAX_PARALLEL = 4
    }
}

/** Снимок для мгновенного показа редактора; [attachments] может быть null, если в кэше только форма. */
data class EditPostWarmSnapshot(
        val form: EditPostForm,
        /** null = в кэше не было ответа attach init — догружаем отдельно. */
        val attachments: List<AttachmentItem>?,
)

/** Пост только из вложений (пустой message) — тоже кэшируем. */
private fun isEditFormContentPresent(form: EditPostForm): Boolean =
        form.message.isNotBlank() || form.attachments.isNotEmpty()
