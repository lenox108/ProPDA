package forpdateam.ru.forpda.common

import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Golden tests for the three HTML-entity decoder entry points used across
 * the ForPDA codebase. See AUDIT-L05.
 *
 *   - [ApiUtils.fromHtml]         : String output, uses [Html.FROM_HTML_MODE_LEGACY]
 *   - [ApiUtils.spannedFromHtml]  : Spanned output, uses [Html.FROM_HTML_MODE_LEGACY]
 *   - [ApiUtils.coloredFromHtml]  : Spanned output, uses [Html.FROM_HTML_OPTION_USE_CSS_COLORS]
 *
 * The three entry points are NOT interchangeable: the colored variant honors
 * inline `style="color: …"` / `<font color="…">` attributes, while the two
 * legacy-mode variants strip color information. These tests pin down the
 * current contract so that any future consolidation does not silently
 * regress the behavior of call sites that rely on the difference.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class HtmlEntityDecoderTest {

    @Test
    fun fromHtml_decodesNamedEntitiesToPlainString() {
        // &amp; → &, &lt; → <, &gt; → >, &quot; → "
        // &nbsp; is preserved as a non-breaking space (U+00A0) — the audit's
        // L05 item must not silently collapse it to a regular space.
        val out = ApiUtils.fromHtml("&lt;b&gt;a&amp;b&lt;/b&gt;&nbsp;&quot;c&quot;")
        assertEquals("<b>a&b</b> \"c\"", out)
        assertTrue(
                "expected NBSP (U+00A0) to be preserved, got ${out?.toCharArray()?.toList()}",
                out?.contains(' ') == true
        )
    }

    @Test
    fun fromHtml_preservesNumericEntitiesAsText() {
        // Numeric character references are surfaced as the corresponding
        // characters in legacy mode.
        val out = ApiUtils.fromHtml("&#65;&#x42;")
        assertEquals("AB", out)
    }

    @Test
    fun fromHtml_nullInputReturnsNull() {
        assertNull(ApiUtils.fromHtml(null))
    }

    @Test
    fun spannedFromHtml_returnsNonNullSpannedForSimpleText() {
        val spanned = ApiUtils.spannedFromHtml("hello world")
        assertNotNull(spanned)
        assertEquals("hello world", spanned.toString())
    }

    @Test
    fun coloredFromHtml_returnsNonNullSpannedForSimpleText() {
        val spanned = ApiUtils.coloredFromHtml("hello world")
        assertNotNull(spanned)
        assertEquals("hello world", spanned.toString())
    }

    @Test
    fun coloredFromHtml_differsFromSpannedWhenCssColorIsPresent() {
        // The colored variant decodes inline CSS color into a ForegroundColorSpan
        // with the actual color value (0xFFFF0000 for #ff0000). The legacy
        // variant still attaches a ForegroundColorSpan but it is forced to
        // black (0xFF000000) because getHtmlColor() returns TRANSPARENT (0)
        // when FROM_HTML_OPTION_USE_CSS_COLORS is not set.
        val input = """<span style="color:#ff0000">red</span>"""

        val legacy = requireNotNull(ApiUtils.spannedFromHtml(input))
        val colored = requireNotNull(ApiUtils.coloredFromHtml(input))

        val legacyFg = legacy.getSpans(0, legacy.length, android.text.style.ForegroundColorSpan::class.java)
        val coloredFg = colored.getSpans(0, colored.length, android.text.style.ForegroundColorSpan::class.java)

        // Both variants must produce a span — that's a property of the
        // current HtmlToSpannedConverter (it always emits a ForegroundColorSpan
        // for any <span style="color:…">), so we don't lock that down.
        assertEquals(1, legacyFg.size)
        assertEquals(1, coloredFg.size)

        // What actually differs is the resolved color value. If the option
        // flag is ever ignored, `coloredFg.foregroundColor` will collapse to
        // black (0xFF000000) and every caller of `coloredFromHtml` (signatures,
        // post bodies) will start showing black text instead of the user color.
        assertEquals(0xFF000000.toInt(), legacyFg[0].foregroundColor)
        assertEquals(0xFFFF0000.toInt(), coloredFg[0].foregroundColor)
    }
}
