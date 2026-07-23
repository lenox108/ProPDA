package forpdateam.ru.forpda.common.appicon

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import forpdateam.ru.forpda.R

/**
 * Один вариант иконки приложения.
 *
 * Каждому варианту в манифесте соответствует свой `activity-alias` на
 * [forpdateam.ru.forpda.ui.activities.MainActivity]: у самой MainActivity
 * фильтра MAIN/LAUNCHER нет, иначе включённый псевдоним давал бы второй ярлык.
 *
 * Значок ставится не только на ярлык: [previewRes] показывают пикер, экран
 * загрузки ([splashThemeRes]) и уведомления загрузок.
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
        /** Splash-тема с этим значком — наследник `Theme.ForPDA.Splash`. */
        @StyleRes val splashThemeRes: Int,
)

/**
 * Реестр иконок. Новые варианты добавляет `design/app-icon/add_alt_icon.py`
 * — он же кладёт ресурсы, строки и псевдоним в манифест.
 * Порядок списка = порядок в пикере, первый элемент = значение по умолчанию.
 */
object AppIcons {

    const val DEFAULT_ID = "default"

    /** Префикс имён псевдонимов; он же — имя псевдонима иконки по умолчанию. */
    const val ALIAS_PREFIX = "forpdateam.ru.forpda.Launcher"

    val variants: List<AppIconVariant> = listOf(
            AppIconVariant(
                    id = DEFAULT_ID,
                    alias = ALIAS_PREFIX,
                    titleRes = R.string.app_icon_default,
                    subtitleRes = R.string.app_icon_default_desc,
                    iconRes = R.mipmap.ic_launcher,
                    splashThemeRes = R.style.Theme_ForPDA_Splash,
            ),
            AppIconVariant(
                    id = "four_dark",
                    alias = "forpdateam.ru.forpda.Launcher.FourDark",
                    titleRes = R.string.app_icon_four_dark,
                    subtitleRes = R.string.app_icon_four_dark_desc,
                    iconRes = R.mipmap.ic_launcher_four_dark,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_FourDark,
            ),
            AppIconVariant(
                    id = "four_blue",
                    alias = "forpdateam.ru.forpda.Launcher.FourBlue",
                    titleRes = R.string.app_icon_four_blue,
                    subtitleRes = R.string.app_icon_four_blue_desc,
                    iconRes = R.mipmap.ic_launcher_four_blue,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_FourBlue,
            ),
            AppIconVariant(
                    id = "puzzle",
                    alias = "forpdateam.ru.forpda.Launcher.Puzzle",
                    titleRes = R.string.app_icon_puzzle,
                    subtitleRes = R.string.app_icon_puzzle_desc,
                    iconRes = R.mipmap.ic_launcher_puzzle,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Puzzle,
            ),
            AppIconVariant(
                    id = "four_orange",
                    alias = "forpdateam.ru.forpda.Launcher.FourOrange",
                    titleRes = R.string.app_icon_four_orange,
                    subtitleRes = R.string.app_icon_four_orange_desc,
                    iconRes = R.mipmap.ic_launcher_four_orange,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_FourOrange,
            ),
            AppIconVariant(
                    id = "glass_4",
                    alias = "forpdateam.ru.forpda.Launcher.Glass4",
                    titleRes = R.string.app_icon_glass_4,
                    subtitleRes = R.string.app_icon_glass_4_desc,
                    iconRes = R.mipmap.ic_launcher_glass_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Glass4,
            ),
            AppIconVariant(
                    id = "metal_4",
                    alias = "forpdateam.ru.forpda.Launcher.Metal4",
                    titleRes = R.string.app_icon_metal_4,
                    subtitleRes = R.string.app_icon_metal_4_desc,
                    iconRes = R.mipmap.ic_launcher_metal_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Metal4,
            ),
            AppIconVariant(
                    id = "holo_4",
                    alias = "forpdateam.ru.forpda.Launcher.Holo4",
                    titleRes = R.string.app_icon_holo_4,
                    subtitleRes = R.string.app_icon_holo_4_desc,
                    iconRes = R.mipmap.ic_launcher_holo_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Holo4,
            ),
            AppIconVariant(
                    id = "matrix_4",
                    alias = "forpdateam.ru.forpda.Launcher.Matrix4",
                    titleRes = R.string.app_icon_matrix_4,
                    subtitleRes = R.string.app_icon_matrix_4_desc,
                    iconRes = R.mipmap.ic_launcher_matrix_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Matrix4,
            ),
            AppIconVariant(
                    id = "droid_4",
                    alias = "forpdateam.ru.forpda.Launcher.Droid4",
                    titleRes = R.string.app_icon_droid_4,
                    subtitleRes = R.string.app_icon_droid_4_desc,
                    iconRes = R.mipmap.ic_launcher_droid_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Droid4,
            ),
            AppIconVariant(
                    id = "pixel_4",
                    alias = "forpdateam.ru.forpda.Launcher.Pixel4",
                    titleRes = R.string.app_icon_pixel_4,
                    subtitleRes = R.string.app_icon_pixel_4_desc,
                    iconRes = R.mipmap.ic_launcher_pixel_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Pixel4,
            ),
            AppIconVariant(
                    id = "term_4",
                    alias = "forpdateam.ru.forpda.Launcher.Term4",
                    titleRes = R.string.app_icon_term_4,
                    subtitleRes = R.string.app_icon_term_4_desc,
                    iconRes = R.mipmap.ic_launcher_term_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Term4,
            ),
            AppIconVariant(
                    id = "circuit_4",
                    alias = "forpdateam.ru.forpda.Launcher.Circuit4",
                    titleRes = R.string.app_icon_circuit_4,
                    subtitleRes = R.string.app_icon_circuit_4_desc,
                    iconRes = R.mipmap.ic_launcher_circuit_4,
                    splashThemeRes = R.style.Theme_ForPDA_Splash_Circuit4,
            ),
            // app-icon-variants:registry — не удалять, сюда дописывает add_alt_icon.py
    )

    val default: AppIconVariant get() = variants.first()

    fun byId(id: String?): AppIconVariant = variants.firstOrNull { it.id == id } ?: default

    /**
     * Значок выбранной иконки для показа внутри приложения (уведомления и пр.).
     * Ярлык лаунчера сюда не относится — им управляет [AppIconManager].
     */
    @DrawableRes
    fun currentIconRes(context: Context): Int = AppIconManager.selected(context).iconRes
}
