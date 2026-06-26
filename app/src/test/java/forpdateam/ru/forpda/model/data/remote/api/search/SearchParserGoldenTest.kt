package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchParserGoldenTest {

    private val parser: SearchParser by lazy { SearchParser(loadProductionPatterns()) }

    private val topicListRowHtml =
            "<div data-topic=\"555\">" +
                    "<a href=\"https://4pda.to/forum/index.php?showtopic=555\">Android search hit</a>" +
                    "<span class=\"topic_desc\">[FAQ] описание<br />" +
                    "форум: <a href=\"https://4pda.to/forum/index.php?showforum=10\">Раздел</a> " +
                    "автор: <a href=\"https://4pda.to/forum/index.php?showuser=1\">AuthorNick</a> " +
                    "Послед <a href=\"https://4pda.to/forum/index.php?showuser=2\">LastPoster</a> 01.01.26" +
                    "</div>"

    private val forumPostRowHtml =
            """<html><body><div class="topic_title_post">Тема<br></div><a name="entry100"></a><div class="post_header_container"><div class="post_header"><span class="post_date">22.05.2026 &nbsp; <a>#1</a></span><font color="#3a8f3a">Постоянный</font><a data-av="avatar.jpg">Vedmak08</a><x><a href="index.php?showuser=7"> </a><span class="post_user_info"><strong>Постоянный</strong><br>Сообщений: 19 342<br></span> (<a><span data-member-rep="7">55</span></a>)<br><span class="post_action"><a href="report">r</a> <a href="edit_post">e</a> <a href="delete">d</a> <a href="CODE=02">q</a></span><div class="post_body">Search body</div></div><div class="topic_foot_nav"></div></body></html>"""

    @Test
    fun parse_forumTopics_modernTopicListLayout_extractsItem() {
        val result = parser.parse(
                topicListRowHtml,
                SearchSettings().apply { result = SearchSettings.RESULT_TOPICS.first }
        )
        assertEquals(1, result.items.size)
        assertEquals(555, result.items[0].topicId)
        assertEquals("Android search hit", result.items[0].title)
        assertEquals("LastPoster", result.items[0].nick)
    }

    private val legacyTopicRowHtml =
            "<div data-topic=\"777\">" +
                    "<a href=\"https://4pda.to/forum/index.php?showtopic=777\">Legacy topic hit</a>" +
                    "<span class=\"topic_desc\">описание legacy<br />" +
                    "форум: <a href=\"https://4pda.to/forum/index.php?showforum=42\">Раздел</a> " +
                    "автор: <a href=\"https://4pda.to/forum/index.php?showuser=3\">LegacyAuthor</a> " +
                    "Послед <a href=\"https://4pda.to/forum/index.php?showuser=4\">LegacyLast</a> 02.02.26</div>"

    @Test
    fun parse_forumTopics_legacySearchLayout_fallsBackAndExtractsItem() {
        val result = parser.parse(
                legacyTopicRowHtml,
                SearchSettings().apply { result = SearchSettings.RESULT_TOPICS.first }
        )
        assertEquals(1, result.items.size)
        assertEquals(777, result.items[0].topicId)
        assertEquals("Legacy topic hit", result.items[0].title)
        assertEquals("LegacyLast", result.items[0].nick)
    }

    @Test
    fun parse_forumPosts_topicPostLayout_extractsItem() {
        val result = parser.parse(
                forumPostRowHtml,
                SearchSettings().apply { result = SearchSettings.RESULT_POSTS.first }
        )
        assertEquals(1, result.items.size)
        assertEquals(100, result.items[0].id)
        assertEquals("Vedmak08", result.items[0].nick)
        assertEquals("Search body", result.items[0].body)
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json"),
        ).first { it.isFile }
        val patternsJson = patternsFile.readText()
        val root = Json.parseToJsonElement(patternsJson).jsonObject
        val scopes = root.getValue("scopes").jsonArray
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        scopes.forEach { scopeElement ->
            val scope = scopeElement.jsonObject
            val name = scope.getValue("scope").jsonPrimitive.content
            val map = mutableMapOf<String, Pattern>()
            val patterns = scope.getValue("patterns").jsonArray
            patterns.forEach { patternElement ->
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
                            ?: error("No pattern $scope/$key in production patterns.json")

            override fun update(jsonString: String) = Unit
        }
    }
}
