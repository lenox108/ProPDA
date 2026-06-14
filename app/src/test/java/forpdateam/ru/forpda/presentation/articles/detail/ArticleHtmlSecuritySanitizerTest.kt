package forpdateam.ru.forpda.presentation.articles.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHtmlSecuritySanitizerTest {

    @Test
    fun `keeps app generated youtube video card play button`() {
        val card = """
            <div class="news-video-card" data-video-provider="youtube" data-video-id="dQw4w9WgXcQ" data-video-embed-url="https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&amp;rel=0">
              <button type="button" class="news-video-card-preview" data-video-play="true" aria-label="Смотреть видео">
                <span class="news-video-card-thumb" style="background-image:url(https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg)"><span class="news-video-card-play">&#9658;</span></span>
                <span class="news-video-card-title">Смотреть видео в статье</span>
              </button>
              <a class="news-video-card-youtube" href="https://www.youtube.com/watch?v=dQw4w9WgXcQ" target="_blank" rel="nofollow noopener">Открыть в YouTube</a>
            </div>
        """.trimIndent()

        val sanitized = ArticleHtmlSecuritySanitizer.sanitize(card).orEmpty()

        assertTrue(sanitized.contains("news-video-card"))
        assertTrue(sanitized.contains("data-video-play=\"true\""))
        assertTrue(sanitized.contains("data-video-embed-url"))
        assertTrue(sanitized.contains("<button"))
        assertTrue(sanitized.contains("Открыть в YouTube"))
        assertTrue(sanitized.contains("img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
    }

    @Test
    fun `keeps normalized poll vote form options and submit button`() {
        val poll = """
            <div id="poll-ajax-frame-news" class="poll-ajax-frame news-poll news-poll-normalized" data-normalized-poll="true" data-news-poll-token="poll-1335-100">
              <h2>Планирую купить смартфон в этом году</h2>
              <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=1335" method="post">
                <input type="hidden" name="poll_id" value="1335">
                <input type="hidden" name="from" value="https://4pda.to/2026/06/04/457102/opros/">
                <ul class="poll-list">
                  <li><label class="text"><input type="radio" name="answer[]" value="7512" autocomplete="off"> <span>До 10 000 руб.</span></label></li>
                  <li><label class="text"><input type="radio" name="answer[]" value="7513"> <span>От 10 000 до 23 000 руб.</span></label></li>
                </ul>
                <button type="submit" class="btn">Проголосовать</button>
              </form>
            </div>
        """.trimIndent()

        val sanitized = ArticleHtmlSecuritySanitizer.sanitize(poll).orEmpty()

        assertTrue(sanitized.contains("news-poll-normalized"))
        assertTrue(sanitized.contains("<form"))
        assertTrue(sanitized.contains("name=\"answer[]\""))
        assertTrue(sanitized.contains("value=\"7512\""))
        assertTrue(sanitized.contains("value=\"7513\""))
        assertTrue(sanitized.contains("Проголосовать"))
        assertTrue(sanitized.contains("https://4pda.to/pages/poll/?act=vote"))
        assertTrue(sanitized.contains("До 10 000 руб."))
    }

    @Test
    fun `still strips network forms outside trusted blocks`() {
        val html = """
            <div class="entry-content">
              <form action="https://evil.example/phish" method="post">
                <input type="text" name="card">
                <button type="submit">Send</button>
              </form>
              <script>alert(1)</script>
            </div>
        """.trimIndent()

        val sanitized = ArticleHtmlSecuritySanitizer.sanitize(html).orEmpty()

        assertFalse(sanitized.contains("<form", ignoreCase = true))
        assertFalse(sanitized.contains("<input", ignoreCase = true))
        assertFalse(sanitized.contains("<button", ignoreCase = true))
        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertTrue(sanitized.contains("entry-content"))
    }

    @Test
    fun `scrubs handlers and unsafe urls inside trusted poll block`() {
        val poll = """
            <div class="news-poll poll-ajax-frame" data-normalized-poll="true">
              <h2 onclick="alert(1)">Опрос</h2>
              <a href="javascript:alert(1)">bad</a>
              <form action="https://4pda.to/pages/poll/?act=vote&amp;poll_id=1">
                <input type="radio" name="answer[]" value="1"><label>Yes</label>
                <button type="submit">Проголосовать</button>
              </form>
            </div>
        """.trimIndent()

        val sanitized = ArticleHtmlSecuritySanitizer.sanitize(poll).orEmpty()

        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertTrue(sanitized.contains("<form"))
        assertTrue(sanitized.contains("Проголосовать"))
    }
}
