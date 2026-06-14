package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsInlineCommentHtmlTest {

    @Test
    fun metaDateHtml_uneditedComment_hasDateOnly() {
        val html = NewsInlineCommentHtml.metaDateHtml("27.05.26", isEdited = false, editedHint = "Edited")

        assertTrue(html.contains("""class="news-inline-comment-date">27.05.26</span>"""))
        assertFalse(html.contains("news-inline-comment-edited"))
        assertFalse(html.contains("news-inline-comment-meta-date"))
    }

    @Test
    fun metaDateHtml_editedComment_placesMarkerAfterDate() {
        val html = NewsInlineCommentHtml.metaDateHtml("27.05.26", isEdited = true, editedHint = "Edited")

        assertTrue(html.contains("news-inline-comment-meta-date"))
        assertTrue(html.contains("""class="news-inline-comment-date">27.05.26</span>"""))
        assertTrue(html.contains("""class="news-inline-comment-edited" title="Edited">✎</span>"""))
        assertTrue(html.indexOf("27.05.26") < html.indexOf("✎"))
        assertFalse(html.contains("news-inline-comment-content"))
    }

    @Test
    fun metaDateHtml_escapesDateAndHint() {
        val html = NewsInlineCommentHtml.metaDateHtml(
                date = "1<2>&3",
                isEdited = true,
                editedHint = "Comment was edited",
        )

        assertTrue(html.contains("1&lt;2&gt;&amp;3"))
        assertTrue(html.contains("title=\"Comment was edited\""))
    }
}
