package forpdateam.ru.forpda.presentation.articles.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBodyThemeNormalizerTest {

    @Test
    fun `light mode strips embedded style blocks and dark inline backgrounds`() {
        val html = """
            <style>body{background:#141414;color:#fff}</style>
            <div class="theme-dark" style="background-color:#212121;color:#eeeeee">
              <p>Text</p>
            </div>
        """.trimIndent()

        val sanitized = ArticleBodyThemeNormalizer.sanitizeForAppTheme(html, isNight = false).orEmpty()

        assertFalse(sanitized.contains("<style"))
        assertFalse(sanitized.contains("theme-dark"))
        assertFalse(sanitized.contains("#212121"))
        assertFalse(sanitized.contains("#141414"))
        assertTrue(sanitized.contains("Text"))
    }

    @Test
    fun `dark mode keeps body styles but removes embedded stylesheet links`() {
        val html = """
            <link rel="stylesheet" href="https://4pda.to/dark.css">
            <p style="background:#212121">Night block</p>
        """.trimIndent()

        val sanitized = ArticleBodyThemeNormalizer.sanitizeForAppTheme(html, isNight = true).orEmpty()

        assertFalse(sanitized.contains("<link"))
        assertTrue(sanitized.contains("#212121"))
    }

    @Test
    fun `security sanitizer strips executable article markup`() {
        val html = """
            <div onclick="alert(1)">
              <script>alert(1)</script>
              <a href="javascript:alert(1)">bad</a>
              <img src="https://4pda.to/image.png" onerror="alert(1)">
              <iframe src="javascript:alert(1)"></iframe>
              <iframe src="https://www.youtube.com/embed/video"></iframe>
              <object data="https://evil.example/payload"></object>
            </div>
        """.trimIndent()

        val sanitized = ArticleHtmlSecuritySanitizer.sanitize(html).orEmpty()

        assertFalse(sanitized.contains("<script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick", ignoreCase = true))
        assertFalse(sanitized.contains("onerror", ignoreCase = true))
        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertFalse(sanitized.contains("<object", ignoreCase = true))
        assertTrue(sanitized.contains("https://4pda.to/image.png"))
        assertTrue(sanitized.contains("https://www.youtube.com/embed/video"))
    }
}
