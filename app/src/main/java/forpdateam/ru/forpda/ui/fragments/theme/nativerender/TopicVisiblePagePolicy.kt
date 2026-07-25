package forpdateam.ru.forpda.ui.fragments.theme.nativerender

/**
 * Chooses the topic page shown in the toolbar while two page windows share the viewport.
 *
 * The page at the reading edge wins: the newest visible page while scrolling down and the oldest
 * visible page while scrolling up. This makes the toolbar follow a visible «Страница N» boundary
 * instead of waiting until the new page occupies more than half of the screen.
 */
internal object TopicVisiblePagePolicy {

    fun resolve(
            visiblePages: Collection<Int>,
            currentPage: Int,
            scrollDelta: Int,
    ): Int? {
        val pages = visiblePages.filter { it > 0 }
        if (pages.isEmpty()) return null
        return when {
            scrollDelta > 0 -> pages.maxOrNull()
            scrollDelta < 0 -> pages.minOrNull()
            currentPage in pages -> currentPage
            else -> pages.first()
        }
    }
}
