package forpdateam.ru.forpda.ui.compose.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.AppFontMode

/**
 * Compose-эквиваленты XML font-family ресурсов из `res/font/forpda_*.xml`,
 * применяемых нативно через [forpdateam.ru.forpda.ui.FontController.nativeThemeOverlay]
 * (`ThemeOverlay.ForPDA.AppFont` / `InterFont` / `SourceSans3Font` / `OpenSansFont`).
 *
 * Compose не умеет инфлейтить XML `<font-family>` напрямую — каждая запись
 * здесь зеркалит один `<font>` элемент соответствующего XML-ресурса 1:1
 * (тот же файл шрифта, тот же weight/style), чтобы выбор шрифта в настройках
 * выглядел одинаково в нативном UI и в Compose-островах.
 */
private val RobotoFontFamily = FontFamily(
        Font(R.font.forpda_roboto_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.forpda_roboto_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.forpda_roboto_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.forpda_roboto_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

private val InterFontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
)

private val OpenSansFontFamily = FontFamily(
        Font(R.font.open_sans_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.open_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Variable font — `forpda_source_sans_3.xml` pins discrete `wght` instances via fontVariationSettings. */
@OptIn(ExperimentalTextApi::class)
private val SourceSans3FontFamily = FontFamily(
        Font(R.font.source_sans_3_regular, FontWeight.W400, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
        Font(R.font.source_sans_3_regular, FontWeight.W500, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.source_sans_3_regular, FontWeight.W600, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.source_sans_3_regular, FontWeight.W700, FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.source_sans_3_italic, FontWeight.W400, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
        Font(R.font.source_sans_3_italic, FontWeight.W500, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.source_sans_3_italic, FontWeight.W600, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.source_sans_3_italic, FontWeight.W700, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

internal fun forpdaFontFamily(mode: AppFontMode): FontFamily = when (mode) {
    AppFontMode.SYSTEM -> FontFamily.SansSerif
    AppFontMode.ROBOTO -> RobotoFontFamily
    AppFontMode.INTER -> InterFontFamily
    AppFontMode.SOURCE_SANS_3 -> SourceSans3FontFamily
    AppFontMode.OPEN_SANS -> OpenSansFontFamily
}

/** Applies [family] to every role of the M3 baseline [Typography] — mirrors `android:fontFamily` theme-wide override. */
internal fun forpdaTypography(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}
