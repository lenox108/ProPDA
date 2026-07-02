package forpdateam.ru.forpda.ui.compose.theme

import android.content.Context
import androidx.annotation.AttrRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.model.datastore.MainDataStore

/**
 * Единая Compose-тема приложения — мост View→Compose.
 *
 * Раньше все `setContent { … }`-острова (Notes/News/QMS/Favorites) рендерились
 * БЕЗ обёртки [MaterialTheme], то есть получали дефолтную фиолетовую baseline-
 * палитру Material 3, оторванную от темы приложения (Light/Dark/AMOLED/Sepia и
 * Material You её не касались).
 *
 * [ForpdaTheme] строит [ColorScheme] из УЖЕ-разрешённой темы хост-[Context].
 * Ключевой момент: `ComposeView` держит `Context` активити, на которой в
 * `onCreate()` уже выполнены `setTheme(UiThemeStyles.*)` + (на API 31+ с
 * включённым Material You) оверлей динамических цветов из
 * [forpdateam.ru.forpda.ui.MaterialYouApplier]. Поэтому достаточно прочитать
 * итоговые M3-роли из `context.theme` — Compose автоматически унаследует ВСЁ:
 * светлую/тёмную, AMOLED, Sepia/Minimal-палитры и динамику обоев. Отдельная
 * ветка `dynamicLightColorScheme(...)` не нужна (и была бы вредна — расходилась
 * бы с нативным хромом).
 *
 * Роли, которые не заданы в нашей теме явно, наследуются от `Theme.Material3.*`
 * (родитель), поэтому [MaterialColors.getColor] всегда возвращает осмысленное
 * значение; baseline-схема ([forpdaLightFallback]/[forpdaDarkFallback]) служит
 * лишь страховкой и построена на НЕЙТРАЛЬНЫХ брендовых тонах (бренд монохромный),
 * а не на дефолтном фиолетовом.
 *
 * Типографика зеркалит выбор шрифта из настроек ([forpdateam.ru.forpda.ui.AppFontMode]
 * через [forpdateam.ru.forpda.ui.FontController]) тем же путём, что и цвета — читая
 * персистнутое значение напрямую (см. [MainDataStore.getAppFontModeImmediate],
 * тот же паттерн что [forpdateam.ru.forpda.ui.MaterialYouApplier] использует для
 * Material You), а не наследуя `?attr/fontFamily` из хост-темы: Compose `Typography`
 * не умеет инфлейтить XML `<font-family>` через тему, поэтому шрифт строится явно
 * в [forpdaFontFamily] из тех же файлов, что и нативный `ThemeOverlay.ForPDA.*Font`.
 */
@Composable
fun ForpdaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isDark = DayNightHelper.isUiModeNight(configuration)
    // Конфигурация (uiMode) и identity context — достаточный ключ: смена палитры
    // / Material You / темы / шрифта пересоздаёт активити (а с ней ComposeView),
    // смена night-режима меняет configuration.
    val colorScheme = remember(context, isDark, configuration.uiMode) {
        forpdaColorSchemeFromContext(context, isDark)
    }
    val typography = remember(context) {
        val fontMode = runCatching { MainDataStore(context).getAppFontModeImmediate() }
                .getOrDefault(forpdateam.ru.forpda.ui.FontController.DEFAULT_FONT_MODE)
        forpdaTypography(forpdaFontFamily(fontMode))
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = ForpdaShapes,
        content = content,
    )
}

/**
 * Резолвит M3-роли из темы [context] и накладывает их поверх нейтральной
 * baseline-схемы. Каждая роль читается через [MaterialColors.getColor] с
 * фолбэком на соответствующее значение baseline-схемы.
 */
internal fun forpdaColorSchemeFromContext(context: Context, isDark: Boolean): ColorScheme {
    val base = if (isDark) forpdaDarkFallback else forpdaLightFallback
    fun role(@AttrRes attr: Int, fallback: Color): Color =
        Color(MaterialColors.getColor(context, attr, fallback.toArgb()))

    return base.copy(
        primary = role(androidx.appcompat.R.attr.colorPrimary, base.primary),
        onPrimary = role(MaterialR.attr.colorOnPrimary, base.onPrimary),
        primaryContainer = role(MaterialR.attr.colorPrimaryContainer, base.primaryContainer),
        onPrimaryContainer = role(MaterialR.attr.colorOnPrimaryContainer, base.onPrimaryContainer),
        inversePrimary = role(MaterialR.attr.colorPrimaryInverse, base.inversePrimary),
        secondary = role(MaterialR.attr.colorSecondary, base.secondary),
        onSecondary = role(MaterialR.attr.colorOnSecondary, base.onSecondary),
        secondaryContainer = role(MaterialR.attr.colorSecondaryContainer, base.secondaryContainer),
        onSecondaryContainer = role(MaterialR.attr.colorOnSecondaryContainer, base.onSecondaryContainer),
        tertiary = role(MaterialR.attr.colorTertiary, base.tertiary),
        onTertiary = role(MaterialR.attr.colorOnTertiary, base.onTertiary),
        tertiaryContainer = role(MaterialR.attr.colorTertiaryContainer, base.tertiaryContainer),
        onTertiaryContainer = role(MaterialR.attr.colorOnTertiaryContainer, base.onTertiaryContainer),
        // ВАЖНО: читаем background/onBackground из КОНКРЕТНЫХ M3-ролей colorSurface/
        // colorOnSurface, а НЕ из android:colorBackground / colorOnBackground. В теме
        // `android:colorBackground = ?attr/colorSurface` (ссылка), и её резолв через
        // MaterialColors.getColor подхватывает НЕ переопределение палитры, а базовый
        // M3-colorSurface (нейтральный #FEF7FF) → фон Compose-экранов (QMS Contacts/
        // Favorites/News, все берут colorScheme.background) на читающих палитрах (Sepia
        // и др.) вылезал «холодным белым» мимо тёплых карточек. colorSurface же задан
        // конкретным @color/<palette>_… и резолвится верно. background==surface —
        // ровно то, что тема и декларирует (`colorBackground = ?attr/colorSurface`).
        background = role(MaterialR.attr.colorSurface, base.background),
        onBackground = role(MaterialR.attr.colorOnSurface, base.onBackground),
        surface = role(MaterialR.attr.colorSurface, base.surface),
        onSurface = role(MaterialR.attr.colorOnSurface, base.onSurface),
        surfaceVariant = role(MaterialR.attr.colorSurfaceVariant, base.surfaceVariant),
        onSurfaceVariant = role(MaterialR.attr.colorOnSurfaceVariant, base.onSurfaceVariant),
        surfaceTint = role(androidx.appcompat.R.attr.colorPrimary, base.surfaceTint),
        inverseSurface = role(MaterialR.attr.colorSurfaceInverse, base.inverseSurface),
        inverseOnSurface = role(MaterialR.attr.colorOnSurfaceInverse, base.inverseOnSurface),
        surfaceBright = role(MaterialR.attr.colorSurfaceBright, base.surfaceBright),
        surfaceDim = role(MaterialR.attr.colorSurfaceDim, base.surfaceDim),
        surfaceContainer = role(MaterialR.attr.colorSurfaceContainer, base.surfaceContainer),
        surfaceContainerLow = role(MaterialR.attr.colorSurfaceContainerLow, base.surfaceContainerLow),
        surfaceContainerLowest = role(MaterialR.attr.colorSurfaceContainerLowest, base.surfaceContainerLowest),
        surfaceContainerHigh = role(MaterialR.attr.colorSurfaceContainerHigh, base.surfaceContainerHigh),
        surfaceContainerHighest = role(MaterialR.attr.colorSurfaceContainerHighest, base.surfaceContainerHighest),
        outline = role(MaterialR.attr.colorOutline, base.outline),
        outlineVariant = role(MaterialR.attr.colorOutlineVariant, base.outlineVariant),
        error = role(androidx.appcompat.R.attr.colorError, base.error),
        onError = role(MaterialR.attr.colorOnError, base.onError),
        errorContainer = role(MaterialR.attr.colorErrorContainer, base.errorContainer),
        onErrorContainer = role(MaterialR.attr.colorOnErrorContainer, base.onErrorContainer),
    )
}

/**
 * Нейтральный (монохромный) baseline для светлой темы — страховка, если роль не
 * резолвится. Намеренно НЕ дефолтный фиолетовый M3: бренд приложения серый
 * (см. `light_colorAccent=#757575`).
 */
private val forpdaLightFallback: ColorScheme = lightColorScheme(
    primary = Color(0xFF616161),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E5EA),
    onPrimaryContainer = Color(0xFF1A1A1A),
    secondary = Color(0xFF616161),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFD9D9DE),
)

/** Нейтральный baseline для тёмной темы (`dark_background_base=#121212`). */
private val forpdaDarkFallback: ColorScheme = darkColorScheme(
    primary = Color(0xFFBDBDBD),
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF242424),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF1A1A1A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF242424),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFF98989F),
    outline = Color(0xFF3A3A3C),
    outlineVariant = Color(0xFF2C2C2E),
)
