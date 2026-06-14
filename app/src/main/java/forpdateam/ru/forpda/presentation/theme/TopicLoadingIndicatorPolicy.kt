package forpdateam.ru.forpda.presentation.theme

/**
 * Single source of truth for the topic loading indicator.
 *
 * A topic open must show exactly ONE loading indicator. The content-area indicator (skeleton overlay /
 * centered content progress for the initial load, swipe-refresh for a refresh) is the only indicator;
 * the toolbar progress (added in TOPIC-007) double-rendered with the content indicator and must stay
 * hidden for topics.
 */
object TopicLoadingIndicatorPolicy {

    enum class Indicator {
        /** Nothing is loading. */
        NONE,

        /** Initial open: the content-area indicator (skeleton overlay / centered progress). */
        CONTENT,

        /** Refresh of an already rendered topic: the swipe-to-refresh spinner. */
        SWIPE_REFRESH
    }

    fun resolve(isRefreshing: Boolean, isPageLoaded: Boolean): Indicator = when {
        !isRefreshing -> Indicator.NONE
        !isPageLoaded -> Indicator.CONTENT
        else -> Indicator.SWIPE_REFRESH
    }

    /**
     * The toolbar progress is never the topic loading indicator: it duplicated the content indicator,
     * so for topics it must always stay hidden regardless of the load phase.
     */
    @Suppress("UNUSED_PARAMETER")
    fun showsToolbarProgress(isRefreshing: Boolean, isPageLoaded: Boolean): Boolean = false
}
