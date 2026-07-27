package forpdateam.ru.forpda.presentation.qms.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class QmsNickHistoryTest {

    @Test
    fun `parse skips blanks and trims`() {
        assertEquals(listOf("Lenox30", "claude.test"), QmsNickHistory.parse(" Lenox30 \n\n claude.test \n"))
    }

    @Test
    fun `parse of empty history returns empty list`() {
        assertEquals(emptyList<String>(), QmsNickHistory.parse(null))
        assertEquals(emptyList<String>(), QmsNickHistory.parse(""))
    }

    @Test
    fun `add puts the newest nick first`() {
        val raw = QmsNickHistory.add(QmsNickHistory.add("", "Lenox30"), "claude.test")
        assertEquals(listOf("claude.test", "Lenox30"), QmsNickHistory.parse(raw))
    }

    @Test
    fun `repeated nick moves up instead of duplicating`() {
        var raw = QmsNickHistory.add("", "Lenox30")
        raw = QmsNickHistory.add(raw, "claude.test")
        raw = QmsNickHistory.add(raw, "lenox30")

        assertEquals(listOf("lenox30", "claude.test"), QmsNickHistory.parse(raw))
    }

    @Test
    fun `history is capped`() {
        var raw = ""
        repeat(QmsNickHistory.MAX_SIZE + 5) { i -> raw = QmsNickHistory.add(raw, "nick$i") }

        val parsed = QmsNickHistory.parse(raw)
        assertEquals(QmsNickHistory.MAX_SIZE, parsed.size)
        assertEquals("nick${QmsNickHistory.MAX_SIZE + 4}", parsed.first())
    }

    @Test
    fun `blank nick does not change history`() {
        val raw = QmsNickHistory.add("", "Lenox30")
        assertEquals(raw, QmsNickHistory.add(raw, "   "))
    }
}
