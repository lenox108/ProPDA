package forpdateam.ru.forpda.model.interactors.theme

import android.os.Looper
import android.os.SystemClock
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.di.AppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.presentation.theme.ThemeBackRestoreUrlPolicy
import forpdateam.ru.forpda.presentation.theme.ThemeSmartPreloadPolicy
import forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import javax.inject.Inject
import timber.log.Timber

/**
 * Инкапсулирует загрузку и шаблонизацию темы:
 * - Загрузка страницы из сети
 * - QMS-страницы (без шаблонизации)
 * - Применение ThemeTemplate (mapEntity)
 * - Прогрев редактора для своих постов
 * - Уведомление CrossScreenInteractor
 * - Обёртка QMS HTML
 *
 * Вынесен из ThemeViewModel для снижения числа зависимостей (SRP).
 */
class ThemeUseCase @Inject constructor(
        private val themeRepository: ThemeRepository,
        private val themeTemplate: ThemeTemplate,
        private val templateManager: TemplateManager,
        private val authHolder: AuthHolder,
        private val crossScreenInteractor: CrossScreenInteractor,
        private val webClient: IWebClient,
        private val editorUseCase: ThemeEditorUseCase,
        private val errorHandler: IErrorHandler,
        private val eventsRepository: EventsRepository,
        private val favoritesRepository: FavoritesRepository,
        private val returnPositionStore: forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore,
        private val userHolder: IUserHolder,
        private val topicPreferencesHolder: TopicPreferencesHolder,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val prefetchService: ThemePrefetchService? = null,
        @AppScope private val appScope: CoroutineScope,
) {

    /**
     * Re-arm window for the server mark-read GET. After this many milliseconds, the next
     * natural-bottom exit for the same topic will re-fire GET view=getlastpost, ensuring the
     * server's last_read_ts catches up with the user's latest read position even across
     * multiple visits in the same process.
     */
    private val SERVER_MARK_READ_DEDUP_TTL_MS: Long = 5 * 60_000L

    sealed class LoadResult {
        data class Success(val page: ThemePage, val isQms: Boolean = false) : LoadResult()
        data class Error(val throwable: Throwable, val userMessage: String = userMessageFor(throwable)) : LoadResult()
    }

    data class PrefetchInfo(
            val myId: Int,
            val prefetchIds: List<Int>
    )

    /**
     * Загрузка обычной темы форума.
     */
    suspend fun loadTheme(
            url: String,
            hatOpen: Boolean,
            pollOpen: Boolean,
            openFromUnreadListHint: Boolean = false
    ): LoadResult {
        return try {
            val startedAt = SystemClock.uptimeMillis()
            val topicId = forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi.extractTopicIdFromUrl(url) ?: 0
            if (topicId > 0) {
                prefetchService?.awaitWarm(topicId, url, hatOpen, pollOpen, openFromUnreadListHint)
                prefetchService?.tryConsumeWarm(url, hatOpen, pollOpen, openFromUnreadListHint)?.let { warmed ->
                    if (TopicUnreadOpenPolicy.isStaleWarmGetNewPostPage(
                                    warmed,
                                    url,
                                    openFromUnreadListHint,
                            )
                    ) {
                        if (BuildConfig.DEBUG) {
                            Timber.tag("ThemeLoadTiming").d(
                                    "loadTheme prefetch_stale_rejected url=%s topicId=%d",
                                    url,
                                    warmed.id,
                            )
                        }
                        null
                    } else {
                        warmed
                    }
                }?.let { warmed ->
                    warmed.isInlineHatOpen = topicPreferencesHolder.getInlineHatOpened(warmed.id)
                    if (BuildConfig.DEBUG) {
                        Timber.tag("ThemeLoadTiming").d(
                                "loadTheme prefetch_hit url=%s posts=%d",
                                url,
                                warmed.posts.size
                        )
                    }
                    return LoadResult.Success(warmed)
                }
            }
            val page = themeRepository.getTheme(url, true, hatOpen, pollOpen, openFromUnreadListHint)
            val repositoryAt = SystemClock.uptimeMillis()
            page.isInlineHatOpen = topicPreferencesHolder.getInlineHatOpened(page.id)
            if (BuildConfig.DEBUG) {
                Timber.tag("ThemeLoadTiming").d(
                        "loadTheme url=%s repositoryMs=%d posts=%d mainThread=%s",
                        url,
                        repositoryAt - startedAt,
                        page.posts.size,
                        Looper.myLooper() == Looper.getMainLooper()
                )
            }
            LoadResult.Success(page)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            errorHandler.handle(e)
            LoadResult.Error(e)
        }
    }

    suspend fun enrichPageMetadata(page: ThemePage): Boolean = withContext(Dispatchers.IO) {
        val before = pageMetadataSnapshot(page)
        themeRepository.enrichPageMetadata(page)
        pageMetadataSnapshot(page) != before
    }

    private fun pageMetadataSnapshot(page: ThemePage): String =
            page.posts.joinToString(separator = ";") { post ->
                "${post.id}:${post.userPostCount}:${post.postRating}:${post.canPlusPostRating}:${post.canMinusPostRating}"
            }

    /**
     * Загрузка QMS-страницы (без шаблонизации темы).
     */
    suspend fun loadQms(url: String): LoadResult {
        return try {
            val response = webClient.get(url)
            val htmlContent = response.body
            val wrappedHtml = wrapQmsHtml(htmlContent, url)
            val page = ThemePage().apply {
                this.url = response.redirect ?: url
                this.html = wrappedHtml
                this.title = "QMS"
                this.id = -1
            }
            LoadResult.Success(page, isQms = true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            errorHandler.handle(e)
            LoadResult.Error(e)
        }
    }

    /**
     * Применить шаблонизацию к странице (для posted page).
     */
    fun expectedListPostCount(page: ThemePage): Int = themeTemplate.expectedListPostCount(page)

    fun extractTopHatOverlayHostHtml(html: String?): String? =
            themeTemplate.extractTopHatOverlayHostHtml(html)

    suspend fun mapEntity(page: ThemePage, reason: String = "theme"): ThemePage = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.uptimeMillis()
        val mapped = themeTemplate.mapEntity(page)
        mapped.postsFragmentHtml = themeTemplate.mapPostsFragment(page)
        if (BuildConfig.DEBUG) {
            Timber.tag("ThemeLoadTiming").d(
                    "mapEntity reason=%s ms=%d posts=%d html=%d fragment=%d mainThread=%s",
                    reason,
                    SystemClock.uptimeMillis() - startedAt,
                    page.posts.size,
                    mapped.html?.length ?: 0,
                    mapped.postsFragmentHtml?.length ?: 0,
                    Looper.myLooper() == Looper.getMainLooper()
            )
        }
        mapped
    }

    suspend fun mapHybridPages(basePage: ThemePage, pages: Collection<ThemePage>, reason: String = "hybridRefresh"): ThemePage = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.uptimeMillis()
        pages.forEach { page ->
            page.postsFragmentHtml = themeTemplate.mapPostsFragment(page)
        }
        val mapped = themeTemplate.mapHybridPages(basePage, pages)
        if (BuildConfig.DEBUG) {
            Timber.tag("ThemeLoadTiming").d(
                    "mapHybridPages reason=%s ms=%d pages=%s html=%d mainThread=%s",
                    reason,
                    SystemClock.uptimeMillis() - startedAt,
                    pages.map { it.pagination.current },
                    mapped.html?.length ?: 0,
                    Looper.myLooper() == Looper.getMainLooper()
            )
        }
        mapped
    }

    suspend fun mapPostsFragment(page: ThemePage): String = withContext(Dispatchers.Default) {
        val startedAt = SystemClock.uptimeMillis()
        val html = themeTemplate.mapPostsFragment(page)
        if (BuildConfig.DEBUG) {
            Timber.tag("ThemeLoadTiming").d(
                    "mapPostsFragment ms=%d posts=%d html=%d mainThread=%s",
                    SystemClock.uptimeMillis() - startedAt,
                    page.posts.size,
                    html.length,
                    Looper.myLooper() == Looper.getMainLooper()
            )
        }
        html
    }

    /**
     * Primary topic open (initial load / refresh) — may mark topic read on last page.
     * Editor prefetch is deferred to post-open enrichment ([prefetchEditorForOpenedPage]).
     */
    fun onPrimaryThemeLoaded(page: ThemePage) {
        // Topic is genuinely unread again -> lift any READ seal so the in-progress position can be
        // recorded for tab re-entry. A still-read re-open keeps the seal (server getlastpost wins).
        if (page.id > 0 && page.hasUnreadTarget) {
            returnPositionStore.clearReadSeal(page.id)
        }
        if (page.pagination.current >= page.pagination.all &&
                !TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.parseOpenSessionKind(page.openSessionKind),
                        page,
                )
        ) {
            markTopicRead(page.id, "theme_last_page_loaded", "theme_use_case")
        }
    }

    /** Post-open enrich: editor prefetch only — no mark-read side effects. */
    fun prefetchEditorForOpenedPage(page: ThemePage): PrefetchInfo? = prefetchEditorForPage(page)

    /** Neighbor HYBRID infinite-scroll page — editor prefetch only, no mark-read side effects. */
    fun onNeighborPageLoaded(page: ThemePage): PrefetchInfo? = prefetchEditorForPage(page)

    /** @see onPrimaryThemeLoaded */
    fun onThemeLoaded(page: ThemePage) = onPrimaryThemeLoaded(page)

    private fun prefetchEditorForPage(page: ThemePage): PrefetchInfo? {
        if (!authHolder.get().isAuth()) return null

        val myId = authHolder.get().userId
        val editable = page.posts.filter { it.canEdit && it.id > 0 }
        val mine = editable.filter { it.userId == myId }.map { it.id }
        val others = editable.filter { it.userId != myId }.map { it.id }.take(8)
        val prefetchIds = (mine + others).distinct()
        if (prefetchIds.isNotEmpty()) {
            editorUseCase.prefetchEditForPosts(prefetchIds)
        }
        return PrefetchInfo(myId, prefetchIds)
    }

    /**
     * Per-process tracking of topic ids for which we already fired the server mark-read this run.
     * De-dup is *bounded* by [SERVER_MARK_READ_DEDUP_TTL_MS]: when a user revisits the same topic
     * after that window (or after the favorites list re-loads), the mark-read GET is re-sent so
     * that 4PDA's last_read_ts tracks the user's latest read state. Without the TTL the second
     * visit of a topic in the same process would silently no-op.
     */
    private val serverMarkReadSentTopics: MutableMap<Int, Long> = HashMap()

    fun markTopicRead(topicId: Int, reason: String, source: String) {
        if (topicId <= 0) return
        ThemePostReadStateDiagnostics.markReadTriggered(
                topicId = topicId,
                reason = reason,
                source = source
        )
        // Seal the cross-tab return position: a finished topic must re-open at the server last-read
        // bookmark, not at a drifted-up viewport snapshot saved while re-reading (log 25_06-10-52-43,
        // "stuck on stale already-read post"). This is the single chokepoint covering every mark-read
        // reason (last_page_loaded / last_page_bottom_reached / after_end).
        returnPositionStore.markRead(topicId)
        crossScreenInteractor.onLoadTopic(topicId)
        // Тема открыта напрямую из приложения и дошли до последней страницы —
        // снимаем «висящие» уведомления из шторки для этой темы.
        eventsRepository.onTopicRead(topicId)
        // Fire-and-forget server mark-read so 4PDA inspector stops returning unread for this topic.
        tryFireServerMarkRead(topicId, source)
    }

    /**
     * Re-arm server mark-read for a topic (e.g. when the favorites list reloads and Inspector
     * may need a refresh of last_read_ts). Called from [FavoritesViewModel] on each favorites
     * load so topics that the user opens in a previous visit don't get stuck without a server
     * mark-read for the duration of the process lifetime.
     */
    fun resetServerMarkReadDedup(topicId: Int) {
        if (topicId <= 0) return
        synchronized(serverMarkReadSentTopics) { serverMarkReadSentTopics.remove(topicId) }
    }

    /** Clear the entire server mark-read de-dup cache (e.g. on favorites refresh). */
    fun resetAllServerMarkReadDedup() {
        synchronized(serverMarkReadSentTopics) { serverMarkReadSentTopics.clear() }
    }

    private fun tryFireServerMarkRead(topicId: Int, source: String) {
        if (!authHolder.get().isAuth()) return
        val now = System.currentTimeMillis()
        synchronized(serverMarkReadSentTopics) {
            val last = serverMarkReadSentTopics[topicId]
            if (last != null && (now - last) < SERVER_MARK_READ_DEDUP_TTL_MS) return
            serverMarkReadSentTopics[topicId] = now
        }
        appScope.launch(Dispatchers.IO) {
            val sent = runCatching {
                favoritesRepository.markFavoriteTopicRead(topicId)
            }.onFailure { errorHandler.handle(it) }
                    .getOrDefault(false)
            ThemePostReadStateDiagnostics.markReadServerSent(
                    topicId = topicId,
                    sent = sent,
                    source = source
            )
        }
    }

    suspend fun syncFavoriteLastPost(page: ThemePage) {
        runCatching { favoritesRepository.syncTopicLastPost(page) }
                .onFailure { errorHandler.handle(it) }
    }

    fun invalidateTopicPageCache(topicId: Int) {
        if (topicId > 0) {
            themeRepository.invalidateTopicPageCache(topicId)
        }
    }

    // --- Smart Preload of the next topic page (Phase 8). Behind kill switch, default OFF. ---

    @Volatile
    private var smartPreloadInFlight = false
    @Volatile
    private var smartPreloadTopicId = 0
    @Volatile
    private var smartPreloadFailures = 0

    /**
     * Decides (via [ThemeSmartPreloadPolicy]) whether to preload the next page of [topicId] and, if
     * so, performs a best-effort fetch into the shared [forpdateam.ru.forpda.model.repository.theme.ThemePageMemoryCache].
     * Pure-policy decision; all I/O is best-effort and never surfaces an error to the user.
     *
     * Returns true if a preload was actually started. The caller passes live scroll/refresh/topic
     * state; this method owns only the in-flight + failure-count bookkeeping.
     */
    suspend fun preloadNextPageIfAllowed(
            topicId: Int,
            currentPage: Int,
            totalPages: Int,
            perPage: Int,
            scrollFraction: Float,
            isRefreshing: Boolean,
            isTopicOpening: Boolean,
            hatOpen: Boolean,
            pollOpen: Boolean,
            nextPageAlreadyAvailable: Boolean,
    ): Boolean {
        val slowModeEnabled = runCatching { mainPreferencesHolder.getCompatibilityMode() }.getOrDefault(false)
        val featureEnabled = runCatching { mainPreferencesHolder.getSmartPreload() }.getOrDefault(false)
        if (smartPreloadTopicId != topicId) {
            // Topic switched: reset per-topic failure bookkeeping (do not preload after topic switch).
            smartPreloadFailures = 0
            smartPreloadTopicId = topicId
        }
        val input = ThemeSmartPreloadPolicy.Input(
                featureEnabled = featureEnabled,
                slowModeEnabled = slowModeEnabled,
                currentTopicId = topicId,
                currentPage = currentPage,
                totalPages = totalPages,
                scrollFraction = scrollFraction,
                isRefreshing = isRefreshing,
                isTopicOpening = isTopicOpening,
                isPreloadInFlight = smartPreloadInFlight,
                nextPageAlreadyAvailable = nextPageAlreadyAvailable,
                consecutiveFailures = smartPreloadFailures,
        )
        val nextPage = ThemeSmartPreloadPolicy.nextPageToPreload(input)
        if (!ThemeSmartPreloadPolicy.shouldStartPreload(input) || nextPage == null) {
            return false
        }
        smartPreloadInFlight = true
        try {
            val st = (nextPage - 1).coerceAtLeast(0) * perPage.coerceAtLeast(1)
            val url = ThemeBackRestoreUrlPolicy.buildCleanThemeUrl(topicId, st)
            val result = themeRepository.preloadTheme(url, hatOpen, pollOpen)
            val usable = result != null && ThemeSmartPreloadPolicy.isPreloadResultUsable(
                    requestedTopicId = topicId,
                    requestedPage = nextPage,
                    resultTopicId = result.id,
                    resultPage = result.pagination.current,
                    currentTopicId = smartPreloadTopicId,
            )
            smartPreloadFailures = if (usable) 0 else (smartPreloadFailures + 1)
            return usable
        } finally {
            smartPreloadInFlight = false
        }
    }

    suspend fun syncSubmittedFavoriteLastPost(topicId: Int, sentAtMillis: Long, page: ThemePage?) {
        val auth = authHolder.get()
        if (!auth.isAuth()) return
        runCatching {
            favoritesRepository.syncSubmittedTopicLastPost(
                    topicId = topicId,
                    currentUserId = auth.userId,
                    currentUserNick = userHolder.user?.nick,
                    sentAtMillis = sentAtMillis,
                    page = page
            )
        }.onFailure { errorHandler.handle(it) }
    }

    /**
     * Проверка авторизации.
     */
    fun isAuth(): Boolean = authHolder.get().isAuth()

    /**
     * ID текущего пользователя.
     */
    fun authUserId(): Int = authHolder.get().userId

    /**
     * Наблюдение за изменением типа темы (light/dark).
     */
    fun observeThemeTypeFlow(): Flow<String> = templateManager.observeThemeTypeFlow()

    fun observeShowAvatarsFlow(): Flow<Boolean> = topicPreferencesHolder.observeShowAvatarsFlow()

    fun observeCircleAvatarsFlow(): Flow<Boolean> = topicPreferencesHolder.observeCircleAvatarsFlow()

    fun isForumBlacklisted(userId: Int, nick: String?): Boolean =
            topicPreferencesHolder.isForumBlacklisted(userId, nick)

    fun getInlineHatOpened(topicId: Int): Boolean = topicPreferencesHolder.getInlineHatOpened(topicId)

    fun hasInlineHatPreference(topicId: Int): Boolean = topicPreferencesHolder.hasInlineHatPreference(topicId)

    suspend fun setInlineHatOpened(topicId: Int, value: Boolean) = topicPreferencesHolder.setInlineHatOpened(topicId, value)

    suspend fun addForumBlacklistedUser(user: ForumBlacklistedUser) =
            topicPreferencesHolder.addForumBlacklistedUser(user)

    suspend fun removeForumBlacklistedUser(user: ForumBlacklistedUser) =
            topicPreferencesHolder.removeForumBlacklistedUser(user)

    fun observeWebViewFontSizeFlow(): Flow<Int> = mainPreferencesHolder.observeWebViewFontSizeFlow()

    fun observeUseSystemFontFlow(): Flow<Boolean> = mainPreferencesHolder.observeUseSystemFontFlow()

    fun observeAppFontModeFlow(): Flow<forpdateam.ru.forpda.ui.AppFontMode> = mainPreferencesHolder.observeAppFontModeFlow()

    companion object {
        fun userMessageFor(throwable: Throwable): String {
            return when (throwable) {
                is SocketTimeoutException,
                is TimeoutException -> "Сервер не отвечает. Проверьте соединение и повторите попытку."
                is IOException -> throwable.message?.takeIf { it.isNotBlank() }
                        ?: "Нет подключения к интернету. Проверьте сеть и повторите попытку."
                else -> "Ошибка загрузки темы"
            }
        }
    }

    fun observeScrollButtonEnabledFlow(): Flow<Boolean> = mainPreferencesHolder.observeScrollButtonEnabledFlow()

    fun observeTopicPaginationPanelEnabledFlow(): Flow<Boolean> = mainPreferencesHolder.observeTopicPaginationPanelEnabledFlow()

    fun observeTopicScrollModeFlow(): Flow<AppPreferences.Main.TopicScrollMode> = mainPreferencesHolder.observeTopicScrollModeFlow()

    fun observeTopicPostDensityFlow(): Flow<AppPreferences.Main.TopicPostDensity> = mainPreferencesHolder.observeTopicPostDensityFlow()

    fun observeTopicToolbarBehaviorFlow(): Flow<AppPreferences.Main.TopicToolbarBehavior> = mainPreferencesHolder.observeTopicToolbarBehaviorFlow()

    fun observeTopicBackBehaviorFlow(): Flow<AppPreferences.Main.TopicBackBehavior> = mainPreferencesHolder.observeTopicBackBehaviorFlow()

    fun observeTopicOpenTargetFlow(): Flow<AppPreferences.Main.TopicOpenTarget> = mainPreferencesHolder.observeTopicOpenTargetFlow()

    fun observeTopicHeaderInitialStateFlow(): Flow<AppPreferences.Main.TopicHeaderInitialState> = mainPreferencesHolder.observeTopicHeaderInitialStateFlow()

    fun observeTopicPageSwipeEnabledFlow(): Flow<Boolean> = mainPreferencesHolder.observeTopicPageSwipeEnabledFlow()

    fun observeTopicBottomRefreshGestureEnabledFlow(): Flow<Boolean> = mainPreferencesHolder.observeTopicBottomRefreshGestureEnabledFlow()

    fun getTopicScrollMode(): AppPreferences.Main.TopicScrollMode = mainPreferencesHolder.getTopicScrollMode()

    fun getTopicPostDensity(): AppPreferences.Main.TopicPostDensity = mainPreferencesHolder.getTopicPostDensity()

    fun getTopicToolbarBehavior(): AppPreferences.Main.TopicToolbarBehavior = mainPreferencesHolder.getTopicToolbarBehavior()

    fun getTopicBackBehavior(): AppPreferences.Main.TopicBackBehavior = mainPreferencesHolder.getTopicBackBehavior()

    fun getTopicOpenTarget(): AppPreferences.Main.TopicOpenTarget = mainPreferencesHolder.getTopicOpenTarget()

    fun getTopicHeaderInitialState(): AppPreferences.Main.TopicHeaderInitialState = mainPreferencesHolder.getTopicHeaderInitialState()

    fun observeEventsTab(): Flow<TabNotification> = eventsRepository.observeEventsTab()

    fun onRenderedTopicPosts(topicId: Int, postIds: Collection<Int>) {
        eventsRepository.onTopicPostsRead(topicId, postIds)
    }

    /**
     * Обёртка QMS HTML в шаблон с CSS.
     */
    private fun wrapQmsHtml(content: String, url: String): String {
        val styleType = templateManager.getThemeType()
        val themeOverridesCss = templateManager.getThemeOverridesCss()
        return """<!doctype html>
<html>
<head>
    <title>QMS</title>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="file:///android_asset/forpda/styles/md_colors.css">
    <link rel="stylesheet" type="text/css" href="file:///android_asset/fonts/roboto/import_mono.css">
    <link rel="stylesheet" type="text/css" href="file:///android_asset/fonts/fontello/import.css">
    <link rel="stylesheet" type="text/css" href="file:///android_asset/forpda/styles/${styleType}/${styleType}_main.css">
    <link rel="stylesheet" type="text/css" href="file:///android_asset/forpda/styles/${styleType}/${styleType}_themes.css">
    ${themeOverridesCss}
    <style>
        body { padding: 16px; }
        .qms-container { max-width: 100%; overflow-x: hidden; }
    </style>
</head>
<body class="topic">
    <div class="qms-container">
        ${content}
    </div>
</body>
</html>"""
    }
}
