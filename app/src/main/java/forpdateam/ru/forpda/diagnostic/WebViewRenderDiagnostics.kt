package forpdateam.ru.forpda.diagnostic

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.webview.WebViewRenderSession

/**
 * DEBUG-only structured diagnostics for the shared WebView render lifecycle.
 *
 * Built on top of [FpdaDebugLog] and gated behind [BuildConfig.DEBUG] (mirrors
 * [forpdateam.ru.forpda.presentation.theme.ThemeRenderSession.logCreated]). Never use
 * android.util.Log here.
 *
 * Privacy: this logger NEVER emits raw HTML, cookies, auth tokens, personal messages,
 * private QMS content, or full URLs with sensitive query parameters. The bridge token is
 * never logged verbatim — only its presence.
 */
object WebViewRenderDiagnostics {

    object Event {
        const val RENDER_REQUESTED = "render_requested"
        const val LOAD_DISPATCHED = "load_dispatched"
        const val LOAD_DEFERRED_ZERO_SIZE = "load_deferred_zero_size"
        const val DUPLICATE_RENDER_SKIPPED = "duplicate_render_skipped"
        const val DOM_CONFIRMED = "dom_confirmed"
        const val PAGE_CONFIRMED = "page_confirmed"
        const val RENDER_FORCED_AFTER_MISSED_DOM = "render_forced_after_missed_dom"
        const val STALE_CALLBACK_IGNORED = "stale_callback_ignored"
        const val BRIDGE_ATTACHED = "bridge_attached"
        const val BRIDGE_REMOVED = "bridge_removed"
        const val QUEUED_JS_FLUSHED = "queued_js_flushed"
        const val QUEUED_JS_CLEARED = "queued_js_cleared"
        const val HTML_CACHE_HIT = "html_cache_hit"
        const val HTML_CACHE_MISS = "html_cache_miss"
        const val SMART_PRELOAD_STARTED = "smart_preload_started"
        const val SMART_PRELOAD_HIT = "smart_preload_hit"
        const val SMART_PRELOAD_MISS = "smart_preload_miss"
        const val SLOW_WEBVIEW_MODE_ENABLED = "slow_webview_mode_enabled"
    }

    /** Emit an event tied to a [WebViewRenderSession] with optional extra (non-sensitive) fields. */
    fun log(
            session: WebViewRenderSession,
            event: String,
            extra: Map<String, Any?> = emptyMap(),
            warn: Boolean = false,
    ) {
        if (!BuildConfig.DEBUG) return
        emit(event, baseFields(session) + extra, warn)
    }

    /** Emit an event not tied to a specific session (e.g. global mode toggles). */
    fun log(
            event: String,
            fields: Map<String, Any?> = emptyMap(),
            warn: Boolean = false,
    ) {
        if (!BuildConfig.DEBUG) return
        emit(event, fields, warn)
    }

    private fun emit(event: String, fields: Map<String, Any?>, warn: Boolean) {
        if (warn) {
            FpdaDebugLog.warn(FpdaDebugLog.TAG_WEBVIEW_SESSION, event, fields)
        } else {
            FpdaDebugLog.log(FpdaDebugLog.TAG_WEBVIEW_SESSION, event, fields)
        }
    }

    /** Visible for tests: the non-sensitive fields emitted for a session. */
    internal fun baseFields(session: WebViewRenderSession): Map<String, Any?> = mapOf(
            "owner" to session.owner.name,
            "targetId" to session.targetId,
            "contentHash" to session.contentHash,
            "generation" to session.renderGeneration,
            "bridgeTokenPresent" to (session.bridgeToken != null),
            "createdAt" to session.createdAt,
    )
}
