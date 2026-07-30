package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разметка блока «сейчас эту тему читают» видна только авторизованному пользователю, а скины форума
 * за годы менялись — поэтому парсер проверяем на нескольких формах фразы, а не на одной строке.
 */
class TopicActiveUsersParserTest {

    private val parser = TopicActiveUsersParser()

    @Test
    fun `classic ipb phrase with guests and member list`() {
        val html = """
            <div class="post">…посты…</div>
            <table class="ipbtable">
              <tr><td class="titlemedium"><b>3 чел. читают эту тему</b> (гостей: 2, скрытых: 0)</td></tr>
              <tr><td class="row2">1 пользователей:
                <a href="https://4pda.to/forum/index.php?showuser=123">Denis_K</a>
              </td></tr>
            </table>
            <div id="gfooter"><a href="index.php?showuser=999">Профиль</a></div>
        """.trimIndent()

        val readers = requireNotNull(parser.parse(html))

        assertEquals(3, readers.total)
        assertEquals(2, readers.guests)
        assertEquals(0, readers.hidden)
        assertEquals(listOf(123 to "Denis_K"), readers.members.map { it.userId to it.nick })
    }

    /**
     * Разметка снята с живой страницы (полная версия сайта, авторизованная сессия, тема 1121483):
     * фраза и список читателей лежат в РАЗНЫХ дивах одного `borderwrap`, а ник завёрнут в `<span>`
     * внутри ссылки на профиль.
     */
    @Test
    fun `real 4pda markup with formsubtitle and row1`() {
        val html = """
            <a class="g-btn blue min-mid" href="#">ОТВЕТИТЬ</a>
            <div class="borderwrap">
              <div class="formsubtitle"><b>11</b> чел. читают эту тему (гостей: 2, скрытых пользователей: 4)</div>
              <div class="row1">Пользователей: <b>5</b>
                <a href="https://4pda.to/forum/index.php?showuser=1"><span>claude.test</span></a>
                <a href="https://4pda.to/forum/index.php?showuser=2"><span>Аnderson</span></a>
                <a href="https://4pda.to/forum/index.php?showuser=3"><span>Bublikov34</span></a>
                <a href="https://4pda.to/forum/index.php?showuser=4"><span>nomeds</span></a>
                <a href="https://4pda.to/forum/index.php?showuser=5"><span>Kaisertw</span></a>
              </div>
            </div>
            <div id="gfooter"><a href="index.php?showuser=99">Профиль</a></div>
        """.trimIndent()

        val readers = requireNotNull(parser.parse(html))

        assertEquals(11, readers.total)
        assertEquals(2, readers.guests)
        assertEquals(4, readers.hidden)
        assertEquals(
                listOf("claude.test", "Аnderson", "Bublikov34", "nomeds", "Kaisertw"),
                readers.members.map { it.nick },
        )
    }

    @Test
    fun `number wrapped in tags and nbsp still parses`() {
        val html = """<td><b>7</b>&nbsp;чел.&nbsp;читают эту тему (гостей: 5, скрытых: 1)</td></table>"""

        val readers = requireNotNull(parser.parse(html))

        assertEquals(7, readers.total)
        assertEquals(5, readers.guests)
        assertEquals(1, readers.hidden)
    }

    @Test
    fun `colon form sejchas etu temu chitayut`() {
        val html = """
            <div class="topic-readers">Сейчас эту тему читают: 4 (гостей: 3)
              <a href="index.php?showuser=42">j.golt</a>
            </div>
        """.trimIndent()

        val readers = requireNotNull(parser.parse(html))

        assertEquals(4, readers.total)
        assertEquals(3, readers.guests)
        assertEquals(listOf("j.golt"), readers.members.map { it.nick })
    }

    @Test
    fun `total falls back to members plus guests when the phrase carries no number`() {
        val html = """
            <div>Эту тему просматривают:
              <a href="index.php?showuser=1">A</a>, <a href="index.php?showuser=2">B</a> (гостей: 2)
            </div>
        """.trimIndent()

        val readers = requireNotNull(parser.parse(html))

        assertEquals(4, readers.total)
        assertEquals(2, readers.members.size)
    }

    @Test
    fun `duplicate member links are collapsed by user id`() {
        val html = """
            <td>2 чел. читают эту тему (гостей: 0)
              <a href="index.php?showuser=7"><img src="ava.png"/></a><a href="index.php?showuser=7">Nick</a>
            </td></table>
        """.trimIndent()

        val readers = requireNotNull(parser.parse(html))

        assertEquals(listOf("Nick"), readers.members.map { it.nick })
    }

    @Test
    fun `guest page without the block yields null`() {
        val html = """
            <div class="post">Обычная страница темы без блока читателей.</div>
            <a href="index.php?showuser=5">Профиль</a>
            <div id="gfooter">Сейчас: 30.07.26, 09:02</div>
        """.trimIndent()

        assertNull(parser.parse(html))
    }

    @Test
    fun `blank response yields null`() {
        assertNull(parser.parse(""))
    }
}
