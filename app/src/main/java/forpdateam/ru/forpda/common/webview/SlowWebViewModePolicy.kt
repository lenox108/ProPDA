package forpdateam.ru.forpda.common.webview

/**
 * Resolves the runtime tuning for "Slow / Compatibility WebView mode" (Phase 7).
 *
 * When enabled (for weak devices or problematic Android System WebView versions), the app
 * reduces aggressive background rendering work in favor of stability. Slow Mode must NOT remove
 * functionality — it only dials down speculative/aggressive behavior.
 *
 * This is a pure, side-effect-free policy so it is fully unit-testable. The DataStore flag is
 * read elsewhere (MainPreferencesHolder) and passed in via [configFor].
 */
data class SlowWebViewModeConfig(
        val enabled: Boolean,
        val jsBatchDelayMs: Long,
        val maxScrollRestoreRetries: Int,
        val allowSmartPreload: Boolean,
        val allowAggressiveHighlightReapply: Boolean,
        val allowSpeculativeRender: Boolean,
        /** Multiplier applied to DOM-ready probe intervals (>= 1.0; larger = less frequent). */
        val domProbeIntervalScale: Double,
)

object SlowWebViewModePolicy {

    /** Normal (Slow Mode OFF) baseline tuning. */
    val NORMAL = SlowWebViewModeConfig(
            enabled = false,
            jsBatchDelayMs = NORMAL_JS_BATCH_DELAY_MS,
            maxScrollRestoreRetries = NORMAL_MAX_SCROLL_RESTORE_RETRIES,
            allowSmartPreload = true,
            allowAggressiveHighlightReapply = true,
            allowSpeculativeRender = true,
            domProbeIntervalScale = 1.0,
    )

    /** Slow Mode ON tuning: longer batch window, fewer retries, no speculative work. */
    val SLOW = SlowWebViewModeConfig(
            enabled = true,
            jsBatchDelayMs = SLOW_JS_BATCH_DELAY_MS,
            maxScrollRestoreRetries = SLOW_MAX_SCROLL_RESTORE_RETRIES,
            allowSmartPreload = false,
            allowAggressiveHighlightReapply = false,
            allowSpeculativeRender = false,
            domProbeIntervalScale = SLOW_DOM_PROBE_INTERVAL_SCALE,
    )

    fun configFor(enabled: Boolean): SlowWebViewModeConfig = if (enabled) SLOW else NORMAL

    /** Smart Preload is allowed only when Slow Mode is off (kill-switch interaction, Phase 8). */
    fun isSmartPreloadAllowed(enabled: Boolean): Boolean = configFor(enabled).allowSmartPreload

    private const val NORMAL_JS_BATCH_DELAY_MS = 16L
    private const val SLOW_JS_BATCH_DELAY_MS = 48L
    private const val NORMAL_MAX_SCROLL_RESTORE_RETRIES = 6
    private const val SLOW_MAX_SCROLL_RESTORE_RETRIES = 2
    private const val SLOW_DOM_PROBE_INTERVAL_SCALE = 2.0
}
