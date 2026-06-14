package forpdateam.ru.forpda.presentation.theme

/**
 * Guards native topic toolbar auto-hide against programmatic WebView scroll
 * (anchor restore, infinite prepend, top-chrome padding sync).
 */
internal object ThemeToolbarScrollPolicy {

    /** Safety fallback when a scroll command never reports completion (soft anchor, Oplus quirks). */
    const val PROGRAMMATIC_SCROLL_FALLBACK_SUPPRESS_MS = 400L

    /** Hybrid prepend shifts scrollY without a scroll command — short timed window only. */
    const val APPLY_INFINITE_AUTO_HIDE_SUPPRESS_MS = 400L

    /**
     * Reacts to any WebView scroll delta while auto-hide is enabled.
     * Programmatic scroll is filtered via active-command flag or timed windows only — OEM WebViews
     * (e.g. Oplus) often change scrollY without touch/inertia signals.
     */
    fun shouldReactToScrollChange(programmaticScrollSuppressed: Boolean = false): Boolean =
            !programmaticScrollSuppressed

    fun isProgrammaticScrollSuppressed(
            nowUptimeMs: Long,
            suppressUntilMs: Long,
    ): Boolean = suppressUntilMs > 0L && nowUptimeMs < suppressUntilMs

    /**
     * Sticky suppress is armed on programmatic scroll start; it must expire with [suppressUntilMs]
     * even when JS never reports scroll completion (soft anchor, Oplus WebView quirks).
     */
    fun resolveProgrammaticScrollSuppressed(
            nowUptimeMs: Long,
            suppressUntilMs: Long,
            stickySuppressActive: Boolean,
    ): ProgrammaticScrollSuppressState {
        val stickyExpired = stickySuppressActive &&
                suppressUntilMs > 0L &&
                nowUptimeMs >= suppressUntilMs
        val effectiveSticky = stickySuppressActive && !stickyExpired
        val suppressed = effectiveSticky ||
                isProgrammaticScrollSuppressed(nowUptimeMs, suppressUntilMs)
        return ProgrammaticScrollSuppressState(
                suppressed = suppressed,
                clearSticky = stickyExpired,
        )
    }

    data class ProgrammaticScrollSuppressState(
            val suppressed: Boolean,
            val clearSticky: Boolean,
    )
}
