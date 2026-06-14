package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Controls when the floating topic hat overlay may be rendered or opened.
 *
 * The overlay must only expand after an explicit toolbar tap ([userHatOpenOverride]).
 * Inline «шапка темы» on page 1 is governed by [TopicInlineHatOpenPolicy].
 */
internal object TopicHatOpenPolicy {

    /**
     * Overlay host is always baked into HTML as `close`; expansion happens only via JS after render
     * ([shouldDispatchOverlayOpenAfterRender]) so deep-page reloads never flash hat content inline.
     */
    fun overlayExpandedForRender(
            @Suppress("UNUSED_PARAMETER") page: ThemePage,
            @Suppress("UNUSED_PARAMETER") userHatOpenOverride: Boolean?,
            @Suppress("UNUSED_PARAMETER") pendingToolbarOverlayOpen: Boolean,
    ): Boolean = false

    /**
     * Normalizes [ThemePage.isHatOpen] before HTML mapping so stale session flags cannot
     * bake `open` into the overlay host on topic open or pagination.
     */
    fun prepareOverlayStateForRender(
            page: ThemePage,
            userHatOpenOverride: Boolean?,
            pendingToolbarOverlayOpen: Boolean,
    ) {
        page.isHatOpen = overlayExpandedForRender(
                page = page,
                userHatOpenOverride = userHatOpenOverride,
                pendingToolbarOverlayOpen = pendingToolbarOverlayOpen,
        )
    }

    fun shouldDispatchOverlayOpenAfterRender(
            userHatOpenOverride: Boolean?,
            pendingToolbarOverlayOpen: Boolean,
    ): Boolean = pendingToolbarOverlayOpen && userHatOpenOverride == true
}
