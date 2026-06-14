package forpdateam.ru.forpda.model.data.remote.api.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBlockTest {

    @Test
    fun findPollBlock_extractsNormalizedVotePollWithPollId() {
        val html = """
            <p>Intro</p>
            <div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized" data-normalized-poll="true" data-news-poll-token="poll-1335-1">
              <h2>Опрос</h2>
              <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=1335" method="post">
                <input type="hidden" name="poll_id" value="1335">
                <ul class="poll-list">
                  <li><label><input type="radio" name="answer[]" value="1"> <span>Да</span></label></li>
                </ul>
                <button type="submit" class="btn">Проголосовать</button>
              </form>
            </div>
        """.trimIndent()

        val poll = ArticleBlock.findPollBlock(html)

        assertNotNull(poll)
        assertEquals("1335", poll!!.pollId)
        assertTrue(poll.html.contains("news-poll-normalized"))
        assertTrue(poll.html.contains("name=\"answer[]\""))
    }

    @Test
    fun findPollBlock_returnsNullForPlainArticleBody() {
        assertNull(ArticleBlock.findPollBlock("<p>Текст без опроса</p>"))
    }

    @Test
    fun splitBody_separatesTextAndPollBlocks() {
        val html = """
            <p>Lead</p>
            <div class="news-poll-normalized" data-normalized-poll="true">
              <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=42">
                <input type="radio" name="answer[]" value="1">
              </form>
            </div>
            <p>Tail</p>
        """.trimIndent()

        val blocks = ArticleBlock.splitBody(html)

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is ArticleBlock.Text)
        assertTrue(blocks[1] is ArticleBlock.Poll)
        assertTrue(blocks[2] is ArticleBlock.Text)
        assertEquals("42", (blocks[1] as ArticleBlock.Poll).pollId)
    }

    @Test
    fun pollSurvivedSanitize_trueWhenNoPollPresent() {
        assertTrue(ArticleBlock.pollSurvivedSanitize("<p>Body</p>", "<p>Body</p>"))
    }

    @Test
    fun pollSurvivedSanitize_falseWhenTypedPollDisappears() {
        val before = normalizedPollHtml(pollId = "1335")
        val after = "<p>Body without poll</p>"

        assertFalse(ArticleBlock.pollSurvivedSanitize(before, after))
    }

    @Test
    fun pollSurvivedSanitize_trueForInteractivePollAfterSanitizer() {
        val before = normalizedPollHtml(pollId = "1335")
        val after = before.replace("onclick=\"alert(1)\"", "")

        assertTrue(ArticleBlock.pollSurvivedSanitize(before, after))
        assertEquals("1335", ArticleBlock.findPollBlock(after)?.pollId)
    }

    @Test
    fun pollSurvivedSanitize_trueForBrowserFallbackPoll() {
        val fallback = """
            <div class="news-poll-normalized news-poll-fallback" data-poll-fallback="true">
              <h2>Опрос</h2>
              <button type="button" class="news-poll-browser-button" data-open-external-browser="true">Открыть</button>
            </div>
        """.trimIndent()

        assertTrue(ArticleBlock.pollSurvivedSanitize(fallback, fallback))
    }

    private fun normalizedPollHtml(pollId: String): String = """
        <div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized" data-normalized-poll="true" data-news-poll-token="poll-$pollId-1">
          <h2>Опрос</h2>
          <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=$pollId" method="post">
            <input type="hidden" name="poll_id" value="$pollId">
            <ul class="poll-list">
              <li><label><input type="radio" name="answer[]" value="1"> <span>Да</span></label></li>
              <li><label><input type="radio" name="answer[]" value="2"> <span>Нет</span></label></li>
            </ul>
            <button type="submit" class="btn">Проголосовать</button>
          </form>
        </div>
    """.trimIndent()
}
