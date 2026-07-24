package forpdateam.ru.forpda.common.appicon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences

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
            // app-icon-variants:registry — не удалять, сюда дописывает add_alt_icon.py
    )

    val default: AppIconVariant get() =
            variants.firstOrNull { it.id == DEFAULT_ID } ?: variants.first()

    fun byId(id: String?): AppIconVariant = variants.firstOrNull { it.id == id } ?: default

    /**
     * Значок выбранной иконки для показа внутри приложения (уведомления и пр.).
     * Ярлык лаунчера сюда не относится — им управляет [AppIconManager].
     */
    @DrawableRes
    fun currentIconRes(context: Context): Int = AppIconManager.selected(context).iconRes

    /** Значение [Preferences.Main.NOTIFICATION_ICON]: штатные глифы по типу события. */
    const val NOTIFICATION_ICON_EVENT = "event"
    /** Значение [Preferences.Main.NOTIFICATION_ICON]: следовать выбранной иконке приложения. */
    const val NOTIFICATION_ICON_APP = "app"

    /** Текущее значение настройки «Иконка уведомлений». */
    fun notificationIconValue(context: Context): String =
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(Preferences.Main.NOTIFICATION_ICON, null) ?: NOTIFICATION_ICON_EVENT

    /**
     * Значок статус-бара для уведомления — по настройке «Иконка уведомлений»
     * (выбирается независимо от иконки приложения):
     *  * [NOTIFICATION_ICON_EVENT] — штатный глиф по типу события
     *    ([eventGlyphRes] — звезда/конверт/колокольчик);
     *  * [NOTIFICATION_ICON_APP] — силуэт иконки, выбранной для приложения;
     *  * id варианта — силуэт этого варианта.
     *
     * Monochrome-слой годится статус-бару по природе (белый рисунок на прозрачном,
     * система берёт только альфу и красит сама), но у него adaptive-поля: знак
     * занимает ~68% холста и в статус-баре выходил бы на треть меньше соседних
     * глифов. Поэтому альфу обрезаем по видимым границам и отдаём bitmap.
     */
    fun notificationSmallIcon(context: Context, @DrawableRes eventGlyphRes: Int): IconCompat =
            when (val value = notificationIconValue(context)) {
                NOTIFICATION_ICON_EVENT -> IconCompat.createWithResource(context, eventGlyphRes)
                NOTIFICATION_ICON_APP -> silhouette(context, AppIconManager.selected(context))
                else -> variants.firstOrNull { it.id == value }?.let { silhouette(context, it) }
                        // Вариант удалили из сборки — не гадаем, возвращаем штатный глиф.
                        ?: IconCompat.createWithResource(context, eventGlyphRes)
            }

    private fun silhouette(context: Context, variant: AppIconVariant): IconCompat =
            runCatching {
                val bitmap = synchronized(notificationGlyphCache) {
                    notificationGlyphCache.get(variant.monochromeRes)
                            ?: croppedGlyph(context, variant.monochromeRes).also {
                                notificationGlyphCache.put(variant.monochromeRes, it)
                            }
                }
                IconCompat.createWithBitmap(bitmap)
            }
                    // Не удалось отрисовать — некритично: покажем силуэт с полями.
                    .getOrElse { IconCompat.createWithResource(context, variant.monochromeRes) }

    /** Не рендерим и не сканируем один и тот же силуэт при каждом новом уведомлении. */
    private val notificationGlyphCache = LruCache<Int, Bitmap>(8)

    /** Рендер силуэта и обрезка альфы по видимым границам (в квадрат, с полем 8%). */
    private fun croppedGlyph(context: Context, @DrawableRes res: Int): Bitmap {
        val size = 192
        val full = createBitmap(size, size)
        val drawable = requireNotNull(AppCompatResources.getDrawable(context, res))
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(full))

        val pixels = IntArray(size * size)
        full.getPixels(pixels, 0, size, 0, 0, size, size)
        var left = size; var top = size; var right = -1; var bottom = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (pixels[y * size + x] ushr 24 > 16) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left) return full // пустой силуэт — отдаём как есть

        // Квадрат вокруг знака + небольшое поле, не выходя за холст.
        val side = maxOf(right - left + 1, bottom - top + 1)
        val pad = (side * 0.08f).toInt()
        val box = (side + 2 * pad).coerceAtMost(size)
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val x = (cx - box / 2).coerceIn(0, size - box)
        val y = (cy - box / 2).coerceIn(0, size - box)
        return Bitmap.createBitmap(full, x, y, box, box)
    }
}
