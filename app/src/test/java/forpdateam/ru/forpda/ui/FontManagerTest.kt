package forpdateam.ru.forpda.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontManagerTest {

    @Test
    fun `roboto mode keeps explicit web font stack`() {
        assertEquals("\"ForPdaRoboto\", Roboto, sans-serif", FontManager.webFontFamily(AppFontMode.ROBOTO))
        assertEquals("font_roboto", FontManager.webFontClass(AppFontMode.ROBOTO))

        val css = FontManager.webFontCss(AppFontMode.ROBOTO)

        assertTrue(css.contains("--app-font-family: \"ForPdaRoboto\", Roboto, sans-serif;"))
        assertTrue(css.contains("--legacy-app-font-family: \"ForPdaRoboto\", Roboto, sans-serif;"))
        assertTrue(css.contains("--app-font-mode-class: \"font_roboto\";"))
        assertTrue(css.contains("document.documentElement.classList.add(\"font_roboto\");"))
        assertTrue(css.contains("font-family: var(--app-font-family) !important;"))
        assertTrue(css.contains(".post_container *"))
        assertTrue(css.contains("@import url(\"file:///android_asset/fonts/roboto/import.min.css\");"))
        assertFalse(css.contains("local(\"Roboto"))
        assertFalse(css.contains("Robot-Italic"))
    }

    @Test
    fun `system mode maps to original app default stack`() {
        assertEquals(AppFontMode.SYSTEM, FontManager.mode(useSystemFont = true))
        assertEquals(AppFontMode.SYSTEM, FontManager.mode(useSystemFont = false))
        assertEquals("system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.SYSTEM))
        assertEquals("font_system", FontManager.webFontClass(AppFontMode.SYSTEM))

        val css = FontManager.webFontCss(useSystemFont = true)

        assertTrue(css.contains("--app-font-family: system-ui, sans-serif;"))
        assertTrue(css.contains("--legacy-app-font-family: system-ui, sans-serif;"))
        assertTrue(css.contains("--app-font-mode-class: \"font_system\";"))
        assertTrue(css.contains("document.documentElement.classList.add(\"font_system\");"))
        assertTrue(css.contains(".post_container *"))
        assertTrue(css.contains("font-family: \"fontello\" !important;"))
        assertTrue(css.contains("font-family: \"Roboto Mono\", monospace !important;"))
        assertFalse(css.contains("--app-font-family: \"ForPdaRoboto\", Roboto, sans-serif;"))
        assertFalse(FontManager.webFontFamily(AppFontMode.SYSTEM).contains("Roboto"))
        assertFalse(css.contains("Robot-Italic"))
        assertFalse(css.contains("Samsung"))
        assertFalse(css.contains("MIUI"))
    }

    @Test
    fun `legacy boolean maps to system default and css class`() {
        val appMode = FontManager.mode(useSystemFont = false)
        val systemMode = FontManager.mode(useSystemFont = true)

        assertEquals(AppFontMode.SYSTEM, appMode)
        assertEquals("font_system", FontManager.webFontClass(appMode))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_SystemFont, FontController.nativeThemeOverlay(appMode))
        assertEquals("system-ui, sans-serif", FontManager.webFontFamily(appMode))

        assertEquals(AppFontMode.SYSTEM, systemMode)
        assertEquals("font_system", FontManager.webFontClass(systemMode))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_SystemFont, FontController.nativeThemeOverlay(systemMode))
        assertEquals("system-ui, sans-serif", FontManager.webFontFamily(systemMode))
    }

    @Test
    fun `bundled font modes map to dedicated native and web families`() {
        assertEquals(AppFontMode.INTER, FontManager.parseMode("INTER"))
        assertEquals("font_inter", FontManager.webFontClass(AppFontMode.INTER))
        assertEquals("\"ForPdaInter\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.INTER))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_InterFont, FontController.nativeThemeOverlay(AppFontMode.INTER))
        assertEquals("forpda_inter", FontController.nativeFontFamilyApplied(AppFontMode.INTER))

        assertEquals(AppFontMode.SOURCE_SANS_3, FontManager.parseMode("SOURCE_SANS_3"))
        assertEquals("font_source_sans_3", FontManager.webFontClass(AppFontMode.SOURCE_SANS_3))
        assertEquals("\"ForPdaSourceSans3\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.SOURCE_SANS_3))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_SourceSans3Font, FontController.nativeThemeOverlay(AppFontMode.SOURCE_SANS_3))
        assertEquals("forpda_source_sans_3", FontController.nativeFontFamilyApplied(AppFontMode.SOURCE_SANS_3))

        assertEquals(AppFontMode.OPEN_SANS, FontManager.parseMode("OPEN_SANS"))
        assertEquals("font_open_sans", FontManager.webFontClass(AppFontMode.OPEN_SANS))
        assertEquals("\"ForPdaOpenSans\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.OPEN_SANS))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_OpenSansFont, FontController.nativeThemeOverlay(AppFontMode.OPEN_SANS))
        assertEquals("forpda_open_sans", FontController.nativeFontFamilyApplied(AppFontMode.OPEN_SANS))

        assertEquals(AppFontMode.IBM_PLEX_SANS, FontManager.parseMode("IBM_PLEX_SANS"))
        assertEquals("font_ibm_plex_sans", FontManager.webFontClass(AppFontMode.IBM_PLEX_SANS))
        assertEquals("\"ForPdaIBMPlexSans\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.IBM_PLEX_SANS))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_IBMPlexSansFont, FontController.nativeThemeOverlay(AppFontMode.IBM_PLEX_SANS))
        assertEquals("forpda_ibm_plex_sans", FontController.nativeFontFamilyApplied(AppFontMode.IBM_PLEX_SANS))

        assertEquals(AppFontMode.GOLOS_TEXT, FontManager.parseMode("GOLOS_TEXT"))
        assertEquals("font_golos_text", FontManager.webFontClass(AppFontMode.GOLOS_TEXT))
        assertEquals("\"ForPdaGolosText\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.GOLOS_TEXT))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_GolosTextFont, FontController.nativeThemeOverlay(AppFontMode.GOLOS_TEXT))
        assertEquals("forpda_golos_text", FontController.nativeFontFamilyApplied(AppFontMode.GOLOS_TEXT))

        assertEquals(AppFontMode.LITERATA, FontManager.parseMode("LITERATA"))
        assertEquals("font_literata", FontManager.webFontClass(AppFontMode.LITERATA))
        assertEquals("\"ForPdaLiterata\", serif", FontManager.webFontFamily(AppFontMode.LITERATA))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_LiterataFont, FontController.nativeThemeOverlay(AppFontMode.LITERATA))
        assertEquals("forpda_literata", FontController.nativeFontFamilyApplied(AppFontMode.LITERATA))

        assertEquals(AppFontMode.APPETITE_PRO, FontManager.parseMode("APPETITE_PRO"))
        assertEquals("font_appetite_pro", FontManager.webFontClass(AppFontMode.APPETITE_PRO))
        assertEquals("\"ForPdaAppetitePro\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.APPETITE_PRO))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_AppetiteProFont, FontController.nativeThemeOverlay(AppFontMode.APPETITE_PRO))
        assertEquals("forpda_appetite_pro", FontController.nativeFontFamilyApplied(AppFontMode.APPETITE_PRO))

        assertEquals(AppFontMode.MAYONEZ_ITALIC, FontManager.parseMode("MAYONEZ_ITALIC"))
        assertEquals("font_mayonez_italic", FontManager.webFontClass(AppFontMode.MAYONEZ_ITALIC))
        assertEquals("\"ForPdaMayonezItalic\", system-ui, sans-serif", FontManager.webFontFamily(AppFontMode.MAYONEZ_ITALIC))
        assertEquals(forpdateam.ru.forpda.R.style.ThemeOverlay_ForPDA_MayonezItalicFont, FontController.nativeThemeOverlay(AppFontMode.MAYONEZ_ITALIC))
        assertEquals("forpda_mayonez_italic", FontController.nativeFontFamilyApplied(AppFontMode.MAYONEZ_ITALIC))
    }

    @Test
    fun `web css declares bundled font faces and preserves special fonts`() {
        val interCss = FontManager.webFontCss(AppFontMode.INTER)

        assertTrue(interCss.contains("font-family: \"ForPdaInter\";"))
        assertTrue(interCss.contains("fonts/inter/inter_regular.ttf"))
        assertFalse(interCss.contains("ForPdaSourceSans3"))
        assertFalse(interCss.contains("ForPdaOpenSans"))
        assertFalse(interCss.contains("@import url(\"file:///android_asset/fonts/roboto/import.min.css\");"))
        assertTrue(interCss.contains("font-family: \"Roboto Mono\", monospace !important;"))
        assertTrue(interCss.contains("font-family: \"fontello\" !important;"))
        assertTrue(interCss.contains("font-family: \"flaticon\" !important;"))

        val systemCss = FontManager.webFontCss(AppFontMode.SYSTEM)
        assertFalse(systemCss.contains("@font-face"))
        assertFalse(systemCss.contains("@import url(\"file:///android_asset/fonts/roboto/import.min.css\");"))

        val sourceCss = FontManager.webFontCss(AppFontMode.SOURCE_SANS_3)
        assertTrue(sourceCss.contains("font-weight: 450;"))
        assertFalse(sourceCss.contains("font-weight: 200 900;"))
        assertTrue(sourceCss.contains("font-weight: 600;"))

        val plexCss = FontManager.webFontCss(AppFontMode.IBM_PLEX_SANS)
        assertTrue(plexCss.contains("font-family: \"ForPdaIBMPlexSans\";"))
        assertTrue(plexCss.contains("fonts/ibm_plex_sans/ibm_plex_sans_regular.ttf"))
        assertTrue(plexCss.contains("fonts/ibm_plex_sans/ibm_plex_sans_bold_italic.ttf"))
        assertFalse(plexCss.contains("ForPdaGolosText"))

        val golosCss = FontManager.webFontCss(AppFontMode.GOLOS_TEXT)
        assertTrue(golosCss.contains("font-family: \"ForPdaGolosText\";"))
        assertTrue(golosCss.contains("fonts/golos_text/golos_text_variable.ttf"))
        assertTrue(golosCss.contains("font-weight: 400 900;"))
        assertFalse(golosCss.contains("ForPdaLiterata"))

        val literataCss = FontManager.webFontCss(AppFontMode.LITERATA)
        assertTrue(literataCss.contains("font-family: \"ForPdaLiterata\";"))
        assertTrue(literataCss.contains("fonts/literata/literata_variable.ttf"))
        assertTrue(literataCss.contains("fonts/literata/literata_italic_variable.ttf"))
        assertTrue(literataCss.contains("font-weight: 200 900;"))
        assertFalse(literataCss.contains("ForPdaIBMPlexSans"))

        val appetiteCss = FontManager.webFontCss(AppFontMode.APPETITE_PRO)
        assertTrue(appetiteCss.contains("font-family: \"ForPdaAppetitePro\";"))
        assertTrue(appetiteCss.contains("fonts/appetite_pro/appetite_pro_regular.ttf"))
        assertTrue(appetiteCss.contains("fonts/appetite_pro/appetite_pro_bold_italic.ttf"))
        assertTrue(appetiteCss.contains("font-weight: 400;"))
        assertTrue(appetiteCss.contains("font-weight: 700;"))
        assertFalse(appetiteCss.contains("ForPdaInter"))

        val mayonezCss = FontManager.webFontCss(AppFontMode.MAYONEZ_ITALIC)
        assertTrue(mayonezCss.contains("font-family: \"ForPdaMayonezItalic\";"))
        assertTrue(mayonezCss.contains("fonts/mayonez_italic/mayonez_italic_regular.ttf"))
        assertTrue(mayonezCss.contains("fonts/mayonez_italic/mayonez_italic_bold_italic.ttf"))
        assertTrue(mayonezCss.contains("font-weight: 400;"))
        assertTrue(mayonezCss.contains("font-weight: 700;"))
        assertFalse(mayonezCss.contains("ForPdaAppetitePro"))
    }

    @Test
    fun `invalid or blank stored value falls back to original system default`() {
        assertEquals(AppFontMode.SYSTEM, FontManager.parseMode(null))
        assertEquals(AppFontMode.SYSTEM, FontManager.parseMode(""))
        assertEquals(AppFontMode.SYSTEM, FontManager.parseMode("APP_FONT"))
    }
}
