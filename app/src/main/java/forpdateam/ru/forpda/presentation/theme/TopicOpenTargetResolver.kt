package forpdateam.ru.forpda.presentation.theme

import android.net.Uri
import forpdateam.ru.forpda.diagnostic.ReadStateTrace
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.stripTopicLastReadPostParams
import forpdateam.ru.forpda.common.stripTopicListResumeSt
import forpdateam.ru.forpda.common.topicUrlHasNonZeroStParameter

enum class TopicUserOpenAction {
    FIRST_PAGE,
    LAST_POST,
    UNREAD
}

enum class TopicOpenTargetType {
    EXPLICIT_POST,
    EXPLICIT_PAGE,
    USER_ACTION,
    SETTING_FIRST_PAGE,
    SETTING_LAST_UNREAD,
    /** Read list row under LAST_UNREAD: server `view=getlastpost` to resume at last-read post. */
    READ_RESUME,
    /** SERVER_BOOKMARK setting: plain `view=getnewpost`, land wherever the server's own mark says. */
    SETTING_SERVER_BOOKMARK,
    SERVER_UNREAD_FALLBACK,
    SAFE_FALLBACK
}

data class TopicOpenContext(
        val rawUrl: String,
        val setting: AppPreferences.Main.TopicOpenTarget,
        val sourceScreen: String = "unknown",
        val sourceUrl: String? = null,
        val openIntentRaw: String? = null,
        val userAction: TopicUserOpenAction? = null,
        val unreadUrlFromList: String? = null,
        val unreadPostIdFromList: Int? = null,
        /** List row marked unread (+N, bold, inspector) — do not apply [list_read_no_unread_hint]. */
        val listTopicMarkedUnread: Boolean = false,
        /** Inspector-fav snapshot marked this topic unread on last merge (see [FavItem.inspectorMarkedUnread]). */
        val inspectorMarkedUnread: Boolean = false,
        val cachedLastPage: Int? = null,
        val cachedScrollPosition: Int? = null,
        /** Read favorites/topics row — open at server last-read, not page 1 or getnewpost. */
        val lastReadUrlFromList: String? = null
) {
    val topicId: Int?
        get() = ThemeUrlPolicy.parse(rawUrl)?.topicId

    val explicitPostId: Int?
        get() {
            val info = ThemeUrlPolicy.parse(rawUrl) ?: return null
            return info.postId?.takeIf {
                it > 0 && (TopicOpenTargetResolver.hasFindPostMarker(rawUrl) || rawUrl.contains("#entry", ignoreCase = true))
            }
        }

    val explicitPageSt: Int?
        get() {
            val info = ThemeUrlPolicy.parse(rawUrl) ?: return null
            return info.page?.takeIf { it > 0 }
        }
}

data class TopicOpenResolution(
        val url: String,
        val targetType: TopicOpenTargetType,
        val resolvedPageSt: Int? = null,
        val resolvedPostId: Int? = null,
        val reason: String,
        /** When true, WebView must not restore scroll/anchor from cache or previous session for this open. */
        val suppressScrollRestore: Boolean = false
)

object TopicOpenTargetResolver {

    fun resolve(context: TopicOpenContext): TopicOpenResolution {
        val url = context.rawUrl.trim()
        if (url.isEmpty()) {
            return TopicOpenResolution(url, TopicOpenTargetType.SAFE_FALLBACK, reason = "empty_url")
        }
        val info = ThemeUrlPolicy.parse(url) ?: return TopicOpenResolution(url, TopicOpenTargetType.SAFE_FALLBACK, reason = "unparsed_url")
        if (info.topicId == null) {
            if (isExplicitPostSource(context)) {
                savedPostIdForExplicitOpen(url)?.let { postId ->
                    return TopicOpenResolution(
                            url = ensureFindPostForExplicitPost(url),
                            targetType = TopicOpenTargetType.EXPLICIT_POST,
                            resolvedPostId = postId,
                            reason = "explicit_post_without_topic_id"
                    )
                }
            }
            return TopicOpenResolution(url, TopicOpenTargetType.SAFE_FALLBACK, reason = "no_topic_id")
        }

        context.explicitPostId?.let { postId ->
            return TopicOpenResolution(
                    url = url,
                    targetType = TopicOpenTargetType.EXPLICIT_POST,
                    resolvedPostId = postId,
                    reason = "explicit_post_id"
            )
        }
        if (isExplicitPost(info, url)) {
            return TopicOpenResolution(
                    url = url,
                    targetType = TopicOpenTargetType.EXPLICIT_POST,
                    resolvedPostId = info.postId,
                    reason = "explicit_post_marker"
            )
        }

        // Bookmark / mention / explicit-post navigation: a saved post link may carry only `p=`/`pid=`
        // (no `view=findpost`). Plain `p=`/`pid=` is normally a list last-read hint and gets downgraded
        // to getnewpost under LAST_UNREAD — but when the open is explicitly a post (bookmark tap, mention,
        // findpost intent) we must land on the saved post. Upgrade to `view=findpost` so the server
        // redirects to the correct page/anchor.
        if (isExplicitPostSource(context)) {
            savedPostIdForExplicitOpen(url)?.let { postId ->
                return TopicOpenResolution(
                        url = ensureFindPostForExplicitPost(url),
                        targetType = TopicOpenTargetType.EXPLICIT_POST,
                        resolvedPostId = postId,
                        reason = explicitPostSourceReason(url)
                )
            }
        }

        if (context.sourceScreen == "pagination") {
            return TopicOpenResolution(
                    url = url,
                    targetType = TopicOpenTargetType.EXPLICIT_PAGE,
                    resolvedPageSt = info.page?.takeIf { it > 0 } ?: 0,
                    reason = "pagination_explicit_page"
            )
        }
        if (shouldHonorExplicitPage(context, url, info)) {
            context.explicitPageSt?.let { st ->
                return TopicOpenResolution(
                        url = url,
                        targetType = TopicOpenTargetType.EXPLICIT_PAGE,
                        resolvedPageSt = st,
                        reason = "explicit_page_st"
                )
            }
            if (isExplicitPage(info, url)) {
                return TopicOpenResolution(
                        url = url,
                        targetType = TopicOpenTargetType.EXPLICIT_PAGE,
                        resolvedPageSt = info.page,
                        reason = "explicit_page_from_url"
                )
            }
        }
        if (hasExplicitAct(url)) {
            return TopicOpenResolution(url, TopicOpenTargetType.EXPLICIT_PAGE, reason = "explicit_act")
        }

        context.userAction?.let { action ->
            return resolveUserAction(url, action)
        }

        when (context.setting) {
            AppPreferences.Main.TopicOpenTarget.FIRST_PAGE -> {
                return TopicOpenResolution(
                        url = url,
                        targetType = TopicOpenTargetType.SETTING_FIRST_PAGE,
                        resolvedPageSt = info.page?.takeIf { it > 0 } ?: 0,
                        reason = "setting_first_page"
                )
            }
            // «Серверная закладка»: единственный источник истины — отметка прочитанного на 4PDA.
            // Всегда `view=getnewpost` и для непрочитанных, и для прочитанных строк списка: сервер сам
            // отдаст первый непрочитанный, а в дочитанной теме уведёт на свою закладку (низ). Списочные
            // хинты (`unreadUrlFromList`/`lastReadUrlFromList`) сознательно игнорируем — они несут
            // клиентское представление о прочитанности, а этот режим ему как раз не доверяет.
            AppPreferences.Main.TopicOpenTarget.SERVER_BOOKMARK -> {
                return TopicOpenResolution(
                        url = normalizeLastUnreadNavigationUrl(url),
                        targetType = TopicOpenTargetType.SETTING_SERVER_BOOKMARK,
                        suppressScrollRestore = true,
                        reason = "setting_server_bookmark"
                )
            }
            AppPreferences.Main.TopicOpenTarget.LAST_UNREAD -> {
                TopicUnreadOpenPolicy.resolveListOpen(context, info)?.let { return it }
                // Legacy list hints may still carry last-read post id — findpost&p= lands on read post, not first unread.
                if (context.unreadPostIdFromList?.takeIf { it > 0 } != null) {
                    val resolved = normalizeLastUnreadNavigationUrl(url)
                    ReadStateTrace.log(
                            event = "list_hint_ignored",
                            topicId = info.topicId,
                            postId = context.unreadPostIdFromList.toString(),
                            allowedAsNavTarget = false,
                            source = context.sourceScreen,
                            reason = "ignored_last_read_post_id_use_getnewpost"
                    )
                    return TopicOpenResolution(
                            url = resolved,
                            targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
                            suppressScrollRestore = true,
                            reason = "ignored_last_read_post_id_use_getnewpost"
                    )
                }
                val resolved = normalizeLastUnreadNavigationUrl(url)
                return TopicOpenResolution(
                        url = resolved,
                        targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
                        suppressScrollRestore = true,
                        reason = lastUnreadReason(url, resolved)
                )
            }
        }
    }

    /** @deprecated Prefer [resolve] with [TopicOpenContext]. */
    fun resolveInitialUrl(
            rawUrl: String?,
            setting: AppPreferences.Main.TopicOpenTarget
    ): String = resolve(
            TopicOpenContext(
                    rawUrl = rawUrl.orEmpty(),
                    setting = setting,
                    sourceScreen = "legacy"
            )
    ).url

    fun isOrdinaryInitialTopicUrl(rawUrl: String?): Boolean {
        val url = rawUrl?.trim().orEmpty()
        if (url.isEmpty()) return false
        val info = ThemeUrlPolicy.parse(url) ?: return false
        if (info.topicId == null) return false
        return !isExplicitPost(info, url) &&
                !isExplicitPage(info, url) &&
                !hasExplicitAct(url) &&
                !hasBlockingTopicView(url)
    }

    private fun resolveUserAction(url: String, action: TopicUserOpenAction): TopicOpenResolution =
            when (action) {
                TopicUserOpenAction.FIRST_PAGE -> TopicOpenResolution(
                        url = stripUnreadNavigationParams(url),
                        targetType = TopicOpenTargetType.USER_ACTION,
                        resolvedPageSt = 0,
                        reason = "user_action_first_page"
                )
                TopicUserOpenAction.LAST_POST -> TopicOpenResolution(
                        url = withView(url, "getlastpost"),
                        targetType = TopicOpenTargetType.USER_ACTION,
                        reason = "user_action_last_post"
                )
                TopicUserOpenAction.UNREAD -> TopicOpenResolution(
                        url = withViewGetNewPost(url),
                        targetType = TopicOpenTargetType.USER_ACTION,
                        suppressScrollRestore = true,
                        reason = "user_action_unread"
                )
            }

    /** Strip list resume `st` and last-read `p`/`pid`; keep or add view=getlastpost for read list opens. */
    fun normalizeLastReadNavigationUrl(url: String): String {
        val stripped = stripTopicListResumeSt(stripTopicLastReadPostParams(url))
        return when (topicViewParam(stripped)?.lowercase()) {
            "getlastpost" -> stripped
            "getnewpost" -> withView(stripped, "getlastpost")
            null -> appendGetLastPostParam(stripped)
            else -> withView(stripped, "getlastpost")
        }
    }

    /** Strip list resume `st`, last-read `p`/`pid`, upgrade getlastpost → getnewpost, ensure getnewpost for plain opens. */
    fun normalizeLastUnreadNavigationUrl(url: String): String {
        val stripped = stripTopicListResumeSt(stripTopicLastReadPostParams(url))
        return when (topicViewParam(stripped)?.lowercase()) {
            "getnewpost" -> stripped
            "getlastpost" -> withViewGetNewPost(stripped)
            null -> appendGetNewPostParam(stripped)
            else -> stripped
        }
    }

    /**
     * Non-zero `st` and `view=getlastpost` in list hrefs are resume hints, not explicit pagination.
     * Honor them only for in-app pagination ([sourceScreen]=pagination) or settings that do not
     * navigate through the server ([TopicOpenTarget.usesServerNavigation]).
     */
    private fun shouldHonorExplicitPage(
            context: TopicOpenContext,
            url: String,
            info: ThemeUrlInfo
    ): Boolean {
        if (!context.setting.usesServerNavigation) return true
        if (hasExplicitPageIntentSource(context.sourceScreen)) return true
        if (topicUrlHasNonZeroStParameter(url)) return false
        return !isListingResumeTopicUrl(url, info)
    }

    private fun hasExplicitPageIntentSource(sourceScreen: String): Boolean =
            when (sourceScreen.lowercase()) {
                // `link` is the default source for ACTION_VIEW/browser navigation. A non-zero `st`
                // there is the page the user explicitly opened, not a list-row last-read hint.
                "link", "search", "qms", "internal_link", "history", "back_restore", "child_restore" -> true
                else -> false
            }

    private fun isListingResumeTopicUrl(url: String, info: ThemeUrlInfo): Boolean {
        if (info.isFindPost || info.postId != null) return false
        if (url.contains("#entry", ignoreCase = true)) return false
        if (hasExplicitAct(url)) return false
        return when (topicViewParam(url)?.lowercase()) {
            null, "getlastpost" -> true
            else -> false
        }
    }

    private fun stripUnreadNavigationParams(url: String): String {
        val hashIdx = url.indexOf('#')
        val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val withoutView = base.replace(Regex("""(?i)([?&])view=[^&#]*"""), "$1").replace("?&", "?").trimEnd('?', '&')
        return withoutView + hash
    }

    private fun lastUnreadReason(source: String, resolved: String): String = when {
        source == resolved -> "already_getnewpost"
        source.contains("view=getlastpost", ignoreCase = true) -> "upgraded_getlastpost_to_getnewpost"
        topicUrlHasNonZeroStParameter(source) -> "stripped_list_st_added_getnewpost"
        else -> "added_getnewpost"
    }

    private fun isExplicitPost(info: ThemeUrlInfo, rawUrl: String): Boolean {
        // view=getnewpost + p=/pid= — серверный last-read hint, не явный переход к посту.
        if (topicViewParam(rawUrl)?.equals("getnewpost", ignoreCase = true) == true) return false
        if (hasFindPostMarker(rawUrl) || rawUrl.contains("#entry", ignoreCase = true)) return true
        // p=/pid= без findpost — last-read из списка тем, не целевой пост пользователя.
        if (info.postId != null) return false
        return ThemeApi.extractHighlightPostIdFromTopicUrl(rawUrl) != null
    }

    /** Bookmark/mention/explicit-post intents tag the open so saved-post links are not downgraded to last-read. */
    private fun isExplicitPostSource(context: TopicOpenContext): Boolean {
        if (context.openIntentRaw?.trim() == TopicOpenIntentClassifier.EXPLICIT_POST) return true
        return when (context.sourceScreen.lowercase()) {
            "bookmark", "bookmarks", "note", "notes", "mentions", "mention" -> true
            "link", "internal_link" -> ThemeUrlPolicy.isExplicitPostNavigationUrl(context.rawUrl)
            else -> false
        }
    }

    /** Post id from `p=`/`pid=` query params only (not view=getnewpost last-read top-of-page hints). */
    private fun explicitPostIdFromPostParams(rawUrl: String): Int? {
        if (topicViewParam(rawUrl)?.equals("getnewpost", ignoreCase = true) == true) return null
        return ThemeApi.extractLastReadStylePostIdFromTopicUrl(rawUrl)?.toIntOrNull()?.takeIf { it > 0 }
    }

    /**
     * Saved bookmark / mention post id: honors `p=`/`pid=` even when legacy URLs wrongly carry
     * `view=getnewpost` (list last-read hint, not the user's saved post).
     */
    private fun savedPostIdForExplicitOpen(rawUrl: String): Int? {
        val postId = ThemeApi.extractLastReadStylePostIdFromTopicUrl(rawUrl)?.toIntOrNull()?.takeIf { it > 0 }
                ?: return null
        return when (topicViewParam(rawUrl)?.lowercase()) {
            null, "findpost" -> postId
            "getnewpost", "getlastpost" -> postId
            else -> null
        }
    }

    private fun explicitPostSourceReason(url: String): String =
            when (topicViewParam(url)?.lowercase()) {
                "getnewpost", "getlastpost" -> "explicit_post_source_legacy_view"
                else -> "explicit_post_source"
            }

    /** Ensure a saved-post URL carries `view=findpost` so the server redirects to the right page/anchor. */
    private fun ensureFindPostForExplicitPost(url: String): String {
        if (hasFindPostMarker(url) || url.contains("#entry", ignoreCase = true)) return url
        return when (topicViewParam(url)?.lowercase()) {
            null -> withView(url, "findpost")
            "getnewpost", "getlastpost" -> withView(stripUnreadNavigationParams(url), "findpost")
            else -> url
        }
    }

    internal fun hasFindPostMarker(rawUrl: String): Boolean {
        if (topicViewParam(rawUrl)?.equals("findpost", ignoreCase = true) == true) return true
        return Regex("""(?i)(?:[?&])act=findpost""").containsMatchIn(rawUrl)
    }

    private fun isExplicitPage(info: ThemeUrlInfo, @Suppress("UNUSED_PARAMETER") rawUrl: String): Boolean {
        return info.page != null && info.page > 0
    }

    private fun hasExplicitAct(rawUrl: String): Boolean {
        return Regex("""(?i)(?:[?&])act=""").containsMatchIn(rawUrl)
    }

    private fun hasBlockingTopicView(rawUrl: String): Boolean {
        val view = topicViewParam(rawUrl)?.lowercase() ?: return false
        return view != "getlastpost"
    }

    private fun topicViewParam(rawUrl: String): String? {
        return Regex("""(?i)[?&]view=([^&#]+)""").find(rawUrl)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun appendGetLastPostParam(url: String): String = withView(url, "getlastpost")

    private fun withView(url: String, view: String): String {
        val hashIdx = url.indexOf('#')
        val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val updated = if (Regex("""(?i)[?&]view=""").containsMatchIn(base)) {
            base.replace(Regex("""(?i)([?&])view=[^&#]*"""), "$1view=$view")
        } else {
            val sep = if ('?' in base) "&" else "?"
            "$base${sep}view=$view"
        }
        return updated + hash
    }

    private fun withViewGetNewPost(url: String): String = withView(url, "getnewpost")

    private fun appendGetNewPostParam(url: String): String {
        val hashIdx = url.indexOf('#')
        val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val hash = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val sep = if ('?' in base) "&" else "?"
        return "$base${sep}view=getnewpost$hash"
    }
}
