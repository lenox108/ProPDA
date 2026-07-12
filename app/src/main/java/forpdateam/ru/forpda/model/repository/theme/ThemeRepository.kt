package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.cache.history.HistoryCacheRoom
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi.Companion.extractTopicIdFromUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 15.03.18.
 */

class ThemeRepository(
        private val themeApi: ThemeApi,
        private val historyCache: HistoryCacheRoom,
        private val forumUsersCache: ForumUsersCacheRoom,
        private val pageMemoryCache: ThemePageMemoryCache = ThemePageMemoryCache()
) {

    /**
     * Optional supplier of the current global theme/render signature (theme type, density, font,
     * avatar mode, blacklist, etc.). When set, the in-memory page cache is dropped whenever the
     * signature changes, so a settings change never serves a stale-styled page (Phase 6B).
     */
    @Volatile
    var renderSignatureProvider: (() -> String?)? = null

    suspend fun getTheme(
            url: String,
            _withHtml: Boolean,
            hatOpen: Boolean,
            pollOpen: Boolean,
            openFromUnreadListHint: Boolean = false
    ): ThemePage = withContext(Dispatchers.IO) {
        val topicId = extractTopicIdFromUrl(url)
        val currentSignature = renderSignatureProvider?.invoke()
        pageMemoryCache.invalidateOnSignatureChange(currentSignature)
        val cacheKey = if (!ThemePageMemoryCache.shouldSkipCache(url)) {
            pageMemoryCache.keyFrom(url, hatOpen, pollOpen)
        } else {
            null
        }
        cacheKey?.let { key ->
            pageMemoryCache.get(key, expectedSignature = currentSignature)?.let { cached ->
                FpdaDebugLog.logTheme(
                        FpdaDebugLog.ThemeArea.LOAD,
                        "cache_hit",
                        mapOf(
                                "topicId" to topicId,
                                "url" to FpdaDebugLog.sanitizeUrl(url),
                                "posts" to cached.posts.size,
                                "htmlLen" to cached.html?.length,
                                "page" to cached.pagination.current,
                                "hatOpen" to hatOpen,
                                "pollOpen" to pollOpen
                        )
                )
                // История посещений должна пополняться и на попадании в кэш страниц: с нативным
                // рендером/префетчем большинство открытий тем — это cache hit, и без этой записи
                // вкладка «История» не запоминает свежие переходы (не добавляет/не поднимает тему).
                val cachedTopicId = if (cached.id > 0) cached.id else topicId ?: 0
                if (cachedTopicId > 0) {
                    historyCache.add(cachedTopicId, cached.url ?: url, cached.title)
                }
                return@withContext cached
            }
        }
        FpdaDebugLog.logTheme(
                FpdaDebugLog.ThemeArea.LOAD,
                "network_fetch",
                mapOf(
                        "topicId" to topicId,
                        "url" to FpdaDebugLog.sanitizeUrl(url),
                        "cacheSkipped" to (cacheKey == null),
                        "hatOpen" to hatOpen,
                        "pollOpen" to pollOpen
                )
        )
        themeApi.getTheme(url, hatOpen, pollOpen, openFromUnreadListHint).also { page ->
            FpdaDebugLog.logTheme(
                    FpdaDebugLog.ThemeArea.LOAD,
                    "network_ok",
                    mapOf(
                            "topicId" to if (page.id > 0) page.id else topicId,
                            "url" to FpdaDebugLog.sanitizeUrl(page.url ?: url),
                            "posts" to page.posts.size,
                            "htmlLen" to page.html?.length,
                            "page" to page.pagination.current,
                            "allPages" to page.pagination.all
                    )
            )
            saveUsersBestEffort(page)
            val resolvedTopicId = if (page.id > 0) page.id else extractTopicIdFromUrl(page.url ?: url) ?: 0
            if (resolvedTopicId > 0) {
                historyCache.add(resolvedTopicId, page.url ?: url, page.title)
            }
            cacheKey?.let { key -> pageMemoryCache.put(key, page) }
        }
    }

    fun invalidateTopicPageCache(topicId: Int) {
        pageMemoryCache.invalidateTopic(topicId)
    }

    /**
     * Best-effort Smart Preload of one topic page (Phase 8). Fetches [url] through the normal
     * [getTheme] path so the result lands in [ThemePageMemoryCache] (the single shared store — no
     * parallel preload cache). Returns the resolved page on success so the caller can verify it
     * against the still-current topic before relying on it; returns null on any failure (the caller
     * treats this as a preload miss and never surfaces an error to the user).
     *
     * The kill switch and all "should I preload" gating live in [ThemeSmartPreloadPolicy]; this
     * method assumes the decision has already been made and only performs the fetch.
     */
    suspend fun preloadTheme(
            url: String,
            hatOpen: Boolean,
            pollOpen: Boolean,
    ): ThemePage? = withContext(Dispatchers.IO) {
        if (ThemePageMemoryCache.shouldSkipCache(url)) return@withContext null
        runCatching {
            FpdaDebugLog.logTheme(
                    FpdaDebugLog.ThemeArea.LOAD,
                    "smart_preload_started",
                    mapOf(
                            "url" to FpdaDebugLog.sanitizeUrl(url),
                            "hatOpen" to hatOpen,
                            "pollOpen" to pollOpen,
                    ),
            )
            getTheme(url, _withHtml = true, hatOpen = hatOpen, pollOpen = pollOpen)
        }.onFailure { error ->
            FpdaDebugLog.logTheme(
                    FpdaDebugLog.ThemeArea.LOAD,
                    "smart_preload_miss",
                    mapOf(
                            "url" to FpdaDebugLog.sanitizeUrl(url),
                            "error" to FpdaDebugLog.errorClass(error),
                    ),
            )
        }.getOrNull()
    }

    suspend fun reportPost(themeId: Int, postId: Int, message: String): Boolean = withContext(Dispatchers.IO) {
        themeApi.reportPost(themeId, postId, message)
    }

    suspend fun deletePost(postId: Int): Boolean = withContext(Dispatchers.IO) {
        themeApi.deletePost(postId)
    }

    suspend fun votePost(postId: Int, type: Boolean): String = withContext(Dispatchers.IO) {
        themeApi.votePost(postId, type)
    }

    suspend fun enrichPageMetadata(page: ThemePage) = withContext(Dispatchers.IO) {
        val finalUrl = page.url?.takeIf { it.isNotBlank() } ?: return@withContext
        themeApi.enrichPageMetadata(page, finalUrl)
    }

    suspend fun submitPoll(action: String, method: String, encodedForm: String): ThemePage = withContext(Dispatchers.IO) {
        themeApi.submitPoll(action, method, encodedForm).also {
            saveUsersBestEffort(it)
            val topicId = if (it.id > 0) it.id else extractTopicIdFromUrl(it.url ?: action) ?: 0
            if (topicId > 0) {
                historyCache.add(topicId, it.url ?: action, it.title)
            }
        }
    }

    private suspend fun saveUsersBestEffort(page: ThemePage) {
        val forumUsers = page.posts.mapNotNull { post ->
            if (post.userId <= 0) return@mapNotNull null
            ForumUser().apply {
                id = post.userId
                nick = post.nick
                avatar = post.avatar
            }
        }
        if (forumUsers.isEmpty()) return
        runCatching { forumUsersCache.saveUsers(forumUsers) }
    }
}
