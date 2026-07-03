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

/**
 * Diagnostic: feeds a REAL cached commentsSource (article 458379, 31 comments incl. a
 * reply from claude.test id=10653747 nested under Lenox30) through the real parser and
 * prints exactly which comment ids survive. Reproduces "reply doesn't show" offline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleParserRealCommentsDiagTest {

    private class Stub : IPatternProvider {
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
                ParserPatterns.Articles.karmaSource ->
                    Pattern.compile("ModKarma\\((\\{[\\s\\S]*?\\})")
                ParserPatterns.Articles.karma ->
                    Pattern.compile("a^")
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    private fun flatten(root: Comment): List<Comment> {
        val out = ArrayList<Comment>()
        fun rec(c: Comment) { for (ch in c.children) { out.add(ch); rec(ch) } }
        rec(root)
        return out
    }

    /**
     * The reply from claude.test (id=10653747) is nested under Lenox30's comment. Regression:
     * both parse paths must keep ALL 31 comments incl. the nested reply, so opening the article
     * from a mention actually shows the reply. (The "reply doesn't show" hunt proved the parser
     * was innocent — this locks that in.)
     */
    @Test
    fun realCachedComments_keepAllIncludingNestedReply() {
        val html = javaClass.classLoader!!
                .getResourceAsStream("news/comments_458379.html")!!
                .bufferedReader().readText()
        val parser = ArticleParser(Stub())

        val expectedIds = listOf(
                10653333,10653335,10653342,10653362,10653747,10653421,10653561,10653345,
                10653346,10653353,10653555,10653612,10653350,10653416,10653369,10653372,
                10653616,10653389,10653507,10653390,10653391,10653553,10653718,10653446,
                10653467,10653448,10653454,10653515,10653513,10653543,10653560
        )

        val gotDom = flatten(parser.parseComments(SparseArray(), html)).map { it.id }.toSet()
        val gotTags = flatten(parser.parseCommentsViaTagsOnly(SparseArray(), html)).map { it.id }.toSet()

        assertEquals("DOM parse dropped comments: ${expectedIds.filter { it !in gotDom }}",
                expectedIds.size, gotDom.size)
        assertEquals("Tag parse dropped comments: ${expectedIds.filter { it !in gotTags }}",
                expectedIds.size, gotTags.size)
        assertTrue("nested reply 10653747 missing from DOM parse", 10653747 in gotDom)
        assertTrue("nested reply 10653747 missing from tag parse", 10653747 in gotTags)
    }
}
