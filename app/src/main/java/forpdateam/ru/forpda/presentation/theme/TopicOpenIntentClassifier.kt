package forpdateam.ru.forpda.presentation.theme

/**
 * Maps stable Fragment/Cicerone string intents + [sourceScreen] to [TopicOpenIntent] and trace names.
 */
object TopicOpenIntentClassifier {

    const val FRESH_FORUM = "fresh_forum"
    const val FRESH_FAVORITES = "fresh_favorites"
    const val FRESH_TRACKER = "fresh_tracker"
    const val FRESH_SEARCH = "fresh_search"
    const val FRESH_NEWS = "fresh_news"
    const val FRESH_LEGACY = "fresh"

    const val EXPLICIT_POST = "explicit_post"
    const val EXPLICIT_PAGE = "explicit_page"
    const val BACK_RESTORE = "back_restore"
    const val ROTATION_RESTORE = "rotation_restore"
    const val PROCESS_RESTORE = "process_restore"
    const val AFTER_POST_SUBMIT = "after_post_submit"

    fun classify(intentRaw: String?, sourceScreen: String, rawUrl: String = ""): TopicOpenIntent {
        val intent = intentRaw?.trim().orEmpty().ifEmpty { FRESH_LEGACY }
        return when (intent) {
            FRESH_FORUM -> TopicOpenIntent.FreshOpenFromForum(rawUrl, sourceScreen)
            FRESH_FAVORITES -> TopicOpenIntent.FreshOpenFromFavorites(rawUrl, sourceScreen)
            FRESH_TRACKER -> TopicOpenIntent.FreshOpenFromTracker(rawUrl, sourceScreen)
            FRESH_SEARCH -> TopicOpenIntent.FreshOpenFromSearch(rawUrl, sourceScreen)
            FRESH_NEWS -> TopicOpenIntent.FreshOpenFromNews(rawUrl, sourceScreen)
            FRESH_LEGACY -> classifyFreshFromSource(rawUrl, sourceScreen)
            EXPLICIT_POST -> TopicOpenIntent.ExplicitPostLink(
                    rawUrl = rawUrl,
                    postId = 0,
                    sourceScreen = sourceScreen
            )
            EXPLICIT_PAGE -> TopicOpenIntent.ExplicitPageLink(
                    rawUrl = rawUrl,
                    pageSt = 0,
                    sourceScreen = sourceScreen
            )
            BACK_RESTORE -> TopicOpenIntent.BackRestore(
                    TopicBackStackEntry(
                            topicId = 0,
                            pageSt = null,
                            postId = null,
                            scrollY = null,
                            anchorPostId = null,
                            sourceUrl = rawUrl,
                            timestampMs = 0L
                    )
            )
            ROTATION_RESTORE -> TopicOpenIntent.RotationRestore(rawUrl, sourceScreen)
            PROCESS_RESTORE -> TopicOpenIntent.ProcessRestore(rawUrl, sourceScreen)
            AFTER_POST_SUBMIT -> TopicOpenIntent.AfterPostSubmit(rawUrl, sourceScreen)
            "open_unread" -> TopicOpenIntent.OpenUnread(rawUrl, sourceScreen)
            "open_first_page" -> TopicOpenIntent.OpenFirstPage(rawUrl, sourceScreen)
            "open_last_post" -> TopicOpenIntent.OpenLastPost(rawUrl, sourceScreen)
            else -> classifyFreshFromSource(rawUrl, sourceScreen)
        }
    }

    fun freshIntentForSource(sourceScreen: String): String = when (sourceScreen.lowercase()) {
        "topics", "forum", "forum_list" -> FRESH_FORUM
        "favorites", "favorite", "fav" -> FRESH_FAVORITES
        "tracker", "tracking" -> FRESH_TRACKER
        "search", "search_result" -> FRESH_SEARCH
        "news", "article", "announce" -> FRESH_NEWS
        else -> FRESH_LEGACY
    }

    fun isFreshOpenIntent(intentRaw: String?): Boolean {
        val intent = intentRaw?.trim().orEmpty().ifEmpty { FRESH_LEGACY }
        return intent == FRESH_LEGACY ||
                intent.startsWith("fresh_")
    }

    fun isRestoreIntent(intentRaw: String?): Boolean {
        val intent = intentRaw?.trim().orEmpty()
        return intent == BACK_RESTORE ||
                intent == ROTATION_RESTORE ||
                intent == PROCESS_RESTORE
    }

    fun mustReloadAloneThemeOnNavigation(intentRaw: String?): Boolean {
        val intent = intentRaw?.trim().orEmpty()
        return isFreshOpenIntent(intent) || intent == EXPLICIT_POST
    }

    fun traceName(intent: TopicOpenIntent): String = when (intent) {
        is TopicOpenIntent.FreshOpenFromForum -> FRESH_FORUM
        is TopicOpenIntent.FreshOpenFromFavorites -> FRESH_FAVORITES
        is TopicOpenIntent.FreshOpenFromTracker -> FRESH_TRACKER
        is TopicOpenIntent.FreshOpenFromSearch -> FRESH_SEARCH
        is TopicOpenIntent.FreshOpenFromNews -> FRESH_NEWS
        is TopicOpenIntent.ExplicitPostLink -> EXPLICIT_POST
        is TopicOpenIntent.ExplicitPageLink -> EXPLICIT_PAGE
        is TopicOpenIntent.OpenUnread -> "open_unread"
        is TopicOpenIntent.OpenFirstPage -> "open_first_page"
        is TopicOpenIntent.OpenLastPost -> "open_last_post"
        is TopicOpenIntent.BackRestore -> BACK_RESTORE
        is TopicOpenIntent.RotationRestore -> ROTATION_RESTORE
        is TopicOpenIntent.ProcessRestore -> PROCESS_RESTORE
        is TopicOpenIntent.AfterPostSubmit -> AFTER_POST_SUBMIT
    }

    private fun classifyFreshFromSource(rawUrl: String, sourceScreen: String): TopicOpenIntent =
            when (freshIntentForSource(sourceScreen)) {
                FRESH_FAVORITES -> TopicOpenIntent.FreshOpenFromFavorites(rawUrl, sourceScreen)
                FRESH_TRACKER -> TopicOpenIntent.FreshOpenFromTracker(rawUrl, sourceScreen)
                FRESH_SEARCH -> TopicOpenIntent.FreshOpenFromSearch(rawUrl, sourceScreen)
                FRESH_NEWS -> TopicOpenIntent.FreshOpenFromNews(rawUrl, sourceScreen)
                FRESH_FORUM -> TopicOpenIntent.FreshOpenFromForum(rawUrl, sourceScreen)
                else -> TopicOpenIntent.FreshOpenFromForum(rawUrl, sourceScreen)
            }
}
