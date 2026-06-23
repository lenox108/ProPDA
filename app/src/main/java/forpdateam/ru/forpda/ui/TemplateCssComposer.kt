package forpdateam.ru.forpda.ui

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import timber.log.Timber

/**
 * Composes the inline CSS overrides that [TemplateManager] injects
 * into WebView templates on top of the base CSS files in
 * `app/src/main/assets/forpda/styles/`.
 *
 * Extracted from [TemplateManager] as part of the god-class
 * decomposition (§1.1 of REFACTOR_PLAN.md). Behaviour is
 * byte-identical: the same conditional branches, the same
 * color palettes, the same return values for the same inputs.
 */
class TemplateCssComposer(
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val dayNightHelper: DayNightHelper,
        private val paletteResolver: TemplatePaletteResolver,
) {

    private fun isSepiaReading(): Boolean = paletteResolver.isSepiaReading()
    private fun isSepiaBlue(): Boolean = paletteResolver.isSepiaBlue()
    private fun isMinimalReader(): Boolean = paletteResolver.isMinimalReader()
    private fun isAmoled(): Boolean = paletteResolver.isAmoled()

    /**
     * Cache key for the last [compose] result. The composed CSS depends only on
     * the font mode plus the night/palette flags, so we memoize on exactly those
     * inputs and reuse the same [String] instance until one of them changes.
     */
    private data class CssConfig(
            val fontMode: Any?,
            val night: Boolean,
            val sepiaReading: Boolean,
            val sepiaBlue: Boolean,
            val minimalReader: Boolean,
            val amoled: Boolean,
    )

    private var cachedConfig: CssConfig? = null
    private var cachedCss: String? = null

    private fun currentConfig(): CssConfig = CssConfig(
            fontMode = FontController.getCurrentFontMode(mainPreferencesHolder),
            night = dayNightHelper.isNight(),
            sepiaReading = isSepiaReading(),
            sepiaBlue = isSepiaBlue(),
            minimalReader = isMinimalReader(),
            amoled = isAmoled(),
    )

    /**
     * Возвращает inline CSS-оверрайды для WebView-шаблонов.
     * Применяется поверх базовых css-файлов и оставляет icon fonts нетронутыми.
     *
     * The result is memoized per [CssConfig]: repeated calls with an unchanged
     * configuration return the exact same [String] instance.
     */
    fun compose(): String {
        val config = currentConfig()
        cachedCss?.let { cached ->
            if (cachedConfig == config) {
                return cached
            }
        }
        val overrides = listOf(
                FontController.webFontCss(FontController.getCurrentFontMode(mainPreferencesHolder)),
                getSepiaReadingOverrideCss(),
                getSepiaBlueOverrideCss(),
                getMinimalReaderOverrideCss(),
                getAmoledOverrideCss(),
                getThemeLayoutSafetyCss(),
                getTopicPostActionIconCss()
        ).filter { it.isNotBlank() }.joinToString(separator = "\n")
        if (BuildConfig.DEBUG && overrides.isNotBlank()) {
            Timber.d(
                    "selectedFontMode=%s webViewRerendered=true",
                    FontController.getCurrentFontMode(mainPreferencesHolder)
            )
        }
        val result = if (overrides.isBlank()) "" else overrides
        cachedConfig = config
        cachedCss = result
        return result
    }

    /**
     * Hash of the currently cached composed CSS (or of a freshly composed result
     * when nothing is cached yet). Equals `compose().hashCode()` for the current
     * configuration; used by the WebView controller to detect whether the inline
     * overrides changed without re-diffing the whole string.
     */
    fun composeHash(): Int = compose().hashCode()

    private fun getTopicPostActionIconCss(): String {
        val css = """
body#topic .post_container .post_footer .post_actions_row {
    --post-action-icon-color: var(--topic-action-icon-color);
    --topic-action-icon-active-color: var(--topic-action-icon-color);
    --post-action-icon-active-color: var(--topic-action-icon-active-color);
    --post-action-stroke-width: 1.85;
    --post-action-light-stroke-width: 1.2;
}
body#topic .post_container .post_footer .post_actions_row .btn.reply,
body#topic .post_container .post_footer .post_actions_row .btn.quote,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down,
body#topic .post_container .post_footer .post_actions_row .btn.reply:focus,
body#topic .post_container .post_footer .post_actions_row .btn.quote:focus,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:focus,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:focus,
body#topic .post_container .post_footer .post_actions_row .btn.reply:visited,
body#topic .post_container .post_footer .post_actions_row .btn.quote:visited,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:visited,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:visited,
body#topic .post_container .post_footer .post_actions_row .btn.reply:hover,
body#topic .post_container .post_footer .post_actions_row .btn.quote:hover,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:hover,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:hover,
body#topic .post_container .post_footer .post_actions_row .btn.reply:active,
body#topic .post_container .post_footer .post_actions_row .btn.quote:active,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:active,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:active {
    color: var(--post-action-icon-color) !important;
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
    border: 0 !important;
    border-color: transparent !important;
    box-shadow: none !important;
    outline: 0 !important;
    -webkit-filter: none !important;
    filter: none !important;
    mix-blend-mode: normal !important;
    opacity: 1 !important;
}
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-icon *,
body#topic .post_container .post_footer .post_actions_row .btn > .rep-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn > .rep-action-icon *,
body#topic .post_container .post_footer .post_actions_row .btn > .rep-action-icon use {
    color: var(--post-action-icon-color) !important;
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
    box-shadow: none !important;
    outline: 0 !important;
    -webkit-filter: none !important;
    filter: none !important;
    mix-blend-mode: normal !important;
    opacity: 1 !important;
    fill-opacity: 1 !important;
    stroke-opacity: 1 !important;
}
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
    fill: none !important;
    stroke: currentColor !important;
    stroke-width: var(--post-action-stroke-width) !important;
    stroke-linecap: round !important;
    stroke-linejoin: round !important;
    stroke-opacity: 1 !important;
    vector-effect: non-scaling-stroke !important;
}
body#topic .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
body#topic .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
body#topic .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
body#topic .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
    stroke-width: var(--post-action-light-stroke-width, 1.2) !important;
    stroke-opacity: 1 !important;
}
body#topic .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon *,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon * {
    fill: currentColor !important;
    stroke: none !important;
}
""".trimIndent()
        return "<style>\n$css</style>"
    }

    private fun newsArticleCtaButtonOverrideCss(backgroundColor: String, textColor: String): String = """
#news > .content .btn,
#news > .content a.btn,
#news > .content button.btn {
    background: $backgroundColor !important;
    color: $textColor !important;
}
#news > .content .btn:link,
#news > .content .btn:visited,
#news > .content .btn:hover,
#news > .content .btn:focus,
#news > .content .btn:active,
#news > .content a.btn:link,
#news > .content a.btn:visited,
#news > .content a.btn:hover,
#news > .content a.btn:focus,
#news > .content a.btn:active,
#news > .content button.btn:link,
#news > .content button.btn:visited,
#news > .content button.btn:hover,
#news > .content button.btn:focus,
#news > .content button.btn:active {
    color: $textColor !important;
}
#news > .content a[style*="background"] {
    color: $textColor !important;
}
#news > .content a[style*="background"] span {
    color: inherit !important;
    background-color: transparent !important;
}
""".trimIndent()

    private fun getThemeLayoutSafetyCss(): String {
        // `--ppda-accent` MUST be defined for every active palette/mode. The
        // topic "where I stopped" highlight uses
        // `var(--ppda-accent, var(--surface-accent, <terminal-fallback>))` in
        // the shipped *themes.css, and historically `--ppda-accent` was only
        // set on the system path — leaving sepia / sepia-blue / minimal /
        // amoled to fall through to `--surface-accent` (defined by the
        // override composers). That worked in theory, but two regressions
        // slipped through:
        //   1) if any override composer ever forgets to set
        //      `--surface-accent` (or sets it to `transparent`/`none`), the
        //      cascade reaches the terminal fallback and the ring colour is
        //      fixed regardless of palette;
        //   2) `--surface-accent` is intentionally absent on the system path,
        //      so any third-party Material You / dynamic-colour layer that
        //      wanted to tint the highlight had no variable to hook into.
        // By unconditionally defining `--ppda-accent` here (this composer runs
        // AFTER the override composers in [compose], so this wins the
        // cascade), the highlight always resolves to a visible, palette-aware
        // accent. The values mirror the per-palette `colors.link` /
        // `colors.accent` baked into the override composers above so the ring
        // colour matches the rest of the palette rather than a hard-coded blue.
        val accent = when {
            isSepiaReading() -> if (dayNightHelper.isNight()) "#C9975B" else "#8A5A2B"
            isSepiaBlue() -> if (dayNightHelper.isNight()) "#8FB3C8" else "#4F7896"
            isMinimalReader() -> if (dayNightHelper.isNight()) "#8DA3B8" else "#7C8FA1"
            isAmoled() -> "#9E9E9E"
            else -> if (dayNightHelper.isNight()) "#78B8E6" else "#2177AF"
        }
        val accentRootCss = ":root { --ppda-accent: $accent; }\n"
        val css = """
$accentRootCss
body#topic .post_container .post_header .header_wrapper {
    position: relative !important;
    --post-header-action-right: -1rem !important;
    --post-header-action-width: 2.5rem !important;
    --post-header-inline-end-reserve: 2.5rem !important;
    --post-header-menu-axis-overhang: 2.25rem !important;
    display: flex !important;
    flex-direction: column !important;
    justify-content: center !important;
    padding-right: var(--post-header-inline-end-reserve) !important;
    box-sizing: border-box !important;
}
body#topic .post_container .post_header .header_wrapper > .inf.nick {
    padding-top: 0 !important;
}
body#topic .post_container .post_header .header_wrapper > .inf.post_meta {
    display: flex !important;
    align-items: baseline !important;
    gap: 0.5rem !important;
    min-width: 0 !important;
    width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
    max-width: none !important;
    margin-right: 0 !important;
    padding-right: 0 !important;
    box-sizing: border-box !important;
    pointer-events: none !important;
}
body#topic .post_container .post_header .header_wrapper > .inf.post_meta > .group_text {
    pointer-events: auto !important;
    flex: 1 1 auto !important;
    min-width: 0 !important;
    overflow: hidden !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
}
body#topic .post_container .post_header .header_wrapper > .inf.post_meta > .date {
    flex: 0 0 auto !important;
    margin-left: auto !important;
    max-width: 9rem !important;
    overflow: hidden !important;
    text-align: right !important;
    text-overflow: ellipsis !important;
    white-space: nowrap !important;
    pointer-events: auto !important;
}
body#topic .post_container .post_header .header_wrapper > .inf.menu {
    z-index: 3 !important;
    pointer-events: auto !important;
}
body#topic.density_compact .post_container .post_header .header_wrapper {
    --post-header-action-right: -0.5rem !important;
    --post-header-action-width: 2.125rem !important;
    --post-header-inline-end-reserve: 2.375rem !important;
    --post-header-menu-axis-overhang: 1.8125rem !important;
    display: flex !important;
    flex-direction: column !important;
    justify-content: center !important;
    padding-right: var(--post-header-inline-end-reserve) !important;
}
body#topic.density_compact .post_container .post_header .header_wrapper > .inf.post_meta {
    width: calc(100% + var(--post-header-menu-axis-overhang)) !important;
    margin-right: 0 !important;
}
body#topic.density_compact .post_container .post_header .header_wrapper > .inf.post_meta > .date {
    max-width: 7.75rem !important;
}
body#topic .post_container .post_header .header_wrapper > .inf.user_post_count {
    display: flex !important;
    align-items: center !important;
    visibility: visible !important;
    height: auto !important;
    min-height: 0.875rem !important;
    overflow: visible !important;
}
body#topic .post_container .post_footer .post_actions_row {
    --post-action-icon-color: var(--topic-action-icon-color);
    --topic-action-icon-active-color: var(--topic-action-icon-color);
    --post-action-icon-active-color: var(--topic-action-icon-active-color);
    --post-action-stroke-width: 1.85;
    --post-action-light-stroke-width: 1.2;
    background: transparent !important;
    border: 0 !important;
    box-shadow: none !important;
}
body#topic .post_container .post_footer .post_actions_row .btn.reply,
body#topic .post_container .post_footer .post_actions_row .btn.quote,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down,
body#topic .post_container .post_footer .post_actions_row .btn.reply:focus,
body#topic .post_container .post_footer .post_actions_row .btn.quote:focus,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:focus,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:focus,
body#topic .post_container .post_footer .post_actions_row .btn.reply:visited,
body#topic .post_container .post_footer .post_actions_row .btn.quote:visited,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:visited,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:visited,
body#topic .post_container .post_footer .post_actions_row .btn.reply:hover,
body#topic .post_container .post_footer .post_actions_row .btn.quote:hover,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:hover,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:hover,
body#topic .post_container .post_footer .post_actions_row .btn.reply:active,
body#topic .post_container .post_footer .post_actions_row .btn.quote:active,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up:active,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down:active {
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
    border: 0 !important;
    border-color: transparent !important;
    box-shadow: none !important;
    outline: 0 !important;
    -webkit-filter: none !important;
    filter: none !important;
    color: var(--post-action-icon-color) !important;
}
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn > .rep-action-icon {
    color: var(--post-action-icon-color) !important;
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
    box-shadow: none !important;
    outline: 0 !important;
    -webkit-filter: none !important;
    filter: none !important;
}
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
body#topic .post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
    fill: none !important;
    stroke: currentColor !important;
    stroke-width: var(--post-action-stroke-width) !important;
    stroke-linecap: round !important;
    stroke-linejoin: round !important;
    stroke-opacity: 1 !important;
    vector-effect: non-scaling-stroke !important;
}
body#topic .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
body#topic .post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
body#topic .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
body#topic .post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
    stroke-width: var(--post-action-light-stroke-width, 1.2) !important;
    stroke-opacity: 1 !important;
}
body#topic .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon,
body#topic .post_container .post_footer .post_actions_row .btn.rep_up > .rep-action-icon *,
body#topic .post_container .post_footer .post_actions_row .btn.rep_down > .rep-action-icon * {
    fill: currentColor !important;
    stroke: none !important;
}
""".trimIndent()
        return "<style>\n$css</style>"
    }

    private fun getSepiaReadingOverrideCss(): String {
        if (!isSepiaReading()) return ""
        val isAmoled = isAmoled()
        val isDark = dayNightHelper.isNight()
        val colors = when {
            isAmoled -> SepiaReadingCssColors(
                    background = "#000000",
                    card = "#000000",
                    surface = "#0B0806",
                    toolbar = "#000000",
                    border = "#2A1D13",
                    primaryText = "#F2E6D3",
                    secondaryText = "#B89F82",
                    link = "#C9975B",
                    button = "#14100B",
                    buttonActive = "#1A120C",
                    actionButtonBorder = "#3A2A1D",
                    buttonShadow = "rgba(0, 0, 0, 0.00)",
                    tapHighlight = "rgba(201, 151, 91, 0.24)"
            )
            isDark -> SepiaReadingCssColors(
                    background = "#15100B",
                    card = "#241A12",
                    surface = "#20160F",
                    toolbar = "#20160F",
                    border = "#5A432E",
                    primaryText = "#F2E6D3",
                    secondaryText = "#B89F82",
                    link = "#C9975B",
                    button = "#3A2A1D",
                    buttonActive = "#3F2E20",
                    actionButtonBorder = "#5A432E",
                    buttonShadow = "rgba(0, 0, 0, 0.18)",
                    tapHighlight = "rgba(201, 151, 91, 0.24)"
            )
            else -> SepiaReadingCssColors(
                    background = "#F4ECD8",
                    card = "#FFF8E7",
                    surface = "#EFE3C8",
                    toolbar = "#FFF3D7",
                    border = "#D8C6A8",
                    primaryText = "#3A2E22",
                    secondaryText = "#7A6A58",
                    link = "#8A5A2B",
                    button = "rgba(138, 90, 43, 0.10)",
                    buttonActive = "rgba(138, 90, 43, 0.18)",
                    actionButtonBorder = "#D8C6A8",
                    buttonShadow = "rgba(58, 46, 34, 0.10)",
                    tapHighlight = "rgba(138, 90, 43, 0.22)"
            )
        }
        val css = """
:root {
    --surface-background: ${colors.background};
    --surface-card: ${colors.card};
    --surface-elevated: ${colors.surface};
    --surface-divider: ${colors.border};
    --surface-text-primary: ${colors.primaryText};
    --surface-text-secondary: ${colors.secondaryText};
    --surface-icon: ${colors.secondaryText};
    --surface-accent: ${colors.link};
    --surface-control: ${colors.button};
    --surface-control-active: ${colors.buttonActive};
    --topic-action-icon-color: var(--sepia-link);
    --topic-pagination-surface: ${colors.toolbar};
    --topic-pagination-icon: ${colors.primaryText};
    --topic-pagination-icon-disabled: ${colors.primaryText};
    --surface-radius-small: 0.375rem;
    --surface-radius-medium: 0.75rem;
    --surface-radius-large: 0.875rem;
    --sepia-bg: ${colors.background};
    --sepia-card: ${colors.card};
    --sepia-surface: ${colors.surface};
    --sepia-text: ${colors.primaryText};
    --sepia-text-secondary: ${colors.secondaryText};
    --sepia-link: ${colors.link};
    --sepia-border: ${colors.border};
    --sepia-toolbar: ${colors.toolbar};
    --sepia-button: ${colors.button};
    --sepia-button-active: ${colors.buttonActive};
    --sepia-action-button-border: ${colors.actionButtonBorder};
    --sepia-button-shadow: ${colors.buttonShadow};
    --sepia-tap-highlight: ${colors.tapHighlight};
}
html, body {
    background: var(--sepia-bg) !important;
    color: var(--sepia-text) !important;
}
a {
    color: var(--sepia-link) !important;
    -webkit-tap-highlight-color: var(--sepia-tap-highlight) !important;
}
button,
.btn {
    color: var(--sepia-link) !important;
}
button:active,
.btn:active,
.aec:active {
    background: var(--sepia-button) !important;
}
.post_container,
.post_container .hat_content,
.post_container .post_header,
.post_container .post_body,
.post_container .post_footer,
.topic_hat_fixed,
.topic_hat_entry,
.poll,
.poll > .title,
.poll > .body,
.poll > .body .questions,
.poll > .body .questions .question,
.poll > .body .questions .question > .items,
.navigation,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news-comments-entry,
.news_item,
.news_block,
.news_container,
.news_post,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess,
.mess_list > .mess_container.our .mess,
.mess_list > .mess_container.his .mess {
    background: var(--sepia-card) !important;
    color: var(--sepia-text) !important;
}
body#qms .wrapper,
body#qms .mess_list,
.mess_list > .mess_container,
.mess_list > .mess_container .mess > .content {
    background: transparent !important;
}
.post_container,
.poll,
.navigation,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news-comments-entry,
.news_item,
.news_block,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess {
    box-shadow: inset 0 0 0 1px var(--sepia-border) !important;
}
.post_header,
.post_footer,
.post_title,
.poll > .title,
.comments .title,
.news-detail-header .news-detail-header-text {
    background: var(--sepia-toolbar) !important;
    border-color: var(--sepia-border) !important;
}
.post_header .inf,
.post_header .inf span,
.post_footer,
.post_footer *,
.post_rating,
.news-detail-header .news-detail-header-meta,
#search .search_post_id_hint,
.comments .title,
.comments .content,
.materials .material_item .title,
.poll .votes_info,
.poll .poll_status {
    color: var(--sepia-text-secondary) !important;
}
.post_body,
#news > .content,
.content,
.comments {
    color: var(--sepia-text) !important;
}
.post_body a,
#news > .content a,
.content a,
.comments a,
.post_title a,
.materials .material_item .title {
    color: var(--sepia-link) !important;
}
body#topic .post_container .post_footer {
    background: var(--sepia-card) !important;
    border-top-color: transparent !important;
}
body#topic .post_container .post_header {
    background: var(--sepia-card) !important;
    border-bottom-color: transparent !important;
}
body#topic .post_container .post_footer .post_actions_row {
    background: transparent !important;
    border: 0 !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn,
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote {
    background: transparent !important;
    border-color: transparent !important;
    color: var(--topic-action-icon-color) !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn:active,
.post_container .post_footer .post_actions_row .btn.rep_up:active,
.post_container .post_footer .post_actions_row .btn.rep_down:active,
.post_container .post_footer .post_actions_row .btn.reply:active,
.post_container .post_footer .post_actions_row .btn.quote:active {
    background: transparent !important;
    border-color: transparent !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn > .post-action-icon {
    color: var(--topic-action-icon-color) !important;
    fill: none !important;
    stroke: currentColor !important;
}
.post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
.post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
    fill: none !important;
    stroke: currentColor !important;
    stroke-width: var(--post-action-stroke-width, 1.85) !important;
    stroke-linecap: round !important;
    stroke-linejoin: round !important;
    stroke-opacity: 1 !important;
    vector-effect: non-scaling-stroke !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
    stroke-width: var(--post-action-light-stroke-width, 1.2) !important;
    stroke-opacity: 1 !important;
}
.post_container .post_footer .post_actions_row .btn > .rep-action-icon {
    color: var(--topic-action-icon-color) !important;
    fill: currentColor !important;
    stroke: none !important;
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
}
.post_container .post_footer .post_actions_row .btn > .rep-action-icon * {
    fill: currentColor !important;
    stroke: none !important;
}
.post-block,
blockquote,
.quote,
.spoil,
.hidden,
.post_body table,
.poll > .body .questions .question > .items .item.result .range_bar,
#news > .content blockquote {
    background: var(--sepia-surface) !important;
    border-color: var(--sepia-border) !important;
}
body#topic .post-block.spoil {
    border-top-color: transparent !important;
    border-bottom-color: transparent !important;
}
body#topic .post-block.spoil > .block-title {
    background: transparent !important;
    border-bottom: 1px solid var(--sepia-border) !important;
}
body#topic .post-block.spoil.close > .block-title {
    border-bottom-color: transparent !important;
}
.post-block > .block-title,
.post-block.spoil > .block-title,
.poll > .body .questions .question > .title {
    color: var(--sepia-text) !important;
}
.poll > .body .questions .question > .items .item.result > .title {
    background: var(--sepia-card) !important;
    box-shadow: 0.75em 0 0.5em -0.25em var(--sepia-card) !important;
}
.poll > .body .questions .question > .items .item.result .range_bar .range {
    background: var(--sepia-button-active) !important;
}
.poll > .body .buttons > .btn,
#search a.search_post_btn {
    background: var(--sepia-button) !important;
    border-color: var(--sepia-border) !important;
    color: var(--sepia-link) !important;
}
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button {
    background: var(--sepia-button) !important;
    border-color: var(--sepia-border) !important;
    color: var(--sepia-link) !important;
    box-shadow: 0 1px 2px var(--sepia-button-shadow) !important;
}
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button:active {
    background: var(--sepia-button-active) !important;
}
.poll > .body .buttons > .btn.vote {
    background: var(--sepia-link) !important;
    color: var(--sepia-card) !important;
}
${newsArticleCtaButtonOverrideCss("var(--sepia-link)", "var(--sepia-card)")}
#search .hat_content.close.over_height:after {
    box-shadow: inset 0 -6rem 3rem -3rem var(--sepia-card) !important;
}
#news > .content img.app-stable-media,
#news > .content iframe.app-stable-media,
.news-detail-header .news-detail-header-image {
    background: var(--sepia-surface) !important;
}
.theme_bottom_pagination button {
    color: var(--topic-pagination-icon) !important;
}
.theme_bottom_pagination button.theme_bottom_pagination_current {
    color: var(--topic-pagination-icon) !important;
}
.theme_bottom_pagination button.disabled {
    color: var(--topic-pagination-icon-disabled) !important;
}
.theme_bottom_pagination {
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
    border-radius: 0 !important;
}
body#topic {
    --topic-collapsible-header-color: var(--sepia-text);
    --topic-collapsible-header-icon-color: var(--topic-collapsible-header-color);
}
body#topic .post_container.topic_hat_fixed > .hat_button,
body#topic .post_container.topic_hat_entry > .hat_button,
body#topic .poll > a.btn.title.aec {
    color: var(--topic-collapsible-header-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > span,
body#topic .post_container.topic_hat_entry > .hat_button > span,
body#topic .poll > a.btn.title.aec > span {
    color: inherit !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon,
body#topic .post_container.topic_hat_entry > .hat_button > .icon,
body#topic .poll > a.btn.title.aec > .icon {
    color: var(--topic-collapsible-header-icon-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:after,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:after,
body#topic .poll > a.btn.title.aec > .icon:after {
    border-color: currentColor !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:before,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:before,
body#topic .poll > a.btn.title.aec > .icon:before {
    background: currentColor !important;
}
""".trimIndent()
        return "<style>\n$css</style>"
    }

    private fun getSepiaBlueOverrideCss(): String {
        if (!isSepiaBlue()) return ""
        val colors = when {
            isAmoled() -> SepiaBlueCssColors(
                    background = "#000000",
                    secondaryBackground = "#070A0C",
                    card = "#000000",
                    surface = "#0C1114",
                    toolbar = "#000000",
                    border = "#1E3039",
                    primaryText = "#F0E7D9",
                    secondaryText = "#B8AA99",
                    mutedText = "#7D7468",
                    link = "#8FB3C8",
                    accentSoft = "#13252E",
                    button = "#0E171C",
                    buttonActive = "#14212A",
                    actionButtonBorder = "#24404D",
                    buttonShadow = "rgba(0, 0, 0, 0.00)",
                    tapHighlight = "rgba(143, 179, 200, 0.24)",
                    success = "#8DAE88",
                    warning = "#D58A7B"
            )
            dayNightHelper.isNight() -> SepiaBlueCssColors(
                    background = "#14191D",
                    secondaryBackground = "#1A2024",
                    card = "#20282D",
                    surface = "#26323A",
                    toolbar = "#1D2327",
                    border = "#354148",
                    primaryText = "#F0E7D9",
                    secondaryText = "#B8AA99",
                    mutedText = "#7D7468",
                    link = "#8FB3C8",
                    accentSoft = "#263B47",
                    button = "#24323A",
                    buttonActive = "#2C414C",
                    actionButtonBorder = "#48606C",
                    buttonShadow = "rgba(0, 0, 0, 0.18)",
                    tapHighlight = "rgba(143, 179, 200, 0.24)",
                    success = "#8DAE88",
                    warning = "#D58A7B"
            )
            else -> SepiaBlueCssColors(
                    background = "#F1E8D8",
                    secondaryBackground = "#E8DBC7",
                    card = "#FFF9EE",
                    surface = "#F6ECDD",
                    toolbar = "#F2E7D5",
                    border = "#D6C5AE",
                    primaryText = "#2F2A23",
                    secondaryText = "#766B5D",
                    mutedText = "#9B8D7B",
                    link = "#4F7896",
                    accentSoft = "#D9E5EA",
                    button = "rgba(79, 120, 150, 0.10)",
                    buttonActive = "rgba(79, 120, 150, 0.18)",
                    actionButtonBorder = "#C7D7DF",
                    buttonShadow = "rgba(47, 42, 35, 0.08)",
                    tapHighlight = "rgba(79, 120, 150, 0.22)",
                    success = "#5B8A65",
                    warning = "#B95F52"
            )
        }
        val css = """
:root {
    --surface-background: ${colors.background};
    --surface-card: ${colors.card};
    --surface-elevated: ${colors.surface};
    --surface-divider: ${colors.border};
    --surface-text-primary: ${colors.primaryText};
    --surface-text-secondary: ${colors.secondaryText};
    --surface-icon: ${colors.secondaryText};
    --surface-accent: ${colors.link};
    --surface-control: ${colors.button};
    --surface-control-active: ${colors.buttonActive};
    --topic-action-icon-color: var(--sepia-blue-link);
    --topic-pagination-surface: ${colors.toolbar};
    --topic-pagination-icon: ${colors.primaryText};
    --topic-pagination-icon-disabled: ${colors.mutedText};
    --surface-radius-small: 0.375rem;
    --surface-radius-medium: 0.75rem;
    --surface-radius-large: 0.875rem;
    --sepia-blue-bg: ${colors.background};
    --sepia-blue-secondary-bg: ${colors.secondaryBackground};
    --sepia-blue-card: ${colors.card};
    --sepia-blue-surface: ${colors.surface};
    --sepia-blue-toolbar: ${colors.toolbar};
    --sepia-blue-border: ${colors.border};
    --sepia-blue-text: ${colors.primaryText};
    --sepia-blue-text-secondary: ${colors.secondaryText};
    --sepia-blue-muted: ${colors.mutedText};
    --sepia-blue-link: ${colors.link};
    --sepia-blue-accent-soft: ${colors.accentSoft};
    --sepia-blue-button: ${colors.button};
    --sepia-blue-button-active: ${colors.buttonActive};
    --sepia-blue-action-button-border: ${colors.actionButtonBorder};
    --sepia-blue-button-shadow: ${colors.buttonShadow};
    --sepia-blue-tap-highlight: ${colors.tapHighlight};
    --sepia-blue-success: ${colors.success};
    --sepia-blue-warning: ${colors.warning};
}
html, body {
    background: var(--sepia-blue-bg) !important;
    color: var(--sepia-blue-text) !important;
}
a {
    color: var(--sepia-blue-link) !important;
    -webkit-tap-highlight-color: var(--sepia-blue-tap-highlight) !important;
    text-decoration-color: var(--sepia-blue-accent-soft) !important;
}
button,
.btn {
    color: var(--sepia-blue-link) !important;
}
::selection {
    background: var(--sepia-blue-accent-soft) !important;
    color: var(--sepia-blue-text) !important;
}
.post_container,
.post_container .hat_content,
.post_container .post_body,
.poll,
.poll > .body,
.poll > .body .questions,
.poll > .body .questions .question,
.poll > .body .questions .question > .items,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news-comments-entry,
.news_item,
.news_block,
.news_container,
.news_post,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess,
.mess_list > .mess_container.our .mess,
.mess_list > .mess_container.his .mess {
    background: var(--sepia-blue-card) !important;
    color: var(--sepia-blue-text) !important;
}
body#qms .wrapper,
body#qms .mess_list,
.mess_list > .mess_container,
.mess_list > .mess_container .mess > .content {
    background: transparent !important;
}
body:not(#qms) .wrapper,
body:not(#qms) .mess_list {
    background: var(--sepia-blue-bg) !important;
}
.post_container,
.poll,
.navigation,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news-comments-entry,
.news_item,
.news_block,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess {
    border-color: var(--sepia-blue-border) !important;
    box-shadow: inset 0 0 0 1px var(--sepia-blue-border) !important;
}
.post_header,
.post_footer,
.post_title,
.poll > .title,
.navigation,
.comments .title,
.news-detail-header .news-detail-header-text {
    background: var(--sepia-blue-toolbar) !important;
    border-color: var(--sepia-blue-border) !important;
    color: var(--sepia-blue-text) !important;
}
.post_container .post_footer {
    background: var(--sepia-blue-surface) !important;
}
.post_header .inf,
.post_header .inf span,
.post_footer,
.post_footer *,
.post_rating,
.news-detail-header .news-detail-header-meta,
#search .search_post_id_hint,
.comments .title,
.comments .content,
.materials .material_item .title,
.poll .votes_info,
.poll .poll_status {
    color: var(--sepia-blue-text-secondary) !important;
}
.post_body,
#news > .content,
.content,
.comments {
    color: var(--sepia-blue-text) !important;
}
.post_body a,
#news > .content a,
.content a,
.comments a,
.post_title a,
.materials .material_item .title {
    color: var(--sepia-blue-link) !important;
}
body#topic .post_container .post_footer {
    background: var(--sepia-blue-card) !important;
    border-top-color: transparent !important;
}
body#topic .post_container .post_header {
    background: var(--sepia-blue-card) !important;
    border-bottom-color: transparent !important;
}
body#topic .post_container .post_footer .post_actions_row {
    background: transparent !important;
    border: 0 !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn,
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote,
.poll > .body .buttons > .btn,
.news-inline-comments .news-inline-comments-retry,
.news-inline-comments .news-inline-comment-action,
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button,
#search a.search_post_btn {
    background: var(--sepia-blue-button) !important;
    border-color: var(--sepia-blue-action-button-border) !important;
    color: var(--sepia-blue-link) !important;
    box-shadow: 0 1px 2px var(--sepia-blue-button-shadow) !important;
}
.post_container .post_footer .post_actions_row .btn,
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote {
    background: transparent !important;
    border-color: transparent !important;
    box-shadow: none !important;
    color: var(--topic-action-icon-color) !important;
}
.post_container .post_footer .post_actions_row .btn:active,
.post_container .post_footer .post_actions_row .btn.rep_up:active,
.post_container .post_footer .post_actions_row .btn.rep_down:active,
.post_container .post_footer .post_actions_row .btn.reply:active,
.post_container .post_footer .post_actions_row .btn.quote:active,
.poll > .body .buttons > .btn:active,
.news-inline-comments .news-inline-comments-retry:active,
.news-inline-comments .news-inline-comment-action:active,
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button:active,
#search a.search_post_btn:active {
    background: var(--sepia-blue-button-active) !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn:active,
.post_container .post_footer .post_actions_row .btn.rep_up:active,
.post_container .post_footer .post_actions_row .btn.rep_down:active,
.post_container .post_footer .post_actions_row .btn.reply:active,
.post_container .post_footer .post_actions_row .btn.quote:active {
    background: transparent !important;
    border-color: transparent !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn > .post-action-icon {
    color: var(--topic-action-icon-color) !important;
    fill: none !important;
    stroke: currentColor !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon *,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-icon,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-icon * {
    color: var(--topic-action-icon-color) !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon * {
    fill: none !important;
    stroke: currentColor !important;
}
.post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
.post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
    fill: none !important;
    stroke: currentColor !important;
    stroke-width: var(--post-action-stroke-width, 1.85) !important;
    stroke-linecap: round !important;
    stroke-linejoin: round !important;
    stroke-opacity: 1 !important;
    vector-effect: non-scaling-stroke !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
    stroke-width: var(--post-action-light-stroke-width, 1.2) !important;
    stroke-opacity: 1 !important;
}
.post_container .post_footer .post_actions_row .btn > .rep-action-icon {
    color: var(--topic-action-icon-color) !important;
    fill: currentColor !important;
    stroke: none !important;
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
}
.post_container .post_footer .post_actions_row .btn > .rep-action-icon * {
    fill: currentColor !important;
    stroke: none !important;
}
.post-block,
blockquote,
.quote,
.spoil,
.hidden,
.post_body table,
.poll > .body .questions .question > .items .item.result .range_bar,
#news > .content blockquote,
pre,
code {
    background: var(--sepia-blue-secondary-bg) !important;
    border-color: var(--sepia-blue-border) !important;
    color: var(--sepia-blue-text) !important;
    box-shadow: none !important;
}
body#topic .post-block.spoil {
    border-top-color: transparent !important;
    border-bottom-color: transparent !important;
}
body#topic .post-block.spoil > .block-title {
    background: transparent !important;
    border-bottom: 1px solid var(--sepia-blue-border) !important;
}
body#topic .post-block.spoil.close > .block-title {
    border-bottom-color: transparent !important;
}
.post-block.quote,
blockquote,
.quote,
#news > .content blockquote {
    border-left: 0 !important;
    border-radius: 0.75rem !important;
    background: var(--sepia-blue-secondary-bg) !important;
}
.post-block.quote:before {
    content: none !important;
}
.post-block.code,
pre,
code {
    color: var(--sepia-blue-text-secondary) !important;
}
.post-block > .block-body,
.post-block.spoil > .block-body,
.post-block.hidden > .block-body,
.post-block.code > .block-body,
.post-block.quote > .block-body {
    color: var(--sepia-blue-text) !important;
}
.post-block > .block-title,
.post-block.spoil > .block-title,
.poll > .body .questions .question > .title {
    background: var(--sepia-blue-surface) !important;
    border-color: var(--sepia-blue-border) !important;
    color: var(--sepia-blue-text) !important;
}
.post_body table th,
.post_body table td {
    border-color: var(--sepia-blue-border) !important;
}
.poll > .body .questions .question > .items .item.result > .title {
    background: var(--sepia-blue-card) !important;
    box-shadow: 0.75em 0 0.5em -0.25em var(--sepia-blue-card) !important;
}
.poll > .body .questions .question > .items .item.result .range_bar .range {
    background: var(--sepia-blue-accent-soft) !important;
}
.poll > .body .buttons > .btn.vote {
    background: var(--sepia-blue-link) !important;
    color: var(--sepia-blue-card) !important;
}
${newsArticleCtaButtonOverrideCss("var(--sepia-blue-link)", "var(--sepia-blue-card)")}
#search .hat_content.close.over_height:after {
    box-shadow: inset 0 -6rem 3rem -3rem var(--sepia-blue-card) !important;
}
#news > .content img.app-stable-media,
#news > .content iframe.app-stable-media,
.news-detail-header .news-detail-header-image {
    background: var(--sepia-blue-secondary-bg) !important;
}
.theme_bottom_pagination button {
    background: transparent !important;
    border-color: transparent !important;
    color: var(--topic-pagination-icon) !important;
    box-shadow: none !important;
}
.theme_bottom_pagination button.theme_bottom_pagination_current {
    color: var(--topic-pagination-icon) !important;
}
.theme_bottom_pagination button.disabled {
    color: var(--topic-pagination-icon-disabled) !important;
}
.theme_bottom_pagination {
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
    border-radius: 0 !important;
}
body#topic {
    --topic-collapsible-header-color: var(--sepia-blue-text);
    --topic-collapsible-header-icon-color: var(--topic-collapsible-header-color);
}
body#topic .post_container.topic_hat_fixed > .hat_button,
body#topic .post_container.topic_hat_entry > .hat_button,
body#topic .poll > a.btn.title.aec {
    background: var(--sepia-blue-toolbar) !important;
    color: var(--topic-collapsible-header-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > span,
body#topic .post_container.topic_hat_entry > .hat_button > span,
body#topic .poll > a.btn.title.aec > span {
    color: inherit !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon,
body#topic .post_container.topic_hat_entry > .hat_button > .icon,
body#topic .poll > a.btn.title.aec > .icon {
    color: var(--topic-collapsible-header-icon-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:after,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:after,
body#topic .poll > a.btn.title.aec > .icon:after {
    border-color: currentColor !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:before,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:before,
body#topic .poll > a.btn.title.aec > .icon:before {
    background: currentColor !important;
}
.post_container > .hat_button,
.post_container .post_header .inf.menu {
    background-color: transparent !important;
}
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote {
    color: var(--topic-action-icon-color) !important;
}
:not(.post_actions_row) > .rep_up,
.positive,
.poll .votes_info {
    color: var(--sepia-blue-success) !important;
}
:not(.post_actions_row) > .rep_down,
.negative,
.error {
    color: var(--sepia-blue-warning) !important;
}
.mess_list > .date span,
.post_footer .btn.disabled {
    color: var(--sepia-blue-muted) !important;
}
""".trimIndent()
        return "<style>\n$css</style>"
    }

    private fun getMinimalReaderOverrideCss(): String {
        if (!isMinimalReader()) return ""
        val isDark = dayNightHelper.isNight()
        val colors = if (isDark) {
            if (isAmoled()) {
                MinimalReaderCssColors(
                        background = "#000000",
                        secondaryBackground = "#0A0B0D",
                        card = "#000000",
                        toolbar = "#000000",
                        footer = "#000000",
                        divider = "#1E242A",
                        primaryText = "#ECE8E1",
                        secondaryText = "#B3ADA4",
                        mutedText = "#7C776F",
                        accent = "#8DA3B8",
                        accentSoft = "#18232D",
                        selection = "#1E2C36",
                        warning = "#D08C7E",
                        success = "#7FA089",
                        actionButton = "#101316",
                        actionButtonActive = "#15191D",
                        actionButtonBorder = "#2E3740",
                        buttonShadow = "rgba(0, 0, 0, 0.00)"
                )
            } else {
                MinimalReaderCssColors(
                        background = "#121416",
                        secondaryBackground = "#171A1D",
                        card = "#1D2126",
                        toolbar = "#181B1F",
                        footer = "#181B1F",
                        divider = "#2A2E34",
                        primaryText = "#ECE8E1",
                        secondaryText = "#B3ADA4",
                        mutedText = "#7C776F",
                        accent = "#8DA3B8",
                        accentSoft = "#26313B",
                        selection = "#2A3742",
                        warning = "#D08C7E",
                        success = "#7FA089",
                        actionButton = "#2E3338",
                        actionButtonActive = "#333A40",
                        actionButtonBorder = "#4A535B",
                        buttonShadow = "rgba(0, 0, 0, 0.10)"
                )
            }
        } else {
            MinimalReaderCssColors(
                    background = "#F7F6F3",
                    secondaryBackground = "#F1EFEA",
                    card = "#FCFBF8",
                    toolbar = "#FAF8F4",
                    footer = "#FCFBF8",
                    divider = "#E4E1DA",
                    primaryText = "#1E1E1E",
                    secondaryText = "#6F6B63",
                    mutedText = "#9A958B",
                    accent = "#7C8FA1",
                    accentSoft = "#D8E1E8",
                    selection = "#E8EEF2",
                    warning = "#C96B5C",
                    success = "#6E8B74",
                    actionButton = "transparent",
                    actionButtonActive = "#E8EEF2",
                    actionButtonBorder = "#E4E1DA",
                    buttonShadow = "rgba(30, 30, 30, 0.04)"
            )
        }
        val css = """
:root {
    --surface-background: ${colors.background};
    --surface-card: ${colors.card};
    --surface-elevated: ${colors.toolbar};
    --surface-divider: ${colors.divider};
    --surface-text-primary: ${colors.primaryText};
    --surface-text-secondary: ${colors.secondaryText};
    --surface-icon: ${colors.secondaryText};
    --surface-accent: ${colors.accent};
    --surface-control: ${colors.actionButton};
    --surface-control-active: ${colors.actionButtonActive};
    --topic-action-icon-color: var(--minimal-reader-accent);
    --topic-pagination-surface: ${colors.toolbar};
    --topic-pagination-icon: ${colors.primaryText};
    --topic-pagination-icon-disabled: ${colors.primaryText};
    --surface-radius-small: 0.375rem;
    --surface-radius-medium: 0.75rem;
    --surface-radius-large: 0.875rem;
    --minimal-reader-bg: ${colors.background};
    --minimal-reader-secondary-bg: ${colors.secondaryBackground};
    --minimal-reader-card: ${colors.card};
    --minimal-reader-toolbar: ${colors.toolbar};
    --minimal-reader-footer: ${colors.footer};
    --minimal-reader-divider: ${colors.divider};
    --minimal-reader-text: ${colors.primaryText};
    --minimal-reader-text-secondary: ${colors.secondaryText};
    --minimal-reader-muted: ${colors.mutedText};
    --minimal-reader-accent: ${colors.accent};
    --minimal-reader-accent-soft: ${colors.accentSoft};
    --minimal-reader-selection: ${colors.selection};
    --minimal-reader-warning: ${colors.warning};
    --minimal-reader-success: ${colors.success};
    --minimal-reader-action-button: ${colors.actionButton};
    --minimal-reader-action-button-active: ${colors.actionButtonActive};
    --minimal-reader-action-button-border: ${colors.actionButtonBorder};
    --minimal-reader-button-shadow: ${colors.buttonShadow};
}
html, body {
    background: var(--minimal-reader-bg) !important;
    color: var(--minimal-reader-text) !important;
}
a {
    color: var(--minimal-reader-accent) !important;
    -webkit-tap-highlight-color: var(--minimal-reader-selection) !important;
    text-decoration-color: var(--minimal-reader-accent-soft) !important;
}
button,
.btn {
    color: var(--minimal-reader-accent) !important;
}
::selection {
    background: var(--minimal-reader-selection) !important;
    color: var(--minimal-reader-text) !important;
}
.post_container,
.post_container .hat_content,
.post_container .post_body,
.poll,
.poll > .body,
.poll > .body .questions,
.poll > .body .questions .question,
.poll > .body .questions .question > .items,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news-comments-entry,
.news_item,
.news_block,
.news_container,
.news_post,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess,
.mess_list > .mess_container.our .mess,
.mess_list > .mess_container.his .mess {
    background: var(--minimal-reader-card) !important;
    color: var(--minimal-reader-text) !important;
}
body#qms .wrapper,
body#qms .mess_list,
.mess_list > .mess_container,
.mess_list > .mess_container .mess > .content {
    background: transparent !important;
}
body:not(#qms) .wrapper,
body:not(#qms) .mess_list {
    background: var(--minimal-reader-bg) !important;
}
.post_container,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news-comments-entry,
.news_item,
.news_block,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess {
    border: 1px solid var(--minimal-reader-divider) !important;
    border-radius: 1.125rem !important;
    box-shadow: none !important;
}
.navigation,
.post_header,
.post_footer,
.post_title,
.poll > .title,
.comments .title,
.news-detail-header .news-detail-header-text {
    background: var(--minimal-reader-toolbar) !important;
    border-color: var(--minimal-reader-divider) !important;
    color: var(--minimal-reader-text) !important;
}
.post_header,
.post_footer {
    border-color: var(--minimal-reader-divider) !important;
}
.post_container .post_footer {
    background: var(--minimal-reader-footer) !important;
}
.post_container,
.poll,
.navigation,
.search_post,
.search_post_container,
.search_result_block,
.bad-search-result,
.news-detail-header,
.news_item,
.news_block,
#news > .content,
.content,
.comments,
.materials,
.materials .material_item,
.mess_list > .mess_container .mess {
    box-shadow: none !important;
}
.post_header .inf,
.post_header .inf span,
.post_footer,
.post_footer *,
.post_rating,
.news-detail-header .news-detail-header-meta,
#search .search_post_id_hint,
.comments .title,
.comments .content,
.materials .material_item .title,
.poll .votes_info,
.poll .poll_status {
    color: var(--minimal-reader-text-secondary) !important;
}
.post_body,
#news > .content,
.content,
.comments {
    color: var(--minimal-reader-text) !important;
}
.post_body a,
#news > .content a,
.content a,
.comments a,
.post_title a,
.materials .material_item .title {
    color: var(--minimal-reader-accent) !important;
}
.post_container .post_footer .post_actions_row .btn,
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote,
.poll > .body .buttons > .btn,
.news-inline-comments .news-inline-comments-retry,
.news-inline-comments .news-inline-comment-action,
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button,
#search a.search_post_btn {
    background: transparent !important;
    border-color: var(--minimal-reader-divider) !important;
    color: var(--minimal-reader-accent) !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn,
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote {
    background: transparent !important;
    border-color: transparent !important;
    color: var(--topic-action-icon-color) !important;
}
.post_container .post_footer .post_actions_row .btn:active,
.post_container .post_footer .post_actions_row .btn.rep_up:active,
.post_container .post_footer .post_actions_row .btn.rep_down:active,
.post_container .post_footer .post_actions_row .btn.reply:active,
.post_container .post_footer .post_actions_row .btn.quote:active,
.poll > .body .buttons > .btn:active,
.news-inline-comments .news-inline-comments-retry:active,
.news-inline-comments .news-inline-comment-action:active,
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button:active,
#search a.search_post_btn:active {
    background: var(--minimal-reader-selection) !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn:active,
.post_container .post_footer .post_actions_row .btn.rep_up:active,
.post_container .post_footer .post_actions_row .btn.rep_down:active,
.post_container .post_footer .post_actions_row .btn.reply:active,
.post_container .post_footer .post_actions_row .btn.quote:active {
    background: transparent !important;
    border-color: transparent !important;
    box-shadow: none !important;
}
.post_container .post_footer .post_actions_row .btn > .post-action-icon {
    color: var(--topic-action-icon-color) !important;
    fill: none !important;
    stroke: currentColor !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon *,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-icon,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-icon * {
    color: var(--topic-action-icon-color) !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-icon * {
    fill: none !important;
    stroke: currentColor !important;
}
.post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon,
.post_container .post_footer .post_actions_row .btn > .post-action-stroke-icon * {
    fill: none !important;
    stroke: currentColor !important;
    stroke-width: var(--post-action-stroke-width, 1.85) !important;
    stroke-linecap: round !important;
    stroke-linejoin: round !important;
    stroke-opacity: 1 !important;
    vector-effect: non-scaling-stroke !important;
}
.post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon,
.post_container .post_footer .post_actions_row .btn.reply > .post-action-reply-icon *,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon,
.post_container .post_footer .post_actions_row .btn.quote > .post-action-quote-icon * {
    stroke-width: var(--post-action-light-stroke-width, 1.2) !important;
    stroke-opacity: 1 !important;
}
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down,
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote {
    color: var(--topic-action-icon-color) !important;
}
.post_container .post_footer .post_actions_row .btn > .rep-action-icon {
    color: var(--topic-action-icon-color) !important;
    fill: currentColor !important;
    stroke: none !important;
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
}
.post_container .post_footer .post_actions_row .btn > .rep-action-icon * {
    fill: currentColor !important;
    stroke: none !important;
}
.post-block,
blockquote,
.quote,
.spoil,
.hidden,
.post_body table,
.poll > .body .questions .question > .items .item.result .range_bar,
#news > .content blockquote,
pre,
code {
    background: var(--minimal-reader-secondary-bg) !important;
    border-color: var(--minimal-reader-divider) !important;
    color: var(--minimal-reader-text) !important;
    border-radius: 0.875rem !important;
    box-shadow: none !important;
}
body#topic .post_container .post_body,
body#topic .post_container .post_body .postcolor,
body#topic .post_container .hat_content {
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
}
body#topic .post_container,
body#topic .post_container .post_header,
body#topic .post_container .post_footer {
    background: var(--minimal-reader-card) !important;
}
body#topic .post_container .hat_content > .post_body,
body#topic .post_container.topic_hat_fixed .post_body,
body#topic .post_container.topic_hat_entry .post_body,
body#topic .post_container .post_body .postcolor {
    box-shadow: none !important;
    border-color: transparent !important;
}
body#topic .post-block,
body#topic blockquote,
body#topic .quote,
body#topic .spoil,
body#topic .hidden,
body#topic .post_body table,
body#topic pre,
body#topic code,
body#topic #news > .content blockquote {
    background: var(--minimal-reader-secondary-bg) !important;
}
body#topic .post_body > .post-block:not(.quote):not(.spoil):not(.hidden):not(.code),
body#topic .post_body > .postcolor > .post-block:not(.quote):not(.spoil):not(.hidden):not(.code),
body#topic .post_body > .post-block:not(.quote):not(.spoil):not(.hidden):not(.code) > .block-title,
body#topic .post_body > .postcolor > .post-block:not(.quote):not(.spoil):not(.hidden):not(.code) > .block-title,
body#topic .post_body > .post-block:not(.quote):not(.spoil):not(.hidden):not(.code) > .block-body,
body#topic .post_body > .postcolor > .post-block:not(.quote):not(.spoil):not(.hidden):not(.code) > .block-body {
    background: transparent !important;
    background-color: transparent !important;
    background-image: none !important;
    border-color: var(--minimal-reader-divider) !important;
}
.post-block.quote,
blockquote,
.quote,
#news > .content blockquote {
    border-left: 0 !important;
    border-radius: 0.75rem !important;
    background: var(--minimal-reader-secondary-bg) !important;
}
.post-block.quote:before {
    content: none !important;
}
.post-block.code,
pre,
code {
    color: var(--minimal-reader-text-secondary) !important;
}
.post-block > .block-body,
.post-block.spoil > .block-body,
.post-block.hidden > .block-body,
.post-block.code > .block-body,
.post-block.quote > .block-body {
    color: var(--minimal-reader-text) !important;
}
.post-block > .block-title,
.post-block.spoil > .block-title,
.poll > .body .questions .question > .title {
    color: var(--minimal-reader-text) !important;
}
.post-block > .block-title,
.post-block.spoil > .block-title {
    background: var(--minimal-reader-secondary-bg) !important;
    border-color: var(--minimal-reader-divider) !important;
}
body#topic .post-block > .block-title,
body#topic .post-block.spoil > .block-title {
    background: var(--minimal-reader-secondary-bg) !important;
}
.post_body table th,
.post_body table td {
    border-color: var(--minimal-reader-divider) !important;
}
.poll > .body .questions .question > .items .item.result > .title {
    background: var(--minimal-reader-card) !important;
    box-shadow: 0.75em 0 0.5em -0.25em var(--minimal-reader-card) !important;
}
.poll > .body .questions .question > .items .item.result .range_bar .range {
    background: var(--minimal-reader-accent-soft) !important;
}
.poll > .body .buttons > .btn.vote {
    background: var(--minimal-reader-accent) !important;
    color: var(--minimal-reader-card) !important;
}
${newsArticleCtaButtonOverrideCss("var(--minimal-reader-accent)", "var(--minimal-reader-card)")}
#search .hat_content.close.over_height:after {
    box-shadow: inset 0 -6rem 3rem -3rem var(--minimal-reader-card) !important;
}
#news > .content img.app-stable-media,
#news > .content iframe.app-stable-media,
.news-detail-header .news-detail-header-image {
    background: var(--minimal-reader-secondary-bg) !important;
}
.theme_bottom_pagination button {
    background: transparent !important;
    border-color: transparent !important;
    color: var(--topic-pagination-icon) !important;
    box-shadow: none !important;
}
.theme_bottom_pagination button.theme_bottom_pagination_current {
    background: transparent !important;
    color: var(--topic-pagination-icon) !important;
}
.theme_bottom_pagination button.disabled {
    color: var(--topic-pagination-icon-disabled) !important;
}
.theme_bottom_pagination {
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
    border-radius: 0 !important;
}
body#topic {
    --topic-collapsible-header-color: var(--minimal-reader-text);
    --topic-collapsible-header-icon-color: var(--topic-collapsible-header-color);
}
body#topic .post_container.topic_hat_fixed > .hat_button,
body#topic .post_container.topic_hat_entry > .hat_button,
body#topic .poll > a.btn.title.aec {
    background: var(--minimal-reader-toolbar) !important;
    color: var(--topic-collapsible-header-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > span,
body#topic .post_container.topic_hat_entry > .hat_button > span,
body#topic .poll > a.btn.title.aec > span {
    color: inherit !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon,
body#topic .post_container.topic_hat_entry > .hat_button > .icon,
body#topic .poll > a.btn.title.aec > .icon {
    color: var(--topic-collapsible-header-icon-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:after,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:after,
body#topic .poll > a.btn.title.aec > .icon:after {
    border-color: currentColor !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:before,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:before,
body#topic .poll > a.btn.title.aec > .icon:before {
    background: currentColor !important;
}
.post_container > .hat_button,
.post_container .post_header .inf.menu {
    background-color: transparent !important;
}
.post_container .post_footer .post_actions_row .btn.rep_up,
.post_container .post_footer .post_actions_row .btn.rep_down {
    color: var(--topic-action-icon-color) !important;
}
.post_container .post_footer .post_actions_row .btn.reply,
.post_container .post_footer .post_actions_row .btn.quote {
    color: var(--topic-action-icon-color) !important;
}
:not(.post_actions_row) > .rep_up,
.positive,
.poll .votes_info {
    color: var(--minimal-reader-success) !important;
}
:not(.post_actions_row) > .rep_down,
.negative,
.error {
    color: var(--minimal-reader-warning) !important;
}
.mess_list > .date span,
.post_footer .btn.disabled {
    color: var(--minimal-reader-muted) !important;
}
""".trimIndent()
        return "<style>\n$css</style>"
    }

    private fun getAmoledOverrideCss(): String {
        if (!isAmoled()) return ""
        // Посты делаем чистым чёрным (#000), пространство между постами — серым (#1a1a1a),
        // чтобы в AMOLED был выражен контраст и разделители были видны.
        val css = """
:root {
    --surface-background: #000000;
    --surface-card: #000000;
    --surface-elevated: #000000;
    --surface-divider: #141414;
    --surface-text-primary: #ffffff;
    --surface-text-secondary: #b2b2b2;
    --surface-icon: #b2b2b2;
    --surface-accent: #9E9E9E;
    --surface-control: rgba(255, 255, 255, 0.08);
    --surface-control-active: rgba(255, 255, 255, 0.14);
    --topic-action-icon-color: var(--surface-accent);
    --surface-radius-small: 0.375rem;
    --surface-radius-medium: 0.75rem;
    --surface-radius-large: 0.875rem;
}
html, body { background: #000000 !important; }
.post_container { background: #000000 !important; }
.search_post, .search_post_container, .search_result_block, .bad-search-result { background: #000000 !important; }
.news_item, .news_block, .news_container, .news_post { background: #000000 !important; }
.news-comments-entry { background: #000000 !important; }
#news > .content { background: #000000 !important; }
.content { background: #000000 !important; }
.poll,
.poll > .title,
.poll > .body,
.poll > .body .questions,
.poll > .body .questions .question,
.poll > .body .questions .question > .items {
    background: #000000 !important;
}
.poll > .body .questions .question > .items .item.result > .title {
    background: #000000 !important;
    box-shadow: 0.75em 0 0.5em -0.25em #000000 !important;
}
.poll > .body .questions .question > .items .item.result .range_bar {
    background: #000000 !important;
    box-shadow: inset 0 0 0 1px #2a2a2a !important;
}
.poll > .body .questions .question > .items .item.result .range_bar .range { background: rgba(255, 255, 255, 0.14) !important; }
.poll > .body .buttons,
.poll > .body .buttons > *,
.poll > .body .poll_status {
    background: #000000 !important;
}
.poll > .body .buttons > .btn {
    background: rgba(255, 255, 255, 0.10) !important;
    color: #ffffff !important;
    border-radius: 0.75rem !important;
}
.poll > .body .buttons > .btn.vote {
    background: #ffffff !important;
    color: #000000 !important;
}
${newsArticleCtaButtonOverrideCss("#2980b9", "#ffffff")}
.poll.poll_overlay_host.open > .body::-webkit-scrollbar,
.poll.poll_overlay_host.open > .body::-webkit-scrollbar-track,
.poll.poll_overlay_host.open > .body::-webkit-scrollbar-corner {
    background: #000000 !important;
}
/* QMS чат — реальные селекторы из dark_qms.css */
body#qms .wrapper,
body#qms .mess_list { background: transparent !important; }
.mess_list > .mess_container .mess { background: #000000 !important; }
.mess_list > .mess_container,
.mess_list > .mess_container .mess > .content { background: transparent !important; }
/* Разделитель между постами (виден как серая полоса) */
.post_container {
    margin: 0.5em 0 !important;
    box-shadow: 0 0 0 1px #1a1a1a !important;
    border-radius: 0.875rem !important;
    overflow: hidden !important;
}
.poll {
    background: #000000 !important;
    box-shadow: inset 0 0 0 1px #141414 !important;
    border: none !important;
    border-radius: 0.875rem !important;
    overflow: hidden !important;
}
/* Пагинация внизу — чёрный фон для AMOLED */
.navigation {
    background: #000000 !important;
    margin: 0.5em 0.25rem 0 0.25rem !important;
    box-shadow: inset 0 0 0 1px #141414 !important;
    border: none !important;
    border-radius: 0.875rem !important;
    overflow: hidden !important;
}
.theme_bottom_pagination {
    background: transparent !important;
    box-shadow: none !important;
    border: none !important;
    border-radius: 0 !important;
}
.theme_bottom_pagination button {
    background: transparent !important;
    color: #ffffff !important;
}
.theme_bottom_pagination button.theme_bottom_pagination_current {
    background: transparent !important;
    color: #ffffff !important;
}
/*
 * Полупрозрачный фон для «чип»-кнопок (опрос, футер поста и т.д.).
 * Не трогаем .hat_button («шапка темы») и .menu (три точки): у них class btn,
 * а shorthand background сбрасывает background-image у иконки меню и даёт серую плашку.
 */
.btn:not(.hat_button):not(.menu):not(.rep_up):not(.rep_down):not(.reply):not(.quote) { background: rgba(255, 255, 255, 0.08) !important; }
body#topic {
    --topic-collapsible-header-color: #ffffff;
    --topic-collapsible-header-icon-color: var(--topic-collapsible-header-color);
}
body#topic .post_container.topic_hat_fixed > .hat_button,
body#topic .post_container.topic_hat_entry > .hat_button,
body#topic .poll > a.btn.title.aec {
    background: #000000 !important;
    color: var(--topic-collapsible-header-color) !important;
}
.poll.close > a.btn.title.aec {
    box-shadow: inset 0 0 0 1px #141414 !important;
    border-radius: 0.875rem !important;
    overflow: hidden !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > span,
body#topic .post_container.topic_hat_entry > .hat_button > span,
body#topic .poll > a.btn.title.aec > span {
    color: inherit !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon,
body#topic .post_container.topic_hat_entry > .hat_button > .icon,
body#topic .poll > a.btn.title.aec > .icon {
    color: var(--topic-collapsible-header-icon-color) !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:after,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:after,
body#topic .poll > a.btn.title.aec > .icon:after {
    border-color: currentColor !important;
}
body#topic .post_container.topic_hat_fixed > .hat_button > .icon:before,
body#topic .post_container.topic_hat_entry > .hat_button > .icon:before,
body#topic .poll > a.btn.title.aec > .icon:before {
    background: currentColor !important;
}
.post_container > .hat_button { background: transparent !important; }
.post_container .post_header .inf.menu {
  background-color: transparent !important;
}
""".trimIndent()
        return "<style>\n$css</style>"
    }

    private data class MinimalReaderCssColors(
            val background: String,
            val secondaryBackground: String,
            val card: String,
            val toolbar: String,
            val footer: String,
            val divider: String,
            val primaryText: String,
            val secondaryText: String,
            val mutedText: String,
            val accent: String,
            val accentSoft: String,
            val selection: String,
            val warning: String,
            val success: String,
            val actionButton: String,
            val actionButtonActive: String,
            val actionButtonBorder: String,
            val buttonShadow: String
    )

    private data class SepiaReadingCssColors(
            val background: String,
            val card: String,
            val surface: String,
            val toolbar: String,
            val border: String,
            val primaryText: String,
            val secondaryText: String,
            val link: String,
            val button: String,
            val buttonActive: String,
            val actionButtonBorder: String,
            val buttonShadow: String,
            val tapHighlight: String
    )

    private data class SepiaBlueCssColors(
            val background: String,
            val secondaryBackground: String,
            val card: String,
            val surface: String,
            val toolbar: String,
            val border: String,
            val primaryText: String,
            val secondaryText: String,
            val mutedText: String,
            val link: String,
            val accentSoft: String,
            val button: String,
            val buttonActive: String,
            val actionButtonBorder: String,
            val buttonShadow: String,
            val tapHighlight: String,
            val success: String,
            val warning: String
    )
}
