package forpdateam.ru.forpda.model.data.remote.api.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты [TopicAttachmentsParser] на фикстуре, повторяющей РЕАЛЬНУЮ разметку страницы
 * `act=attach&code=showtopic` (сверено на живом залогиненном 4pda, 2026-07): таблица, по строке
 * на вложение, колонки [иконка] | «Прикрепление» (`<a .../forum/dl/post/<id>/<name>>name</a>( Добавлено ДАТА )`)
 * | «Размер:» | «Сообщение №».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TopicAttachmentsParserTest {

    private val parser = TopicAttachmentsParser()

    private val realWorldHtml = """
        <table class="ipbtable">
            <tr><td></td><td>Прикрепление</td><td>Размер:</td><td>Сообщение №</td></tr>
            <tr>
                <td></td>
                <td><a href="https://4pda.to/forum/dl/post/7958268/Other.png">Other.png</a>( Добавлено 19.06.11, 15:08 )</td>
                <td>7 КБ</td>
                <td>7958268</td>
            </tr>
            <tr>
                <td></td>
                <td><a href="https://4pda.to/forum/dl/post/1780610/EasyEUICC-v1.6.2.apk">EasyEUICC-v1.6.2.apk</a>( Добавлено 01.01.24, 10:00 )</td>
                <td>12.49 МБ</td>
                <td>1780610</td>
            </tr>
        </table>
    """.trimIndent()

    @Test
    fun parsesNameUrlSizeAndImageFlag() {
        val items = parser.parse(realWorldHtml)
        assertEquals(2, items.size)

        val img = items[0]
        assertEquals("Other.png", img.name)
        assertTrue(img.url.endsWith("/forum/dl/post/7958268/Other.png"))
        assertTrue(img.isImage)
        assertEquals("7 КБ", img.sizeText)
        assertEquals("Добавлено 19.06.11, 15:08", img.meta)

        val apk = items[1]
        assertEquals("EasyEUICC-v1.6.2.apk", apk.name)
        assertFalse(apk.isImage)
        assertEquals("12.49 МБ", apk.sizeText)
    }

    @Test
    fun dedupesRepeatedDownloadUrls() {
        val html = """
            <table>
                <tr><td><a href="https://4pda.to/forum/dl/post/1/a.png">a.png</a></td><td>1 КБ</td></tr>
                <tr><td><a href="https://4pda.to/forum/dl/post/1/a.png">a.png</a></td><td>1 КБ</td></tr>
            </table>
        """.trimIndent()
        assertEquals(1, parser.parse(html).size)
    }

    @Test
    fun fallbackScanWhenNoTable() {
        val html = """
            <div><a href="https://4pda.to/forum/dl/post/42/notes.txt">notes.txt</a></div>
        """.trimIndent()
        val items = parser.parse(html)
        assertEquals(1, items.size)
        assertEquals("notes.txt", items[0].name)
        assertFalse(items[0].isImage)
    }

    @Test
    fun ignoresNonAttachmentLinks() {
        val html = """
            <table>
                <tr><td><a href="https://4pda.to/forum/index.php?showtopic=123">Topic link</a></td></tr>
                <tr><td><a href="https://4pda.to/forum/dl/post/9/pic.jpg">pic.jpg</a></td><td>3 КБ</td></tr>
            </table>
        """.trimIndent()
        val items = parser.parse(html)
        assertEquals(1, items.size)
        assertEquals("pic.jpg", items[0].name)
    }

    @Test
    fun emptyWhenNoAttachments() {
        assertTrue(parser.parse("<html><body>Нет вложений</body></html>").isEmpty())
    }
}
