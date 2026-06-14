package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeUrlPolicyTest {

    @Test
    fun parse_showtopic() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=123")

        assertEquals(123, info?.topicId)
        assertNull(info?.postId)
        assertNull(info?.page)
        assertFalse(info?.isFindPost ?: true)
        assertEquals("https://4pda.to/forum/index.php?showtopic=123", info?.normalizedUrl)
    }

    @Test
    fun parse_showtopicWithSt() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=123&st=20")

        assertEquals(123, info?.topicId)
        assertEquals(20, info?.page)
        assertFalse(info?.isFindPost ?: true)
    }

    @Test
    fun parse_viewFindpost() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=456")

        assertEquals(123, info?.topicId)
        assertEquals(456, info?.postId)
        assertTrue(info?.isFindPost ?: false)
    }

    @Test
    fun parse_actFindpostWithPid() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?act=findpost&pid=456")

        assertNull(info?.topicId)
        assertEquals(456, info?.postId)
        assertTrue(info?.isFindPost ?: false)
    }

    @Test
    fun parse_anchorEntry() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=123#entry456")

        assertEquals(123, info?.topicId)
        assertEquals(456, info?.postId)
        assertTrue(info?.isFindPost ?: false)
    }

    @Test
    fun parse_anchorQueryParameter() {
        val info = ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=123&anchor=entry456")

        assertEquals(123, info?.topicId)
        assertEquals(456, info?.postId)
        assertTrue(info?.isFindPost ?: false)
    }

    @Test
    fun parse_fourPdaVariantsUsedByApp() {
        val relative = ThemeUrlPolicy.parse("/forum/index.php?showtopic=123")
        val queryOnly = ThemeUrlPolicy.parse("?showtopic=123")
        val oldDomain = ThemeUrlPolicy.parse("https://4pda.ru/forum/index.php?showtopic=123")
        val lofi = ThemeUrlPolicy.parse("https://4pda.to/forum/lofiversion/index.php?t123-20.html")

        assertEquals("https://4pda.to/forum/index.php?showtopic=123", relative?.normalizedUrl)
        assertEquals(123, queryOnly?.topicId)
        assertEquals(123, oldDomain?.topicId)
        assertEquals(123, lofi?.topicId)
        assertEquals(20, lofi?.page)
    }

    @Test
    fun parse_rejectsMalformedAndNonThemeUrls() {
        assertNull(ThemeUrlPolicy.parse("https://4pda.to/forum/index.php?showtopic=abc"))
        assertNull(ThemeUrlPolicy.parse("https://example.com/forum/index.php?showtopic=123"))
        assertNull(ThemeUrlPolicy.parse("not a url"))
    }

    @Test
    fun parse_nullAndEmpty() {
        assertNull(ThemeUrlPolicy.parse(null))
        assertNull(ThemeUrlPolicy.parse(""))
        assertNull(ThemeUrlPolicy.parse("   "))
    }

    @Test
    fun isThemeUrl_matchesParserResult() {
        assertTrue(ThemeUrlPolicy.isThemeUrl("https://4pda.to/forum/index.php?showtopic=123"))
        assertFalse(ThemeUrlPolicy.isThemeUrl("https://4pda.to/forum/index.php?showforum=123"))
    }
}
