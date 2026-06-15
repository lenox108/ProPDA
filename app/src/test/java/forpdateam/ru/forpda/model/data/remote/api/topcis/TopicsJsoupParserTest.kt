package forpdateam.ru.forpda.model.data.remote.api.topcis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioural tests for [TopicsJsoupParser]. The parser targets the
 * dominant `div[data-topic]` row layout. Curator/lastUser/desc
 * fields are filled in best-effort; the regex path remains the
 * source of truth for the production build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopicsJsoupParserTest {

    private val parser = TopicsJsoupParser()

    @Test
    fun topicRow_extractsBasicFields() {
        val html = """
            <div data-topic="100">
                <span class="modifier">+</span>
                <div class="topic_title">
                    <a href="https://4pda.to/forum/index.php?showtopic=100">Title 1</a>
                </div>
            </div>
        """.trimIndent()
        val item = parser.parse(html, argId = 1).topicItems.single { it.id == 100 }
        assertEquals(100, item.id)
        assertEquals("Title 1", item.title)
        assertTrue(item.isNew)
    }

    @Test
    fun topicRow_pollAndClosed() {
        val html = """
            <div data-topic="101">
                <span class="modifier">^Х</span>
                <div class="topic_title">
                    <a href="https://4pda.to/forum/index.php?showtopic=101">Closed poll</a>
                </div>
            </div>
        """.trimIndent()
        val item = parser.parse(html, argId = 1).topicItems.single { it.id == 101 }
        assertTrue(item.isPoll)
        assertTrue(item.isClosed)
    }

    @Test
    fun topicRow_pinnedGoesToPinnedList() {
        val html = """
            <div data-topic="200" class="pinned">
                <span class="modifier"></span>
                <div class="topic_title">
                    <a href="https://4pda.to/forum/index.php?showtopic=200">Pinned</a>
                </div>
            </div>
            <div data-topic="201">
                <span class="modifier"></span>
                <div class="topic_title">
                    <a href="https://4pda.to/forum/index.php?showtopic=201">Normal</a>
                </div>
            </div>
        """.trimIndent()
        val data = parser.parse(html, argId = 1)
        assertEquals(1, data.pinnedItems.size)
        assertEquals(200, data.pinnedItems.single().id)
        assertEquals(1, data.topicItems.size)
        assertEquals(201, data.topicItems.single().id)
    }

    @Test
    fun topicRow_relocatedFlag() {
        val html = """
            <div data-topic="300">
                <span class="modifier">»</span>
                <div class="topic_title">
                    <a href="https://4pda.to/forum/index.php?showtopic=300" title="Перемещена">Old</a>
                </div>
            </div>
        """.trimIndent()
        val item = parser.parse(html, argId = 1).topicItems.single { it.id == 300 }
        assertTrue(item.isRelocated)
    }

    @Test
    fun topicRow_hrefIdOverridesDataTopic() {
        val html = """
            <div data-topic="400">
                <span class="modifier"></span>
                <div class="topic_title">
                    <a href="https://4pda.to/forum/index.php?showtopic=450">Title</a>
                </div>
            </div>
        """.trimIndent()
        val data = parser.parse(html, argId = 1)
        val item = data.topicItems.single { it.id == 450 }
        assertEquals(400, item.oldId)
    }

    @Test
    fun forumItem_isExtracted() {
        val html = """
            <a class="forum_link" data-forum-id="5" href="?showforum=5">
                <span class="forum_title">Forum Name</span>
            </a>
        """.trimIndent()
        val data = parser.parse(html, argId = 1)
        val forumItem = data.forumItems.firstOrNull { it.isForum && it.id == 5 }
        assertEquals("Forum Name", forumItem?.title)
    }
}
