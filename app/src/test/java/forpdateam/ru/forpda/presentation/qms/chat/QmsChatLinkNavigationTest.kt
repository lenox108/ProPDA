package forpdateam.ru.forpda.presentation.qms.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QmsChatLinkNavigationTest {

    @Test
    fun resolveInAppUrl_forumPostLink() {
        val url = "https://4pda.to/forum/index.php?showtopic=123&view=findpost&p=141287365"
        assertEquals(url, QmsChatLinkNavigation.resolveInAppUrl(url))
    }

    @Test
    fun resolveInAppUrl_relativeForumLink() {
        assertEquals(
                "https://4pda.to/index.php?showtopic=123&view=findpost&p=141287365",
                QmsChatLinkNavigation.resolveInAppUrl(
                        "index.php?showtopic=123&view=findpost&p=141287365"
                )
        )
    }

    @Test
    fun resolveInAppUrl_legacyFourPdaRuHost() {
        val url = "http://4pda.ru/forum/index.php?showuser=323878"
        assertEquals(url, QmsChatLinkNavigation.resolveInAppUrl(url))
    }

    @Test
    fun resolveInAppUrl_rejectsAnchorOnly() {
        assertNull(QmsChatLinkNavigation.resolveInAppUrl("#entry141287365"))
    }

    @Test
    fun resolveInAppUrl_rejectsExternalHost() {
        assertNull(QmsChatLinkNavigation.resolveInAppUrl("https://example.com/topic"))
    }
}
