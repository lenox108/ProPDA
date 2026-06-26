package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * After a verified-unread `view=getnewpost` fetch in hybrid mode, optionally reload once as
 * `view=findpost&p=`.
 *
 * Two upgrade paths:
 * 1. Parser resolved a trusted unread anchor ([hasUnreadTarget]=true).
 * 2. List-unread open hit ambiguous all-read bottom redirect (log 752) — reload to first
 *    non-bottom post on the loaded page instead of leaving the user on already-read tail posts.
 */
object TopicUnreadFindPostReloadPolicy {

    const val LOG_TAG = "FPDA_THEME_ANCHOR_GUARD"

    fun shouldReloadAsFindPost(
            requestUrl: String,
            loadAction: ThemeLoadAction,
            scrollMode: AppPreferences.Main.TopicScrollMode,
            suppressScrollRestore: Boolean,
            openedViaFindPost: Boolean,
            alreadyUpgradedThisTrace: Boolean,
            hasUnreadTarget: Boolean,
            anchorPostId: String?,
            pageAnchor: String?,
            loadedPagePostIds: List<Int> = emptyList(),
    ): Boolean {
        if (alreadyUpgradedThisTrace) return false
        if (openedViaFindPost) return false
        if (loadAction != ThemeLoadAction.Normal) return false
        if (!suppressScrollRestore) return false
        if (scrollMode != AppPreferences.Main.TopicScrollMode.HYBRID) return false
        if (!hasUnreadTarget) return false
        if (!requestUrl.contains("view=getnewpost", ignoreCase = true)) return false
        val anchorId = resolveAnchorPostId(anchorPostId, pageAnchor) ?: return false
        // Log 033/1103268: getnewpost landed on page 1222 but stale fallback anchor 135617646
        // triggered findpost → page 1. Never reload when anchor is absent from loaded posts.
        if (loadedPagePostIds.isNotEmpty() && !isAnchorOnLoadedPage(anchorId, loadedPagePostIds)) {
            return false
        }
        return true
    }

    /**
     * Log 752 (601691, 943863, 1122662): list-unread getnewpost on last page with bottom redirect
     * and no HTML unread markers — bottom #entry is last-read bookmark, not first unread.
     */
    fun shouldReloadAmbiguousListUnreadAsFindPost(
            requestUrl: String,
            loadAction: ThemeLoadAction,
            scrollMode: AppPreferences.Main.TopicScrollMode,
            suppressScrollRestore: Boolean,
            openedViaFindPost: Boolean,
            alreadyUpgradedThisTrace: Boolean,
            parserListUnreadHint: Boolean,
            ambiguousBottomRedirect: Boolean,
            hasUnreadTarget: Boolean,
            fallbackAnchorPostId: String?,
            redirectIsBottomEntry: Boolean = false,
            listTopicMarkedUnread: Boolean = false,
    ): Boolean {
        if (alreadyUpgradedThisTrace) return false
        if (openedViaFindPost) return false
        if (loadAction != ThemeLoadAction.Normal) return false
        if (!suppressScrollRestore) return false
        if (scrollMode != AppPreferences.Main.TopicScrollMode.HYBRID) return false
        if (hasUnreadTarget) return false
        if (!parserListUnreadHint) return false
        if (!ambiguousBottomRedirect) return false
        if (!requestUrl.contains("view=getnewpost", ignoreCase = true)) return false
        // Log 24_06-20-07 (1103268, trace 415ab025): a GENUINELY unread topic (favorites row
        // `topicMarkedUnread=true`, +N) whose new post arrived at the bottom of the last page makes
        // the server `view=getnewpost` redirect to that bottom #entry. The bottom-redirect heuristic
        // misclassifies this as an all-read last-read bookmark and strands the user on the last post.
        // When the LIST itself marks the topic unread, the server's getnewpost bottom redirect IS the
        // first-unread post — reload findpost to it (see [resolveListUnreadBottomRedirectFindPostAnchor]).
        if (redirectIsBottomEntry) {
            return listTopicMarkedUnread && !fallbackAnchorPostId.isNullOrBlank()
        }
        return !fallbackAnchorPostId.isNullOrBlank()
    }

    /**
     * Anchor for the genuine-list-unread bottom-redirect reload (log 24_06-20-07, 1103268). When the
     * favorites row is marked unread and the server getnewpost redirected to the bottom #entry of the
     * last page, that bottom entry is the new (unread) post — reload findpost directly to it so the
     * view opens at the first-unread post instead of being stranded above/below it.
     */
    fun resolveListUnreadBottomRedirectFindPostAnchor(
            loadedPagePostIds: List<Int>,
            redirectEntryId: Int?,
    ): String? {
        if (redirectEntryId == null || redirectEntryId <= 0) return null
        if (loadedPagePostIds.lastOrNull() != redirectEntryId) return null
        return redirectEntryId.toString()
    }

    fun resolveAmbiguousListUnreadFindPostAnchor(
            loadedPagePostIds: List<Int>,
            redirectEntryId: Int?,
    ): String? {
        if (redirectEntryId == null || loadedPagePostIds.isEmpty()) return null
        val hatSkip = TopicUnreadOpenPolicy.inferPrependedHatEntryId(loadedPagePostIds, redirectEntryId)
        val contentIds = if (hatSkip != null) {
            loadedPagePostIds.filter { it != hatSkip }
        } else {
            loadedPagePostIds
        }
        if (contentIds.isEmpty()) return null
        // Log 1122662: a page-**top** redirect (redirect == first content entry on `st=0` page 1)
        // means every post on the loaded page is already read and the real unread is on a later page.
        // Reloading findpost to the next on-page post would still strand the user on page 1, so there
        // is no valid on-page unread anchor — let the open stay ambiguous-all-read without a reload.
        if (redirectEntryId == contentIds.firstOrNull()) return null
        val fallback = contentIds.firstOrNull { it != redirectEntryId } ?: contentIds.firstOrNull()
        return fallback?.toString()?.takeIf { it != redirectEntryId.toString() }
    }

    /** True when [anchorPostId] appears in the posts parsed for the current getnewpost page. */
    fun isAnchorOnLoadedPage(anchorPostId: String, loadedPagePostIds: List<Int>): Boolean {
        val id = anchorPostId.removePrefix("entry").trim().toIntOrNull() ?: return false
        return loadedPagePostIds.any { it == id }
    }

    fun resolveAnchorPostId(anchorPostId: String?, pageAnchor: String?): String? =
            anchorPostId?.trim()?.takeIf { it.isNotBlank() }
                    ?: pageAnchor?.removePrefix("entry")?.trim()?.takeIf { it.isNotBlank() }

    fun buildFindPostUrl(topicId: Int, anchorPostId: String): String =
            "https://4pda.to/forum/index.php?showtopic=$topicId&view=findpost&p=$anchorPostId"
}
