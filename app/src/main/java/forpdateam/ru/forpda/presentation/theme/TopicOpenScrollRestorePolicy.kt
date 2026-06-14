package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Hard guard: saved scroll/page must not override unread on fresh opens.
 */
object TopicOpenScrollRestorePolicy {

    /**
     * Whether a pending refresh-restore request still targets the page returned from the server.
     * st can shift after reload; page number is the stable fallback.
     */
    fun refreshRestorePageMatches(
            requestTopicId: Int,
            requestPageSt: Int,
            requestTargetPageNumber: Int?,
            pageTopicId: Int,
            pageSt: Int,
            pageNumber: Int
    ): Boolean {
        if (requestTopicId > 0 && pageTopicId != requestTopicId) return false
        if (requestPageSt < 0) return true
        if (pageSt == requestPageSt) return true
        val targetPage = requestTargetPageNumber
        return targetPage != null && targetPage > 0 && pageNumber == targetPage
    }

    /**
     * Primary entry: [openTarget.allowSavedScrollRestore] is the single owner for fresh/normal opens.
     */
    fun savedScrollRestoreAllowed(
            openTarget: TopicOpenTarget?,
            loadAction: ThemeLoadAction
    ): Boolean {
        if (loadAction == ThemeLoadAction.Back || loadAction == ThemeLoadAction.Refresh) return true
        openTarget?.let { return it.allowSavedScrollRestore }
        return loadAction == ThemeLoadAction.Normal
    }

    fun savedScrollRestoreAllowed(
            openIntentRaw: String?,
            setting: AppPreferences.Main.TopicOpenTarget,
            loadAction: ThemeLoadAction,
            suppressScrollRestoreForOpen: Boolean = false,
            openTarget: TopicOpenTarget? = null
    ): Boolean {
        if (openTarget != null) {
            if (!openTarget.allowSavedScrollRestore) return false
            if (loadAction == ThemeLoadAction.Back || loadAction == ThemeLoadAction.Refresh) return true
            if (TopicOpenIntentClassifier.isRestoreIntent(openIntentRaw)) return true
            if (loadAction != ThemeLoadAction.Normal) return false
            return true
        }
        if (suppressScrollRestoreForOpen) return false
        if (loadAction == ThemeLoadAction.Back) return true
        if (TopicOpenIntentClassifier.isRestoreIntent(openIntentRaw)) return true
        if (loadAction != ThemeLoadAction.Normal) {
            return loadAction == ThemeLoadAction.Refresh
        }
        if (TopicOpenIntentClassifier.isFreshOpenIntent(openIntentRaw) &&
                setting == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
        ) {
            return false
        }
        return true
    }

    fun blockedReason(
            openIntentRaw: String?,
            setting: AppPreferences.Main.TopicOpenTarget,
            loadAction: ThemeLoadAction,
            suppressScrollRestoreForOpen: Boolean
    ): String? {
        if (savedScrollRestoreAllowed(openIntentRaw, setting, loadAction, suppressScrollRestoreForOpen)) {
            return null
        }
        return when {
            suppressScrollRestoreForOpen -> "blocked_open_suppress_scroll"
            TopicOpenIntentClassifier.isFreshOpenIntent(openIntentRaw) &&
                    setting == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD ->
                "blocked_fresh_last_unread"
            else -> "blocked_policy"
        }
    }

    /**
     * Detect when scroll restore fields would win over an unread-first open (for FPDA_TOPIC_OPEN error log).
     */
    /**
     * OPEN_UNREAD suppresses saved scroll on fresh opens only — not on topic refresh/update.
     */
    fun shouldSuppressScrollRestoreOnRender(
            suppressScrollRestoreForOpen: Boolean,
            pendingUnreadOpenSuppressScroll: Boolean,
            loadAction: ThemeLoadAction,
            hasActiveRefreshRestore: Boolean,
            themeUrl: String,
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget,
            navigationTarget: TopicOpenTarget? = null
    ): Boolean {
        if (hasActiveRefreshRestore || loadAction == ThemeLoadAction.Refresh) return false
        navigationTarget?.let {
            if (it is TopicOpenTarget.Unread) return true
            if (!it.allowSavedScrollRestore) return true
        }
        if (suppressScrollRestoreForOpen || pendingUnreadOpenSuppressScroll) return true
        if (loadAction != ThemeLoadAction.Normal) return false
        if (!themeUrl.contains("view=getnewpost", ignoreCase = true)) return false
        return topicOpenTarget == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD
    }

    fun detectSavedScrollOverrodeUnread(
            openIntentRaw: String?,
            setting: AppPreferences.Main.TopicOpenTarget,
            suppressScrollRestore: Boolean,
            scrollY: Int,
            refreshRestoreId: String?,
            restoreScheduled: Boolean,
            scrollToUnreadExecuted: Boolean?,
            loadAction: ThemeLoadAction = ThemeLoadAction.Normal
    ): Boolean {
        if (loadAction == ThemeLoadAction.Refresh) return false
        if (!refreshRestoreId.isNullOrBlank()) return false
        if (!TopicOpenIntentClassifier.isFreshOpenIntent(openIntentRaw)) return false
        if (setting != AppPreferences.Main.TopicOpenTarget.LAST_UNREAD) return false
        if (!suppressScrollRestore) return false
        if (scrollToUnreadExecuted == true) return false
        if (scrollY > 0 && (restoreScheduled || !refreshRestoreId.isNullOrBlank())) return true
        if (!refreshRestoreId.isNullOrBlank() && restoreScheduled) return true
        return false
    }
}
