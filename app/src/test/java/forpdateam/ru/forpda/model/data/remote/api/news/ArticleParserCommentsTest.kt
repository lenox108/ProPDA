package forpdateam.ru.forpda.model.data.remote.api.news

import android.util.SparseArray
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleParserCommentsTest {

    private class ArticlesPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 29
        override fun update(jsonString: String) {}
        override fun getPattern(scope: String, key: String): Pattern {
            require(scope == ParserPatterns.Articles.scope) { scope }
            return when (key) {
                ParserPatterns.Articles.exclude_form_comment ->
                    Pattern.compile("<form[\\s\\S]*", Pattern.CASE_INSENSITIVE)
                ParserPatterns.Articles.comment_id ->
                    Pattern.compile("comment-(\\d+)")
                ParserPatterns.Articles.comment_user_id ->
                    Pattern.compile("showuser=(\\d+)")
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    @Test
    fun parseComments_divAnchor_parsesComment() {
        val html = """<ul class="comment-list"><li><div id="comment-42" class="comment">
        <a class="comment-avatar" href="https://4pda.to/forum/index.php?showuser=99">x</a>
        <a class="nickname">Kapustorei</a>
        <a class="date">01.01.2025</a>
        <p class="content">Hello</p>
    </div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(42, root.children[0].id)
        assertEquals("Kapustorei", root.children[0].userNick)
        assertEquals("Hello", root.children[0].content.orEmpty().trim())
    }

    @Test
    fun parseComments_articleAnchor_parsesComment() {
        val html = """<ul class="comment-list"><li>
        <article id="comment-7" class="comment-item">
        <a class="comment-avatar" href="showuser=3">x</a>
        <span class="nickname">Nick</span>
        <a class="date">today</a>
        <div class="content">Body</div>
        </article></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(7, root.children[0].id)
        assertTrue(root.children[0].content.orEmpty().contains("Body"))
    }

    @Test
    fun parseComments_commentsListClass_findsUl() {
        val html = """<ul class="comments-list theme_4pda"><li><div id="comment-1">
        <p class="content">X</p></div></li></ul>"""
        val parser = ArticleParser(ArticlesPatternProviderStub())
        val root = parser.parseComments(SparseArray<Comment.Karma>(), html)
        assertEquals(1, root.children.size)
        assertEquals(1, root.children[0].id)
    }
}
