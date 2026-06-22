package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Контракт парсинга заголовка цитаты — зеркало [blocks.js] transformQuotes().
 */
class QuoteTitleTransformTest {

    private val titleRegexp =
            Regex("""([\s\S]*?)\s@\s((?:\d+\.\d+\.\d+|[\wа-яА-ЯёЁ][\wа-яА-ЯёЁ._-]*)(?:,\s*\d+:\d+)?)?""")

    private fun parseQuoteTitle(text: String): Pair<String, String?> {
        val normalized = text.replace(Regex("""\s+"""), " ").trim()
        val match = titleRegexp.find(normalized) ?: return "" to null
        val nick = match.groupValues[1].replace(Regex("""^\s*@+"""), "").trim()
        val date = match.groupValues[2].ifBlank { null }
        return nick to date
    }

    @Test
    fun stripsLeadingAtFromNickAfterSnapbackIcon() {
        val (nick, date) = parseQuoteTitle("@antisk115 @ 12.06.26, 19:40")
        assertEquals("antisk115", nick)
        assertEquals("12.06.26, 19:40", date)
    }

    @Test
    fun parsesPlainNickWithoutSnapbackPrefix() {
        val (nick, date) = parseQuoteTitle("antisk115 @ 12.06.26, 19:40")
        assertEquals("antisk115", nick)
        assertEquals("12.06.26, 19:40", date)
    }

    @Test
    fun returnsEmptyDateWhenOnlyNickPresent() {
        val (nick, date) = parseQuoteTitle("user @ name")
        assertEquals("user", nick)
        assertEquals("name", date)
    }

    @Test
    fun returnsNoMatchWhenSeparatorHasNoTrailingToken() {
        val (nick, date) = parseQuoteTitle("user @ ")
        assertEquals("", nick)
        assertNull(date)
    }
}
