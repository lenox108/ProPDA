package forpdateam.ru.forpda.model.data.remote.api.topcis

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

/**
 * Проверяет, что [TopicsParser] помечает темы-указатели IPB (`&raquo;` + «перемещена:») флагом
 * `isRelocated`. Без этого флага приложение пытается открыть `?showtopic=OLD&view=getnewpost`,
 * на который сервер 4PDA отдаёт **404 без подсказок** о новой локации; именно эта связка
 * вызывает баг с темой Beholder в ветке Android - Игры (forum 213).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopicsParserRelocatedTest {

    private val parser: TopicsParser by lazy { TopicsParser(loadProductionPatterns()) }

    @Test
    fun relocatedStubMarkedAsRelocated() {
        // HTML без переносов строк — на 4PDA mobile-render выдаёт строки без отступов
        // (см. реальный ответ `?showforum=213` для темы Beholder).
        val html = "<div data-topic=\"1121632\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">&raquo;</span>перемещена: " +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=1121632\">Beholder: Conductor[Release]</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">[Стратегия] Симулятор<br /></span>" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=8361955\">Dav124</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=1415763\">wizard</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(html, argId = 213)
        val item = data.topicItems.firstOrNull { it.id == 1121632 }
        assertNotNull("relocated stub should be parsed", item)
        assertTrue("stub should be flagged relocated only with 'перемещена' marker", item!!.isRelocated)
        assertEquals("https://4pda.to/forum/index.php?showtopic=1121632", item.listingHref)
    }

    @Test
    fun ordinaryTopicNotMarkedAsRelocated() {
        val html = "<div data-topic=\"555\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">+</span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=555\">Обычная тема</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(html, argId = 213)
        val item = data.topicItems.firstOrNull { it.id == 555 }
        assertNotNull(item)
        assertFalse("normal topic must not be flagged relocated", item!!.isRelocated)
    }

    @Test
    fun chevronWithoutMovedText_notMarkedAsRelocated() {
        val html = "<div data-topic=\"777\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">&raquo;</span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=777\">Тема со стрелкой, но без переноса</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(html, argId = 213)
        val item = data.topicItems.firstOrNull { it.id == 777 }
        assertNotNull(item)
        assertFalse("chevron alone must not mark relocated", item!!.isRelocated)
    }

    @Test
    fun hrefExtraction_capturesShowtopicLink() {
        val html = "<div data-topic=\"888\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">+</span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=888&view=getlastpost\">Нужная тема</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(html, argId = 213)
        val item = data.topicItems.firstOrNull { it.id == 888 }
        assertNotNull(item)
        assertEquals("https://4pda.to/forum/index.php?showtopic=888&view=getlastpost", item!!.listingHref)
    }

    @Test
    fun topicTitleHrefExtraction_ignoresIconShowtopicLink() {
        // Регресс: в `topic_title` может быть несколько <a href="...showtopic=..."> (иконки/метки).
        // Раньше парсер мог взять ПЕРВУЮ ссылку и открыть чужой showtopic.
        val html = "<div data-topic=\"1121632\"><div class=\"topic_title\">" +
                "<span class=\"modifier\">+</span>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=766822\"><img src=\"/i.png\" /></a>" +
                "<a href=\"https://4pda.to/forum/index.php?showtopic=1121632\">Beholder: Conductor[Release]</a>" +
                "</div><div class=\"topic_body\">" +
                "<span class=\"topic_desc\">автор: " +
                "<a href=\"https://4pda.to/forum/index.php?showuser=1\">A</a></span>" +
                "<br /><a href=\"https://4pda.to/forum/index.php?showuser=2\">B</a> 01.01.26" +
                "</div></div>"

        val data = parser.parse(html, argId = 213)
        val item = data.topicItems.firstOrNull { it.title?.contains("Beholder") == true }
        assertNotNull(item)
        assertEquals("https://4pda.to/forum/index.php?showtopic=1121632", item!!.listingHref)
        assertEquals(1121632, item.id)
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
