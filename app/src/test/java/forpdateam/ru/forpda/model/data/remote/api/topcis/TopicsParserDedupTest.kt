package forpdateam.ru.forpda.model.data.remote.api.topcis

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopicsParserDedupTest {

    private val parser: TopicsParser by lazy { TopicsParser(loadProductionPatterns()) }

    @Test
    fun duplicateNonPinnedTopics_areDeduplicatedById() {
        val row = "<div data-topic=\"555\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">+</span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=555\">Обычная тема</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(row + row, argId = 213)
        assertEquals(1, data.topicItems.count { it.id == 555 })
    }

    @Test
    fun pinnedTopic_hasPriorityOverRegular() {
        val regular = "<div data-topic=\"777\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">+</span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=777\">Тема</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"
        val pinned = "<div data-topic=\"777\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">+</span>" +
                "(!)" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=777\">Тема</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(regular + pinned, argId = 213)
        assertEquals(0, data.topicItems.count { it.id == 777 })
        assertEquals(1, data.pinnedItems.count { it.id == 777 })
        assertTrue(data.pinnedItems.first { it.id == 777 }.isPinned)
    }

    @Test
    fun topicPaginationLinks_areParsedIntoPagesCount() {
        val row = "<div data-topic=\"888\"><div class=\"topic_title\">" +
                "<span class=\"modifier\"></span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=888\">Многостраничная тема</a>" +
                "<a onclick=\"return tpg(20,2540)\">128</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">Orient9</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(row, argId = 213)

        assertEquals(128, data.topicItems.single { it.id == 888 }.pages)
    }

    @Test
    fun topicRow_parsesDistinctStarterLastPosterAndPages() {
        val row = "<div data-topic=\"999\"><div class=\"topic_title\">" +
                "<span class=\"modifier\"></span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=999\">OnePlus 15 - Обсуждение</a>" +
                "<a onclick=\"return tpg(20,23080)\">1155</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">#Санёк</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">Lenox30</a> Сегодня, 10:30" +
                "</div></div>"

        val item = parser.parse(row, argId = 213).topicItems.single { it.id == 999 }

        assertEquals("#Санёк", item.authorNick)
        assertEquals("Lenox30", item.lastUserNick)
        assertEquals(1155, item.pages)
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsJson = File("src/main/assets/patterns.json").readText()
        val root = JSONObject(patternsJson)
        val scopes = root.getJSONArray("scopes")
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        for (i in 0 until scopes.length()) {
            val scope = scopes.getJSONObject(i)
            val name = scope.getString("scope")
            val map = mutableMapOf<String, Pattern>()
            val patterns = scope.getJSONArray("patterns")
            for (j in 0 until patterns.length()) {
                val p = patterns.getJSONObject(j)
                map[p.getString("key")] = Pattern.compile(p.getString("value"))
            }
            patternsByScope[name] = map
        }
        return object : IPatternProvider {
            override fun getCurrentVersion(): Int = -1
            override fun getPattern(scope: String, key: String): Pattern {
                return patternsByScope[scope]?.get(key)
                        ?: error("No pattern $scope/$key in production patterns.json")
            }

            override fun update(jsonString: String) = Unit
        }
    }
}

