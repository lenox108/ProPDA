package forpdateam.ru.forpda.ui

import android.content.Context
import android.os.Build
import com.google.android.material.color.utilities.MaterialDynamicColors
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
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
        private val context: Context,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val dayNightHelper: DayNightHelper,
        private val paletteResolver: TemplatePaletteResolver,
) {

    private fun isSepiaReading(): Boolean = paletteResolver.isSepiaReading()
    private fun isSepiaBlue(): Boolean = paletteResolver.isSepiaBlue()
    private fun isMinimalReader(): Boolean = paletteResolver.isMinimalReader()
    private fun isAmoled(): Boolean = paletteResolver.isAmoled()

    /** Активна ли одна из новых читающих палитр (Green/Nord/Solarized/…). */
    private fun isNewReadingPalette(): Boolean =
            newPaletteSpecs.containsKey(paletteResolver.activePalette())

    /**
     * Спеки новых читающих палитр для WebView-репейнта форума. Порядок полей —
     * page(background)/post(card)/elevated/text/text2/link/divider/toolbar,
     * ЗЕРКАЛИТ нативные `@color/<pid>_*` токены (styles/colors_reading_palettes).
     */
    private class PaletteSpec(
            val base: String, val card: String, val elevated: String,
            val text: String, val text2: String, val link: String,
            val divider: String, val toolbar: String,
    )

    private val newPaletteSpecs: Map<Preferences.Main.UiPalette, Pair<PaletteSpec, PaletteSpec>> = mapOf(
            Preferences.Main.UiPalette.GREEN_CARE to Pair(
                    PaletteSpec("#C8E6C9", "#DFF0E1", "#BEDDC1", "#1B3B24", "#4C6B54", "#2E7D4F", "#AACDAD", "#D3EAD4"),
                    PaletteSpec("#0F1810", "#1E2E20", "#182619", "#CFE9D3", "#8EB398", "#6FCF97", "#2A3B2D", "#17251A")),
            Preferences.Main.UiPalette.NORD to Pair(
                    PaletteSpec("#ECEFF4", "#FFFFFF", "#E5E9F0", "#2E3440", "#4C566A", "#5E81AC", "#D8DEE9", "#E5E9F0"),
                    PaletteSpec("#2E3440", "#3B4252", "#434C5E", "#ECEFF4", "#AEB6C6", "#88C0D0", "#434C5E", "#353C4B")),
            Preferences.Main.UiPalette.SOLARIZED to Pair(
                    PaletteSpec("#F3ECD8", "#FDF6E3", "#EEE8D5", "#586E75", "#839496", "#268BD2", "#E3DCC9", "#EEE8D5"),
                    PaletteSpec("#002B36", "#073642", "#0A404E", "#93A1A1", "#7C8E8E", "#2AA198", "#0E4B5A", "#063240")),
            Preferences.Main.UiPalette.GRUVBOX to Pair(
                    PaletteSpec("#F2E5BC", "#FBF1C7", "#EBDBB2", "#3C3836", "#665C54", "#AF3A03", "#DDCCA7", "#EBDBB2"),
                    PaletteSpec("#282828", "#32302F", "#3C3836", "#EBDBB2", "#A89984", "#FE8019", "#504945", "#32302F")),
            Preferences.Main.UiPalette.ROSE_PINE to Pair(
                    PaletteSpec("#FAF4ED", "#FFFAF3", "#F2E9E1", "#575279", "#797593", "#907AA9", "#E5DDD3", "#F2E9E1"),
                    PaletteSpec("#191724", "#1F1D2E", "#26233A", "#E0DEF4", "#908CAA", "#C4A7E7", "#2A2739", "#1F1D2E")),
            // Dracula следует светлому/тёмному, как остальные палитры: light — холодный
            // Alucard (не тёплый крем, чтобы не путать с Sepia; фиолетовый акцент + видимый
            // разделитель), dark — классическая тёмная Dracula.
            Preferences.Main.UiPalette.DRACULA to Pair(
                    PaletteSpec("#F2F2F7", "#FFFFFF", "#E9E9F0", "#1F1F2B", "#565669", "#7A5CD0", "#D3D3E0", "#E9E9F0"),
                    PaletteSpec("#282A36", "#343746", "#3C3F51", "#F8F8F2", "#A7ABBE", "#BD93F9", "#44475A", "#2E303E")),
    )

    /** Seed-цвета курируемых палитр — синхронно с AccentPaletteGenerator/AccentApplier. */
    private val curatedSeeds: Map<Preferences.Main.AccentPalette, Int> = mapOf(
            Preferences.Main.AccentPalette.BLUE to 0xFF0B57D0.toInt(),
            Preferences.Main.AccentPalette.INDIGO to 0xFF4355B9.toInt(),
            Preferences.Main.AccentPalette.VIOLET to 0xFF7B4FCF.toInt(),
            Preferences.Main.AccentPalette.PURPLE to 0xFF9C27B0.toInt(),
            Preferences.Main.AccentPalette.PINK to 0xFFC2185B.toInt(),
            Preferences.Main.AccentPalette.RED to 0xFFD32F2F.toInt(),
            Preferences.Main.AccentPalette.DEEPORANGE to 0xFFE64A19.toInt(),
            Preferences.Main.AccentPalette.ORANGE to 0xFFEF6C00.toInt(),
            Preferences.Main.AccentPalette.AMBER to 0xFFFF8F00.toInt(),
            Preferences.Main.AccentPalette.GREEN to 0xFF2E7D32.toInt(),
            Preferences.Main.AccentPalette.TEAL to 0xFF00796B.toInt(),
            Preferences.Main.AccentPalette.CYAN to 0xFF0097A7.toInt(),
    )

    /**
     * Акцент для контента WebView, СЛЕДУЮЩИЙ за выбором цвета/Material You (G2).
     * Возвращает hex `#RRGGBB` или null → оставить статический palette-default.
     *
     * Считается in-process (у композера @ApplicationContext без per-activity
     * оверлея, поэтому НЕ читаем тему): Material You → системная роль primary,
     * повторяя маппинг Material по версии Android (API 34+: `system_primary_*`;
     * API 31–33: `system_accent1_600/_200` — см. тело функции); курируемая/
     * произвольная палитра → M3 primary из seed через общий [AccentSchemes.scheme],
     * тот же, что и нативные ресурсы. Палитры чтения (Sepia/Minimal) сохраняют
     * свои выверенные акценты → null.
     */
    private fun resolveDynamicAccentHex(): String? {
        if (isSepiaReading() || isSepiaBlue() || isMinimalReader() || isNewReadingPalette()) return null
        val isNight = dayNightHelper.isNight()
        return runCatching {
            val materialYou = mainPreferencesHolder.getUseMaterialYou()
            if (materialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Композер работает на @ApplicationContext (без per-activity
                // оверлея DynamicColors), поэтому не может прочитать роль
                // ?attr/colorPrimary напрямую и повторяет маппинг Material
                // ВРУЧНУЮ. Маппитель у Material РАЗНЫЙ по версии Android:
                //  - API 31–33 (values-v31): colorPrimary → сырой тон-слот
                //    палитры обоев system_accent1_600 (light) / _200 (dark).
                //  - API 34+ (values-v34): colorPrimary → НОВАЯ системная роль
                //    system_primary_light / system_primary_dark, которая учитывает
                //    выбранный в системе «стиль цвета» (Tonal/Vibrant/Expressive)
                //    и при любом не-дефолтном стиле НЕ равна system_accent1_600.
                // Если тут всегда читать accent1_600/_200, то на Android 14+ акцент
                // форума расходится с нативным хромом (жалоба «неправильные акценты»).
                val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (isNight) android.R.color.system_primary_dark
                    else android.R.color.system_primary_light
                } else {
                    if (isNight) android.R.color.system_accent1_200
                    else android.R.color.system_accent1_600
                }
                return@runCatching "#%06X".format(context.getColor(res) and 0xFFFFFF)
            }
            val accent = mainPreferencesHolder.getAccentPalette()
            val seed = when (accent) {
                Preferences.Main.AccentPalette.NEUTRAL -> return@runCatching null
                Preferences.Main.AccentPalette.CUSTOM -> mainPreferencesHolder.getAccentCustomColor()
                else -> curatedSeeds[accent] ?: return@runCatching null
            }
            // ЕДИНЫЙ источник правды со схемой (AccentSchemes) — тот же, что
            // генерит нативные @color/accent_* и превью пикера. Раньше здесь
            // строилась схема инлайном, и для стиля EXPRESSIVE бралась RAW
            // SchemeExpressive (primary = hue(seed)+240°): форум в WebView
            // красился «не тем» оттенком (Синий → зелёный), пока хром/свотч/
            // превью оставались корректными. AccentSchemes.expressive() чинит
            // primary по тону seed — теперь все потребители сходятся.
            val scheme = AccentSchemes.scheme(seed, mainPreferencesHolder.getAccentStyle(), isNight)
            val argb = MaterialDynamicColors().primary().getArgb(scheme)
            "#%06X".format(argb and 0xFFFFFF)
        }.getOrNull()
    }

    /**
     * Cache key for the last [compose] result. The composed CSS depends on the
     * font mode, night/palette flags И на акцент (--ppda-accent) + Material You
     * (тонирование тела форума). Раньше акцент/стиль/MY НЕ входили в ключ — если
     * их переключали в рамках одной сессии (recreate, без рестарта процесса),
     * композер-синглтон отдавал устаревший CSS до перезапуска приложения. Теперь
     * входят — CSS пересобирается сразу.
     *
     * [materialYouSignature] — фактические системные MY-цвета (обои). Флага
     * [materialYou] в ключе НЕ достаточно: при смене ОБОЕВ он остаётся true, а
     * accent/style/customColor не меняются (палитра = SYSTEM), поэтому без
     * сигнатуры ключ идентичен и синглтон-композер отдавал СТАРЫЙ CSS — и оттенок
     * (--ppda-accent/--forum-accent), и фон (--forum-bg/card/text) не менялись
     * после смены обоев до перезапуска процесса. Сигнатура = реально
     * резолвнутые системные роли primary и surface (API 34+: system_primary /
     * system_surface; 31–33: system_accent1 / system_neutral1): при смене обоев
     * она меняется — кэш инвалидируется, CSS пересобирается со свежими цветами.
     */
    private data class CssConfig(
            val fontMode: Any?,
            val night: Boolean,
            val sepiaReading: Boolean,
            val sepiaBlue: Boolean,
            val minimalReader: Boolean,
            val newReadingPalette: Any?,
            val amoled: Boolean,
            val materialYou: Boolean,
            val accent: Any?,
            val accentStyle: Any?,
            val accentCustomColor: Int,
            val materialYouSignature: String?,
    )

    private var cachedConfig: CssConfig? = null
    private var cachedCss: String? = null

    private fun currentConfig(): CssConfig = CssConfig(
            fontMode = FontController.getCurrentFontMode(mainPreferencesHolder),
            night = dayNightHelper.isNight(),
            sepiaReading = isSepiaReading(),
            sepiaBlue = isSepiaBlue(),
            minimalReader = isMinimalReader(),
            newReadingPalette = if (isNewReadingPalette()) paletteResolver.activePalette() else null,
            amoled = isAmoled(),
            materialYou = mainPreferencesHolder.getUseMaterialYou(),
            accent = mainPreferencesHolder.getAccentPalette(),
            accentStyle = mainPreferencesHolder.getAccentStyle(),
            accentCustomColor = mainPreferencesHolder.getAccentCustomColor(),
            materialYouSignature = materialYouSignature(),
    )

    /**
     * Отпечаток текущих системных MY-цветов (обоев) для ключа кэша CSS. null,
     * если MY выкл или API < 31 (тогда цвет обоев не участвует, и кэш держат
     * остальные поля [CssConfig]). Читаем ровно те роли, что использует рендер
     * (акцент + фон/карточки), чтобы кэш инвалидировался при ЛЮБОЙ смене обоев.
     */
    private fun materialYouSignature(): String? {
        if (!mainPreferencesHolder.getUseMaterialYou() ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        fun c(id: Int): Int = runCatching { context.getColor(id) }.getOrDefault(0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            "${c(android.R.color.system_primary_light)}:${c(android.R.color.system_primary_dark)}:" +
                    "${c(android.R.color.system_surface_light)}:${c(android.R.color.system_surface_dark)}"
        } else {
            "${c(android.R.color.system_accent1_600)}:${c(android.R.color.system_accent1_200)}:" +
                    "${c(android.R.color.system_neutral1_50)}:${c(android.R.color.system_neutral1_900)}"
        }
    }

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
                getNewReadingPaletteOverrideCss(),
                getAmoledOverrideCss(),
                getForumSurfaceOverrideCss(),
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

    /**
     * Palette-aware "where I stopped" highlight ring, injected into
     * [getThemeLayoutSafetyCss] (which runs LAST in [compose], after every
     * per-palette override composer) immediately after the `--ppda-accent`
     * `:root` declaration it depends on.
     *
     * CAUSE-2 fix (Audit Findings H-04 + follow-up): several palettes declare
     * `.post_container { box-shadow: inset 0 0 0 1px <palette-border> !important }`
     * — sepia (`--sepia-border`), sepia-blue (`--sepia-blue-border`), amoled
     * (`#1a1a1a`). That pale 1px palette border kept WINNING the box-shadow
     * PROPERTY on device even though our highlight selector out-specifies it
     * (device log: `boxShadow=[rgb(53,65,72) 0 0 0 1px inset, ...]` ==
     * dark `--sepia-blue-border`). Because a single `box-shadow` is one
     * property, the palette rule and the ring rule fight for the SAME property,
     * and on this build the palette won regardless.
     *
     * The robust fix: draw the visible contour as a transparent-fill BORDER on
     * a dedicated `::before` pseudo-element. `border` on `::before` is a
     * DIFFERENT property on a DIFFERENT box, so NO `.post_container {
     * box-shadow }` palette rule can ever override it. `inset:0 + border` keeps
     * the ring inside the wrapper's `overflow:hidden`, so it is visible on ALL
     * FOUR sides (the box-shadow-only version showed only top/bottom on
     * full-bleed posts). The inset box-shadow is kept only as a soft inner glow.
     *
     * The ring colour resolves to the palette-aware `--ppda-accent` defined just
     * above it in the same CSS block, so the contour matches the active palette
     * (blue on sepia-blue, warm on sepia, etc.). NO `background` tint (the
     * "whole post flashes" defect) and NO `color-mix()` (breaks pre-Chromium-111
     * WebViews). The fading class fades the `::before` opacity to 0.
     */
    private val topicHighlightRingOverrideCss: String = """
body#topic .post_container.ppda_highlight_post::before {
    content: "" !important;
    position: absolute !important;
    left: 0 !important;
    top: 0 !important;
    right: 0 !important;
    bottom: 0 !important;
    border: 0.1875rem solid var(--ppda-accent, var(--surface-accent, #2177af)) !important;
    -webkit-border-radius: inherit !important;
    border-radius: inherit !important;
    background: transparent !important;
    pointer-events: none !important;
    z-index: 5 !important;
    opacity: 1 !important;
    -webkit-transition: opacity 0.3s ease !important;
    transition: opacity 0.3s ease !important;
}
body#topic .post_container.ppda_highlight_post.ppda_highlight_fading::before {
    opacity: 0 !important;
}
body#topic .post_container.ppda_highlight_post {
    -webkit-box-shadow: inset 0 0 0.5rem 0.1rem var(--ppda-accent, var(--surface-accent, #2177af)) !important;
    box-shadow: inset 0 0 0.5rem 0.1rem var(--ppda-accent, var(--surface-accent, #2177af)) !important;
}
body#topic .post_container.ppda_highlight_post.ppda_highlight_fading {
    -webkit-box-shadow: inset 0 0 0 0 rgba(0, 0, 0, 0) !important;
    box-shadow: inset 0 0 0 0 rgba(0, 0, 0, 0) !important;
}
""".trimIndent()

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
        // G2: если выбран акцент/Material You/произвольный цвет — контент форума
        // следует за ним; иначе — статический palette-default (как раньше).
        val accent = resolveDynamicAccentHex() ?: when {
            isSepiaReading() -> if (dayNightHelper.isNight()) "#C9975B" else "#8A5A2B"
            isSepiaBlue() -> if (dayNightHelper.isNight()) "#8FB3C8" else "#4F7896"
            isMinimalReader() -> if (dayNightHelper.isNight()) "#8DA3B8" else "#7C8FA1"
            isNewReadingPalette() -> activeNewPaletteColors().link
            isAmoled() -> "#9E9E9E"
            else -> if (dayNightHelper.isNight()) "#78B8E6" else "#2177AF"
        }
        val accentRootCss = ":root { --ppda-accent: $accent; }\n"
        val css = """
$accentRootCss
$topicHighlightRingOverrideCss
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

    /**
     * Тонирование тела форума (WebView) под выбранный акцент / Material You — то же,
     * что и нативный хром, но WebView не читает Android-M3-роли (у него свой CSS-слой
     * с захардкоженными *_themes.css), поэтому мостим цвета в CSS вручную.
     *
     * Только для системной палитры (Sepia/SepiaBlue/Minimal самодостаточны, AMOLED —
     * намеренно чёрный). Два режима повторяют поведение нативной оболочки:
     *  - Material You (API 31+): DynamicColors тонирует ПОВЕРХНОСТИ обоями → в форуме
     *    красим фон/карточки/текст теми же системными ролями, что резолвит нативный
     *    хром (API 34+: system_surface_* / system_on_surface_* + зеркало DARK-floor;
     *    API 31–33: аппроксимация system_neutral1_*) + ссылки системным акцентом.
     *  - Курируемый/произвольный акцент (MY выкл): accent-оверлей НЕ трогает
     *    поверхности (нейтральны) → в форуме поверхности НЕ трогаем, красим только
     *    ссылки/кнопки акцентом (из seed через ту же схему, что resolveDynamicAccentHex).
     *  - NEUTRAL без MY: resolveDynamicAccentHex()==null → ничего (ссылки дефолтные).
     *
     * Границы постов (box-shadow) НЕ трогаем — конфликт с кольцом подсветки
     * «где остановился» (см. CAUSE-2 в getThemeLayoutSafetyCss).
     */
    private fun getForumSurfaceOverrideCss(): String {
        if (isSepiaReading() || isSepiaBlue() || isMinimalReader() || isNewReadingPalette() || isAmoled()) return ""
        val accentHex = resolveDynamicAccentHex() ?: return ""
        val isDark = dayNightHelper.isNight()
        val materialYou = mainPreferencesHolder.getUseMaterialYou() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val root = StringBuilder("    --forum-accent: $accentHex;\n    --surface-accent: $accentHex;\n")
        val rules = StringBuilder(
                "a { color: var(--forum-accent) !important; }\n" +
                "button, .btn { color: var(--forum-accent) !important; }\n")

        if (materialYou) {
            fun hex(resId: Int): String = "#%06X".format(context.getColor(resId) and 0xFFFFFF)
            val bg: String; val card: String; val text: String
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+: Material мапит surface-роли на НОВЫЕ системные роли
                // (values-v34), а не на сырые тон-слоты system_neutral1_*.
                // Зеркалим то, что реально резолвит нативный хром:
                //   фон страницы = colorSurfaceContainerLowest → system_surface_container_lowest_*
                //   карточки     = colorSurface               → system_surface_*
                //   текст        = colorOnSurface             → system_on_surface_*
                if (isDark) {
                    // Зеркало DARK-floor — как MaterialYouApplier.
                    // dynamicDarkSurfaceIsNearBlack(): каноничный тёмный lowest
                    // (тон L*=4, на Android 16 и чистый #000000) темнее эталона,
                    // и натив перепинивает светлоту рампы floor-цветами. Читаем
                    // ТЕ ЖЕ lStar-селекторы (@color/material_you_dark_floor_*):
                    // они резолвятся и на ApplicationContext (база — системный
                    // слот обоев), поэтому совпадение с нативом точное.
                    val lowest = context.getColor(android.R.color.system_surface_container_lowest_dark)
                    val reference = context.getColor(R.color.dark_background_base)
                    if (androidx.core.graphics.ColorUtils.calculateLuminance(lowest) <=
                            androidx.core.graphics.ColorUtils.calculateLuminance(reference)) {
                        bg = hex(R.color.material_you_dark_floor_base)
                        card = hex(R.color.material_you_dark_floor_card)
                    } else {
                        bg = hex(android.R.color.system_surface_container_lowest_dark)
                        card = hex(android.R.color.system_surface_dark)
                    }
                    text = hex(android.R.color.system_on_surface_dark)
                } else {
                    bg = hex(android.R.color.system_surface_container_lowest_light)
                    card = hex(android.R.color.system_surface_light)
                    text = hex(android.R.color.system_on_surface_light)
                }
            } else if (isDark) {
                // API 31–33: нативный DARK-floor-гейт срабатывает и здесь
                // (динамический lowest — тон L*=4, темнее эталона независимо
                // от обоев) → нативная рампа = floor-цвета. Зеркалим их же
                // (lStar-селекторы резолвятся без темы), текст — прежняя
                // аппроксимация нейтралями (точного слота v31 не отдаёт).
                bg = hex(R.color.material_you_dark_floor_base)
                card = hex(R.color.material_you_dark_floor_card)
                text = hex(android.R.color.system_neutral1_50)
            } else {
                bg = hex(android.R.color.system_neutral1_50)
                card = hex(android.R.color.system_neutral1_10)
                text = hex(android.R.color.system_neutral1_900)
            }
            root.append("    --forum-bg: $bg;\n    --forum-card: $card;\n    --forum-text: $text;\n")
            rules.append("""
html, body {
    background: var(--forum-bg) !important;
    color: var(--forum-text) !important;
}
.post_container,
.post_container .hat_content,
.post_container .post_header,
.post_container .post_body,
.post_container .post_footer,
.topic_hat_fixed,
.topic_hat_entry,
.poll, .poll > .title, .poll > .body,
.navigation,
.search_post, .search_post_container, .search_result_block,
.news-detail-header, .news-comments-entry, .news_item, .news_block,
.news_container, .news_post, #news > .content, .content, .comments,
.materials, .materials .material_item,
.mess_list > .mess_container .mess {
    background: var(--forum-card) !important;
    color: var(--forum-text) !important;
}
""")
        }
        if (BuildConfig.DEBUG) {
            Timber.tag("ForumAccent").d("materialYou=%s dark=%s accent=%s", materialYou, isDark, accentHex)
        }
        return "<style>\n:root {\n$root}\n$rules</style>"
    }

    /** rgba() из #RRGGBB для полупрозрачных акцент-оверлеев. */
    private fun rgba(hex: String, alpha: Double): String {
        val v = hex.removePrefix("#")
        val r = v.substring(0, 2).toInt(16)
        val g = v.substring(2, 4).toInt(16)
        val b = v.substring(4, 6).toInt(16)
        return "rgba($r, $g, $b, $alpha)"
    }

    private fun colorsFromSpec(s: PaletteSpec, shadow: String): SepiaReadingCssColors =
            SepiaReadingCssColors(
                    background = s.base, card = s.card, surface = s.elevated,
                    toolbar = s.toolbar, border = s.divider,
                    primaryText = s.text, secondaryText = s.text2, link = s.link,
                    button = rgba(s.link, 0.12), buttonActive = rgba(s.link, 0.20),
                    actionButtonBorder = s.divider, buttonShadow = shadow,
                    tapHighlight = rgba(s.link, 0.22))

    /** AMOLED-вариант новой палитры: чистый чёрный фон + акцент/текст из dark-спека. */
    private fun amoledColorsFromSpec(s: PaletteSpec): SepiaReadingCssColors =
            SepiaReadingCssColors(
                    // AMOLED: карточки постов остаются чистым чёрным, но граница и elevated-
                    // поверхность (цитаты/вложения) заметно светлее — иначе посты сливались с
                    // фоном (border #1A1A1A на #000 был невидим).
                    background = "#000000", card = "#000000", surface = "#17171C",
                    toolbar = "#000000", border = "#43434D",
                    primaryText = s.text, secondaryText = s.text2, link = s.link,
                    button = rgba(s.link, 0.14), buttonActive = rgba(s.link, 0.22),
                    actionButtonBorder = "#34343B", buttonShadow = "rgba(0, 0, 0, 0.00)",
                    tapHighlight = rgba(s.link, 0.24))

    /** Разрешённые цвета активной новой читающей палитры (по режиму). */
    private fun activeNewPaletteColors(): SepiaReadingCssColors {
        val specs = newPaletteSpecs.getValue(paletteResolver.activePalette())
        val (light, dark) = specs
        return when {
            isAmoled() -> amoledColorsFromSpec(dark)
            dayNightHelper.isNight() -> colorsFromSpec(dark, "rgba(0, 0, 0, 0.18)")
            else -> colorsFromSpec(light, "rgba(0, 0, 0, 0.12)")
        }
    }

    /**
     * WebView-репейнт форума для новых читающих палитр (Green/Nord/Solarized/
     * Gruvbox/Rosé Pine/Dracula). Тот же полный набор селекторов, что у Sepia,
     * но параметризован цветами палитры (--rp-*). Самодостаточен: не следует за
     * Material You/акцентом (как остальные читающие палитры).
     */
    private fun getNewReadingPaletteOverrideCss(): String {
        if (!isNewReadingPalette()) return ""
        val c = activeNewPaletteColors()
        val css = """
:root {
    --surface-background: ${c.background};
    --surface-card: ${c.card};
    --surface-elevated: ${c.surface};
    --surface-divider: ${c.border};
    --surface-text-primary: ${c.primaryText};
    --surface-text-secondary: ${c.secondaryText};
    --surface-icon: ${c.secondaryText};
    --surface-accent: ${c.link};
    --surface-control: ${c.button};
    --surface-control-active: ${c.buttonActive};
    --topic-action-icon-color: var(--rp-link);
    --topic-pagination-surface: ${c.toolbar};
    --topic-pagination-icon: ${c.primaryText};
    --topic-pagination-icon-disabled: ${c.primaryText};
    --surface-radius-small: 0.375rem;
    --surface-radius-medium: 0.75rem;
    --surface-radius-large: 0.875rem;
    --rp-bg: ${c.background};
    --rp-card: ${c.card};
    --rp-surface: ${c.surface};
    --rp-text: ${c.primaryText};
    --rp-text-secondary: ${c.secondaryText};
    --rp-link: ${c.link};
    --rp-border: ${c.border};
    --rp-toolbar: ${c.toolbar};
    --rp-button: ${c.button};
    --rp-button-active: ${c.buttonActive};
    --rp-action-button-border: ${c.actionButtonBorder};
    --rp-button-shadow: ${c.buttonShadow};
    --rp-tap-highlight: ${c.tapHighlight};
}
html, body {
    background: var(--rp-bg) !important;
    color: var(--rp-text) !important;
}
a {
    color: var(--rp-link) !important;
    -webkit-tap-highlight-color: var(--rp-tap-highlight) !important;
}
button,
.btn {
    color: var(--rp-link) !important;
}
button:active,
.btn:active,
.aec:active {
    background: var(--rp-button) !important;
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
    background: var(--rp-card) !important;
    color: var(--rp-text) !important;
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
    box-shadow:
        inset 0 0 0 1px var(--rp-border),
        0rem 0.0625rem 0.0625rem var(--rp-button-shadow),
        0rem 0rem 0.0625rem var(--rp-button-shadow) !important;
}
.post_header,
.post_footer,
.post_title,
.poll > .title,
.comments .title,
.news-detail-header .news-detail-header-text {
    background: var(--rp-toolbar) !important;
    border-color: var(--rp-border) !important;
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
    color: var(--rp-text-secondary) !important;
}
.post_body,
#news > .content,
.content,
.comments {
    color: var(--rp-text) !important;
}
.post_body a,
#news > .content a,
.content a,
.comments a,
.post_title a,
.materials .material_item .title {
    color: var(--rp-link) !important;
}
body#topic .post_container .post_footer {
    background: var(--rp-card) !important;
    border-top-color: transparent !important;
}
body#topic .post_container .post_header {
    background: var(--rp-card) !important;
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
    background: var(--rp-surface) !important;
    border-color: var(--rp-border) !important;
}
.attach_block,
.ipb-attach {
    background: var(--rp-surface) !important;
    border-color: var(--rp-border) !important;
}
.attach_block .title,
.attach_block .title a,
.ipb-attach .title {
    color: var(--rp-text) !important;
}
.attach_block .desc,
.ipb-attach .desc {
    color: var(--rp-text-secondary) !important;
}
.attach_block .icon {
    background: var(--rp-link) !important;
}
body#topic .post-block.spoil {
    border-top-color: transparent !important;
    border-bottom-color: transparent !important;
}
body#topic .post-block.spoil > .block-title {
    background: transparent !important;
    border-bottom: 1px solid var(--rp-border) !important;
}
body#topic .post-block.spoil.close > .block-title {
    border-bottom-color: transparent !important;
}
.post-block > .block-title,
.post-block.spoil > .block-title,
.poll > .body .questions .question > .title {
    color: var(--rp-text) !important;
}
.poll > .body .questions .question > .items .item.result > .title {
    background: var(--rp-card) !important;
    box-shadow: 0.75em 0 0.5em -0.25em var(--rp-card) !important;
}
.poll > .body .questions .question > .items .item.result .range_bar .range {
    background: var(--rp-button-active) !important;
}
.poll > .body .buttons > .btn,
#search a.search_post_btn {
    background: var(--rp-button) !important;
    border-color: var(--rp-border) !important;
    color: var(--rp-link) !important;
}
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button {
    background: var(--rp-button) !important;
    border-color: var(--rp-border) !important;
    color: var(--rp-link) !important;
    box-shadow: 0 1px 2px var(--rp-button-shadow) !important;
}
#news > .content div[id*="poll-ajax-frame"].news-poll-normalized .news-poll-browser-button:active {
    background: var(--rp-button-active) !important;
}
.poll > .body .buttons > .btn.vote {
    background: var(--rp-link) !important;
    color: var(--rp-card) !important;
}
${newsArticleCtaButtonOverrideCss("var(--rp-link)", "var(--rp-card)")}
#search .hat_content.close.over_height:after {
    box-shadow: inset 0 -6rem 3rem -3rem var(--rp-card) !important;
}
#news > .content img.app-stable-media,
#news > .content iframe.app-stable-media,
.news-detail-header .news-detail-header-image {
    background: var(--rp-surface) !important;
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
    --topic-collapsible-header-color: var(--rp-text);
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
        // Читающие палитры (Sepia / SepiaBlue / Minimal / новые Nord…Dracula) сами
        // рисуют свой AMOLED-вариант — чистый чёрный фон + СОБСТВЕННЫЕ текст/акцент/
        // поверхности, и притом ПОЛНОСТЬЮ: посты, шапки, новости, комменты, опросы,
        // пагинация, цитаты/спойлеры/вложения, QMS. Этот generic-слой шёл в compose()
        // ПОСЛЕ них, и его `:root { --surface-* }` перебивал переменные палитры —
        // из-за чего, например, текст комментов новости в Sepia+AMOLED становился
        // белым вместо тёплого палитрового (см. [[news-comment-action-chips]] заметку
        // про нестыковку). AMOLED — это «палитра на чистом чёрном», а не отдельная
        // палитра: когда выбрана читающая палитра, её AMOLED-вариант авторитетен
        // целиком, generic-слой не нужен и только протекает. Отдаём оформление ей.
        if (isSepiaReading() || isSepiaBlue() || isMinimalReader() || isNewReadingPalette()) return ""
        // Динамический акцент (Material You / курируемый seed) для HTML-контента.
        // AMOLED — это «палитра на чистом чёрном», ОРТОГОНАЛЬНАЯ выбору акцента
        // (см. [[amoled-mode-orthogonal-to-palette]]): поверхности остаются чёрными,
        // но ссылки/кнопки/иконки действий должны следовать ЖИВОМУ акценту, а не
        // статическому серому. getForumSurfaceOverrideCss() тут делает ранний return
        // (он ещё и красит поверхности, чего в AMOLED нельзя), поэтому акцент мостим
        // здесь. resolveDynamicAccentHex() уже корректно считает MY-primary для
        // Android 12–13 и 14+ и M3-primary курируемого seed; isAmoled() ⇒ night, так
        // что берётся тёмная (светлотоновая) роль — читаемая на #000. NEUTRAL без MY
        // → null: сохраняем прежний нейтральный серый (ссылки — дефолт базовой css).
        val accentHex = resolveDynamicAccentHex()
        val surfaceAccent = accentHex ?: "#9E9E9E"
        val accentLinkRules = if (accentHex != null) {
            "a { color: var(--surface-accent) !important; }\n" +
                    "button, .btn { color: var(--surface-accent) !important; }\n"
        } else ""
        // Filled-CTA новостей: на тёмной палитре акцент светлый (тон ~80), поэтому
        // текст на нём чёрный (аналог onPrimary), иначе — прежний синий/белый.
        val newsCtaCss = if (accentHex != null) newsArticleCtaButtonOverrideCss(accentHex, "#000000")
                else newsArticleCtaButtonOverrideCss("#2980b9", "#ffffff")
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
    --surface-accent: $surfaceAccent;
    --surface-control: rgba(255, 255, 255, 0.08);
    --surface-control-active: rgba(255, 255, 255, 0.14);
    --topic-action-icon-color: var(--surface-accent);
    --surface-radius-small: 0.375rem;
    --surface-radius-medium: 0.75rem;
    --surface-radius-large: 0.875rem;
}
html, body { background: #000000 !important; }
$accentLinkRules.post_container { background: #000000 !important; }
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
$newsCtaCss
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
