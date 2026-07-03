package forpdateam.ru.forpda.model.data.remote.api.mentions

import org.junit.Assert.assertEquals
import org.junit.Test

class MentionUrlPreferenceTest {

    @Test
    fun preferMentionPostUrl_prefersFindpostHref() {
        val row = """<a href="index.php?showtopic=1">t</a> <a href="index.php?showtopic=1&amp;view=findpost&amp;p=99">go</a>"""
        val primary = "index.php?showtopic=1"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=99",
                preferMentionPostUrl(row, primary)
        )
    }

    @Test
    fun preferMentionPostUrl_prefersShowtopicWithP() {
        val row = """<a href="https://4pda.to/forum/index.php?showtopic=5&amp;p=12">x</a>"""
        val primary = "https://4pda.to/forum/index.php?showtopic=5"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=5&p=12",
                preferMentionPostUrl(row, primary)
        )
    }

    @Test
    fun preferMentionPostUrl_fallbackPrimaryDecodesAmp() {
        val primary = "https://4pda.to/forum/index.php?showtopic=1&amp;st=0"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&st=0",
                preferMentionPostUrl("<div>no extra links</div>", primary)
        )
    }

    @Test
    fun patchMentionLink_addsFindpostFromDataPostId() {
        val row = """<tr><td data-post-id="77"><a href="index.php?showtopic=3">t</a></td></tr>"""
        val link = "https://4pda.to/forum/index.php?showtopic=3"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=77",
                patchMentionLinkIfTopicOnly(link, row)
        )
    }

    @Test
    fun decodeMentionNickIfBase64_decodesBase64AuthorField() {
        // Реальный кейс: в строке news-mention автор приходит base64 (0JfQsNGHPw== -> Зач?).
        assertEquals("Зач?", decodeMentionNickIfBase64("0JfQsNGHPw=="))
    }

    @Test
    fun decodeMentionNickIfBase64_keepsOrdinaryNicks() {
        // Обычные ники не трогаем: не base64 по длине/charset, либо декодятся в мусор.
        assertEquals("karton1", decodeMentionNickIfBase64("karton1"))
        assertEquals("iSpark", decodeMentionNickIfBase64("iSpark"))
        assertEquals("26d9", decodeMentionNickIfBase64("26d9")) // валидный base64-charset, но декод в невалидный UTF-8
        assertEquals("Megaherz09", decodeMentionNickIfBase64("Megaherz09"))
        assertEquals(null, decodeMentionNickIfBase64(null))
        assertEquals("", decodeMentionNickIfBase64(""))
    }

    @Test
    fun preferMentionPostUrl_ignoresDegenerateFindpost_keepsNewsCommentHref() {
        // Реальная строка упоминания-ответа на комментарий к новости содержит пустой
        // placeholder findpost (showtopic=&view=findpost&p=). Раньше он возвращался как есть,
        // и тап уходил в форумный 404 вместо новости с комментарием.
        val row = """
            News: <a href="https://4pda.to/2026/07/02/458351/#comment10652404">Sony</a>
            <a href="index.php?showtopic=&amp;view=findpost&amp;p=">go</a>
        """.trimIndent()
        val primary = "https://4pda.to/2026/07/02/458351/#comment10652404"
        val resolved = preferMentionPostUrl(row, primary)
        assertEquals("https://4pda.to/2026/07/02/458351/#comment10652404", resolved)
    }

    @Test
    fun patchMentionLink_ignoresDegenerateFindpostLink() {
        // Пустой findpost на входе не должен «проглатываться» как валидная ссылка.
        val link = "https://4pda.to/forum/index.php?showtopic=&view=findpost&p="
        // Нет data-post-id/entry — патчить нечем, но и ломаться не должно: вернём как есть
        // (реальный фикс — не отдавать такой link из preferMentionPostUrl вообще).
        assertEquals(link, patchMentionLinkIfTopicOnly(link, "<div>no ids</div>"))
    }

    @Test
    fun preferMentionPostUrl_prefersAnchorEntryPostId() {
        val row = """<a href="index.php?showtopic=3">t</a> <a href="#entry88">post</a>"""
        val primary = "https://4pda.to/forum/index.php?showtopic=3"
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=88",
                patchMentionLinkIfTopicOnly(preferMentionPostUrl(row, primary), row)
        )
    }
}
