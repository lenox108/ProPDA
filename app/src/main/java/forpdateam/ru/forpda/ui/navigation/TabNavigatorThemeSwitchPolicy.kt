package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier

/** Testable slice of [TabNavigator.activateAloneThemeTabIfPresent] rules. */
object TabNavigatorThemeSwitchPolicy {
    fun mustReloadAloneThemeOnNavigation(openIntent: String): Boolean =
            TopicOpenIntentClassifier.mustReloadAloneThemeOnNavigation(openIntent)

    fun isFreshOpenForReuse(openIntent: String): Boolean =
            TopicOpenIntentClassifier.isFreshOpenIntent(openIntent)

    fun isCrossTopicFreshOpen(targetTopicId: Int?, openTopicId: Int?, openIntent: String): Boolean {
        if (!mustReloadAloneThemeOnNavigation(openIntent)) return false
        if (targetTopicId == null || targetTopicId <= 0) return false
        if (openTopicId == null || openTopicId <= 0) return true
        return targetTopicId != openTopicId
    }

    fun listHintsFromThemeScreen(screen: Screen.Theme): TopicOpenListHints =
            TopicOpenListHints(
                    unreadUrlFromList = screen.unreadUrlFromList,
                    unreadPostIdFromList = screen.unreadPostIdFromList.takeIf { it > 0 },
                    topicMarkedUnread = screen.listTopicMarkedUnread ||
                            !screen.unreadUrlFromList.isNullOrBlank() ||
                            screen.unreadPostIdFromList > 0,
                    lastReadUrlFromList = screen.lastReadUrlFromList
            )
}
