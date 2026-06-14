package forpdateam.ru.forpda.presentation.favorites

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.diagnostic.ThemePostReadStateDiagnostics
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.theme.TopicOpenContext
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier
import forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver
import forpdateam.ru.forpda.presentation.theme.TopicUnreadOpenPolicy

/** Testable navigation contract for opening a topic from favorites. */
object FavoritesTopicNavigationPolicy {
    private const val TOPIC_BASE = "https://4pda.to/forum/index.php?showtopic="

    fun buildListHints(item: FavItem): TopicOpenListHints {
        if (item.isForum || item.topicId <= 0) return TopicOpenListHints()
        return TopicUnreadOpenPolicy.buildListHints(
                topicId = item.topicId,
                listingHref = item.listingHref,
                topicMarkedUnread = item.isUnreadForNavigation()
        )
    }

    /** Unread row: +N / bold title / [readState] / legacy [isNew] / inspector hint. */
    internal fun FavItem.isUnreadForNavigation(): Boolean =
            isUnreadForDisplay() || inspectorMarkedUnread

    fun resolvePrefetchUrl(
            item: FavItem,
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget
    ): String? {
        if (item.isForum || item.topicId <= 0) return null
        val hints = buildListHints(item)
        logOpenPath(item, hints, "resolve_prefetch_url")
        return TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = TOPIC_BASE + item.topicId,
                        setting = topicOpenTarget,
                        sourceScreen = "favorites_prefetch",
                        unreadUrlFromList = hints.unreadUrlFromList,
                        unreadPostIdFromList = hints.unreadPostIdFromList,
                        listTopicMarkedUnread = hints.topicMarkedUnread,
                        lastReadUrlFromList = hints.lastReadUrlFromList
                )
        ).url
    }

    private fun logOpenPath(item: FavItem, hints: TopicOpenListHints, source: String) {
        if (item.isForum || item.topicId <= 0) return
        val unreadUrl = hints.unreadUrlFromList
        ThemePostReadStateDiagnostics.favoritesOpenPath(
                topicId = item.topicId,
                isNew = item.isNew,
                readState = item.readState.name,
                unreadPostCount = item.unreadPostCount,
                topicMarkedUnread = item.isUnreadForNavigation(),
                inspectorMarkedUnread = item.inspectorMarkedUnread,
                unreadUrlFromListPresent = !unreadUrl.isNullOrBlank(),
                openFromUnreadListHint = hints.topicMarkedUnread &&
                        !hints.unreadUrlFromList.isNullOrBlank(),
                unreadUrlFromList = unreadUrl,
                source = source
        )
    }

    fun buildThemeScreen(item: FavItem): Screen.Theme {
        val hints = buildListHints(item)
        logOpenPath(item, hints, "build_theme_screen")
        return Screen.Theme().apply {
            themeUrl = TOPIC_BASE + item.topicId
            screenTitle = item.topicTitle.orEmpty()
            topicOpenSource = "favorites"
            topicOpenIntent = TopicOpenIntentClassifier.FRESH_FAVORITES
            unreadUrlFromList = hints.unreadUrlFromList
            unreadPostIdFromList = hints.unreadPostIdFromList ?: 0
            lastReadUrlFromList = hints.lastReadUrlFromList
            listTopicMarkedUnread = hints.topicMarkedUnread
        }
    }
}
