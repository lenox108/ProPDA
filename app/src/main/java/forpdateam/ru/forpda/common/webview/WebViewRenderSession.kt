package forpdateam.ru.forpda.common.webview

import android.os.SystemClock

/**
 * Generic, immutable render-session snapshot shared across all WebView pipelines
 * (Theme, Search, QMS, News, static pages).
 *
 * Phase 2 of the WebView stabilization task: this generalizes the Theme-only
 * [forpdateam.ru.forpda.presentation.theme.ThemeRenderSession] so every pipeline can
 * describe "which content is currently being rendered" in one shape.
 *
 * It is introduced as an ADDITIONAL diagnostic/guard layer. It does NOT replace any
 * existing per-feature render-generation system (ThemeRenderGuard, ThemePage
 * renderGenerationId, searchRenderGeneration, qmsLoadGeneration, articleRequestId).
 *
 * Identity model:
 *  - [owner] + [targetId] answer "is this the same logical target?" (e.g. same topic page).
 *  - [contentHash] distinguishes different payloads for the same target (e.g. refreshed HTML).
 *  - [renderGeneration] is a monotonically increasing counter that breaks ties when the same
 *    target+content is re-rendered; the highest generation is the freshest.
 */
data class WebViewRenderSession(
        val owner: Owner,
        val targetId: Int,
        val contentHash: Int,
        val renderGeneration: Int,
        val bridgeToken: String?,
        val createdAt: Long,
) {

    enum class Owner {
        THEME,
        SEARCH,
        QMS,
        NEWS,
        STATIC,
    }

    /** Same logical target (same pipeline + target id), regardless of content/generation. */
    fun isSameTarget(other: WebViewRenderSession): Boolean =
            owner == other.owner && targetId == other.targetId

    /**
     * True if this session is the one currently active. A session is current when it refers to
     * the same target and is not older (by generation) than [active]. When [active] is null there
     * is nothing newer, so this session is considered current.
     */
    fun isCurrent(active: WebViewRenderSession?): Boolean {
        if (active == null) return true
        if (!isSameTarget(active)) return false
        return renderGeneration >= active.renderGeneration
    }

    /**
     * True if a callback tied to this session arrived after a newer session for the same target
     * (or a different target) became active — i.e. this session's callbacks should be ignored.
     */
    fun isStaleComparedTo(active: WebViewRenderSession?): Boolean {
        if (active == null) return false
        if (!isSameTarget(active)) return true
        return renderGeneration < active.renderGeneration
    }

    companion object {

        fun create(
                owner: Owner,
                targetId: Int,
                contentHash: Int,
                renderGeneration: Int,
                bridgeToken: String? = null,
                createdAt: Long = SystemClock.uptimeMillis(),
        ): WebViewRenderSession = WebViewRenderSession(
                owner = owner,
                targetId = targetId,
                contentHash = contentHash,
                renderGeneration = renderGeneration,
                bridgeToken = bridgeToken,
                createdAt = createdAt,
        )
    }
}
