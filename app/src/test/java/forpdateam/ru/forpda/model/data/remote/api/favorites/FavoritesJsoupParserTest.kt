package forpdateam.ru.forpda.model.data.remote.api.favorites

import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral tests for [FavoritesJsoupParser] — the §2.1 Jsoup
 * migration of the regex-based [FavoritesParser]. The same HTML
 * fixtures as [FavoritesParserTest] are used; the Jsoup path is
 * expected to produce equivalent topic rows for the dominant
 * "topic in favorites" layout (data-item-fid rows).
 *
 * Forum-in-favorites rows and the regex-specific topic_body
 * parsing are intentionally out of scope for the Jsoup path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FavoritesJsoupParserTest {

    private val parser = FavoritesJsoupParser()

    @Test
    fun topicRow_extractsFavIdAndTitle() {
        val html = """
            <div data-item-fid="77" data-item-track="immediate" data-item-pin="0">
                <span class="modifier"></span>
                <a href="https://4pda.to/forum/index.php?showtopic=111">Unread by modifier</a>
                <a onclick="return tpg(20,40)">3</a>
            </div>
        """.trimIndent()
        val item = parser.parseFavorites(html).items.single()
        assertEquals(77, item.favId)
        assertEquals(111, item.topicId)
        assertEquals("Unread by modifier", item.topicTitle)
        assertEquals(40, item.stParam)
        assertEquals(40 / 20 + 1, item.pages)
    }

    @Test
    fun topicRow_marksUnreadWhenPlusModifier() {
        val html = """
            <div data-item-fid="77" data-item-track="immediate" data-item-pin="0">
                <span class="modifier">+2</span>
                <a href="https://4pda.to/forum/index.php?showtopic=111">Unread by modifier</a>
            </div>
        """.trimIndent()
        val item = parser.parseFavorites(html).items.single()
        assertTrue("plus modifier should mark favorite unread", item.isNew)
        assertEquals(FavoriteReadState.UNREAD, item.readState)
        assertEquals(2, item.unreadPostCount)
    }

    @Test
    fun topicRow_pollAndClosedFlags() {
        val html = """
            <div data-item-fid="78" data-item-track="immediate" data-item-pin="0">
                <span class="modifier">^Х</span>
                <a href="https://4pda.to/forum/index.php?showtopic=222">Closed poll topic</a>
            </div>
        """.trimIndent()
        val item = parser.parseFavorites(html).items.single()
        assertTrue(item.isPoll)
        assertTrue(item.isClosed)
    }

    @Test
    fun topicRow_pinnedFlag() {
        val html = """
            <div data-item-fid="79" data-item-track="immediate" data-item-pin="1">
                <span class="modifier"></span>
                <a href="https://4pda.to/forum/index.php?showtopic=333">Pinned topic</a>
            </div>
        """.trimIndent()
        val item = parser.parseFavorites(html).items.single()
        assertTrue(item.isPin)
    }

    @Test
    fun topicRow_barePlusMarksUnread() {
        val html = """
            <div data-item-fid="80" data-item-track="immediate" data-item-pin="0">
                <span class="modifier"><font color="green">+</font></span>
                <a href="https://4pda.to/forum/index.php?showtopic=672">Unread by bare plus</a>
            </div>
        """.trimIndent()
        val item = parser.parseFavorites(html).items.single()
        assertTrue(item.isNew)
    }

    @Test
    fun topicRow_readWithoutMarkers() {
        val html = """
            <div data-item-fid="81" data-item-track="immediate" data-item-pin="0">
                <span class="modifier"></span>
                <a href="https://4pda.to/forum/index.php?showtopic=333">Read topic</a>
            </div>
        """.trimIndent()
        val item = parser.parseFavorites(html).items.single()
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
    }
}
