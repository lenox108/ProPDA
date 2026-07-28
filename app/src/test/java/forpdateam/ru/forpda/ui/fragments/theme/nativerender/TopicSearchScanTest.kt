package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The contract the whole occurrence-based find-on-page rests on: the host counts matches with
 * [TopicSearchScan] and the renderer paints them while walking the SAME order, so ordinal N on one
 * side must be ordinal N on the other.
 */
class TopicSearchScanTest {

    /** Tests pass raw markup through unchanged — the renderer's HTML parse is an Android dependency. */
    private val identity: (String) -> CharSequence = { it }

    @Test
    fun `counts every occurrence, not every matching block`() {
        val blocks = listOf(BodyBlock.Text("аккум держит, второй аккум не брал"))

        assertEquals(2, TopicSearchScan.countInBlocks(blocks, "аккум", identity))
    }

    @Test
    fun `matching is case-insensitive and non-overlapping`() {
        val blocks = listOf(BodyBlock.Text("Аккум АККУМ аккум"))

        assertEquals(3, TopicSearchScan.countInBlocks(blocks, "аккум", identity))
        assertEquals(2, TopicSearchScan.countInBlocks(listOf(BodyBlock.Text("аааа")), "аа", identity))
    }

    @Test
    fun `looks inside quotes, spoilers, hidden blocks, code, tables and attachment names`() {
        val blocks = listOf(
                BodyBlock.Quote("nick", "date", null, listOf(BodyBlock.Text("прошивка в цитате"))),
                BodyBlock.Spoiler("замеры", false, listOf(BodyBlock.Text("прошивка в спойлере"))),
                BodyBlock.Hidden(listOf(BodyBlock.Text("прошивка в скрытом"))),
                BodyBlock.Code(null, "прошивка в коде"),
                BodyBlock.Table(listOf(listOf("прошивка", "в таблице"))),
                BodyBlock.FileAttachment("прошивка.zip", "https://4pda.to/dl"),
        )

        assertEquals(6, TopicSearchScan.countInBlocks(blocks, "прошивка", identity))
    }

    @Test
    fun `skips what the renderer never highlights`() {
        val blocks = listOf(
                BodyBlock.Image("https://4pda.to/prошивка.png", null, 0, 0),
                BodyBlock.EditNote("прошивка отредактировал"),
        )

        assertEquals(0, TopicSearchScan.countInBlocks(blocks, "прошивка", identity))
    }

    @Test
    fun `visits units in document order so ordinals line up with the render pass`() {
        val blocks = listOf(
                BodyBlock.Text("первый"),
                BodyBlock.Spoiler(null, false, listOf(
                        BodyBlock.Text("второй"),
                        BodyBlock.Quote(null, null, null, listOf(BodyBlock.Text("третий"))),
                )),
                BodyBlock.Code(null, "четвёртый"),
        )

        val seen = mutableListOf<String>()
        TopicSearchScan.forEachUnit(blocks, identity) { seen.add(it.toString()) }

        assertEquals(listOf("первый", "второй", "третий", "четвёртый"), seen)
    }

    @Test
    fun `empty query matches nothing`() {
        assertEquals(0, TopicSearchScan.countInBlocks(listOf(BodyBlock.Text("текст")), "", identity))
    }
}
