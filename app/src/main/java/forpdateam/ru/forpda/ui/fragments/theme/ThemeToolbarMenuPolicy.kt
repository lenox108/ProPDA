package forpdateam.ru.forpda.ui.fragments.theme

/**
 * Compact topic toolbar has two topic-action slots:
 * 1. Refresh XOR poll (poll replaces refresh when the topic has voting)
 * 2. Topic hat (шапка) when available — independent of poll
 */
internal object ThemeToolbarMenuPolicy {

    data class ToolbarMenuState(
            val showCompactRefresh: Boolean,
            val showCompactPoll: Boolean,
            val showCompactHat: Boolean,
            val showOverflowRefresh: Boolean,
            val showOverflowPoll: Boolean,
    ) {
        companion object {
            val DISABLED = ToolbarMenuState(false, false, false, false, false)
        }
    }

    fun resolve(
            pageLoaded: Boolean,
            hasPoll: Boolean,
            hasTopicHat: Boolean,
            hatToolbarEnabled: Boolean,
    ): ToolbarMenuState {
        if (!pageLoaded) return ToolbarMenuState.DISABLED
        val showCompactPoll = hasPoll
        val showCompactRefresh = !hasPoll
        val showCompactHat = hasTopicHat && hatToolbarEnabled
        return ToolbarMenuState(
                showCompactRefresh = showCompactRefresh,
                showCompactPoll = showCompactPoll,
                showCompactHat = showCompactHat,
                showOverflowRefresh = hasPoll,
                showOverflowPoll = hasPoll && !showCompactPoll,
        )
    }

    fun showCompactPollButton(hasPoll: Boolean): Boolean = hasPoll

    fun showCompactHatButton(hasTopicHat: Boolean, hatToolbarEnabled: Boolean): Boolean =
            hasTopicHat && hatToolbarEnabled
}
