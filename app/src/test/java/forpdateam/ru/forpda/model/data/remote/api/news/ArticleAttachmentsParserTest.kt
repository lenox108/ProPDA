package forpdateam.ru.forpda.model.data.remote.api.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for [ArticleAttachmentsParser] covering the most important public methods that
 * were extracted from [ArticleParser] as part of the §1.1 decomposition.
 */
class ArticleAttachmentsParserTest {

    private val firstImageTagRegex = Regex("""(?is)<img\b[^>]*>""")
    private val articleLightboxImageRegex = Regex(
            """(?is)<a\b(?=[^>]*\bdata-lightbox\s*=\s*(["'])post-\d+\1)[^>]*>[\s\S]*?<img\b[^>]*>[\s\S]*?</a>"""
    )
    private val firstSourceTagRegex = Regex("""(?is)<source\b[^>]*>""")

    private fun newParser(): ArticleAttachmentsParser = ArticleAttachmentsParser(
            articleFromHtml = { input -> input },
            articleLightboxImageRegex = articleLightboxImageRegex,
            firstImageTagRegex = firstImageTagRegex,
            firstSourceTagRegex = firstSourceTagRegex,
            articleUrlFromResponse = { _ -> "https://4pda.to/2024/01/01/12345.html" },
            selectArticleImageUrl = { block, _ -> block },
            parseSrcset = { srcset ->
                srcset?.split(',')
                        ?.mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
                        ?.firstOrNull()
            },
            getAttribute = { tag, name ->
                val pattern = Regex("""(?is)\b${Regex.escape(name)}\s*=\s*(["'])(.*?)\1""")
                pattern.find(tag)?.groupValues?.getOrNull(2)
            }
    )

    @Test
    fun normalizeArticleImageUrl_passesThroughHttps() {
        val parser = newParser()
        assertEquals(
                "https://example.com/img.jpg",
                parser.normalizeArticleImageUrl("https://example.com/img.jpg", "irrelevant")
        )
    }

    @Test
    fun normalizeArticleImageUrl_upgradesHttpToHttps() {
        val parser = newParser()
        assertEquals(
                "https://example.com/img.jpg",
                parser.normalizeArticleImageUrl("http://example.com/img.jpg", "irrelevant")
        )
    }

    @Test
    fun normalizeArticleImageUrl_resolvesProtocolRelative() {
        val parser = newParser()
        assertEquals(
                "https://example.com/img.jpg",
                parser.normalizeArticleImageUrl("//example.com/img.jpg", "irrelevant")
        )
    }

    @Test
    fun normalizeArticleImageUrl_resolvesRootRelative() {
        val parser = newParser()
        assertEquals(
                "https://4pda.to/img.jpg",
                parser.normalizeArticleImageUrl("/img.jpg", "irrelevant")
        )
    }

    @Test
    fun normalizeArticleImageUrl_resolvesBareRelativeAgainstArticleUrl() {
        val parser = newParser()
        assertEquals(
                "https://4pda.to/2024/01/01/img.jpg",
                parser.normalizeArticleImageUrl("img.jpg", "ignored")
        )
    }

    @Test
    fun normalizeArticleImageUrl_returnsNullForBlank() {
        val parser = newParser()
        assertNull(parser.normalizeArticleImageUrl(null, "ignored"))
        assertNull(parser.normalizeArticleImageUrl("", "ignored"))
        assertNull(parser.normalizeArticleImageUrl("   ", "ignored"))
    }

    @Test
    fun urlsReferToSameImage_stripsDprSuffix() {
        val parser = newParser()
        assertTrue(parser.urlsReferToSameImage(
                "https://example.com/img-1024x768.jpg",
                "https://example.com/img-2048x1536.jpg"
        ))
    }

    @Test
    fun urlsReferToSameImage_ignoresQueryString() {
        val parser = newParser()
        assertTrue(parser.urlsReferToSameImage(
                "https://example.com/img.jpg?w=100",
                "https://example.com/img.jpg?w=200"
        ))
    }

    @Test
    fun urlsReferToSameImage_rejectsNullsAndBlanks() {
        val parser = newParser()
        assertFalse(parser.urlsReferToSameImage(null, "https://example.com/img.jpg"))
        assertFalse(parser.urlsReferToSameImage("https://example.com/img.jpg", null))
        assertFalse(parser.urlsReferToSameImage("", "https://example.com/img.jpg"))
    }

    @Test
    fun urlsReferToSameImage_rejectsDifferentPaths() {
        val parser = newParser()
        assertFalse(parser.urlsReferToSameImage(
                "https://example.com/a.jpg",
                "https://example.com/b.jpg"
        ))
    }
}
