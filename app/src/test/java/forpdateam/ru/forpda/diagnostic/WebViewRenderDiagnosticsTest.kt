package forpdateam.ru.forpda.diagnostic

import forpdateam.ru.forpda.common.webview.WebViewRenderSession
import forpdateam.ru.forpda.common.webview.WebViewRenderSession.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewRenderDiagnosticsTest {

    private fun session(bridgeToken: String? = "secret-token-value") =
            WebViewRenderSession.create(
                    owner = Owner.THEME,
                    targetId = 123,
                    contentHash = 456,
                    renderGeneration = 7,
                    bridgeToken = bridgeToken,
                    createdAt = 99L,
            )

    @Test
    fun `base fields expose only non-sensitive metadata`() {
        val fields = WebViewRenderDiagnostics.baseFields(session())
        assertEquals("THEME", fields["owner"])
        assertEquals(123, fields["targetId"])
        assertEquals(456, fields["contentHash"])
        assertEquals(7, fields["generation"])
        assertEquals(99L, fields["createdAt"])
    }

    @Test
    fun `bridge token is never logged verbatim - only presence`() {
        val fields = WebViewRenderDiagnostics.baseFields(session(bridgeToken = "secret-token-value"))
        assertEquals(true, fields["bridgeTokenPresent"])
        assertFalse(fields.values.any { it == "secret-token-value" })
        assertFalse(fields.containsKey("bridgeToken"))
    }

    @Test
    fun `bridge token presence is false when absent`() {
        val fields = WebViewRenderDiagnostics.baseFields(session(bridgeToken = null))
        assertEquals(false, fields["bridgeTokenPresent"])
    }

    @Test
    fun `event constants are stable snake_case identifiers`() {
        assertEquals("render_requested", WebViewRenderDiagnostics.Event.RENDER_REQUESTED)
        assertEquals("dom_confirmed", WebViewRenderDiagnostics.Event.DOM_CONFIRMED)
        assertEquals("stale_callback_ignored", WebViewRenderDiagnostics.Event.STALE_CALLBACK_IGNORED)
        assertEquals("html_cache_hit", WebViewRenderDiagnostics.Event.HTML_CACHE_HIT)
        assertEquals("smart_preload_started", WebViewRenderDiagnostics.Event.SMART_PRELOAD_STARTED)
    }

    @Test
    fun `logging in non-debug build is a safe no-op`() {
        // BuildConfig.DEBUG may be false under unit test; calls must never throw regardless.
        WebViewRenderDiagnostics.log(session(), WebViewRenderDiagnostics.Event.RENDER_REQUESTED)
        WebViewRenderDiagnostics.log(WebViewRenderDiagnostics.Event.SLOW_WEBVIEW_MODE_ENABLED, mapOf("enabled" to true))
        assertTrue(true)
    }
}
