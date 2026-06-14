package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleDeferredExtrasMergerTest {

    @Test
    fun `body unchanged when html matches after whitespace normalize`() {
        val body = "<div class=\"content\">Article text with enough content for validation.</div>"
        assertTrue(ArticleDeferredExtrasMerger.isBodyUnchanged(body, "  $body  "))
    }

    @Test
    fun `body changed when poll block appended`() {
        val before = "<div class=\"content\">Article text with enough content for validation.</div>"
        val after = before + """<div class="news-poll">poll</div>"""
        assertFalse(ArticleDeferredExtrasMerger.isBodyUnchanged(before, after))
    }

    @Test
    fun `body unchanged only when snapshots are equal not same reference guard`() {
        val before = "<div>Article body</div>"
        val after = before
        assertTrue(ArticleDeferredExtrasMerger.isBodyUnchanged(before, after))
        val appended = "$before<div class=\"news-poll-normalized\">poll</div>"
        assertFalse(ArticleDeferredExtrasMerger.isBodyUnchanged(before, appended))
    }

    @Test
    fun `needs deferred extras when mobile comments source without desktop`() {
        val page = DetailsPage().apply {
            commentsSource = "https://4pda.to/index.php?p=456#comments"
            commentsCount = 2
        }
        assertTrue(ArticleDeferredExtrasMerger.needsDeferredExtras(page))
    }

    @Test
    fun `needs deferred extras when raw poll markup without normalization`() {
        val page = DetailsPage().apply {
            html = """<div class="content">Article</div><div id="poll-ajax-frame-1"><form action="/pages/poll/"></form></div>"""
        }
        assertTrue(ArticleDeferredExtrasMerger.needsDeferredExtras(page))
    }

    @Test
    fun `does not need deferred extras when poll already normalized`() {
        val page = DetailsPage().apply {
            html = """<div class="news-poll-normalized" data-normalized-poll="true">poll</div>"""
        }
        assertFalse(ArticleDeferredExtrasMerger.needsDeferredExtras(page))
    }

    @Test
    fun `needs deferred extras when poll title without renderable poll block`() {
        val page = DetailsPage().apply {
            title = "Опрос: какой смартфон купить"
            html = """<div class="content">Текст статьи без poll markup.</div>"""
        }
        assertTrue(ArticleDeferredExtrasMerger.needsDeferredExtras(page))
    }

    @Test
    fun `applyMetadata does not inflate above parsed dom count`() {
        val target = forpdateam.ru.forpda.entity.remote.news.DetailsPage().apply {
            commentsCount = 27
        }
        val source = forpdateam.ru.forpda.entity.remote.news.DetailsPage().apply {
            commentsCount = 222
        }
        ArticleDeferredExtrasMerger.applyMetadata(target, source, parsedDomCount = 27)
        assertEquals(27, target.commentsCount)
    }

    @Test
    fun `does not need deferred extras when desktop source present`() {
        val page = DetailsPage().apply {
            commentsSource = "https://4pda.to/index.php?p=1#comments"
            desktopCommentsSource = "https://4pda.to/index.php?p=1&desktop=1"
            html = """<div class="content">опрос</div>"""
        }
        assertFalse(ArticleDeferredExtrasMerger.needsDeferredExtras(page))
    }
}
