package forpdateam.ru.forpda.presentation.theme

/**
 * Result of a toolbar «шапка темы» tap — drives whether WebView JS runs or a render is scheduled.
 */
enum class ThemeHatToolbarClickAction {
    /** Overlay host is already in DOM — toggle floating overlay via JS on the current page. */
    RUN_JS,
    /** Full HTML render with hat overlay open was scheduled. */
    RENDER_SCHEDULED,
    /** Hat metadata is still loading; render will follow when cache is ready. */
    PENDING_METADATA,
    /** No page or hat cannot be resolved. */
    UNAVAILABLE,
}

internal object ThemeHatToolbarClickPolicy {

    fun shouldPreserveHatOnRenderRetry(
            userHatOpenOverride: Boolean?,
            @Suppress("UNUSED_PARAMETER") reason: String,
            listPostsUnderRendered: Boolean,
    ): Boolean = userHatOpenOverride == true && !listPostsUnderRendered

    /** @deprecated Use [shouldToggleOverlayViaJs] — marker alone is not enough when DOM is out of sync. */
    fun overlayHostPresentInHtml(html: String?): Boolean = shouldToggleOverlayViaJs(html)

    /**
     * Floating overlay host is present in mapped HTML with hat body — safe to toggle via JS
     * without a full WebView reload (inline «шапка темы» on page 1 may coexist).
     */
    fun shouldToggleOverlayViaJs(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        if (!html.contains("top_hat_overlay_host")) return false
        val hostIndex = html.indexOf("top_hat_overlay_host")
        val hostSlice = html.substring(hostIndex, (hostIndex + 4096).coerceAtMost(html.length))
        if (!hostSlice.contains("hat_content")) return false
        if (hostSlice.contains("post_body") ||
                hostSlice.contains("post_header") ||
                hostSlice.contains("class=\"hat_content")
        ) {
            return true
        }
        return Regex("""data-post-id=["'](\d+)["']""").find(hostSlice)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let { it > 0 } == true
    }
}
