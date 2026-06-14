package forpdateam.ru.forpda.model.data.remote.api.reputation

import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReputationParserTest {

    @Test
    fun parse_repRow_extractsReputationId() {
        val parser = ReputationParser(loadProductionPatterns())
        val item = parser.parse(reputationHistoryHtml()).items.single { it.id == 13268602 }
        assertEquals(13268602, item.id)
    }

    @Test
    fun parse_repRow_extractsReportUrl() {
        val parser = ReputationParser(loadProductionPatterns())
        val item = parser.parse(reputationHistoryHtml()).items.single { it.id == 13268602 }
        assertEquals(
                "https://4pda.to/forum/index.php?act=report&reputation=13268602&st=0",
                item.reportActionUrl,
        )
    }

    @Test
    fun parse_repRow_extractsAuthorSourceReasonDateAndSign() {
        val parser = ReputationParser(loadProductionPatterns())
        val item = parser.parse(reputationHistoryHtml()).items.single { it.id == 13268602 }

        assertEquals(999001, item.authorId)
        assertEquals("AuthorNick", item.authorName)
        assertEquals("Topic title here", item.sourceTitle)
        assertTrue(item.sourceUrl.orEmpty().contains("showtopic=100"))
        assertEquals("Bad behavior reason", item.reason)
        assertEquals("15.05.2025, 12:00", item.date)
        assertFalse(item.isPositive!!)
    }

    @Test
    fun parse_repRow_reportActionOnlyWhenUrlExists() {
        val parser = ReputationParser(loadProductionPatterns())
        val items = parser.parse(reputationHistoryHtml()).items
        val withReport = items.single { it.id == 13268602 }
        val withoutReport = items.single { it.id == 13268603 }

        assertTrue(withReport.hasReportAction())
        assertFalse(withoutReport.hasReportAction())
        assertNull(withoutReport.reportActionUrl)
    }

    @Test
    fun parse_loginPageRejectedByValidator() {
        assertThrows(IllegalStateException::class.java) {
            ReputationHtmlValidator.ensureHistoryPage(200, loginPageHtml())
        }
    }

    @Test
    fun parseReportForm_usesRealFormActionAndToken() {
        val parser = ReputationParser(loadProductionPatterns())
        val form = parser.parseReportForm(
                reportFormHtml(),
                "https://4pda.to/forum/index.php?act=report&reputation=13268602&st=0",
                13268602,
        )

        assertEquals("https://4pda.to/forum/index.php", form.actionUrl)
        assertEquals("report", form.fields["act"])
        assertEquals("13268602", form.fields["reputation"])
        assertEquals("abc123", form.token)
        assertEquals("message", form.messageFieldName)
    }

    private fun reputationHistoryHtml(): String = """
        <div class="maintitle"><a href="https://4pda.to/forum/index.php?showuser=123456">TestUser</a> [+10/-2]</div>
        <table>
        <tr id="rep-row-13268602">
            <td><strong><a href="https://4pda.to/forum/index.php?showuser=999001">AuthorNick</a></strong></td>
            <td><strong><a href="https://4pda.to/forum/index.php?showtopic=100&amp;view=findpost&amp;p=200">Topic title here</a></strong></td>
            <td>Bad behavior reason</td>
            <td><img src="/forum/style_images/1/rep_minus.gif" alt="-"></td>
            <td>15.05.2025, 12:00</td>
            <td><a class="g-btn blue min-mid" href="https://4pda.to/forum/index.php?act=report&amp;reputation=13268602&amp;st=0">ЖАЛОБА</a></td>
        </tr>
        <tr id="rep-row-13268603">
            <td><strong><a href="https://4pda.to/forum/index.php?showuser=888002">GoodUser</a></strong></td>
            <td></td>
            <td>Helpful post</td>
            <td><img src="/forum/style_images/1/rep_add.gif" alt="+"></td>
            <td>14.05.2025, 10:00</td>
        </tr>
        </table>
    """.trimIndent()

    private fun loginPageHtml(): String = """
        <html><body>
        <form action="index.php?act=login" id="loginform">
            <input type="password" name="PassWord">
        </form>
        </body></html>
    """.trimIndent()

    private fun reportFormHtml(): String = """
        <form action="/forum/index.php" method="post">
            <input type="hidden" name="act" value="report">
            <input type="hidden" name="reputation" value="13268602">
            <input type="hidden" name="auth_key" value="abc123">
            <input type="hidden" name="st" value="0">
            <textarea name="message"></textarea>
        </form>
    """.trimIndent()

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json"),
        ).first { it.exists() }
        val root = Json.parseToJsonElement(patternsFile.readText()).jsonObject
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        root.getValue("scopes").jsonArray.forEach { scopeElement ->
            val scope = scopeElement.jsonObject
            val name = scope.getValue("scope").jsonPrimitive.content
            val map = mutableMapOf<String, Pattern>()
            scope.getValue("patterns").jsonArray.forEach { patternElement ->
                val p = patternElement.jsonObject
                map[p.getValue("key").jsonPrimitive.content] =
                        Pattern.compile(p.getValue("value").jsonPrimitive.content)
            }
            patternsByScope[name] = map
        }
        return object : IPatternProvider {
            override fun getCurrentVersion(): Int = -1
            override fun getPattern(scope: String, key: String): Pattern =
                    patternsByScope[scope]?.get(key)
                            ?: Pattern.compile("a^")
            override fun update(jsonString: String) = Unit
        }
    }
}
