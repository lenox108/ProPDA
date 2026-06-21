package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase
import forpdateam.ru.forpda.presentation.theme.ThemeUiEvent
import android.os.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Контроллер для управления бесконечной прокруткой (hybrid scroll) в теме.
 * Отвечает за загрузку соседних страниц и управление состоянием бесконечной прокрутки.
 */
class ThemeInfiniteScrollController(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val themeUseCase: ThemeUseCase,
    private val uiEvents: kotlinx.coroutines.flow.MutableSharedFlow<ThemeUiEvent>,
    private val buildCleanThemeUrl: (Int, Int) -> String,
    private val detectTopicHatPost: (ThemePage) -> ThemePost?,
    private val stripDuplicateHatFromNonFirstPage: (ThemePage, Int) -> Boolean,
    private val validateNonFirstPagePostNumbers: (ThemePage, Int) -> Boolean,
    private val promoteTopicHatForHybridPage: (ThemePage) -> Unit,
    private val getCurrentPage: () -> ThemePage?,
    private val getLoadedPages: () -> LinkedHashMap<Int, ThemePage>,
    private val setLoadedPages: (LinkedHashMap<Int, ThemePage>) -> Unit,
    private val getFirstPageHatPostId: () -> Int?,
    private val setFirstPageHatPostId: (Int?) -> Unit,
    private val getCurrentTopicScrollMode: () -> AppPreferences.Main.TopicScrollMode,
    private val getUserHatOpenOverride: () -> Boolean?
) {

    private var infiniteTopJob: Job? = null
    private var infiniteBottomJob: Job? = null
    private var infiniteSession = 0
    private val infiniteErrors = mutableSetOf<InfiniteDirection>()
    private val recentRequests = mutableMapOf<String, Long>()

    private val loadedPages: LinkedHashMap<Int, ThemePage>
        get() = getLoadedPages()

    enum class InfiniteDirection(val jsName: String) {
        TOP("top"),
        BOTTOM("bottom");

        companion object {
            fun from(value: String): InfiniteDirection? = values().firstOrNull {
                it.jsName.equals(value, ignoreCase = true)
            }
        }
    }

    enum class InfiniteState(val jsName: String) {
        IDLE("idle"),
        LOADING("loading"),
        ERROR("error")
    }

    /**
     * Отменяет все активные задачи бесконечной прокрутки и увеличивает сессию.
     */
    fun cancelAll() {
        infiniteTopJob?.cancel()
        infiniteBottomJob?.cancel()
        infiniteSession++
        infiniteErrors.clear()
        recentRequests.clear()
    }

    /**
     * Запрашивает загрузку соседней страницы в указанном направлении.
     */
    fun requestInfinitePage(direction: String) {
        val dir = InfiniteDirection.from(direction) ?: return
        val currentTopicScrollMode = getCurrentTopicScrollMode()
        if (currentTopicScrollMode != AppPreferences.Main.TopicScrollMode.HYBRID) {
            Timber.i("HybridScroll: reject $direction: mode=$currentTopicScrollMode")
            uiEvents.tryEmit(ThemeUiEvent.SetInfiniteState(dir.jsName, InfiniteState.IDLE.jsName, null))
            return
        }
        val page = getCurrentPage() ?: run {
            Timber.w("HybridScroll: reject $direction: no current page")
            return
        }
        if (page.id <= 0) {
            Timber.w("HybridScroll: reject $direction: invalid topic")
            return
        }
        val targetPage = when (dir) {
            InfiniteDirection.TOP -> (loadedPages.keys.minOrNull() ?: page.pagination.current) - 1
            InfiniteDirection.BOTTOM -> (loadedPages.keys.maxOrNull() ?: page.pagination.current) + 1
        }
        if (targetPage < 1 || targetPage > page.pagination.all) {
            Timber.i(
                "HybridScroll",
                "reject ${dir.jsName}: target=$targetPage bounds=1..${page.pagination.all} loaded=${loadedPages.keys.joinToString()} current=${page.pagination.current}"
            )
            uiEvents.tryEmit(ThemeUiEvent.SetInfiniteState(dir.jsName, InfiniteState.IDLE.jsName, null))
            return
        }
        if (loadedPages.containsKey(targetPage)) {
            Timber.i("HybridScroll: reject ${dir.jsName}: target=$targetPage already loaded")
            return
        }
        val activeJob = if (dir == InfiniteDirection.TOP) infiniteTopJob else infiniteBottomJob
        if (activeJob?.isActive == true) {
            Timber.i("HybridScroll: reject ${dir.jsName}: request already active")
            return
        }

        val session = infiniteSession
        val topicId = page.id
        val perPage = page.pagination.perPage.coerceAtLeast(1)
        val url = buildCleanThemeUrl(topicId, (targetPage - 1) * perPage)
        if (!markRequestAllowed(dir, topicId, targetPage, url)) {
            return
        }
        infiniteErrors.remove(dir)
        Timber.i(
            "HybridScroll",
            "load ${dir.jsName}: topic=$topicId current=${page.pagination.current} target=$targetPage all=${page.pagination.all} perPage=$perPage url=$url"
        )
        uiEvents.tryEmit(ThemeUiEvent.SetInfiniteState(dir.jsName, InfiniteState.LOADING.jsName, null))
        val job = scope.launch {
            val hatOpen = getUserHatOpenOverride() ?: false
            when (val result = themeUseCase.loadTheme(url, hatOpen, page.isPollOpen)) {
                is ThemeUseCase.LoadResult.Success -> {
                    val loaded = result.page
                    if (session != infiniteSession || loaded.id != topicId) return@launch
                    val loadedNumber = loaded.pagination.current
                    Timber.i(
                        "HybridScroll",
                        "success ${dir.jsName}: requested=$targetPage loaded=$loadedNumber all=${loaded.pagination.all} posts=${loaded.posts.size}"
                    )
                    if (loadedNumber != targetPage) {
                        Timber.w(
                            "Hybrid page mismatch: requested=%d loaded=%d url=%s",
                            targetPage,
                            loadedNumber,
                            loaded.url
                        )
                        infiniteErrors.add(dir)
                        uiEvents.tryEmit(
                            ThemeUiEvent.SetInfiniteState(
                                dir.jsName,
                                InfiniteState.ERROR.jsName,
                                "Ошибка загрузки страницы. Нажмите, чтобы повторить."
                            )
                        )
                        return@launch
                    }
                    if (loadedPages.containsKey(loadedNumber)) {
                        uiEvents.tryEmit(ThemeUiEvent.SetInfiniteState(dir.jsName, InfiniteState.IDLE.jsName, null))
                        return@launch
                    }
                    val loadedHat = detectTopicHatPost(loaded)
                    if (loadedHat != null) {
                        loaded.topicHatPost = loadedHat
                        val currentFirstPageHatPostId = getFirstPageHatPostId()
                        if (currentFirstPageHatPostId == null) {
                            setFirstPageHatPostId(loadedHat.id)
                        }
                    }
                    if (!stripDuplicateHatFromNonFirstPage(loaded, targetPage) || !validateNonFirstPagePostNumbers(loaded, targetPage)) {
                        Timber.w(
                            "Hybrid rejected first-page-looking content: requested=%d loaded=%d firstPostNumber=%s url=%s",
                            targetPage,
                            loadedNumber,
                            loaded.posts.firstOrNull()?.number?.toString() ?: "",
                            loaded.url
                        )
                        infiniteErrors.add(dir)
                        uiEvents.tryEmit(
                            ThemeUiEvent.SetInfiniteState(
                                dir.jsName,
                                InfiniteState.ERROR.jsName,
                                "Ошибка загрузки страницы. Нажмите, чтобы повторить."
                            )
                        )
                        return@launch
                    }
                    promoteTopicHatForHybridPage(loaded)
                    val newLoadedPages = LinkedHashMap(loadedPages)
                    newLoadedPages[loadedNumber] = loaded
                    val ordered = newLoadedPages.toSortedMap()
                    newLoadedPages.clear()
                    newLoadedPages.putAll(ordered)
                    setLoadedPages(newLoadedPages)
                    themeUseCase.onNeighborPageLoaded(loaded)
                    val fragmentHtml = themeUseCase.mapPostsFragment(loaded)
                    Timber.i("HybridScroll: insert ${dir.jsName}: page=$loadedNumber posts=${loaded.posts.size} htmlLen=${fragmentHtml.length}")
                    uiEvents.tryEmit(
                        ThemeUiEvent.ApplyInfinitePage(
                            direction = dir.jsName,
                            pageNumber = loadedNumber,
                            html = fragmentHtml
                        )
                    )
                    uiEvents.tryEmit(ThemeUiEvent.SetInfiniteState(dir.jsName, InfiniteState.IDLE.jsName, null))
                    scope.launch {
                        themeUseCase.syncFavoriteLastPost(loaded)
                    }
                    if (dir == InfiniteDirection.BOTTOM) {
                        maybeSmartPreloadAfter(loaded, loadedNumber, session)
                    }
                }
                is ThemeUseCase.LoadResult.Error -> {
                    if (session != infiniteSession) return@launch
                    Timber.w("HybridScroll: failure ${dir.jsName}: error=${result.throwable.javaClass.simpleName}")
                    infiniteErrors.add(dir)
                    uiEvents.tryEmit(
                        ThemeUiEvent.SetInfiniteState(
                            dir.jsName,
                            InfiniteState.ERROR.jsName,
                            "Ошибка загрузки страницы. Нажмите, чтобы повторить."
                        )
                    )
                }
            }
        }
        if (dir == InfiniteDirection.TOP) {
            infiniteTopJob = job
        } else {
            infiniteBottomJob = job
        }
    }

    /**
     * Smart Preload (Phase 8, behind kill switch — default OFF): after a BOTTOM page is appended via
     * hybrid scroll, warm exactly ONE page ahead into the shared ThemePageMemoryCache so the next
     * natural bottom-reach is instant. Fully best-effort: any failure is swallowed by the use case,
     * and the kill switch / Slow WebView Mode gating lives in ThemeSmartPreloadPolicy.
     */
    private fun maybeSmartPreloadAfter(loaded: ThemePage, loadedNumber: Int, session: Int) {
        val topicId = loaded.id
        if (topicId <= 0) return
        val totalPages = loaded.pagination.all
        val perPage = loaded.pagination.perPage.coerceAtLeast(1)
        val nextPage = loadedNumber + 1
        val nextAlreadyAvailable = loadedPages[nextPage]?.id == topicId
        val hatOpen = getUserHatOpenOverride() ?: false
        scope.launch {
            if (session != infiniteSession) return@launch
            runCatching {
                themeUseCase.preloadNextPageIfAllowed(
                    topicId = topicId,
                    currentPage = loadedNumber,
                    totalPages = totalPages,
                    perPage = perPage,
                    // A bottom infinite-scroll insert means the user crossed the scroll trigger, so the
                    // threshold is satisfied by construction; the policy still enforces all other gates.
                    scrollFraction = ThemeSmartPreloadPolicy.DEFAULT_PRELOAD_THRESHOLD,
                    isRefreshing = false,
                    isTopicOpening = false,
                    hatOpen = hatOpen,
                    pollOpen = loaded.isPollOpen,
                    nextPageAlreadyAvailable = nextAlreadyAvailable,
                )
            }
        }
    }

    /**
     * Повторяет загрузку страницы в указанном направлении, если предыдущая попытка завершилась ошибкой.
     */
    fun retryInfinitePage(direction: String) {
        val dir = InfiniteDirection.from(direction) ?: return
        if (!infiniteErrors.contains(dir)) return
        recentRequests.keys.removeAll { it.startsWith("${dir.jsName}|") }
        requestInfinitePage(dir.jsName)
    }

    private fun markRequestAllowed(
        dir: InfiniteDirection,
        topicId: Int,
        targetPage: Int,
        url: String
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        val key = "${dir.jsName}|$topicId|$targetPage|$url"
        recentRequests.entries.removeAll { now - it.value > DUPLICATE_REQUEST_WINDOW_MS }
        val last = recentRequests[key]
        if (last != null && now - last < DUPLICATE_REQUEST_WINDOW_MS) {
            Timber.i("HybridScroll: reject ${dir.jsName}: duplicate target=$targetPage url=$url")
            return false
        }
        recentRequests[key] = now
        return true
    }

    companion object {
        private const val DUPLICATE_REQUEST_WINDOW_MS = 1500L
    }
}
