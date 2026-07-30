package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * Разбор личных подписей из ПОЛНОЙ версии темы (`div.signature`). Разметка в фикстурах — верстка
 * сайта: тело поста, отбивка из двадцати дефисов, затем блок подписи внутри ячейки `post-main-<id>`.
 * Мобильная выдача, по которой рендерится страница, подписей не содержит вовсе, поэтому парсер
 * работает только по ответу отложенного десктопного обогащения.
 */
class ThemeParserSignatureTest {

    @Test
    fun `parses signatures per post id`() {
        val html = page(
                post(101, "Первый пост", """Be yourself, everyone else is taken.<br />Nokia 3310"""),
                post(102, "Второй пост", """<span style="color:#0099FF">Правила ресурса</span>"""),
        )

        val signatures = parseSignaturesByPostId(html)

        assertEquals(setOf(101, 102), signatures.keys)
        assertTrue(signatures.getValue(101).startsWith("Be yourself"))
        assertEquals("""<span style="color:#0099FF">Правила ресурса</span>""", signatures.getValue(102))
    }

    @Test
    fun `post without signature is absent from the map`() {
        val html = page(
                post(201, "Пост-шапка", signature = null),
                post(202, "Обычный пост", "Подпись автора"),
        )

        val signatures = parseSignaturesByPostId(html)

        assertNull(signatures[201])
        assertEquals("Подпись автора", signatures[202])
    }

    /** Сервер режет длинные подписи и дописывает англоязычный маркер — в клиенте он ни к чему. */
    @Test
    fun `server truncation marker is replaced with an ellipsis`() {
        val html = page(post(301, "Пост", "Nokia 3310 -&gt; S25 Ultra... Signature Truncated"))

        val signature = parseSignaturesByPostId(html).getValue(301)

        assertFalse(signature.contains("Signature Truncated"))
        assertTrue(signature.endsWith("…"))
    }

    /** Конец подписи ищется балансировкой `div`: вложенный блок не должен обрезать её на середине. */
    @Test
    fun `nested div inside a signature is kept whole`() {
        val html = page(post(401, "Пост", """До <div class="inner">внутри</div> после"""))

        assertEquals("""До <div class="inner">внутри</div> после""", parseSignaturesByPostId(html).getValue(401))
    }

    /** Подпись следующего поста не должна утекать в предыдущий, у которого её нет. */
    @Test
    fun `signature does not leak into the previous post`() {
        val html = page(
                post(501, "Без подписи", signature = null),
                post(502, "С подписью", "Моя подпись"),
        )

        val signatures = parseSignaturesByPostId(html)

        assertEquals(1, signatures.size)
        assertEquals("Моя подпись", signatures[502])
    }

    private fun page(vararg posts: String): String =
            """<div class="topic">${posts.joinToString("")}<div class="topic_foot_nav"></div></div>"""

    private fun post(postId: Int, body: String, signature: String?): String {
        val signatureBlock = signature
                ?.let { """<br /><br />--------------------<br /><div class="signature">$it</div>""" }
                .orEmpty()
        return """
            <a name="entry$postId"></a>
            <table class="ipbtable"><tr><td class="post1" id="post-main-$postId">
                <div><div class="postcolor">$body</div>$signatureBlock</div>
            </td></tr></table>
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseSignaturesByPostId(pageHtml: String): Map<Int, String> {
        val parser = ThemeParser(StubPatternProvider)
        val method = ThemeParser::class.java.getDeclaredMethod("parseSignaturesByPostId", String::class.java)
        method.isAccessible = true
        return method.invoke(parser, pageHtml) as Map<Int, String>
    }

    private object StubPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = 0
        override fun getPattern(scope: String, key: String): Pattern = Pattern.compile("")
        override fun update(jsonString: String) = Unit
    }
}
