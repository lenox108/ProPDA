package forpdateam.ru.forpda.ui.fragments.notes.adapters

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Значок в строке закладки выводится из ссылки — здесь фиксируются формы ссылок 4pda,
 * которые кладут в закладки разные экраны приложения.
 */
class NoteLinkTypeTest {

    @Test
    fun `findpost link is a post`() {
        assertEquals(
            NoteLinkType.POST,
            NoteLinkType.of("https://4pda.to/forum/index.php?s=&showtopic=888888&view=findpost&p=91234567")
        )
    }

    @Test
    fun `topic link without post param is a topic`() {
        assertEquals(
            NoteLinkType.TOPIC,
            NoteLinkType.of("https://4pda.to/forum/index.php?showtopic=1054321")
        )
    }

    @Test
    fun `forum section link is a forum`() {
        assertEquals(
            NoteLinkType.FORUM,
            NoteLinkType.of("https://4pda.to/forum/index.php?showforum=456")
        )
    }

    @Test
    fun `dated article link is news`() {
        assertEquals(
            NoteLinkType.NEWS,
            NoteLinkType.of("https://4pda.to/2026/07/28/431234/")
        )
    }

    @Test
    fun `qms and devdb and profile links are recognised`() {
        assertEquals(NoteLinkType.QMS, NoteLinkType.of("https://4pda.to/forum/index.php?act=qms&mid=42"))
        assertEquals(NoteLinkType.DEVICE, NoteLinkType.of("https://4pda.to/devdb/oneplus_15"))
        assertEquals(NoteLinkType.PROFILE, NoteLinkType.of("https://4pda.to/forum/index.php?showuser=1234567"))
    }

    @Test
    fun `foreign or empty link falls back to plain link`() {
        assertEquals(NoteLinkType.LINK, NoteLinkType.of("https://example.com/bluetooth-codec-changer"))
        assertEquals(NoteLinkType.LINK, NoteLinkType.of(""))
        assertEquals(NoteLinkType.LINK, NoteLinkType.of(null))
    }

    @Test
    fun `legacy post prefix is stripped for display`() {
        assertEquals(
            "Клуб пользователей Kinopub Vasy 91234567",
            NoteLinkType.displayTitle("пост Клуб пользователей Kinopub Vasy 91234567", NoteLinkType.POST)
        )
    }

    @Test
    fun `prefix is kept when the bookmark is not a post`() {
        assertEquals(
            "пост про обои",
            NoteLinkType.displayTitle("пост про обои", NoteLinkType.TOPIC)
        )
    }

    @Test
    fun `title that is only the prefix is left alone`() {
        assertEquals("пост ", NoteLinkType.displayTitle("пост ", NoteLinkType.POST))
    }
}
