package forpdateam.ru.forpda.presentation

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LinkHandlerUrlPolicyTest {

    private val systemLinkHandler = mockk<ISystemLinkHandler>(relaxed = true)
    private val router = mockk<TabRouter>(relaxed = true)
    private val linkHandler = LinkHandler(systemLinkHandler, router)

    @Test
    fun `handle blocks unsafe schemes before external handler`() {
        linkHandler.handle("javascript:alert(1)", router)
        linkHandler.handle("file:///sdcard/secret.txt", router)
        linkHandler.handle("data:text/html,<script>alert(1)</script>", router)
        linkHandler.handle("content://com.example/item", router)
        linkHandler.handle("https://4pda.to/%0d%0ajavascript:alert(1)", router)

        verify(exactly = 0) { systemLinkHandler.handle(any()) }
        verify(exactly = 0) { systemLinkHandler.handleDownload(any(), any(), any(), any()) }
    }

    @Test
    fun `news comment mention url with slug parses commentId for anchor`() {
        // Реальная ссылка news-упоминания: слаг статьи между id и якорем #comment.
        val screen = slot<Screen>()
        linkHandler.handle(
                "https://4pda.to/2026/07/03/458379/onlajn_watch_dogs_2_vzletel_iz_za_skidki/#comment10653747",
                router
        )
        verify { router.navigateTo(capture(screen)) }
        val article = screen.captured as Screen.ArticleDetail
        assertEquals(458379, article.articleId)
        assertEquals(10653747, article.commentId)
    }

    @Test
    fun `news url without comment fragment still opens article`() {
        val screen = slot<Screen>()
        linkHandler.handle(
                "https://4pda.to/2026/07/03/458379/onlajn_watch_dogs_2_vzletel/",
                router
        )
        verify { router.navigateTo(capture(screen)) }
        val article = screen.captured as Screen.ArticleDetail
        assertEquals(458379, article.articleId)
        assertEquals(-1, article.commentId)
    }

    @Test
    fun `handle keeps external http links external`() {
        linkHandler.handle("https://example.com/path", router)

        verify { systemLinkHandler.handle("https://example.com/path") }
    }

    @Test
    fun `handle blocks unsafe pages go redirect`() {
        linkHandler.handle(
                "https://4pda.to/pages/go/?u=javascript%3Aalert(1)",
                router
        )

        verify(exactly = 0) { systemLinkHandler.handle(any()) }
    }

    @Test
    fun `handle keeps safe pages go redirect external`() {
        linkHandler.handle(
                "https://4pda.to/pages/go/?u=https%3A%2F%2Fexample.com%2Fdownload",
                router
        )

        verify { systemLinkHandler.handle("https://example.com/download") }
    }
}
