package forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender

import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.ui.fragments.theme.nativerender.BodyBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsChatItemMapperTest {

    private val mapper = QmsChatItemMapper()

    private fun message(
            id: Int,
            content: String = "hello",
            mine: Boolean = false,
            read: Boolean = true,
            time: String? = "12:30",
    ) = QmsMessage().apply {
        this.id = id
        this.content = content
        this.isMyMessage = mine
        this.readStatus = read
        this.time = time
    }

    private fun dateRow(label: String) = QmsMessage().apply {
        isDate = true
        date = label
    }

    @Test
    fun `date rows map to dividers and messages to bubbles`() {
        val items = mapper.map(listOf(dateRow("вчера"), message(id = 1)))

        assertEquals(2, items.size)
        assertEquals(QmsChatItem.DateDivider("вчера"), items[0])
        assertTrue(items[1] is QmsChatItem.Message)
    }

    @Test
    fun `message fields carry over`() {
        val items = mapper.map(listOf(message(id = 7, mine = true, read = false, time = "09:05")))

        val bubble = items.single() as QmsChatItem.Message
        assertEquals(7, bubble.id)
        assertTrue(bubble.isMine)
        assertTrue(bubble.isUnread)
        assertEquals("09:05", bubble.time)
    }

    @Test
    fun `body is segmented by the shared post renderer`() {
        val html = "<p>текст</p><div class=\"post-block quote\">" +
                "<div class=\"block-title\">Ник @ вчера</div>" +
                "<div class=\"block-body\">цитата</div></div>"

        val bubble = mapper.map(listOf(message(id = 1, content = html))).single() as QmsChatItem.Message

        assertTrue("quote must render natively", bubble.blocks.any { it is BodyBlock.Quote })
        assertTrue("plain text must stay a Text block", bubble.blocks.any { it is BodyBlock.Text })
    }

    @Test
    fun `unchanged messages are not re-parsed on the next window`() {
        val first = mapper.map(listOf(message(id = 1))).single()
        val second = mapper.map(listOf(message(id = 1), message(id = 2))).first()

        assertSame("an untouched message must be reused, not re-rendered", first, second)
    }

    @Test
    fun `read status flip produces a fresh item`() {
        val unread = mapper.map(listOf(message(id = 1, read = false))).single() as QmsChatItem.Message
        val read = mapper.map(listOf(message(id = 1, read = true))).single() as QmsChatItem.Message

        assertNotSame(unread, read)
        assertTrue(unread.isUnread)
        assertFalse(read.isUnread)
    }

    @Test
    fun `edited content invalidates the memoised body`() {
        val before = mapper.map(listOf(message(id = 1, content = "старое"))).single() as QmsChatItem.Message
        val after = mapper.map(listOf(message(id = 1, content = "новое"))).single() as QmsChatItem.Message

        assertNotSame(before, after)
        assertEquals("новое", after.contentHtml)
    }

    @Test
    fun `empty body yields no blocks and never crashes`() {
        val bubble = mapper.map(listOf(message(id = 1, content = ""))).single() as QmsChatItem.Message

        assertTrue(bubble.blocks.isEmpty())
    }
}
