package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.remote.theme.PollQuestionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Locks the poll-vote wire format to the WebView JS `submitThemePoll`: checked options first, then
 * hidden inputs, UTF-8 percent-encoded, joined with `&`.
 */
class PollVoteFormEncoderTest {

    private fun item(name: String, value: String, type: String = "radio") = PollQuestionItem().apply {
        this.name = name
        this.value = value
        this.type = type
    }

    @Test
    fun `null when nothing selected`() {
        assertNull(PollVoteFormEncoder.encode(emptyList(), listOf("st" to "0")))
    }

    @Test
    fun `checked option then hidden inputs joined with ampersand`() {
        val encoded = PollVoteFormEncoder.encode(
                listOf(item("addpoll_1", "3")),
                listOf("addpoll" to "1", "st" to "0"),
        )
        assertEquals("addpoll_1=3&addpoll=1&st=0", encoded)
    }

    @Test
    fun `multiple checkbox selections all included in order`() {
        val encoded = PollVoteFormEncoder.encode(
                listOf(item("q1", "1", "checkbox"), item("q1", "2", "checkbox")),
                emptyList(),
        )
        // Empty hidden inputs default to addpoll=1 (matching ThemeTemplate).
        assertEquals("q1=1&q1=2&addpoll=1", encoded)
    }

    @Test
    fun `names and values are url encoded like encodeURIComponent`() {
        val encoded = PollVoteFormEncoder.encode(
                listOf(item("choice[1]", "да нет")),
                emptyList(),
        )
        assertEquals("choice%5B1%5D=%D0%B4%D0%B0+%D0%BD%D0%B5%D1%82&addpoll=1", encoded)
    }

    @Test
    fun `blank hidden names are dropped`() {
        val encoded = PollVoteFormEncoder.encode(
                listOf(item("q", "5")),
                listOf("" to "x", "keep" to "y"),
        )
        assertEquals("q=5&keep=y", encoded)
    }
}
