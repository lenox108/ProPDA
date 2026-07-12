package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.content.res.Resources
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the "smilies show as raw symbols" report: 4pda delivers post smilies as TEXT
 * shortcodes (the `.post_body emoticons` markup; the browser's `z_emoticons.js` converts them, the app's
 * no-JS parser does not), and the old [SmileProvider] map regex only caught the self-delimited
 * `:[a-z0-9_-]+:` word forms — so the classic ASCII emoticons (`:)`, `:(`, `:D`, `;)`, `:P`, `B)`, `<_<`,
 * `o.O`, …), which are the common case, leaked through as literal text.
 *
 * These tests exercise the real bundled `assets/forpda/scripts/z_emoticons.js` map + the `assets/smiles`
 * gifs, asserting each shortcode becomes exactly one inline [ImageSpan] over the code's own indices, and that the
 * whitespace-boundary guard (ported from `z_emoticons.js`'s `buildRegexp`) keeps ASCII emoticons from
 * firing inside URLs/words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SmileProviderTest {

    private val res: Resources
        get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun spans(text: CharSequence): CharSequence =
            SmileProvider.applySmiles(text, res, 40)

    private fun imageSpans(cs: CharSequence): Array<out ImageSpan> {
        val s = cs as? Spanned ?: return emptyArray()
        return s.getSpans(0, s.length, ImageSpan::class.java)
    }

    @Test
    fun `classic ASCII emoticon at start is converted to one inline image`() {
        val out = spans(":) hello")
        val sp = imageSpans(out)
        assertEquals("`:)` must become exactly one ImageSpan", 1, sp.size)
        val s = out as Spanned
        assertEquals(0, s.getSpanStart(sp[0]))
        assertEquals("span must cover only the 2-char code, not trailing text", 2, s.getSpanEnd(sp[0]))
    }

    @Test
    fun `multiple ASCII emoticons on one line each convert`() {
        // ":D" and "B)" both need whitespace boundaries and both should fire.
        val out = spans("haha :D cool B) end")
        assertEquals(2, imageSpans(out).size)
    }

    @Test
    fun `word-form shortcode is converted without needing boundaries`() {
        val out = spans("спасибо:thank_you:друг")
        assertEquals(1, imageSpans(out).size)
    }

    @Test
    fun `uppercase word-form shortcode is converted (regression for dropped colon-4PDA-colon)`() {
        // The old lowercase-only map regex (`:[a-z0-9_-]+:`) silently dropped `:4PDA:`.
        val out = spans("люблю :4PDA: !")
        assertEquals(1, imageSpans(out).size)
    }

    @Test
    fun `longer emoticon wins over its shorter prefix`() {
        // ":-)" (smile.gif) must match as ONE span over [0,3], not ":)" over [1,3].
        val out = spans(":-) hi")
        val sp = imageSpans(out)
        assertEquals(1, sp.size)
        val s = out as Spanned
        assertEquals(0, s.getSpanStart(sp[0]))
        assertEquals(3, s.getSpanEnd(sp[0]))
    }

    @Test
    fun `ASCII emoticon without whitespace boundary does not fire (URL-safety guard)`() {
        // No leading/trailing whitespace → must NOT be treated as a smiley (e.g. inside a URL or word).
        assertEquals(0, imageSpans(spans("http://host:)path")).size)
        assertEquals(0, imageSpans(spans("foo:)bar")).size)
    }

    @Test
    fun `text with no shortcodes is returned unchanged with no spans`() {
        val out = spans("обычный текст без смайлов")
        assertTrue(imageSpans(out).isEmpty())
    }
}
