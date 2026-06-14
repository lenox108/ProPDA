package forpdateam.ru.forpda.ui.fragments.news.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleWebViewRenderProbeTest {

    @Test
    fun `parseVisibilityResult reads plain and escaped json payload`() {
        val plain = """{"textLen":120,"contentLen":80,"hasContent":true,"hasHeader":true}"""
        val plainResult = ArticleWebViewRenderProbe.parseVisibilityResult(plain)
        assertTrue(plainResult.hasContent)
        assertTrue(plainResult.hasHeader)
        assertTrue(plainResult.contentLen >= 24)

        val escaped = """"{\"textLen\":120,\"contentLen\":80,\"hasContent\":true,\"hasHeader\":true}""""
        val escapedResult = ArticleWebViewRenderProbe.parseVisibilityResult(escaped)
        assertTrue(escapedResult.hasContent)
        assertTrue(escapedResult.hasHeader)
    }

    @Test
    fun `isArticleVisible accepts content block text`() {
        val result = ArticleVisibilityResult(
                textLen = 10,
                contentLen = 64,
                hasContent = true,
                hasHeader = false
        )

        assertTrue(
                ArticleWebViewRenderProbe.isArticleVisible(
                        result = result,
                        contentHeight = 0,
                        blankContentHeightThreshold = 4
                )
        )
    }

    @Test
    fun `isArticleVisible rejects empty body`() {
        val result = ArticleVisibilityResult()

        assertFalse(
                ArticleWebViewRenderProbe.isArticleVisible(
                        result = result,
                        contentHeight = 0,
                        blankContentHeightThreshold = 4
                )
        )
    }

    @Test
    fun `isArticleVisible rejects header only shell`() {
        val result = ArticleVisibilityResult(
                textLen = 120,
                contentLen = 0,
                hasContent = true,
                hasHeader = true
        )

        assertFalse(
                ArticleWebViewRenderProbe.isArticleVisible(
                        result = result,
                        contentHeight = 120,
                        blankContentHeightThreshold = 4
                )
        )
        assertFalse(ArticleWebViewRenderProbe.isArticleBodyVisible(result))
    }

    @Test
    fun `isArticleBodyVisible accepts non empty content block`() {
        val result = ArticleVisibilityResult(
                textLen = 10,
                contentLen = 64,
                hasContent = true,
                hasHeader = true
        )
        assertTrue(ArticleWebViewRenderProbe.isArticleBodyVisible(result))
    }

    @Test
    fun `isConfirmedRenderBlank flags painted-blank body that text probe accepts`() {
        // textContent reported the article text (bodyVisibleByText=true) but the WebView never
        // painted, so the native rendered height is at the blank threshold: must retry.
        assertTrue(
                ArticleWebViewRenderProbe.isConfirmedRenderBlank(
                        bodyVisibleByText = true,
                        contentHeight = 0,
                        blankContentHeightThreshold = 4
                )
        )
        assertTrue(
                ArticleWebViewRenderProbe.isConfirmedRenderBlank(
                        bodyVisibleByText = true,
                        contentHeight = 4,
                        blankContentHeightThreshold = 4
                )
        )
    }

    @Test
    fun `isConfirmedRenderBlank flags body the text probe rejects`() {
        assertTrue(
                ArticleWebViewRenderProbe.isConfirmedRenderBlank(
                        bodyVisibleByText = false,
                        contentHeight = 2000,
                        blankContentHeightThreshold = 4
                )
        )
    }

    @Test
    fun `isConfirmedRenderBlank accepts a real painted article`() {
        assertFalse(
                ArticleWebViewRenderProbe.isConfirmedRenderBlank(
                        bodyVisibleByText = true,
                        contentHeight = 1800,
                        blankContentHeightThreshold = 4
                )
        )
    }

    @Test
    fun `isArticleBodyVisible accepts news shell fallback text`() {
        val result = ArticleVisibilityResult(
                textLen = 93,
                contentLen = 93,
                hasContent = true,
                hasHeader = true
        )
        assertTrue(ArticleWebViewRenderProbe.isArticleBodyVisible(result))
    }

    @Test
    fun `visibilityProbeScript includes fallback selectors`() {
        val script = ArticleWebViewRenderProbe.visibilityProbeScript()
        assertTrue(script.contains("#news .content"))
        assertTrue(script.contains("newsShell"))
        assertTrue(script.contains("scrollHeight"))
    }

    @Test
    fun `parseVisibilityResult reads scrollHeight fields`() {
        val raw = """{"textLen":120,"contentLen":80,"hasContent":true,"hasHeader":true,"scrollHeight":640,"viewportHeight":720}"""
        val result = ArticleWebViewRenderProbe.parseVisibilityResult(raw)
        assertEquals(640, result.scrollHeight)
        assertEquals(720, result.viewportHeight)
    }

    @Test
    fun `shouldWaitForPageFinishBeforeBlankEscalation blocks reload until soft timeout`() {
        assertTrue(
                ArticleWebViewRenderProbe.shouldWaitForPageFinishBeforeBlankEscalation(
                        renderPageFinishedRequestId = -1,
                        requestId = 1,
                        elapsedMs = 5_500L,
                        softTimeoutMs = 6_000L,
                        contentLen = 64,
                        jsScrollHeight = 800,
                )
        )
        assertFalse(
                ArticleWebViewRenderProbe.shouldWaitForPageFinishBeforeBlankEscalation(
                        renderPageFinishedRequestId = -1,
                        requestId = 1,
                        elapsedMs = 6_500L,
                        softTimeoutMs = 6_000L,
                        contentLen = 64,
                        jsScrollHeight = 800,
                )
        )
        assertFalse(
                ArticleWebViewRenderProbe.shouldWaitForPageFinishBeforeBlankEscalation(
                        renderPageFinishedRequestId = 1,
                        requestId = 1,
                        elapsedMs = 6_500L,
                        softTimeoutMs = 6_000L,
                        contentLen = 64,
                        jsScrollHeight = 800,
                )
        )
    }

    @Test
    fun `shouldWaitForPageFinishBeforeBlankEscalation skips wait for completely empty dom`() {
        assertFalse(
                ArticleWebViewRenderProbe.shouldWaitForPageFinishBeforeBlankEscalation(
                        renderPageFinishedRequestId = -1,
                        requestId = 1,
                        elapsedMs = 4_000L,
                        softTimeoutMs = 6_000L,
                        contentLen = 0,
                        jsScrollHeight = 0,
                )
        )
    }

    @Test
    fun `shouldForceStalledEmptyDomReload triggers at stalled threshold`() {
        assertTrue(
                ArticleWebViewRenderProbe.shouldForceStalledEmptyDomReload(
                        elapsedMs = 3_000L,
                        contentLen = 0,
                        jsScrollHeight = 0,
                        domConfirmedRequestId = 0,
                        requestId = 1,
                        stalledLoadMs = 3_000L,
                )
        )
        assertFalse(
                ArticleWebViewRenderProbe.shouldForceStalledEmptyDomReload(
                        elapsedMs = 2_000L,
                        contentLen = 0,
                        jsScrollHeight = 0,
                        domConfirmedRequestId = 0,
                        requestId = 1,
                        stalledLoadMs = 3_000L,
                )
        )
    }

    @Test
    fun `shouldDeferBlankEscalation blocks reload when dom text exists but webview is unpainted`() {
        assertTrue(
                ArticleWebViewRenderProbe.shouldDeferBlankEscalation(
                        bodyVisibleByText = true,
                        contentLen = 912,
                        contentHeight = 0,
                        jsScrollHeight = 984,
                        blankContentHeightThreshold = 4
                )
        )
        assertFalse(
                ArticleWebViewRenderProbe.shouldDeferBlankEscalation(
                        bodyVisibleByText = false,
                        contentLen = 0,
                        contentHeight = 0,
                        jsScrollHeight = 984,
                        blankContentHeightThreshold = 4
                )
        )
    }

    @Test
    fun `isUnpaintedButLaidOutInDom detects dom text with zero native height`() {
        assertTrue(
                ArticleWebViewRenderProbe.isUnpaintedButLaidOutInDom(
                        bodyVisibleByText = true,
                        contentHeight = 0,
                        jsScrollHeight = 320,
                        blankContentHeightThreshold = 4
                )
        )
        assertFalse(
                ArticleWebViewRenderProbe.isUnpaintedButLaidOutInDom(
                        bodyVisibleByText = true,
                        contentHeight = 0,
                        jsScrollHeight = 12,
                        blankContentHeightThreshold = 4
                )
        )
    }
}
