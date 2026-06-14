package forpdateam.ru.forpda.model.repository.posteditor

import android.content.Context
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by radiationx on 01.01.18.
 */
class PostEditorRepository(
        private val context: Context,
        private val editPostApi: EditPostApi,
        private val attachmentsApi: AttachmentsApi,
        private val forumUsersCache: ForumUsersCacheRoom
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val warmCache = ConcurrentHashMap<Int, EditPostWarmEntry>()
    private val diskCache = EditPostDiskCache(context)
    /** Один активный Deferred на postId — префетч и открытие редактора не дублируют TCP. */
    private val inflightForm = ConcurrentHashMap<Int, Deferred<EditPostForm>>()
    private val inflightAttach = ConcurrentHashMap<Int, Deferred<List<AttachmentItem>>>()
    /** [bumpEditPrefetchGeneration] при новой загрузке темы; префетч сравнивает снимок с [get]. */
    private val prefetchGeneration = AtomicInteger(0)

    init {
        // Подтягиваем сохранённый кэш с диска при старте — формы будут доступны даже после
        // перезапуска приложения.
        scope.launch {
            val loaded = diskCache.loadAll()
            for ((postId, entry) in loaded) {
                // Не перетираем свежие in-memory записи, если они успели появиться.
                warmCache.putIfAbsent(postId, entry)
            }
        }
    }

    fun bumpEditPrefetchGeneration() {
        prefetchGeneration.incrementAndGet()
    }

    fun invalidateEditCache(postId: Int) {
        if (postId > 0) {
            warmCache.remove(postId)
            diskCache.remove(postId)
        }
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
     * Загружаем параллельно — форма и attach независимы, суммарная задержка = max(form, attach)
     * вместо form + attach.
     */
    fun kickWarmNetworkLoad(postId: Int) {
        if (postId <= 0) return
        scope.launch {
            val form = async { runCatching { loadFormBody(postId) } }
            val att = async { runCatching { loadAttachBody(postId) } }
            form.await()
            att.await()
        }
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
            scope.launch {
                sem.acquire()
                try {
                    prefetchEditOnePost(postId, gen)
                } finally {
                    sem.release()
                }
            }
        }
    }

    private suspend fun prefetchEditOnePost(postId: Int, gen: Int) {
        if (prefetchGeneration.get() != gen) return
        try {
            val cached = warmCache[postId]
            if (cached != null && cached.form != null && cached.attachments != null && cached.isFresh(CACHE_TTL_MS)) {
                return
            }
            if (prefetchGeneration.get() != gen) return
            val form = loadFormBody(postId)
            if (form.errorCode != EditPostForm.ERROR_NONE) return
            if (!isEditFormContentPresent(form)) return
            if (prefetchGeneration.get() != gen) return
            // Если форма уже содержит вложения — attach init обычно пустой, не тратим запрос.
            val att = if (form.attachments.isNotEmpty()) {
                emptyList()
            } else {
                runCatching { loadAttachBody(postId) }.getOrNull() ?: return
            }
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
        diskCache.put(postId, entry)
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
     * Загрузка формы редактирования: кеш → сеть (с дедупликацией inflight).
     */
    suspend fun loadForm(postId: Int): EditPostForm = withContext(Dispatchers.IO) {
        loadFormBody(postId)
    }

    private suspend fun loadFormBody(postId: Int): EditPostForm {
        val hit = warmCache[postId]
        val cachedForm = hit?.form
        if (cachedForm != null && hit.isFresh(CACHE_TTL_MS) && isEditFormContentPresent(cachedForm)) {
            return cachedForm.deepCopyForCache()
        }
        return sharedNetworkLoadForm(postId)
    }

    private suspend fun sharedNetworkLoadForm(postId: Int): EditPostForm {
        val deferred = inflightForm.computeIfAbsent(postId) {
            scope.async {
                try {
                    val form = suspendRetry(maxAttempts = 1, initialDelayMs = 500L) {
                        withTimeoutOrNull(NETWORK_FORM_TIMEOUT_SEC * 1000) {
                            withContext(Dispatchers.IO) { editPostApi.loadForm(postId) }
                        } ?: throw TimeoutException("loadForm timeout ${NETWORK_FORM_TIMEOUT_SEC}s")
                    }
                    if (form.errorCode == EditPostForm.ERROR_NONE && isEditFormContentPresent(form)) {
                        mergeWarmCacheAfterForm(postId, form)
                    }
                    form
                } finally {
                    inflightForm.remove(postId)
                }
            }
        }
        return deferred.await()
    }

    suspend fun loadEditAttachments(postId: Int): List<AttachmentItem> = withContext(Dispatchers.IO) {
        loadAttachBody(postId)
    }

    private suspend fun loadAttachBody(postId: Int): List<AttachmentItem> {
        val hit = warmCache[postId]
        if (hit != null && hit.attachments != null && hit.isFresh(CACHE_TTL_MS)) {
            return deepCopyAttachmentList(hit.attachments).toMutableList()
        }
        return sharedNetworkLoadAttach(postId)
    }

    private suspend fun sharedNetworkLoadAttach(postId: Int): List<AttachmentItem> {
        val deferred = inflightAttach.computeIfAbsent(postId) {
            scope.async {
                try {
                    val list = suspendRetry(maxAttempts = 1, initialDelayMs = 400L) {
                        withTimeoutOrNull(ATTACH_NETWORK_TIMEOUT_SEC * 1000) {
                            withContext(Dispatchers.IO) { editPostApi.loadEditAttachments(postId) }
                        } ?: throw TimeoutException("loadAttach timeout ${ATTACH_NETWORK_TIMEOUT_SEC}s")
                    }
                    mergeWarmCacheAfterAttachments(postId, list)
                    list
                } catch (_: Exception) {
                    emptyList<AttachmentItem>()
                } finally {
                    inflightAttach.remove(postId)
                }
            }
        }
        return deferred.await()
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

    suspend fun uploadFiles(id: Int, files: List<RequestFile>, pending: List<AttachmentItem>): List<AttachmentItem> = withContext(Dispatchers.IO) {
        attachmentsApi.uploadTopicFiles(id, files, pending)
    }

    suspend fun deleteFiles(id: Int, items: List<AttachmentItem>): List<AttachmentItem> = withContext(Dispatchers.IO) {
        attachmentsApi.deleteTopicFiles(id, items)
    }

    suspend fun sendPost(form: EditPostForm, scrollTraceId: String? = null): ThemePage = withContext(Dispatchers.IO) {
        val page = editPostApi.sendPost(form, scrollTraceId)
        saveUsers(page)
        page
    }

    private suspend fun saveUsers(page: ThemePage) {
        val forumUsers = page.posts.map { post ->
            ForumUser().apply {
                id = post.userId
                nick = post.nick
                avatar = post.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

    private suspend fun <T> suspendRetry(
            maxAttempts: Int = 3,
            initialDelayMs: Long = 500,
            block: suspend () -> T
    ): T {
        var lastException: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                val transient = e is IOException || e is SocketTimeoutException || e is TimeoutException
                lastException = e
                if (!transient || attempt >= maxAttempts - 1) throw e
                delay(initialDelayMs * (1L shl attempt))
            }
        }
        throw lastException ?: IOException("Unknown error")
    }

    companion object {
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
        private const val NETWORK_FORM_TIMEOUT_SEC = 120L
        private const val ATTACH_NETWORK_TIMEOUT_SEC = 45L
        private const val MAX_WARM_ENTRIES = 32
        private const val PREFETCH_MAX_PARALLEL = 8
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
