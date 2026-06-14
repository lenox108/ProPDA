package forpdateam.ru.forpda.presentation

import io.mockk.mockk
import io.mockk.verify
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
