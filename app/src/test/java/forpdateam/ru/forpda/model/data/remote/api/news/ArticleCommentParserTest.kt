package forpdateam.ru.forpda.model.data.remote.api.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for [ArticleCommentParser] covering the most important public methods that were
 * extracted from [ArticleParser] as part of the §1.1 decomposition. The pattern provider is
 * stubbed because the comment-parser methods we exercise don't actually need real patterns.
 */
class ArticleCommentParserTest {

    private val noopPatternProvider = object : forpdateam.ru.forpda.model.data.storage.IPatternProvider {
        override fun getCurrentVersion(): Int = 0
        override fun getPattern(scope: String, key: String): java.util.regex.Pattern =
                java.util.regex.Pattern.compile("(?:)")
        override fun update(jsonString: String) = Unit
    }

    private fun newCommentParser(): ArticleCommentParser = ArticleCommentParser(
            patternProvider = noopPatternProvider,
            commentNumericIdRegex = Regex("""(?i)^comment-?(\d+)$""")
    )

    @Test
    fun commentNumericIdFromAttribute_extractsFromCommentPrefix() {
        val parser = newCommentParser()
        assertEquals(123, parser.commentNumericIdFromAttribute("comment-123"))
    }

    @Test
    fun commentNumericIdFromAttribute_acceptsRawNumeric() {
        val parser = newCommentParser()
        assertEquals(42, parser.commentNumericIdFromAttribute("42"))
    }

    @Test
    fun commentNumericIdFromAttribute_rejectsBlank() {
        val parser = newCommentParser()
        assertNull(parser.commentNumericIdFromAttribute(null))
        assertNull(parser.commentNumericIdFromAttribute(""))
        assertNull(parser.commentNumericIdFromAttribute("   "))
    }

    @Test
    fun commentNumericIdFromAttribute_rejectsNonPositive() {
        val parser = newCommentParser()
        assertNull(parser.commentNumericIdFromAttribute("comment-0"))
        assertNull(parser.commentNumericIdFromAttribute("0"))
    }

    @Test
    fun stripCommentForm_returnsNullForNull() {
        val parser = newCommentParser()
        assertNull(parser.stripCommentForm(null))
    }

    @Test
    fun stripCommentForm_passesThroughForEmpty() {
        val parser = newCommentParser()
        val out = parser.stripCommentForm("")
        // The noop pattern doesn't match, so the original is returned as-is.
        assertNotNull(out)
    }

    @Test
    fun commentNumericIdFromAttribute_handlesCaseInsensitivePrefix() {
        val parser = newCommentParser()
        assertEquals(7, parser.commentNumericIdFromAttribute("Comment-7"))
    }
}
