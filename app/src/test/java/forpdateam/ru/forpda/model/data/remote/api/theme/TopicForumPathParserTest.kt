package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Разметка снята с живой страницы темы 4PDA (topic 1103268, 30.07.2026): `div.navstrip` со ссылками
 * от корня к разделу темы, разделитель — `&gt;`, самой темы в строке нет.
 */
class TopicForumPathParserTest {

    private val navstrip = """
        <div class="navstrip">
          <img src="style_images/1/nav_m.gif" border="0" alt="&gt;">
          <a href="index.php?act=idx">4PDA</a> &gt;
          <a href="index.php?showforum=281">Android</a> &gt;
          <a href="index.php?showforum=269">Android - Устройства</a> &gt;
          <a href="index.php?showforum=1061">OnePlus</a>
        </div>
    """.trimIndent()

    @Test
    fun parsesFullChainFromRootToTopicForum() {
        val path = TopicForumPathParser.parse(navstrip)
        assertEquals(4, path.size)
        assertEquals(listOf("4PDA", "Android", "Android - Устройства", "OnePlus"), path.map { it.title })
        assertEquals(listOf(0, 281, 269, 1061), path.map { it.forumId })
    }

    /** Корень — не раздел: у него нет showforum, открывать надо список форумов. */
    @Test
    fun rootItemIsMarkedAsForumRoot() {
        val path = TopicForumPathParser.parse(navstrip)
        assertTrue(path.first().isForumRoot)
        assertTrue(path.drop(1).none { it.isForumRoot })
    }

    /** Нет навигационной строки (гостевая заглушка, 404) → пустой путь, UI остаётся на прежнем поведении. */
    @Test
    fun missingNavstripGivesEmptyPath() {
        assertTrue(TopicForumPathParser.parse("<div class=\"post\">текст</div>").isEmpty())
        assertTrue(TopicForumPathParser.parse("").isEmpty())
    }

    /** Служебные ссылки строки (без showforum и без act=idx) в путь не попадают. */
    @Test
    fun ignoresNonForumLinks() {
        val html = """
            <div class="navstrip">
              <a href="index.php?act=idx">4PDA</a> &gt;
              <a href="index.php?act=search&amp;f=281">Поиск</a> &gt;
              <a href="index.php?showforum=281">Android</a>
            </div>
        """.trimIndent()
        val path = TopicForumPathParser.parse(html)
        assertEquals(listOf("4PDA", "Android"), path.map { it.title })
    }

    /** Сущности и вложенные теги в названии раздела разворачиваются. */
    @Test
    fun decodesEntitiesAndStripsInnerTags() {
        val html = """
            <div class="navstrip">
              <a href="index.php?showforum=42"><span>Связь&nbsp;&amp;&nbsp;интернет</span></a>
            </div>
        """.trimIndent()
        val path = TopicForumPathParser.parse(html)
        assertEquals(1, path.size)
        assertEquals("Связь & интернет", path.first().title)
        assertEquals(42, path.first().forumId)
    }
}
