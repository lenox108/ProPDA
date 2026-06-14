package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Debug-only structured log for topic open resolution and scroll decisions.
 */
object TopicOpenTrace {

    fun log(
            context: TopicOpenContext,
            resolution: TopicOpenResolution,
            extras: TopicOpenTraceExtras = TopicOpenTraceExtras()
    ) {
        val blockedReason = extras.savedScrollRestoreBlockedReason
                ?: TopicOpenScrollRestorePolicy.blockedReason(
                        openIntentRaw = extras.openIntent,
                        setting = context.setting,
                        loadAction = extras.loadAction?.let { parseLoadAction(it) } ?: ThemeLoadAction.Normal,
                        suppressScrollRestoreForOpen = resolution.suppressScrollRestore
                )
        val savedScrollRestoreAllowed = extras.savedScrollRestoreAllowed
                ?: (blockedReason == null)
        val scrollRestoreAllowed = extras.scrollRestoreAllowed
                ?: (savedScrollRestoreAllowed && !resolution.suppressScrollRestore)

        val savedScrollOverrodeUnread = extras.savedScrollOverrodeUnread
                ?: TopicOpenScrollRestorePolicy.detectSavedScrollOverrodeUnread(
                        openIntentRaw = extras.openIntent,
                        setting = context.setting,
                        suppressScrollRestore = resolution.suppressScrollRestore,
                        scrollY = extras.finalScrollY ?: context.cachedScrollPosition ?: 0,
                        refreshRestoreId = extras.refreshRestoreId,
                        restoreScheduled = extras.scrollRestoreScheduled == true,
                        scrollToUnreadExecuted = extras.scrollToUnreadExecuted,
                        loadAction = extras.loadAction?.let { parseLoadAction(it) } ?: ThemeLoadAction.Normal
                )

        val fields = linkedMapOf<String, Any?>(
                "traceId" to extras.traceId,
                "requestId" to extras.requestId,
                "topicId" to context.topicId,
                "topicTitleHash" to extras.topicTitleHash,
                "sourceScreen" to context.sourceScreen,
                "sourceUrl" to FpdaDebugLog.sanitizeUrl(context.sourceUrl),
                "openIntent" to extras.openIntent,
                "isFreshOpen" to extras.isFreshOpen,
                "isRestore" to extras.isBackRestore,
                "userSettingOpenTarget" to context.setting.name,
                "userSetting" to context.setting.name,
                "userAction" to context.userAction?.name,
                "explicitPostId" to context.explicitPostId,
                "explicitPage" to context.explicitPageSt,
                "explicitPageSt" to context.explicitPageSt,
                "unreadUrlPresent" to !context.unreadUrlFromList.isNullOrBlank(),
                "unreadUrlSanitized" to FpdaDebugLog.sanitizeUrl(context.unreadUrlFromList),
                "unreadUrlFromList" to FpdaDebugLog.sanitizeUrl(context.unreadUrlFromList),
                "unreadPostId" to context.unreadPostIdFromList,
                "unreadPostIdFromList" to context.unreadPostIdFromList,
                "listTopicMarkedUnread" to context.listTopicMarkedUnread,
                "unreadPage" to extras.unreadPage,
                "cachedPage" to context.cachedLastPage,
                "cachedScrollY" to context.cachedScrollPosition,
                "savedPage" to extras.savedPage,
                "savedPostId" to extras.savedPostId,
                "savedScrollY" to extras.savedScrollY,
                "resolverSelectedTarget" to resolution.targetType.name,
                "resolvedTargetType" to resolution.targetType.name,
                "resolverSelectedPage" to resolution.resolvedPageSt,
                "resolvedPage" to resolution.resolvedPageSt,
                "resolverSelectedPostId" to resolution.resolvedPostId,
                "resolvedPostId" to resolution.resolvedPostId,
                "resolverReason" to resolution.reason,
                "resolvedUrl" to FpdaDebugLog.sanitizeUrl(resolution.url),
                "suppressScrollRestore" to resolution.suppressScrollRestore,
                "savedScrollRestoreAllowed" to savedScrollRestoreAllowed,
                "savedScrollRestoreBlockedReason" to blockedReason,
                "savedRestoreDecision" to extras.savedRestoreDecision,
                "scrollRestoreAllowed" to scrollRestoreAllowed,
                "scrollRestoreApplied" to extras.scrollRestoreApplied,
                "scrollRestoreScheduled" to extras.scrollRestoreScheduled,
                "scrollRestoreExecuted" to extras.scrollRestoreExecuted,
                "scrollToUnreadScheduled" to extras.scrollToUnreadScheduled,
                "scrollToUnreadExecuted" to extras.scrollToUnreadExecuted,
                "serverRedirectUrl" to FpdaDebugLog.sanitizeUrl(extras.serverRedirectUrl),
                "finalLoadedPage" to extras.finalLoadedPage,
                "finalScrolledPostId" to extras.finalScrolledPostId,
                "finalScrollY" to extras.finalScrollY,
                "hasUnreadTarget" to extras.hasUnreadTarget,
                "openSessionKind" to extras.openSessionKind,
                "loadAction" to extras.loadAction,
                "renderGenerationId" to extras.renderGenerationId,
                "refreshRestoreId" to extras.refreshRestoreId,
                "savedScrollOverrodeUnread" to savedScrollOverrodeUnread
        )
        val event = extras.event ?: "resolution"
        val warnLastUnreadMissed = resolution.targetType != TopicOpenTargetType.SETTING_LAST_UNREAD &&
                resolution.targetType != TopicOpenTargetType.SERVER_UNREAD_FALLBACK &&
                context.setting == AppPreferences.Main.TopicOpenTarget.LAST_UNREAD &&
                context.userAction == null &&
                TopicOpenIntentClassifier.isFreshOpenIntent(extras.openIntent)
        val warnIgnoredLastReadHint = context.unreadPostIdFromList?.takeIf { it > 0 } != null &&
                resolution.reason == "ignored_last_read_post_id_use_getnewpost"
        when {
            savedScrollOverrodeUnread ->
                FpdaDebugLog.warn(
                        FpdaDebugLog.TAG_TOPIC_OPEN,
                        "saved_scroll_overrode_unread",
                        fields
                )
            warnLastUnreadMissed || warnIgnoredLastReadHint ->
                FpdaDebugLog.warn(FpdaDebugLog.TAG_TOPIC_OPEN, event, fields)
            else ->
                FpdaDebugLog.log(FpdaDebugLog.TAG_TOPIC_OPEN, event, fields)
        }
    }

    private fun parseLoadAction(name: String): ThemeLoadAction = when (name) {
        "Refresh" -> ThemeLoadAction.Refresh
        "Back" -> ThemeLoadAction.Back
        "End" -> ThemeLoadAction.End
        else -> ThemeLoadAction.Normal
    }
}

data class TopicOpenTraceExtras(
        val event: String? = null,
        val traceId: String? = null,
        val requestId: String? = null,
        val topicTitleHash: Int? = null,
        val openIntent: String? = null,
        val isFreshOpen: Boolean? = null,
        val isBackRestore: Boolean? = null,
        val savedBackStack: TopicBackStackEntry? = null,
        val savedRestoreDecision: String? = null,
        val savedScrollRestoreAllowed: Boolean? = null,
        val savedScrollRestoreBlockedReason: String? = null,
        val scrollRestoreAllowed: Boolean? = null,
        val scrollRestoreApplied: Boolean = false,
        val scrollRestoreScheduled: Boolean? = null,
        val scrollRestoreExecuted: Boolean? = null,
        val scrollToUnreadScheduled: Boolean? = null,
        val scrollToUnreadExecuted: Boolean? = null,
        val savedScrollOverrodeUnread: Boolean? = null,
        val serverRedirectUrl: String? = null,
        val finalLoadedPage: Int? = null,
        val finalScrolledPostId: String? = null,
        val finalScrollY: Int? = null,
        val savedPage: Int? = null,
        val savedPostId: String? = null,
        val savedScrollY: Int? = null,
        val unreadPage: Int? = null,
        val suppressScrollRestore: Boolean = false,
        val hasUnreadTarget: Boolean = false,
        val openSessionKind: String? = null,
        val loadAction: String? = null,
        val renderGenerationId: Int? = null,
        val refreshRestoreId: String? = null
)
