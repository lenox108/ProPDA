package forpdateam.ru.forpda.model.data.remote.api.forum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioural tests for [ForumJsoupParser]. The parser targets
 * the `div[id^="fo_"]` category blocks and the nested
 * `div.board_forum_row` items. The flat collapsed `fc_` block
 * is intentionally skipped — the regex path remains the source
 * of truth for that layout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ForumJsoupParserTest {

    private val parser = ForumJsoupParser()

    @Test
    fun index_parsesCategoryAndSubforum() {
        val html = """
            <div id="fo_1">
                <div class="cat_name">
                    <a href="https://4pda.to/forum/index.php?showforum=1">Category 1</a>
                </div>
                <div class="board_forum_row">
                    <div class="board_forum_name">
                        <a href="?act=markforum&amp;f=10&amp;fromforum=1&amp;foo">Sub A</a>
                        <a href="https://4pda.to/forum/index.php?showforum=10">Sub A</a>
                    </div>
                </div>
                <div class="board_forum_row">
                    <div class="board_forum_name">
                        <a href="?act=markforum&amp;f=11&amp;fromforum=1&amp;foo">Sub B</a>
                        <a href="https://4pda.to/forum/index.php?showforum=11">Sub B</a>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val tree = parser.parseForums(html)
        assertNotNull(tree.forums)
        val cat = tree.forums?.singleOrNull { it.level == 0 }
        assertEquals(1, cat?.id)
        assertEquals("Category 1", cat?.title)
        val subs = cat?.forums
        assertEquals(2, subs?.size)
        assertEquals(10, subs?.get(0)?.id)
        assertEquals("Sub A", subs?.get(0)?.title)
        assertEquals(11, subs?.get(1)?.id)
        assertEquals("Sub B", subs?.get(1)?.title)
        assertEquals(1, subs?.get(0)?.parentId)
    }

    @Test
    fun index_multipleCategories() {
        val html = """
            <div id="fo_1">
                <div class="cat_name">
                    <a href="?showforum=1">Cat 1</a>
                </div>
                <div class="board_forum_row">
                    <div class="board_forum_name">
                        <a href="?showforum=10">A</a>
                    </div>
                </div>
            </div>
            <div id="fo_2">
                <div class="cat_name">
                    <a href="?showforum=2">Cat 2</a>
                </div>
                <div class="board_forum_row">
                    <div class="board_forum_name">
                        <a href="?showforum=20">B</a>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val tree = parser.parseForums(html)
        assertEquals(2, tree.forums?.size)
        val cats = tree.forums?.filter { it.level == 0 }
        assertEquals(2, cats?.size)
        assertEquals(1, cats?.get(0)?.id)
        assertEquals(2, cats?.get(1)?.id)
        assertEquals(10, cats?.get(0)?.forums?.get(0)?.id)
        assertEquals(20, cats?.get(1)?.forums?.get(0)?.id)
    }

    @Test
    fun index_skipsCollapsedFcBlock() {
        val html = """
            <div id="fo_1">
                <div class="cat_name">
                    <a href="?showforum=1">Cat 1</a>
                </div>
                <div class="board_forum_row">
                    <div class="board_forum_name">
                        <a href="?showforum=10">Direct child</a>
                    </div>
                </div>
                <div id="fc_99">
                    <div class="board_forum_row">
                        <div class="board_forum_name">
                            <a href="?showforum=99">Collapsed</a>
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()
        val tree = parser.parseForums(html)
        val cat = tree.forums?.singleOrNull { it.level == 0 }
        // Only the direct child is emitted; the collapsed fc_ block
        // is intentionally skipped in the Jsoup path.
        assertEquals(1, cat?.forums?.size)
        assertEquals(10, cat?.forums?.get(0)?.id)
    }

    @Test
    fun index_emptyReturnsEmptyTree() {
        val tree = parser.parseForums("<html><body></body></html>")
        assertEquals(0, tree.forums?.size ?: 0)
    }
}
