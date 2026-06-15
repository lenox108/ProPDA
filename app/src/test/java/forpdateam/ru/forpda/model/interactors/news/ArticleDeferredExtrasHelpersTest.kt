package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Golden tests for [ArticleDeferredExtrasHelpers] — pure helpers used
 * by the deferred-extras phase in [ArticleInteractor]. Extracted from
 * the god-class (god-class §1.1).
 *
 * Robolectric runner is required because [ArticleFetchResult] carries
 * a [NetworkResponse] which has Android dependencies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArticleDeferredExtrasHelpersTest {

    @Test
    fun `buildDeferredExtrasFetch reuses parsed body when present`() {
        val source = DetailsPage().apply {
            id = 42
            title = "T"
            url = "https://4pda.to/index.php?p=42"
            html = "ORIGINAL"
        }
        val fetch = ArticleFetchResult(
                page = source,
                rawBody = "RAW",
                response = stubResponse(),
                originalUrl = source.url!!,
                probeUrl = source.url!!,
                parsedBodyHtml = "PARSED"
        )
        val out = ArticleDeferredExtrasHelpers.buildDeferredExtrasFetch(fetch)
        assertEquals("PARSED", out.page.html)
        assertNotSame(source, out.page)
    }

    @Test
    fun `buildDeferredExtrasFetch falls back to page html when parsed body is blank`() {
        val source = DetailsPage().apply {
            id = 42
            title = "T"
            url = "https://4pda.to/index.php?p=42"
            html = "ORIGINAL"
        }
        val fetch = ArticleFetchResult(
                page = source,
                rawBody = "RAW",
                response = stubResponse(),
                originalUrl = source.url!!,
                probeUrl = source.url!!,
                parsedBodyHtml = ""
        )
        val out = ArticleDeferredExtrasHelpers.buildDeferredExtrasFetch(fetch)
        assertEquals("ORIGINAL", out.page.html)
    }

    @Test
    fun `hasPollBodyMarker recognizes legacy poll markers`() {
        assertTrue(ArticleDeferredExtrasHelpers.hasPollBodyMarker("<div class=\"poll\">...</div>"))
        assertTrue(ArticleDeferredExtrasHelpers.hasPollBodyMarker("<form><input name=\"answer[]\"/></form>"))
        assertTrue(ArticleDeferredExtrasHelpers.hasPollBodyMarker("<a href=\"/pages/poll/123\">v</a>"))
        assertFalse(ArticleDeferredExtrasHelpers.hasPollBodyMarker("<p>regular article body</p>"))
        assertFalse(ArticleDeferredExtrasHelpers.hasPollBodyMarker(null))
    }

    @Test
    fun `hasNormalizedPollBodyMarker recognizes template marker`() {
        assertTrue(ArticleDeferredExtrasHelpers.hasNormalizedPollBodyMarker("<div class=\"news-poll-normalized\">"))
        assertTrue(ArticleDeferredExtrasHelpers.hasNormalizedPollBodyMarker("<div data-normalized-poll=\"1\">"))
        assertTrue(ArticleDeferredExtrasHelpers.hasNormalizedPollBodyMarker("<div data-poll-fallback=\"1\">"))
        assertFalse(ArticleDeferredExtrasHelpers.hasNormalizedPollBodyMarker("<p>plain content</p>"))
    }

    private fun stubResponse(): NetworkResponse =
            NetworkResponse(url = "https://4pda.to/", body = "RAW", redirect = "https://4pda.to/")
}
