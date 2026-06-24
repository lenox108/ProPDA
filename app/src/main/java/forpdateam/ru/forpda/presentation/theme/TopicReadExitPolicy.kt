package forpdateam.ru.forpda.presentation.theme

/**
 * When the user leaves a topic on its last page, mark it read if they scrolled near the bottom.
 *
 * Log 752: ratio 0.975–0.979 on last page did not pass the legacy 0.995 gate, so favorites
 * unread badges persisted after the user had effectively finished reading.
 */
object TopicReadExitPolicy {

    // Снижено с 0.97 → 0.93: см. лог 24_06, native scrollY не дотягивает до maxScroll из-за незафиксированного contentHeight в WebView.
    const val LAST_PAGE_MARK_READ_RATIO_THRESHOLD = 0.93

    fun shouldMarkReadOnLastPageExit(wasNearBottom: Boolean?, scrollRatio: Double?): Boolean =
            wasNearBottom == true ||
                    (scrollRatio != null && scrollRatio >= LAST_PAGE_MARK_READ_RATIO_THRESHOLD)
}
