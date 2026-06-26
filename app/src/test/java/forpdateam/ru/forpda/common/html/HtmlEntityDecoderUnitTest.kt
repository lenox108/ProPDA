package forpdateam.ru.forpda.common.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Focused tests for the canonical [HtmlEntityDecoder]. These pin the core
 * entity-decoding behavior that the scattered `ApiUtils` entry points used to
 * own directly. They must stay consistent with `HtmlEntityDecoderTest`, which
 * exercises the same logic through the `ApiUtils` wrappers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class HtmlEntityDecoderUnitTest {

    @Test
    fun decodeToString_namedEntities() {
        // &nbsp; is preserved as U+00A0 (NBSP), not collapsed to a regular space.
        assertEquals(
            "<b>a&b</b>\u00A0\"c\"",
            HtmlEntityDecoder.decodeToString("&lt;b&gt;a&amp;b&lt;/b&gt;&nbsp;&quot;c&quot;")
        )
    }

    @Test
    fun decodeToString_apostropheEntity() {
        assertEquals("it's", HtmlEntityDecoder.decodeToString("it&#39;s"))
    }

    @Test
    fun decodeToString_numericDecimalEntity() {
        // &#1234; → Ӓ (U+04D2)
        assertEquals("\u04D2", HtmlEntityDecoder.decodeToString("&#1234;"))
    }

    @Test
    fun decodeToString_numericHexEntity() {
        // &#x41; → A
        assertEquals("A", HtmlEntityDecoder.decodeToString("&#x41;"))
    }

    @Test
    fun decodeToString_mixedNumericEntities() {
        assertEquals("AB", HtmlEntityDecoder.decodeToString("&#65;&#x42;"))
    }

    @Test
    fun decodeToString_unknownEntityPassthrough() {
        // Unknown / malformed entities are surfaced verbatim, not dropped.
        assertEquals("&bogus;", HtmlEntityDecoder.decodeToString("&bogus;"))
    }

    @Test
    fun decodeToString_nullReturnsNull() {
        assertNull(HtmlEntityDecoder.decodeToString(null))
    }

    @Test
    fun decodeToSpanned_simpleText() {
        val spanned = HtmlEntityDecoder.decodeToSpanned("hello world")
        assertNotNull(spanned)
        assertEquals("hello world", spanned.toString())
    }

    @Test
    fun decodeColoredToSpanned_preservesCssColor() {
        val colored = requireNotNull(
            HtmlEntityDecoder.decodeColoredToSpanned("""<span style="color:#ff0000">red</span>""")
        )
        val fg = colored.getSpans(0, colored.length, android.text.style.ForegroundColorSpan::class.java)
        assertEquals(1, fg.size)
        assertEquals(0xFFFF0000.toInt(), fg[0].foregroundColor)
    }
}
