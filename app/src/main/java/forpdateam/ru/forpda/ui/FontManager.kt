package forpdateam.ru.forpda.ui

import android.app.Activity
import android.content.Context
import androidx.annotation.StyleRes
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import timber.log.Timber

enum class AppFontMode {
    SYSTEM,
    ROBOTO,
    INTER,
    SOURCE_SANS_3,
    OPEN_SANS,
    ROBOTO_MONO
}

object FontController {
    val DEFAULT_FONT_MODE = AppFontMode.SYSTEM
    private const val ROBOTO_WEB_FONT_STACK = "\"ForPdaRoboto\", Roboto, sans-serif"
    private const val INTER_WEB_FONT_STACK = "\"ForPdaInter\", system-ui, sans-serif"
    private const val SOURCE_SANS_3_WEB_FONT_STACK = "\"ForPdaSourceSans3\", system-ui, sans-serif"
    private const val OPEN_SANS_WEB_FONT_STACK = "\"ForPdaOpenSans\", system-ui, sans-serif"
    private const val SYSTEM_WEB_FONT_STACK = "system-ui, sans-serif"
    private const val ROBOTO_MONO_WEB_FONT_STACK = "\"Roboto Mono\", monospace"
    const val ROBOTO_WEB_FONT_CLASS = "font_roboto"
    const val INTER_WEB_FONT_CLASS = "font_inter"
    const val SOURCE_SANS_3_WEB_FONT_CLASS = "font_source_sans_3"
    const val OPEN_SANS_WEB_FONT_CLASS = "font_open_sans"
    const val SYSTEM_WEB_FONT_CLASS = "font_system"
    const val ROBOTO_MONO_WEB_FONT_CLASS = "font_roboto_mono"

    fun getCurrentFontMode(mainPreferencesHolder: MainPreferencesHolder): AppFontMode =
        mainPreferencesHolder.getAppFontMode()

    fun isSystemFontEnabled(mainPreferencesHolder: MainPreferencesHolder): Boolean =
        getCurrentFontMode(mainPreferencesHolder) == AppFontMode.SYSTEM

    fun mode(useSystemFont: Boolean): AppFontMode =
        if (useSystemFont) AppFontMode.SYSTEM else DEFAULT_FONT_MODE

    fun parseMode(value: String?): AppFontMode = try {
        if (value.isNullOrBlank()) {
            DEFAULT_FONT_MODE
        } else {
            AppFontMode.valueOf(value)
        }
    } catch (_: IllegalArgumentException) {
        DEFAULT_FONT_MODE
    }

    fun webFontFamily(mode: AppFontMode): String = when (mode) {
        AppFontMode.SYSTEM -> SYSTEM_WEB_FONT_STACK
        AppFontMode.ROBOTO -> ROBOTO_WEB_FONT_STACK
        AppFontMode.INTER -> INTER_WEB_FONT_STACK
        AppFontMode.SOURCE_SANS_3 -> SOURCE_SANS_3_WEB_FONT_STACK
        AppFontMode.OPEN_SANS -> OPEN_SANS_WEB_FONT_STACK
        AppFontMode.ROBOTO_MONO -> ROBOTO_MONO_WEB_FONT_STACK
    }

    fun webFontClass(mode: AppFontMode): String = when (mode) {
        AppFontMode.SYSTEM -> SYSTEM_WEB_FONT_CLASS
        AppFontMode.ROBOTO -> ROBOTO_WEB_FONT_CLASS
        AppFontMode.INTER -> INTER_WEB_FONT_CLASS
        AppFontMode.SOURCE_SANS_3 -> SOURCE_SANS_3_WEB_FONT_CLASS
        AppFontMode.OPEN_SANS -> OPEN_SANS_WEB_FONT_CLASS
        AppFontMode.ROBOTO_MONO -> ROBOTO_MONO_WEB_FONT_CLASS
    }

    @StyleRes
    fun nativeThemeOverlay(mode: AppFontMode): Int = when (mode) {
        AppFontMode.SYSTEM -> R.style.ThemeOverlay_ForPDA_SystemFont
        AppFontMode.ROBOTO -> R.style.ThemeOverlay_ForPDA_AppFont
        AppFontMode.INTER -> R.style.ThemeOverlay_ForPDA_InterFont
        AppFontMode.SOURCE_SANS_3 -> R.style.ThemeOverlay_ForPDA_SourceSans3Font
        AppFontMode.OPEN_SANS -> R.style.ThemeOverlay_ForPDA_OpenSansFont
        AppFontMode.ROBOTO_MONO -> R.style.ThemeOverlay_ForPDA_RobotoMonoFont
    }

    fun nativeFontFamilyApplied(mode: AppFontMode): String = when (mode) {
        AppFontMode.SYSTEM -> "platform default"
        AppFontMode.ROBOTO -> "forpda_roboto"
        AppFontMode.INTER -> "forpda_inter"
        AppFontMode.SOURCE_SANS_3 -> "forpda_source_sans_3"
        AppFontMode.OPEN_SANS -> "forpda_open_sans"
        AppFontMode.ROBOTO_MONO -> "monospace"
    }

    fun applyNativeTheme(activity: Activity, mode: AppFontMode) {
        activity.theme.applyStyle(nativeThemeOverlay(mode), true)
        if (BuildConfig.DEBUG) {
            Timber.d(
                "selectedFontMode=%s nativeFontFamilyApplied=%s",
                mode,
                nativeFontFamilyApplied(mode)
            )
        }
    }

    fun applyNativeTheme(context: Context, mode: AppFontMode) {
        context.theme.applyStyle(nativeThemeOverlay(mode), true)
        if (BuildConfig.DEBUG) {
            Timber.d(
                "selectedFontMode=%s nativeFontFamilyApplied=%s",
                mode,
                nativeFontFamilyApplied(mode)
            )
        }
    }

    fun getWebFontFamilyCss(mode: AppFontMode): String =
        "--app-font-family: ${webFontFamily(mode)};"

    fun buildCssVariables(mode: AppFontMode): String {
        return """
:root {
    ${getWebFontFamilyCss(mode)}
    --legacy-app-font-family: ${webFontFamily(mode)};
}
html {
    --app-font-mode-class: "${webFontClass(mode)}";
}
""".trim()
    }

    /** Only the active mode's faces are embedded — avoids loading every bundled TTF on each WebView open. */
    fun webFontFaceDeclarations(mode: AppFontMode): String = when (mode) {
        AppFontMode.SYSTEM -> ""
        AppFontMode.ROBOTO_MONO -> "" // «Roboto Mono»/monospace web-safe, @font-face не нужен
        AppFontMode.ROBOTO -> """
@import url("file:///android_asset/fonts/roboto/import.min.css");
""".trim()
        AppFontMode.INTER -> """
@font-face {
    font-family: "ForPdaInter";
    src: url("file:///android_asset/fonts/inter/inter_regular.ttf");
    font-style: normal;
    font-weight: 100 900;
}
@font-face {
    font-family: "ForPdaInter";
    src: url("file:///android_asset/fonts/inter/inter_italic.ttf");
    font-style: italic;
    font-weight: 100 900;
}
""".trim()
        AppFontMode.SOURCE_SANS_3 -> """
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_regular.ttf");
    font-style: normal;
    font-weight: 450;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_regular.ttf");
    font-style: normal;
    font-weight: 500;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_regular.ttf");
    font-style: normal;
    font-weight: 600;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_regular.ttf");
    font-style: normal;
    font-weight: 700;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_italic.ttf");
    font-style: italic;
    font-weight: 450;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_italic.ttf");
    font-style: italic;
    font-weight: 500;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_italic.ttf");
    font-style: italic;
    font-weight: 600;
}
@font-face {
    font-family: "ForPdaSourceSans3";
    src: url("file:///android_asset/fonts/source_sans_3/source_sans_3_italic.ttf");
    font-style: italic;
    font-weight: 700;
}
""".trim()
        AppFontMode.OPEN_SANS -> """
@font-face {
    font-family: "ForPdaOpenSans";
    src: url("file:///android_asset/fonts/open_sans/open_sans_regular.ttf");
    font-style: normal;
    font-weight: 300 800;
}
@font-face {
    font-family: "ForPdaOpenSans";
    src: url("file:///android_asset/fonts/open_sans/open_sans_italic.ttf");
    font-style: italic;
    font-weight: 300 800;
}
""".trim()
    }

    fun webFontCss(useSystemFont: Boolean): String {
        val mode = mode(useSystemFont)
        if (BuildConfig.DEBUG) {
            Timber.d(
                "checkedUseSystemFont=%s selectedFontMode=%s",
                useSystemFont,
                mode
            )
        }
        return webFontCss(mode)
    }

    fun webFontCss(mode: AppFontMode): String {
        val fontFamily = webFontFamily(mode)
        val fontClass = webFontClass(mode)
        if (BuildConfig.DEBUG) {
            Timber.d(
                "selectedFontMode=%s webCssFontFamily=%s",
                mode,
                fontFamily
            )
        }
        val fontFaces = webFontFaceDeclarations(mode)
        return """
<style>
${if (fontFaces.isBlank()) "" else "$fontFaces\n"}${buildCssVariables(mode)}
html,
body,
button,
input,
select,
textarea,
.post_body,
.post_body *,
.post_header,
.post_header *,
.post_footer,
.post_footer *,
.post_container,
.post_container *,
.poll,
.poll *,
.search_post,
.search_post *,
.search_result_block,
.search_result_block *,
.bad-search-result,
.bad-search-result *,
.news-detail-header,
.news-detail-header *,
.news_item,
.news_item *,
.news_block,
.news_block *,
.news_container,
.news_container *,
.news_post,
.news_post *,
#news > .content,
#news > .content *,
.news-detail-header,
.news-detail-header *,
.news-comments-entry,
.news-comments-entry *,
.content,
.content *,
.comments,
.comments *,
.materials,
.materials *,
.mess_list,
.mess_list * {
    font-family: var(--app-font-family) !important;
}
pre,
code,
.post-block.code,
.post-block.code * {
    font-family: "Roboto Mono", monospace !important;
}
[class^="icon-"]:before,
[class*=" icon-"]:before,
.icon.as-text,
.text-rub {
    font-family: "fontello" !important;
}
[class^="flaticon-"]:before,
[class*=" flaticon-"]:before,
[class^="flaticon-"]:after,
[class*=" flaticon-"]:after {
    font-family: "flaticon" !important;
}
</style>
<script>
document.documentElement.classList.remove("$ROBOTO_WEB_FONT_CLASS", "$INTER_WEB_FONT_CLASS", "$SOURCE_SANS_3_WEB_FONT_CLASS", "$OPEN_SANS_WEB_FONT_CLASS", "$SYSTEM_WEB_FONT_CLASS", "$ROBOTO_MONO_WEB_FONT_CLASS");
document.documentElement.classList.add("$fontClass");
</script>
""".trim()
    }
}

object FontManager {
    const val APP_DEFAULT_WEB_FONT_CLASS = FontController.SYSTEM_WEB_FONT_CLASS
    const val APP_WEB_FONT_CLASS = FontController.SYSTEM_WEB_FONT_CLASS
    const val ROBOTO_WEB_FONT_CLASS = FontController.ROBOTO_WEB_FONT_CLASS
    const val INTER_WEB_FONT_CLASS = FontController.INTER_WEB_FONT_CLASS
    const val SOURCE_SANS_3_WEB_FONT_CLASS = FontController.SOURCE_SANS_3_WEB_FONT_CLASS
    const val OPEN_SANS_WEB_FONT_CLASS = FontController.OPEN_SANS_WEB_FONT_CLASS
    const val SYSTEM_WEB_FONT_CLASS = FontController.SYSTEM_WEB_FONT_CLASS

    fun mode(useSystemFont: Boolean): AppFontMode = FontController.mode(useSystemFont)

    fun parseMode(value: String?): AppFontMode = FontController.parseMode(value)

    fun webFontFamily(mode: AppFontMode): String = FontController.webFontFamily(mode)

    fun webFontClass(mode: AppFontMode): String = FontController.webFontClass(mode)

    fun webFontCss(useSystemFont: Boolean): String = FontController.webFontCss(useSystemFont)

    fun webFontCss(mode: AppFontMode): String = FontController.webFontCss(mode)
}
