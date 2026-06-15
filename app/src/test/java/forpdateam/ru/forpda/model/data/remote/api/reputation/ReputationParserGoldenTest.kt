package forpdateam.ru.forpda.model.data.remote.api.reputation

import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

/**
 * Golden tests for [ReputationParser] that load saved HTML fixtures from
 * `app/src/test/resources/parser/reputation/`.
 *
 * These tests fix the current (regex) behavior so §2.1 (Jsoup migration)
 * and §1.1 (ArticleParser decomposition) can be verified by re-running
 * the same assertions against the new implementation.
 *
 * The fixture contents are synthetic but mirror the desktop 4pda
 * `act=reputation` page structure: `rep-row-{id}` rows with the
 * expected cells, and a pagination block at the bottom.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReputationParserGoldenTest {

    @Test
    fun parsesHeader_userNickAndRepCounts() {
        val data = parser().parse(loadFixture("parser/reputation/reputation_basic.html"))
        assertEquals(123456, data.id)
        assertEquals("TestUser", data.nick)
        assertEquals(42, data.positive)
        assertEquals(7, data.negative)
    }

    @Test
    fun parsesRepRow_extractsAllFields() {
        val data = parser().parse(loadFixture("parser/reputation/reputation_basic.html"))
        val item = data.items.single { it.id == 13268602 }

        assertEquals(999001, item.userId)
        assertEquals("AuthorNick", item.userNick)
        assertEquals("Bad behavior reason", item.title)
        assertTrue(item.sourceUrl.orEmpty().contains("showtopic=100"))
        assertEquals("Topic title here", item.sourceTitle)
        assertEquals("15.05.2025, 12:00", item.date)
        assertEquals(false, item.isPositive)
    }

    @Test
    fun parsesRepRow_extractsReportActionUrlWhenPresent() {
        val data = parser().parse(loadFixture("parser/reputation/reputation_basic.html"))
        val withReport = data.items.single { it.id == 13268602 }
        val withoutReport = data.items.single { it.id == 13268603 }

        assertTrue(withReport.hasReportAction())
        assertEquals(
                "https://4pda.to/forum/index.php?act=report&reputation=13268602&st=0",
                withReport.reportActionUrl,
        )
        assertFalse(withoutReport.hasReportAction())
        assertNull(withoutReport.reportActionUrl)
    }

    @Test
    fun parsesRepRow_positiveSignForAddImage() {
        val data = parser().parse(loadFixture("parser/reputation/reputation_basic.html"))
        val positive = data.items.single { it.id == 13268603 }
        assertEquals(true, positive.isPositive)
        assertEquals(888002, positive.userId)
    }

    @Test
    fun parsesReportForm_extractsFormUrlAndHiddenFields() {
        val form = parser().parseReportForm(
                loadFixture("parser/reputation/report_form.html"),
                "https://4pda.to/forum/index.php?act=report&reputation=13268602&st=0",
                13268602,
        )

        assertEquals("https://4pda.to/forum/index.php", form.actionUrl)
        assertEquals("report", form.fields["act"])
        assertEquals("13268602", form.fields["reputation"])
        assertEquals("abc123", form.token)
        assertEquals("message", form.messageFieldName)
    }

    @Test
    fun parsesReportForm_actionIsAlwaysPresent() {
        val form = parser().parseReportForm(
                loadFixture("parser/reputation/report_form.html"),
                "https://4pda.to/forum/index.php?act=report&reputation=13268602&st=0",
                13268602,
        )
        assertNotNull(form.actionUrl)
        assertTrue(form.actionUrl.isNotBlank())
    }

    /**
     * Jsoup path: the new implementation must produce the same
     * observable model fields as the legacy regex path on the golden
     * fixture. This is the §2.1 parity check that lets us flip the
     * default in [ReputationParser] from regex to Jsoup.
     */
    @Test
    fun jsoupPath_matchesRegexPath_onGolden() {
        val html = loadFixture("parser/reputation/reputation_basic.html")
        val regex = parser(useJsoup = false).parse(html)
        val jsoup = parser(useJsoup = true).parse(html)

        assertEquals(regex.id, jsoup.id)
        assertEquals(regex.nick, jsoup.nick)
        assertEquals(regex.positive, jsoup.positive)
        assertEquals(regex.negative, jsoup.negative)
        assertEquals(regex.items.size, jsoup.items.size)
        regex.items.forEachIndexed { idx, expected ->
            val actual = jsoup.items[idx]
            assertEquals(expected.id, actual.id)
            assertEquals(expected.userId, actual.userId)
            assertEquals(expected.userNick, actual.userNick)
            assertEquals(expected.title, actual.title)
            assertEquals(expected.sourceUrl, actual.sourceUrl)
            assertEquals(expected.sourceTitle, actual.sourceTitle)
            assertEquals(expected.image, actual.image)
            assertEquals(expected.isPositive, actual.isPositive)
            assertEquals(expected.date, actual.date)
            assertEquals(expected.reportActionUrl, actual.reportActionUrl)
        }
    }

    private fun parser(useJsoup: Boolean = false): ReputationParser =
            ReputationParser(loadProductionPatterns(), useJsoup = useJsoup)

    private fun loadFixture(path: String): String =
            requireNotNull(javaClass.classLoader!!.getResourceAsStream(path)) {
                "Fixture not found on classpath: $path"
            }.bufferedReader(Charsets.UTF_8).use { it.readText() }

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

    @Suppress("unused")
    private val unusedScope = ParserPatterns.Reputation.scope
}
