package forpdateam.ru.forpda.ui.fragments.theme

import forpdateam.ru.forpda.common.TopicOpenListHints

/**
 * Contract the [forpdateam.ru.forpda.ui.navigation.TabNavigator] uses to drive a topic tab,
 * independent of the rendering engine. Implemented by both [ThemeFragmentWeb] (legacy WebView)
 * and [forpdateam.ru.forpda.ui.fragments.theme.nativerender.NativeTopicFragment] (roadmap
 * `native-topic-renderer.md`), so tab reuse / topic-switch / lifecycle work for either engine.
 *
 * Extracted from [ThemeFragmentWeb]'s existing methods verbatim — the WebView path behavior is
 * unchanged (the navigator's `as? ThemeFragmentWeb` casts became `as? ThemeTabHost`, and every
 * live topic tab is still a [ThemeFragmentWeb] which is a [ThemeTabHost]).
 */
interface ThemeTabHost {

    /**
     * The same topic (or a different one) is being navigated to while this tab already exists —
     * load [url] (findpost / fresh open / unread) without spawning a duplicate tab. Called by the
     * navigator AFTER it has already updated the fragment's arguments (ARG_TAB etc.).
     */
    fun loadThemeUrlFromNavigator(
        url: String,
        sourceScreen: String,
        openIntent: String,
        listHints: TopicOpenListHints?,
    )

    /** Topic id currently open in this tab, for the navigator's reuse/dedup match. Null if unknown. */
    fun getOpenTopicIdForReuse(): Int?

    /** This tab became the current one (tab switch / Forward onto an existing screen). */
    fun onTabStackBecameCurrent()

    /** A child fragment (e.g. image viewer) that was covering this tab has been removed. */
    fun onRestoredAfterChildFragmentRemoved()
}
