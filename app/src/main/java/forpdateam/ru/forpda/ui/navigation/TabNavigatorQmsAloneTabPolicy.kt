package forpdateam.ru.forpda.ui.navigation

/** Testable slice of [TabNavigator.activateAloneQmsChatTabIfPresent] / [forpdateam.ru.forpda.ui.fragments.qms.chat.QmsChatFragment.applyChatScreenFromNavigator]. */
object TabNavigatorQmsAloneTabPolicy {

    fun chatIdentityKey(userId: Int, themeId: Int): String = "$userId:$themeId"

    fun isIdentityChanged(prevUserId: Int, prevThemeId: Int, newUserId: Int, newThemeId: Int): Boolean =
            chatIdentityKey(prevUserId, prevThemeId) != chatIdentityKey(newUserId, newThemeId)

    fun isWebShellStale(messagesApplied: Boolean, domReady: Boolean, jsBridgeReady: Boolean): Boolean =
            !messagesApplied || !domReady || !jsBridgeReady

    fun canReuseWebShellForDialogSwitch(domReady: Boolean, jsBridgeReady: Boolean): Boolean =
            domReady && jsBridgeReady

    enum class ReuseAction {
        /** View not created yet — ViewModel identity reset only. */
        VIEW_NOT_READY,
        /** Same dialog; shell is healthy. */
        NO_OP,
        /** Different dialog; keep HTML shell, reset messages via JS. */
        FAST_SWITCH,
        /** Different dialog; reload shell (DOM/bridge not reusable). */
        FULL_RELOAD,
        /** Same dialog but blank/stale WebView — reload + resync loaded messages. */
        RESYNC_STALE,
    }

    fun resolveReuseAction(
            identityChanged: Boolean,
            viewCreated: Boolean,
            messagesApplied: Boolean,
            domReady: Boolean,
            jsBridgeReady: Boolean,
    ): ReuseAction {
        if (!viewCreated) return ReuseAction.VIEW_NOT_READY
        if (identityChanged) {
            return if (canReuseWebShellForDialogSwitch(domReady, jsBridgeReady)) {
                ReuseAction.FAST_SWITCH
            } else {
                ReuseAction.FULL_RELOAD
            }
        }
        if (isWebShellStale(messagesApplied, domReady, jsBridgeReady)) {
            return ReuseAction.RESYNC_STALE
        }
        return ReuseAction.NO_OP
    }
}
