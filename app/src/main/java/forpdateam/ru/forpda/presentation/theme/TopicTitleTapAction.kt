package forpdateam.ru.forpda.presentation.theme

/** What a single tap on the topic toolbar title does. */
enum class TopicTitleTapAction {
    /** Topic has a hat — open the «Шапка темы» overlay (the retired ⓘ toolbar button's action). */
    OPEN_HAT,
    /** No hat to show — fall back to the popup with the full (untruncated) topic name. */
    SHOW_FULL_TITLE,
}

/**
 * The toolbar title carries the hat action since the dedicated ⓘ button was dropped from the topic
 * toolbar. A long press is NOT routed here: it always shows the full title, so copying the topic name
 * stays reachable on topics with a hat too.
 */
object TopicTitleTapPolicy {

    fun resolve(hatAvailable: Boolean): TopicTitleTapAction =
            if (hatAvailable) TopicTitleTapAction.OPEN_HAT else TopicTitleTapAction.SHOW_FULL_TITLE
}
