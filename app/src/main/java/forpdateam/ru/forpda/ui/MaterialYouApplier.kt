package forpdateam.ru.forpda.ui

import android.app.Activity
import android.os.Build
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.presentation.theme.AccentPolicy
import forpdateam.ru.forpda.presentation.theme.MaterialYouPolicy
import timber.log.Timber

/**
 * Применение Material You (Dynamic Color) к нативному UI на уровне КАЖДОЙ
 * активити.
 *
 * Почему НЕ через [DynamicColors.applyToActivitiesIfAvailable] на уровне
 * Application: активити приложения ([forpdateam.ru.forpda.ui.activities.MainActivity],
 * [forpdateam.ru.forpda.ui.activities.SettingsActivity] и др.) в своём
 * `onCreate()` вызывают `setTheme(UiThemeStyles.*)`. Вызов `Activity.setTheme()`
 * пересоздаёт тему «с нуля» и стирает любой оверлей, наложенный раньше (в т.ч.
 * динамические цвета, которые библиотека Material накладывает в
 * `onActivityPreCreated` ДО `onCreate`). Поэтому глобальная регистрация не
 * выживала, и тумблер Material You визуально ничего не менял.
 *
 * Решение: накладывать динамические цвета вручную ПОСЛЕ `setTheme(...)` и до
 * `setContentView(...)` — тогда оверлей ложится поверх уже выбранной темы и
 * доживает до инфляции вью.
 *
 * Поверх динамических цветов дополнительно накладывается оверлей, который
 * перенаправляет захардкоженные атрибуты темы на динамические роли Material 3
 * (иначе DynamicColors красит только стандартные M3-роли, а весь «хром»
 * приложения через собственные атрибуты остаётся прежним):
 * - [R.style.ThemeOverlay_ForPDA_MaterialYouAccent] — только акцент
 *   (`colorAccent`, `link_color`, `colorControlActivated` → `colorPrimary`).
 *   База для остальных оверлеев.
 * - [R.style.ThemeOverlay_ForPDA_MaterialYouAmoled] — акцент (наследует Accent)
 *   + ПИН всех M3-surface-ролей обратно на amoled-чёрный. Используется для
 *   AMOLED: DynamicColors тонирует surface-роли тёмно-серым (не чёрным), а наши
 *   фоны контента их читают — без пина фон вылезал серым вместо чёрного.
 * - [R.style.ThemeOverlay_ForPDA_MaterialYouSurface] — акцент (наследует
 *   Accent) + динамический базовый фон окна (`android:colorBackground` /
 *   `colorOnBackground` → `colorSurface` / `colorOnSurface`). Используется для
 *   SYSTEM в светлой/тёмной теме.
 * - [R.style.ThemeOverlay_ForPDA_MaterialYouDarkFloor] — акцент (наследует
 *   Accent) + перепин СВЕТЛОТЫ surface-рампы на уровни статичной тёмной темы
 *   (`lStar`-селекторы поверх системной палитры обоев — оттенок обоев
 *   сохраняется, см. комментарий к стилю в styles.xml). Накладывается ПОВЕРХ
 *   Surface только для НЕ-AMOLED тёмной темы, когда система отдала слишком
 *   тёмный `colorSurfaceContainerLowest` (см. [dynamicDarkSurfaceIsNearBlack];
 *   на практике — почти всегда): иначе «Тёмная» под Material You
 *   проваливалась в AMOLED-чёрный.
 *
 * ВАЖНО про реальный охват (обновлено после Этапа C `concurrent-dreaming-wren`
 * consumer-side миграции — см. план): динамику обоев получают акцент, базовый
 * `colorBackground`/`colorOnBackground`, `colorError` (через HarmonizedError
 * ниже), и ТЕПЕРЬ ТАКЖЕ — везде, где XML/код были перенаправлены на прямые
 * M3-роли вместо custom-attr — page background (`colorSurfaceContainerLowest`,
 * было `background_base`), карточки/списки (`colorSurface`/`colorSurfaceVariant`,
 * было `cards_background`/`background_for_cards`), разделители
 * (`colorOutlineVariant`, было `divider_line`), и большая часть текста
 * (`colorOnSurface`/`colorOnSurfaceVariant`/`colorSecondary` — было
 * `default_text_color`/`second_text_color`/`link_color`, оба атрибута
 * полностью удалены; `icon_toolbar` мигрирован частично — leaf-потребители
 * на `colorOnSurface`, сам атрибут жив для ~30 внутренних ссылок каскада
 * тулбара, см. план).
 * Остаются статичными: текст/иконки/фон ВНУТРИ самого оверлея
 * `MaterialYouSurface` (пины `android:textColor*`/`link_color` — обязательны,
 * см. его KDoc) и атрибуты, у которых ещё нет M3-зеркала (`contrast_text_color`,
 * `icon_base`, `chrome_plane_background`, scrim-цвета и пр. — см. план,
 * раздел «Снятые с рассмотрения»). Корень принципиального ограничения тот же:
 * наши ОСТАВШИЕСЯ custom-attr читаются через `TypedArray.getColorStateList`,
 * который не дерефит `?attr/...` (TYPE_ATTRIBUTE) и падает с
 * UnsupportedOperationException — поэтому миграция идёт со стороны
 * потребителя (XML/код → M3-роль напрямую), а не редиректом самого атрибута.
 * Подробности и история в KDoc `ThemeOverlay.ForPDA.MaterialYouSurface`
 * (`styles.xml`) и в `FragmentBaseTextViewInflateTest`.
 *
 * Выбор оверлея делает [MaterialYouPolicy.resolveMode]. Палитры чтения
 * (Sepia/Minimal) динамику не получают вовсе.
 *
 * Поверх обоих оверлеев дополнительно накладывается
 * [R.style.ThemeOverlay_ForPDA_HarmonizedError], который сдвигает тон
 * M3-ролей `colorError*` (и `colorOnError*` / `colorErrorContainer*`) в
 * сторону wallpaper-derived `colorPrimary`, чтобы destructive actions
 * (Delete / Report / и т.п.) гармонизировались с обоями.
 *
 * Это замена для `HarmonizedColors.applyToContextIfAvailable(...)` из
 * Material 1.10+. Раньше (§4.5) использовался канонический вызов
 * `HarmonizedColors.applyToContextIfAvailable(activity, HarmonizedColorsOptions.createMaterialDefaults())`
 * внутри `OnAppliedCallback`. В проде это приводило к InflateException
 * (`fragment_base.xml:118` в `TextView.readTextAppearance` →
 * `TypedArray.getColorStateList` → `UnsupportedOperationException`):
 * `ThemeOverlay.Material3.HarmonizedColors` наследуется от
 * `ThemeOverlay.Material3`, в нашей теме цепочка резолва
 * `?attr/textColorPrimary` (которую читает `TextView.readTextAppearance` для
 * атрибута `textAppearance`) доходит до `Theme.AppCompat.Empty`, где нет
 * конкретного значения — `TypedArray.getColorStateList` падает на
 * `TYPE_ATTRIBUTE`. Это тот же класс крэша, что в `7f1de68` для
 * `Toolbar.<init>` / `CollapsingToolbarLayout.<init>` / `CardView.<init>`.
 *
 * Свой `ThemeOverlay.ForPDA.HarmonizedError` наследуется от
 * `ThemeOverlay.ForPDA.MaterialYouAccent` (а не от `ThemeOverlay.Material3`),
 * поэтому резолв идёт через проверенную M3-цепочку и НЕ падает в
 * `Theme.AppCompat.Empty`. Это повторяет паттерн из `7f1de68`, где
 * `MaterialYouAccent` уже верифицированно безопасен. Robolectric guard
 * (`if (!isRobolectric())` вокруг проблемного вызова) при этом больше не
 * нужен — наш overlay корректно резолвится и в Robolectric, и на реальных
 * устройствах, что закрывает регрессию «тесты зелёные, прод падает».
 *
 * Применяется ТОЛЬКО при `mode != NONE` (см. early-return выше) — иначе
 * гармонизация перекрыла бы hand-picked `colorError` палитр чтения
 * (Sepia/MinimalReader: `styles_minimal_reader.xml`).
 *
 * §4.1 / §4.5 of REFACTOR_PLAN.md.
 */
object MaterialYouApplier {

    /**
     * Должен вызываться из `Activity.onCreate()` сразу после `setTheme(...)`.
     */
    fun applyIfEnabled(activity: Activity) {
        // API < 31: dynamic color not supported — bail out so we never try to apply it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (BuildConfig.DEBUG) Timber.tag(LOG_TAG).d("skip: sdk<31 (sdk=%d)", Build.VERSION.SDK_INT)
            return
        }
        val dataStore = MainDataStore(activity)
        val enabled = runCatching { dataStore.getUseMaterialYouImmediate() }.getOrDefault(false)
        val palette = runCatching { dataStore.getUiPaletteImmediate() }
                .getOrDefault(Preferences.Main.UiPalette.SYSTEM)
        val accent = runCatching { dataStore.getAccentPaletteImmediate() }
                .getOrDefault(Preferences.Main.AccentPalette.NEUTRAL)
        val themeMode = runCatching { dataStore.getThemeModeImmediate() }
                .getOrDefault(Preferences.Main.ThemeMode.SYSTEM)
        val isNight = DayNightHelper.isUiModeNight(activity.resources.configuration)

        // Единое решение (обои vs произвольный seed vs курируемая палитра vs
        // ничего). DynamicColors обрабатывает только WALLPAPER и CUSTOM_SEED —
        // CURATED/NONE достаются AccentApplier.
        val decision = AccentPolicy.resolveMode(enabled, palette, accent, Build.VERSION.SDK_INT)
        val contentSeed: Int? = when (decision) {
            AccentPolicy.Mode.WALLPAPER -> null
            AccentPolicy.Mode.CUSTOM_SEED ->
                runCatching { dataStore.getAccentCustomColorImmediate() }.getOrNull()
            else -> {
                if (BuildConfig.DEBUG) Timber.tag(LOG_TAG).d("decision=%s -> DynamicColors skip", decision)
                return
            }
        }

        // SURFACE (акцент + динамический фон) для light/dark; ACCENT_ONLY для
        // AMOLED (не поднимаем поверхности с чистого чёрного). Работает и для
        // обоев, и для произвольного seed.
        val isAmoled = MaterialYouPolicy.isAmoledSkin(themeMode, isNight)
        val overlay = if (isAmoled) {
            R.style.ThemeOverlay_ForPDA_MaterialYouAmoled
        } else {
            R.style.ThemeOverlay_ForPDA_MaterialYouSurface
        }
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d(
                    "decision=%s seed=%s night=%s sdk=%d overlay=surface?%s",
                    decision, contentSeed?.let { "#%08X".format(it) } ?: "wallpaper",
                    isNight, Build.VERSION.SDK_INT,
                    (overlay == R.style.ThemeOverlay_ForPDA_MaterialYouSurface)
            )
        }
        val options = DynamicColorsOptions.Builder()
                .apply { if (contentSeed != null) setContentBasedSource(contentSeed) }
                .setOnAppliedCallback {
                    activity.theme.applyStyle(overlay, true)
                    // M3 Color Harmonization: сдвигает тон M3 error-ролей
                    // (colorError / colorOnError / colorErrorContainer /
                    // colorOnErrorContainer) в сторону wallpaper-derived
                    // colorPrimary, чтобы destructive actions
                    // гармонизировались с обоями. Раньше тут стоял вызов
                    // HarmonizedColors.applyToContextIfAvailable(... +
                    // HarmonizedColorsOptions.createMaterialDefaults())
                    // — он работал в Robolectric, но в проде приводил к
                    // InflateException в TextView.readTextAppearance из-за
                    // ?attr/... chain через Theme.AppCompat.Empty. Теперь
                    // используем собственный ThemeOverlay.ForPDA.HarmonizedError,
                    // который наследуется от MaterialYouAccent (НЕ от
                    // ThemeOverlay.Material3.HarmonizedColors) и безопасен
                    // на реальных устройствах. См. KDoc класса.
                    activity.theme.applyStyle(R.style.ThemeOverlay_ForPDA_HarmonizedError, true)
                    // DARK-floor: тема «Тёмная» обещает тёмно-серый фон, НЕ
                    // полный AMOLED-чёрный, а тёмная динамика M3 живёт ниже
                    // (Lowest — тон L*=4, на Android 16 наблюдался и чистый
                    // #000000). Если резолвнутый Lowest темнее эталона
                    // dark_background_base — поднимаем светлоту surface-рампы
                    // до уровней статичной тёмной темы, СОХРАНЯЯ оттенок обоев
                    // (lStar-селекторы в оверлее; акцент тоже остаётся из
                    // обоев).
                    if (!isAmoled && isNight && dynamicDarkSurfaceIsNearBlack(activity)) {
                        activity.theme.applyStyle(
                                R.style.ThemeOverlay_ForPDA_MaterialYouDarkFloor, true)
                    }
                    if (BuildConfig.DEBUG) logResolvedColors(activity)
                }
                .build()
        DynamicColors.applyToActivityIfAvailable(activity, options)
    }

    /**
     * true, если ПОСЛЕ применения DynamicColors системная динамическая роль
     * `colorSurfaceContainerLowest` (тон фона страницы / плоской шапки / нижнего
     * таббара) резолвится темнее нашего эталона [R.color.dark_background_base]
     * (#121212, L* ~5.5).
     *
     * НА ПРАКТИКЕ это выполняется в тёмной динамике почти всегда: канонический
     * M3-тон этой роли — L*=4 (Material 1.13: values-v31 →
     * `m3_ref_palette_dynamic_neutral_variant4` с `lStar="4"`, values-v34 →
     * `system_surface_container_lowest_dark` того же тона), а lStar пинит
     * светлоту НЕЗАВИСИМО от оттенка обоев. Т.е. гейт отсеивает не «часть
     * устройств», а лишь вендорские палитры, отдающие Lowest светлее #121212
     * (тогда floor не нужен). Крайний случай — Android 16 с чистым #000000.
     * Потеря оттенка обоев при срабатывании floor'а — БЫВШИЙ побочный эффект:
     * с 18.07.2026 оверлей перепинивает только светлоту, оттенок сохраняется
     * (см. комментарий к ThemeOverlay.ForPDA.MaterialYouDarkFloor).
     *
     * Сравнение по фактическому цвету, а не по [Build.VERSION.SDK_INT], потому
     * что точное поведение зависит и от версии, и от обоев/движка палитры
     * вендора — реактивная проверка надёжнее хардкода порога SDK.
     */
    private fun dynamicDarkSurfaceIsNearBlack(activity: Activity): Boolean {
        val tv = TypedValue()
        val resolved = activity.theme.resolveAttribute(
                com.google.android.material.R.attr.colorSurfaceContainerLowest, tv, true)
        if (!resolved) return false
        // НЕ tv.data: на API 31-33 динамическая роль — селектор-файл с lStar
        // (m3_ref_palette_dynamic_neutral_variant4), tv.data для него не цвет.
        val lowest = activity.getColorFromAttr(
                com.google.android.material.R.attr.colorSurfaceContainerLowest)
        val reference = activity.resources.getColor(R.color.dark_background_base, activity.theme)
        return ColorUtils.calculateLuminance(lowest) <= ColorUtils.calculateLuminance(reference)
    }

    private fun logResolvedColors(activity: Activity) {
        val tv = TypedValue()
        val primary = if (activity.theme.resolveAttribute(
                        androidx.appcompat.R.attr.colorPrimary, tv, true)) tv.data else 0
        val accent = if (activity.theme.resolveAttribute(
                        androidx.appcompat.R.attr.colorAccent, tv, true)) tv.data else 0
        Timber.tag(LOG_TAG).d(
                "applied to %s: colorPrimary=#%08X colorAccent=#%08X",
                activity.javaClass.simpleName, primary, accent
        )
    }

    /**
     * Детект Robolectric-окружения. Сохранён для совместимости с тестами
     * (см. [forpdateam.ru.forpda.ui.MaterialYouApplierTest.isRobolectric]),
     * которые пинят поведение гармонизации. На текущий момент сами гармонизация
     * идёт через собственный [R.style.ThemeOverlay_ForPDA_HarmonizedError],
     * который НЕ зависит от Robolectric, но helper оставлен, чтобы тесты
     * продолжали иметь смысл (см. историю про upstream-баг Robolectric #9552
     * в KDoc класса).
     */
    internal fun isRobolectric(): Boolean =
            Build.FINGERPRINT.startsWith("robolectric")

    private const val LOG_TAG = "MaterialYou"
}
