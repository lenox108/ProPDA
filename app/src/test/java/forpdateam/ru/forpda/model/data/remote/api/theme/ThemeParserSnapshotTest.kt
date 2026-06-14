package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ThemeParserSnapshotTest {

    @Test
    fun themeFixtures_documentParserCoverageInputs() {
        val normal = resource("parser/theme/topic_normal.html")
        val deletedHidden = resource("parser/theme/topic_deleted_hidden.html")
        val malformed = resource("parser/theme/topic_malformed.html")
        val moved = resource("parser/theme/topic_moved_relocation.html")

        assertTrue(normal.contains("post_container"))
        assertTrue(normal.contains("poll_question"))
        assertTrue(normal.contains("spoil"))
        assertTrue(normal.contains("<code>code()</code>"))
        assertEquals(3, Regex("post_container").findAll(deletedHidden).count())
        assertTrue(deletedHidden.contains("Сообщение удалено"))
        assertTrue(deletedHidden.contains("Сообщение скрыто"))
        assertTrue(malformed.contains("unterminated quote"))
        assertTrue(moved.contains("errorwrap"))
        assertTrue(moved.contains("showtopic=999"))
    }

    @Test
    fun normalFixture_documentsPollAndSpoilerCorpus() {
        val html = resource("parser/theme/topic_normal.html")
        assertTrue(html.contains("topic_poll"))
        assertTrue(html.contains("poll_question"))
        assertTrue(html.contains("spoil"))
        assertTrue(html.contains("<code>code()</code>"))
        assertTrue(html.contains("var topic_id = parseInt(42)"))
    }

    @Test
    fun movedFixture_extractsRelocationUrl() {
        val html = resource("parser/theme/topic_moved_relocation.html")
        val original = "https://4pda.to/forum/index.php?showtopic=111&view=getnewpost"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=999&st=40",
                ThemeApi.extractTopicRelocationUrlFromHtml(html, originalRequestUrl = original)
        )
    }

    private fun resource(path: String): String {
        return javaClass.classLoader?.getResource(path)?.readText()
                ?: error("Missing test resource $path")
    }

}
