package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.entity.remote.news.Comment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsInlineCommentHtmlTest {

    private fun comment(block: Comment.() -> Unit): Comment = Comment().apply(block)

    private fun commentHtml(
            comment: Comment,
            canReply: Boolean = true,
            hasMenu: Boolean = false,
    ): String = NewsInlineCommentHtml.commentHtml(
            comment = comment,
            editedHint = "edited hint",
            deletedLabel = "deleted",
            replyLabel = "Reply",
            canReply = canReply,
            hasMenu = hasMenu,
    )

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

    @Test
    fun commentHtml_topLevelComment_hasZeroIndent() {
        val out = commentHtml(comment { id = 1; level = 0; userNick = "A"; content = "top" })

        assertTrue(out.contains("margin-left:0px"))
        assertTrue(out.contains("""data-news-comment-id="1""""))
    }

    @Test
    fun commentHtml_reply_isIndentedByLevel() {
        // Регресс-гвоздь для бага «ответы без отступа»: level должен превращаться в margin-left.
        assertTrue(commentHtml(comment { id = 2; level = 1 }).contains("margin-left:12px"))
        assertTrue(commentHtml(comment { id = 3; level = 2 }).contains("margin-left:24px"))
    }

    @Test
    fun commentHtml_deepReply_indentIsClampedTo72px() {
        val out = commentHtml(comment { id = 4; level = 20 })

        assertTrue(out.contains("margin-left:72px"))
        assertFalse(out.contains("margin-left:240px"))
    }

    @Test
    fun commentHtml_deletedComment_showsDeletedLabelAndNoActions() {
        val out = commentHtml(comment { id = 5; isDeleted = true; content = "" }, canReply = false)

        assertTrue(out.contains("deleted"))
        assertTrue(out.contains("""data-deleted="true""""))
        assertFalse(out.contains("news-inline-comment-actions"))
    }

    @Test
    fun commentHtml_replyAndMenuButtons_followPredicates() {
        val withBoth = commentHtml(comment { id = 6; content = "x" }, canReply = true, hasMenu = true)
        assertTrue(withBoth.contains("""data-news-comment-action="reply""""))
        assertTrue(withBoth.contains("""data-news-comment-action="menu""""))

        val withNeither = commentHtml(comment { id = 7; content = "x" }, canReply = false, hasMenu = false)
        assertFalse(withNeither.contains("""data-news-comment-action="reply""""))
        assertFalse(withNeither.contains("""data-news-comment-action="menu""""))
    }
}
