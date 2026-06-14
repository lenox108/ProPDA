package forpdateam.ru.forpda.model.data.remote.api.mentions

import org.junit.Assert.assertEquals
import org.junit.Test

class MentionUrlPreferenceTest {

    @Test
    fun preferMentionPostUrl_prefersFindpostHref() {
        val row = """<a href="index.php?showtopic=1">t</a> <a href="index.php?showtopic=1&amp;view=findpost&amp;p=99">go</a>"""
        val primary = "index.php?showtopic=1"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=99",
                preferMentionPostUrl(row, primary)
        )
    }

    @Test
    fun preferMentionPostUrl_prefersShowtopicWithP() {
        val row = """<a href="https://4pda.to/forum/index.php?showtopic=5&amp;p=12">x</a>"""
        val primary = "https://4pda.to/forum/index.php?showtopic=5"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=5&p=12",
                preferMentionPostUrl(row, primary)
        )
    }

    @Test
    fun preferMentionPostUrl_fallbackPrimaryDecodesAmp() {
        val primary = "https://4pda.to/forum/index.php?showtopic=1&amp;st=0"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&st=0",
                preferMentionPostUrl("<div>no extra links</div>", primary)
        )
    }

    @Test
    fun patchMentionLink_addsFindpostFromDataPostId() {
        val row = """<tr><td data-post-id="77"><a href="index.php?showtopic=3">t</a></td></tr>"""
        val link = "https://4pda.to/forum/index.php?showtopic=3"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=77",
                patchMentionLinkIfTopicOnly(link, row)
        )
    }

    @Test
    fun preferMentionPostUrl_prefersAnchorEntryPostId() {
        val row = """<a href="index.php?showtopic=3">t</a> <a href="#entry88">post</a>"""
        val primary = "https://4pda.to/forum/index.php?showtopic=3"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=88",
                patchMentionLinkIfTopicOnly(preferMentionPostUrl(row, primary), row)
        )
    }
}
