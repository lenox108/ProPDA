package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleRenderInflightPolicyTest {

    private fun snapshot(
            inflightArticleId: Int = 42,
            inflightHtmlHash: Int = 100,
            renderLoadDispatched: Boolean = false,
            articleRequestId: Int = 1,
            domContentLoadedRequestId: Int = 0,
            lastDomConfirmedArticleId: Int = -1,
            lastRequestedArticleId: Int = 42,
    ) = ArticleRenderInflightPolicy.Snapshot(
            inflightArticleId = inflightArticleId,
            inflightHtmlHash = inflightHtmlHash,
            renderLoadDispatched = renderLoadDispatched,
            articleRequestId = articleRequestId,
            domContentLoadedRequestId = domContentLoadedRequestId,
            lastDomConfirmedArticleId = lastDomConfirmedArticleId,
            lastRequestedArticleId = lastRequestedArticleId,
    )

    @Test
    fun `skip inflight duplicate only after load was dispatched`() {
        val beforeDispatch = snapshot(renderLoadDispatched = false)
        assertFalse(
                ArticleRenderInflightPolicy.shouldSkipInflightDuplicate(
                        force = false,
                        articleId = 42,
                        htmlHash = 100,
                        snapshot = beforeDispatch
                )
        )

        val inFlight = snapshot(renderLoadDispatched = true, articleRequestId = 3)
        assertTrue(
                ArticleRenderInflightPolicy.shouldSkipInflightDuplicate(
                        force = false,
                        articleId = 42,
                        htmlHash = 100,
                        snapshot = inFlight
                )
        )
    }

    @Test
    fun `force bypasses inflight duplicate guard`() {
        assertFalse(
                ArticleRenderInflightPolicy.shouldSkipInflightDuplicate(
                        force = true,
                        articleId = 42,
                        htmlHash = 100,
                        snapshot = snapshot(renderLoadDispatched = true, articleRequestId = 2)
                )
        )
    }

    @Test
    fun `ensure render forces when inflight was never dispatched`() {
        assertTrue(
                ArticleRenderInflightPolicy.shouldForceEnsureRender(
                        articleId = 42,
                        snapshot = snapshot(renderLoadDispatched = false)
                )
        )
    }

    @Test
    fun `ensure render forces when load dispatched but not confirmed`() {
        assertTrue(
                ArticleRenderInflightPolicy.shouldForceEnsureRender(
                        articleId = 42,
                        snapshot = snapshot(
                                renderLoadDispatched = true,
                                articleRequestId = 5,
                                domContentLoadedRequestId = 0
                        )
                )
        )
    }

    @Test
    fun `ensure render forces when inflight cleared after timeout but last requested remains`() {
        assertTrue(
                ArticleRenderInflightPolicy.shouldForceEnsureRender(
                        articleId = 42,
                        snapshot = snapshot(
                                inflightArticleId = -1,
                                renderLoadDispatched = false,
                                lastRequestedArticleId = 42,
                        )
                )
        )
    }

    @Test
    fun `ensure render does not force after dom confirmation`() {
        assertFalse(
                ArticleRenderInflightPolicy.shouldForceEnsureRender(
                        articleId = 42,
                        snapshot = snapshot(
                                renderLoadDispatched = true,
                                articleRequestId = 5,
                                domContentLoadedRequestId = 5,
                                lastDomConfirmedArticleId = 42
                        )
                )
        )
    }
}
