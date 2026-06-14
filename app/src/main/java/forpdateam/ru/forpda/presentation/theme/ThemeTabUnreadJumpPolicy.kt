package forpdateam.ru.forpda.presentation.theme

/**
 * Guards [ThemeFragmentWeb.scheduleJumpToUnreadAfterTabSwitch] so explicit findpost / in-flight
 * opens are not clobbered by LAST_UNREAD tab-focus reload (log: scroll_to_unread_executed during
 * QMS→theme findpost open → hybrid pagePosts=1, no scroll).
 */
internal object ThemeTabUnreadJumpPolicy {

    fun shouldScheduleUnreadJumpOnTabFocus(
            reloadUnreadOnTabFocus: Boolean,
            openedViaFindPostLink: Boolean,
            loadInFlight: Boolean,
            renderSettled: Boolean,
            pendingPostedPageScroll: Boolean,
    ): Boolean {
        if (!reloadUnreadOnTabFocus) return false
        if (openedViaFindPostLink) return false
        if (loadInFlight) return false
        if (!renderSettled) return false
        if (pendingPostedPageScroll) return false
        return true
    }
}
