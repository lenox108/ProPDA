package forpdateam.ru.forpda.presentation.theme

import android.os.SystemClock
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Unified render-session snapshot that mirrors the three independent validity
 * systems ([ThemeRenderGuard.token], [ThemePage.renderGenerationId], and the
 * WebView controller render generation) without replacing them yet.
 */
data class ThemeRenderSession(
        val topicId: Int,
        val page: Int,
        val renderGenerationId: Int,
        val bridgeToken: String,
        val themeSignature: String,
        val createdAt: Long = SystemClock.uptimeMillis(),
) {

    companion object {
        fun create(
                page: ThemePage,
                bridgeToken: String,
        ): ThemeRenderSession {
            // TODO restore on next pass: ThemePage.renderGenerationId does not exist yet
            //  in the tracked entity. The session currently falls back to 0.
            @Suppress("UNUSED_VARIABLE")
            val pageRenderGenerationId = 0
            return ThemeRenderSession(
                    topicId = page.id,
                    page = page.pagination.current,
                    renderGenerationId = pageRenderGenerationId,
                    bridgeToken = bridgeToken,
                    themeSignature = page.renderSignature.orEmpty(),
            )
        }

        fun logCreated(
                session: ThemeRenderSession,
                traceId: String? = null,
                controllerRenderGeneration: Int? = null,
        ) {
            if (!BuildConfig.DEBUG) return
            FpdaDebugLog.log(
                    FpdaDebugLog.TAG_THEME_RENDER,
                    "render_session_created",
                    mapOf(
                            "traceId" to traceId,
                            "topicId" to session.topicId,
                            "page" to session.page,
                            "renderGenerationId" to session.renderGenerationId,
                            "controllerRenderGeneration" to controllerRenderGeneration,
                            "themeSignature" to session.themeSignature,
                            "bridgeTokenLen" to session.bridgeToken.length,
                            "createdAt" to session.createdAt,
                    )
            )
        }
    }
}
