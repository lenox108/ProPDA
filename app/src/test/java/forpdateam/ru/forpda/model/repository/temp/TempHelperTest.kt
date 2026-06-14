package forpdateam.ru.forpda.model.repository.temp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TempHelperTest {

    @Test
    fun `escapes JS line and paragraph separators so showNewMess parses`() {
        val out = TempHelper.transformMessageSrc("a\u2028b\u2029c")

        assertFalse("raw U+2028 must not survive", out.contains('\u2028'))
        assertFalse("raw U+2029 must not survive", out.contains('\u2029'))
        assertTrue(out.contains("\\u2028"))
        assertTrue(out.contains("\\u2029"))
        assertTrue("output must stay a quoted JS string literal", out.startsWith("\"") && out.endsWith("\""))
    }

    @Test
    fun `normalizes newlines and apostrophes and quotes html safely`() {
        val out = TempHelper.transformMessageSrc("<div class=\"x\">hi</div>\nnext'quote")

        assertFalse("newlines stripped", out.contains("\n"))
        assertTrue("apostrophes encoded", out.contains("&apos;"))
        assertTrue("double quotes escaped", out.contains("\\\""))
        assertTrue(out.startsWith("\"") && out.endsWith("\""))
    }

    @Test
    fun `plain content is wrapped in quotes`() {
        assertEquals("\"hello\"", TempHelper.transformMessageSrc("hello"))
    }
}
