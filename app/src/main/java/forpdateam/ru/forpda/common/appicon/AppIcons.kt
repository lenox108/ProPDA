package forpdateam.ru.forpda.common.appicon

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import timber.log.Timber

/**
 * Один вариант иконки приложения.
 *
 * Каждому варианту в манифесте соответствует свой `activity-alias` на
 * [forpdateam.ru.forpda.ui.activities.MainActivity]: у самой MainActivity
 * фильтра MAIN/LAUNCHER нет, иначе включённый псевдоним давал бы второй ярлык.
 *
 * Значок ставится не только на ярлык: [iconRes] показывает пикер, а
 * [splashThemeRes] оформляет экран загрузки.
 */
data class AppIconVariant(
        /** Стабильный id — пишется в настройки. МЕНЯТЬ НЕЛЬЗЯ: сломает выбор у пользователей. */
        val id: String,
        /** Полное имя `activity-alias`. МЕНЯТЬ НЕЛЬЗЯ: на него ссылаются ярлыки на рабочем столе. */
        val alias: String,
        @StringRes val titleRes: Int,
        /** Короткое пояснение под названием в пикере; null — только название. */
        @StringRes val subtitleRes: Int? = null,
        /** Adaptive-иконка варианта; сама подхватывает день/ночь через `-night` ресурсы. */
        @DrawableRes val iconRes: Int,
        /** Monochrome-слой (белый силуэт на прозрачном) — он же значок статус-бара. */
        @DrawableRes val monochromeRes: Int,
        /** Splash-тема с этим значком — наследник `Theme.ForPDA.Splash`. */
        @StyleRes val splashThemeRes: Int,
)

/**
 * Реестр иконок. Новые варианты добавляет `design/app-icon/add_alt_icon.py`
 * — он же кладёт ресурсы, строки и псевдоним в манифест.
 * Порядок списка = порядок в пикере. Значение по умолчанию задаёт [DEFAULT_ID]
 * (оно же должно быть единственным enabled="true" псевдонимом в манифесте).
 */
object AppIcons {

    /** Стоковая иконка: рассыпающаяся пиксельная четвёрка. */
    const val DEFAULT_ID = "pixel_4"

    /** Префикс имён псевдонимов; он же — имя псевдонима иконки по умолчанию. */
    const val ALIAS_PREFIX = "forpdateam.ru.forpda.Launcher"

    val variants: List<AppIconVariant> = listOf(
            AppIconVariant(
                    // id менять нельзя: он уже сохранён в настройках у тех,
                    // кто выбрал эту иконку, пока она была стоковой.
                    id = "default",
                    alias = ALIAS_PREFIX,
                    titleRes = R.string.app_icon_default,
                    subtitleRes = R.string.app_icon_default_desc,
                    iconRes = R.mipmap.ic_launcher,
                    monochromeRes = R.drawable.ic_launcher_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash,
            ),
            AppIconVariant(
                    id = "four_dark",
                    alias = "forpdateam.ru.forpda.Launcher.FourDark",
                    titleRes = R.string.app_icon_four_dark,
                    subtitleRes = R.string.app_icon_four_dark_desc,
                    iconRes = R.mipmap.ic_launcher_four_dark,
                    monochromeRes = R.drawable.ic_launcher_four_dark_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_FourDark,
            ),
            AppIconVariant(
                    id = "four_blue",
                    alias = "forpdateam.ru.forpda.Launcher.FourBlue",
                    titleRes = R.string.app_icon_four_blue,
                    subtitleRes = R.string.app_icon_four_blue_desc,
                    iconRes = R.mipmap.ic_launcher_four_blue,
                    monochromeRes = R.drawable.ic_launcher_four_blue_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_FourBlue,
            ),
            AppIconVariant(
                    id = "puzzle",
                    alias = "forpdateam.ru.forpda.Launcher.Puzzle",
                    titleRes = R.string.app_icon_puzzle,
                    subtitleRes = R.string.app_icon_puzzle_desc,
                    iconRes = R.mipmap.ic_launcher_puzzle,
                    monochromeRes = R.drawable.ic_launcher_puzzle_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Puzzle,
            ),
            AppIconVariant(
                    id = "four_orange",
                    alias = "forpdateam.ru.forpda.Launcher.FourOrange",
                    titleRes = R.string.app_icon_four_orange,
                    subtitleRes = R.string.app_icon_four_orange_desc,
                    iconRes = R.mipmap.ic_launcher_four_orange,
                    monochromeRes = R.drawable.ic_launcher_four_orange_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_FourOrange,
            ),
            AppIconVariant(
                    id = "glass_4",
                    alias = "forpdateam.ru.forpda.Launcher.Glass4",
                    titleRes = R.string.app_icon_glass_4,
                    subtitleRes = R.string.app_icon_glass_4_desc,
                    iconRes = R.mipmap.ic_launcher_glass_4,
                    monochromeRes = R.drawable.ic_launcher_glass_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Glass4,
            ),
            AppIconVariant(
                    id = "metal_4",
                    alias = "forpdateam.ru.forpda.Launcher.Metal4",
                    titleRes = R.string.app_icon_metal_4,
                    subtitleRes = R.string.app_icon_metal_4_desc,
                    iconRes = R.mipmap.ic_launcher_metal_4,
                    monochromeRes = R.drawable.ic_launcher_metal_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Metal4,
            ),
            AppIconVariant(
                    id = "holo_4",
                    alias = "forpdateam.ru.forpda.Launcher.Holo4",
                    titleRes = R.string.app_icon_holo_4,
                    subtitleRes = R.string.app_icon_holo_4_desc,
                    iconRes = R.mipmap.ic_launcher_holo_4,
                    monochromeRes = R.drawable.ic_launcher_holo_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Holo4,
            ),
            AppIconVariant(
                    id = "matrix_4",
                    alias = "forpdateam.ru.forpda.Launcher.Matrix4",
                    titleRes = R.string.app_icon_matrix_4,
                    subtitleRes = R.string.app_icon_matrix_4_desc,
                    iconRes = R.mipmap.ic_launcher_matrix_4,
                    monochromeRes = R.drawable.ic_launcher_matrix_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Matrix4,
            ),
            AppIconVariant(
                    id = "droid_4",
                    alias = "forpdateam.ru.forpda.Launcher.Droid4",
                    titleRes = R.string.app_icon_droid_4,
                    subtitleRes = R.string.app_icon_droid_4_desc,
                    iconRes = R.mipmap.ic_launcher_droid_4,
                    monochromeRes = R.drawable.ic_launcher_droid_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Droid4,
            ),
            AppIconVariant(
                    id = "pixel_4",
                    alias = "forpdateam.ru.forpda.Launcher.Pixel4",
                    titleRes = R.string.app_icon_pixel_4,
                    subtitleRes = R.string.app_icon_pixel_4_desc,
                    iconRes = R.mipmap.ic_launcher_pixel_4,
                    monochromeRes = R.drawable.ic_launcher_pixel_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Pixel4,
            ),
            AppIconVariant(
                    id = "term_4",
                    alias = "forpdateam.ru.forpda.Launcher.Term4",
                    titleRes = R.string.app_icon_term_4,
                    subtitleRes = R.string.app_icon_term_4_desc,
                    iconRes = R.mipmap.ic_launcher_term_4,
                    monochromeRes = R.drawable.ic_launcher_term_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Term4,
            ),
            AppIconVariant(
                    id = "circuit_4",
                    alias = "forpdateam.ru.forpda.Launcher.Circuit4",
                    titleRes = R.string.app_icon_circuit_4,
                    subtitleRes = R.string.app_icon_circuit_4_desc,
                    iconRes = R.mipmap.ic_launcher_circuit_4,
                    monochromeRes = R.drawable.ic_launcher_circuit_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Circuit4,
            ),
            AppIconVariant(
                    id = "minimal_4",
                    alias = "forpdateam.ru.forpda.Launcher.Minimal4",
                    titleRes = R.string.app_icon_minimal_4,
                    subtitleRes = R.string.app_icon_minimal_4_desc,
                    iconRes = R.mipmap.ic_launcher_minimal_4,
                    monochromeRes = R.drawable.ic_launcher_minimal_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Minimal4,
            ),
            AppIconVariant(
                    id = "bold_4",
                    alias = "forpdateam.ru.forpda.Launcher.Bold4",
                    titleRes = R.string.app_icon_bold_4,
                    subtitleRes = R.string.app_icon_bold_4_desc,
                    iconRes = R.mipmap.ic_launcher_bold_4,
                    monochromeRes = R.drawable.ic_launcher_bold_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Bold4,
            ),
            AppIconVariant(
                    id = "blue_4",
                    alias = "forpdateam.ru.forpda.Launcher.Blue4",
                    titleRes = R.string.app_icon_blue_4,
                    subtitleRes = R.string.app_icon_blue_4_desc,
                    iconRes = R.mipmap.ic_launcher_blue_4,
                    monochromeRes = R.drawable.ic_launcher_blue_4_monochrome,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Blue4,
            ),
            // app-icon-variants:registry — не удалять, сюда дописывает add_alt_icon.py
    )

    val default: AppIconVariant get() =
            variants.firstOrNull { it.id == DEFAULT_ID } ?: variants.first()

    fun byId(id: String?): AppIconVariant = variants.firstOrNull { it.id == id } ?: default

    /** Значение [Preferences.Main.NOTIFICATION_ICON]: штатные глифы по типу события. */
    const val NOTIFICATION_ICON_EVENT = "event"
    /** Значение [Preferences.Main.NOTIFICATION_ICON]: следовать выбранной иконке приложения. */
    const val NOTIFICATION_ICON_APP = "app"

    /** Текущее значение настройки «Значок в статус-баре». */
    fun notificationIconValue(context: Context): String =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(Preferences.Main.NOTIFICATION_ICON, null) ?: NOTIFICATION_ICON_EVENT

    /**
     * Значок статус-бара для уведомления — по настройке «Значок в статус-баре»
     * (выбирается независимо от иконки приложения):
     *  * [NOTIFICATION_ICON_EVENT] — штатный глиф по типу события
     *    ([eventGlyphRes] — звезда/конверт/колокольчик);
     *  * [NOTIFICATION_ICON_APP] — силуэт иконки, выбранной для приложения;
     *  * id варианта — силуэт этого варианта.
     *
     * Варианты передаются именно как скомпилированные drawable-ресурсы.
     * Android 16 принимает bitmap в поле smallIcon уведомления, но не выводит
     * такой значок в статус-баре. Цвет ресурса система всё равно заменяет сама,
     * используя его альфа-канал как маску.
     */
    fun notificationSmallIcon(context: Context, @DrawableRes eventGlyphRes: Int): IconCompat {
        val value = notificationIconValue(context)
        val (icon, source) = when (value) {
            NOTIFICATION_ICON_EVENT ->
                IconCompat.createWithResource(context, eventGlyphRes) to
                    "event:${resourceName(context, eventGlyphRes)}"
            NOTIFICATION_ICON_APP -> AppIconManager.selected(context).let { variant ->
                silhouette(context, variant) to "app:${variant.id}"
            }
            else -> variants.firstOrNull { it.id == value }?.let { variant ->
                silhouette(context, variant) to "variant:${variant.id}"
            }
                // Вариант удалили из сборки — не гадаем, возвращаем штатный глиф.
                ?: (IconCompat.createWithResource(context, eventGlyphRes) to
                    "fallback:${resourceName(context, eventGlyphRes)}")
        }
        if (BuildConfig.DEBUG) {
            Timber.tag(NOTIFICATION_ICON_LOG_TAG).d(
                "resolved value=%s source=%s type=%d eventRes=0x%08x",
                value,
                source,
                icon.type,
                eventGlyphRes,
            )
        }
        return icon
    }

    private fun resourceName(context: Context, @DrawableRes res: Int): String =
        runCatching { context.resources.getResourceEntryName(res) }
            .getOrDefault("0x${res.toString(16)}")

    private fun silhouette(context: Context, variant: AppIconVariant): IconCompat =
        IconCompat.createWithResource(context, variant.monochromeRes)
}

/** Ставит выбранный small icon статус-бара. */
fun NotificationCompat.Builder.applySelectedNotificationIcon(
        context: Context,
        @DrawableRes eventGlyphRes: Int,
): NotificationCompat.Builder = apply {
    val icon = AppIcons.notificationSmallIcon(context, eventGlyphRes)
    setSmallIcon(icon)
    if (BuildConfig.DEBUG) {
        Timber.tag(NOTIFICATION_ICON_LOG_TAG).d(
            "applied type=%d eventRes=0x%08x",
            icon.type,
            eventGlyphRes,
        )
    }
}

private const val NOTIFICATION_ICON_LOG_TAG = "NotificationIcon"
