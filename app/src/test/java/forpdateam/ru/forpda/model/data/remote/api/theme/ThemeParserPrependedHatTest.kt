package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Validates the authoritative (Layer-1) prepended-hat detection against a REAL page capture: on deep
 * pages the server wraps the topic hat in `data-spoil-poll-pinned-content` («Показать/Скрыть шапку»),
 * and the hat is the first `data-post` inside it. This replaces the fragile number/title heuristics
 * that intermittently stripped the first real content post (see TopicPrependedHatPolicyTest).
 */
class ThemeParserPrependedHatTest {

    @Test
    fun realDeepPageCapture_extractsServerMarkedHatId() {
        val html = resource("parser/theme/topic_deep_prepended_hat.html")
        // hat=140711020 (inside the pinned wrapper), content post=144071947 (outside it).
        assertEquals(140711020, extractPrependedHatPostId(html))
    }

    @Test
    fun cssSelectorOccurrences_areNotMistakenForTheHat() {
        // The page <style> contains `[data-spoil-poll-pinned-content]{…}` selectors before any post;
        // the extractor must anchor on the real `<div …data-spoil-poll-pinned-content>` element only.
        val html = resource("parser/theme/topic_deep_prepended_hat.html")
        assertEquals(140711020, extractPrependedHatPostId(html))
    }

    @Test
    fun pageWithoutPinnedHatWrapper_returnsZero() {
        val noHat = """
            <div class="post_header_container"><div data-post="555"><a name="entry555"></a></div></div>
            <div class="post_header_container"><div data-post="556"><a name="entry556"></a></div></div>
        """.trimIndent()
        assertEquals(0, extractPrependedHatPostId(noHat))
    }

    @Test
    fun onlyCssSelectorPresent_withoutHatElement_returnsZero() {
        val cssOnly = "<style>[data-spoil-poll-pinned-content]>.post_header{margin:0}</style>" +
                "<div data-post=\"777\"></div>"
        assertEquals(0, extractPrependedHatPostId(cssOnly))
    }

    private fun resource(path: String): String {
        return javaClass.classLoader?.getResource(path)?.readText()
                ?: error("Missing test resource $path")
    }
}
