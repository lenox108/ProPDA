package forpdateam.ru.forpda.model.data.remote.api.editpost

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
class EditPostParserTest {

    private class EditPostPatternProviderStub : IPatternProvider {
        override fun getCurrentVersion(): Int = 1
        override fun update(jsonString: String) = Unit
        override fun getPattern(scope: String, key: String): Pattern {
            require(scope == ParserPatterns.EditPost.scope) { scope }
            return when (key) {
                ParserPatterns.EditPost.form ->
                    Pattern.compile("(?is)<textarea[^>]*name=[\"'][Pp]ost[\"'][^>]*>([\\s\\S]*?)</textarea>")
                else -> throw IllegalArgumentException(key)
            }
        }
    }

    @Test
    fun parseForm_prefersFullMergedEditorTextareaOverShortQuoteCandidate() {
        val full = """
            [quote name="Ananas8" date="12.05.2026, 05:43" post=143349328]Первая часть[/quote]
            [mergetime]12.05.2026, 05:43[/mergetime]
            Добавлено: вторая часть
        """.trimIndent()
        val html = """
            <form>
                <textarea name="Post">[quote]короткая цитата[/quote]</textarea>
                <textarea id="ed-0_textarea" name="post">${full.replace("<", "&lt;").replace(">", "&gt;")}</textarea>
            </form>
        """.trimIndent()

        val form = EditPostParser(EditPostPatternProviderStub()).parseForm(html)

        assertEquals(full, form.message)
        assertTrue(form.message.contains("Добавлено"))
    }
}
