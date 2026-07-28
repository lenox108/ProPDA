package forpdateam.ru.forpda.common

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import forpdateam.ru.forpda.common.AppToast as Toast
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.R
import timber.log.Timber
import kotlin.math.max

/**
 * Единый helper для показа Snackbar вместо Toast.
 * Snackbar привязан к текущему View и не перекрывает контент.
 */
fun Fragment.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbar(message, duration)
}

fun Fragment.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbar(messageRes, duration)
}

fun View.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    showSnackbarSafely(message, duration)
}

fun View.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    showSnackbarSafely(context.getString(messageRes), duration)
}

fun Fragment.showSnackbarAboveSystemBars(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbarAboveSystemBars(message, duration)
}

fun Fragment.showSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.showSnackbarAboveSystemBars(messageRes, duration)
}

fun View.showSnackbarAboveSystemBars(
        message: CharSequence,
        duration: Int = Snackbar.LENGTH_SHORT,
        configure: (Snackbar.() -> Unit)? = null,
) {
    showSnackbarSafely(message, duration, configure)
}

fun View.showSnackbarAboveSystemBars(
        @StringRes messageRes: Int,
        duration: Int = Snackbar.LENGTH_SHORT,
        configure: (Snackbar.() -> Unit)? = null,
) {
    showSnackbarSafely(context.getString(messageRes), duration, configure)
}

/**
 * Показ Snackbar, который НЕ роняет приложение и по возможности выглядит ОДИНАКОВО
 * на всех устройствах.
 *
 * На некоторых прошивках/палитрах (зафиксировано на Samsung Galaxy S25 / Android 16) в теме
 * контекста не резолвятся Material-роли, и инфляция `Snackbar$SnackbarLayout` падает
 * с InflateException. Раньше это сразу деградировало в [Toast] — плашку другой формы и без
 * кнопки-действия, из-за чего одно и то же сообщение на разных телефонах выглядело
 * по-разному («на одном полоска снизу, на другом пилюля с иконкой»).
 *
 * Порядок попыток:
 *  1. тема view как есть — сохраняет динамику Material You / выбранной палитры;
 *  2. тема приложения, наложенная ПРИНУДИТЕЛЬНО (force=true) — цвета чуть менее динамичные,
 *     но все Material-роли гарантированно на месте, а виджет и вёрстка те же самые;
 *  3. и только если даже это не собралось — [Toast] как последний рубеж (кнопки не будет).
 */
private fun View.showSnackbarSafely(
        message: CharSequence,
        duration: Int,
        configure: (Snackbar.() -> Unit)? = null,
) {
    try {
        makeSnackbarAboveSystemBars(message, duration).apply { configure?.invoke(this) }.show()
        return
    } catch (e: Throwable) {
        Timber.w(e, "Snackbar failed on the view theme; retrying with the app theme forced")
    }
    try {
        makeSnackbarAboveSystemBars(message, duration, forceAppTheme = true)
                .apply { configure?.invoke(this) }
                .show()
        return
    } catch (e: Throwable) {
        Timber.w(e, "Snackbar failed with the app theme forced; falling back to Toast")
    }
    runCatching { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
}

fun View.makeSnackbarAboveSystemBars(
        message: CharSequence,
        duration: Int = Snackbar.LENGTH_SHORT,
        forceAppTheme: Boolean = false,
): Snackbar {
    val themed = snackbarThemedContext(forceAppTheme)
    return Snackbar.make(themed, this, message, duration)
            .applyThemedSurfaceColors(themed)
            .applyNavigationBarInset(this)
}

/**
 * Контекст, в котором инфлейтится снэк: тема текущего view + недостающие Material-роли.
 *
 * Некоторые комбинации рантайм-оверлеев (Material You / движок акцент-палитр, см.
 * MaterialYouApplier / AccentApplier) оставляют тему без разрешимого `colorOnSurface`,
 * и тогда инфляция `Snackbar$SnackbarLayout` падает с InflateException
 * (MaterialColors.getColor → «requires a value for the colorOnSurface attribute»).
 * `applyStyle(DayNightAppTheme, force = false)` дозаполняет ТОЛЬКО недостающее,
 * не затирая уже наложенную динамику; `force = true` — аварийный режим (см.
 * [showSnackbarSafely]), когда важнее собрать виджет, чем сохранить динамику.
 *
 * Тема копируется вручную (`newTheme().setTo(...)`) и передаётся в
 * `ContextThemeWrapper(base, Resources.Theme)`. Соблазнительный вариант
 * `ContextThemeWrapper(context, 0)` использовать НЕЛЬЗЯ: при нулевом themeResId
 * appcompat подставляет свой дефолт и накладывает его force = true —
 * `getTheme()` → `mThemeResource = R.style.Theme_AppCompat_Light`,
 * `onApplyThemeResource()` → `theme.applyStyle(resid, true)`
 * (androidx.appcompat.view.ContextThemeWrapper). В результате поверх темы приложения
 * форсились светлые AppCompat-значения, в том числе `colorPrimary`
 * (= `primary_material_light`, почти белый серый). Именно из него
 * [applyThemedSurfaceColors] брала цвет кнопки действия — «Скачать» в проверке
 * обновлений оказывалась блёкло-серой на светлой плашке, а акцент Material You
 * до снэка вообще не доезжал.
 */
private fun View.snackbarThemedContext(forceAppTheme: Boolean): ContextThemeWrapper {
    val theme = context.resources.newTheme().apply {
        context.theme?.let { setTo(it) }
        applyStyle(R.style.DayNightAppTheme, forceAppTheme)
    }
    return ContextThemeWrapper(context, theme)
}

/**
 * По умолчанию Material красит Snackbar в `colorSurfaceInverse` / `colorOnSurfaceInverse`
 * (контрастная «инверсная» плашка). В тёмной теме это даёт СВЕТЛЫЙ фон с тёмным текстом —
 * плашка выглядит ярко-белой и «режет глаза». Перекрашиваем её в собственную поверхность
 * темы (`colorSurfaceContainerHigh` — приподнятый серый), чтобы snackbar сливался с текущей
 * палитрой: тёмно-серый в тёмной теме, светлый — в светлой. Текст/действие берём с
 * соответствующих ролей той же поверхности.
 */
private fun Snackbar.applyThemedSurfaceColors(themed: ContextThemeWrapper): Snackbar {
    val background = MaterialColors.getColor(
            themed,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            MaterialColors.getColor(themed, com.google.android.material.R.attr.colorSurface, 0))
    val onSurface = MaterialColors.getColor(
            themed, com.google.android.material.R.attr.colorOnSurface, 0)
    val accent = MaterialColors.getColor(
            themed, androidx.appcompat.R.attr.colorPrimary, onSurface)

    if (background != 0) setBackgroundTint(background)
    if (onSurface != 0) setTextColor(onSurface)
    if (accent != 0) setActionTextColor(accent)
    return this
}

fun View.makeSnackbarAboveSystemBars(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar {
    return makeSnackbarAboveSystemBars(context.getString(messageRes), duration)
}

private fun Snackbar.applyNavigationBarInset(anchor: View): Snackbar {
    val bottomBar = anchor.rootView
            ?.findViewById<View>(R.id.bottomMenuRecycler)
            ?.takeIf { it.isShown }

    // Пока клавиатура скрыта, самый надёжный способ не спрятать снэк за нижним
    // таббаром — заякорить его ПРЯМО над таббаром через setAnchorView: Material сам
    // держит снэк над anchorView независимо от высот/инсетов/тайминга измерений.
    // Ручная маржин-математика ниже иногда не докладывала несколько dp (когда
    // bottomMenuRecycler на момент attach ещё не отдавал высоту), и снэк «выглядывал»
    // тонкой полоской из-под панели вместо того чтобы быть над ней.
    if (bottomBar != null && !anchor.isImeVisible()) {
        setAnchorView(bottomBar)
        return this
    }

    // Клавиатура открыта (снэк держим над IME) либо таббара нет (экран настроек и
    // прочие полноэкранные) — якорим снэк к невидимой распорке высотой с нижний
    // системный бар / IME.
    //
    // Ручная маржин-математика здесь НЕ работает: Material в updateMargins()
    // ПЕРЕЗАПИСЫВАЕТ bottomMargin (= originalMargins.bottom + extraBottomMargin…,
    // проверено по байткоду material 1.13.0), затирая всё, что мы дописали в
    // doOnAttach. А собственный инсет-слушатель снэка на экране настроек давал
    // ноль — при трёхкнопочной навигации плашка уезжала ПОД кнопки (репро на
    // эмуляторе + скриншот владельца с Xiaomi). Через anchorView Material считает
    // отступ по координатам на экране и наш dp уже не теряет.
    val spacer = anchor.ensureBottomInsetSpacer()
    if (spacer != null) {
        setAnchorView(spacer)
        return this
    }

    // Крайний случай: не нашли content-контейнер, чтобы повесить распорку.
    view.doOnAttach { snackbarView ->
        val bottomInset = anchor.transientMessageBottomOffsetPx()
        if (bottomInset <= 0) return@doOnAttach

        snackbarView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin += bottomInset
        }
    }
    return this
}

/**
 * Невидимая распорка в `android.R.id.content` высотой с нижние системные бары (или IME):
 * её верхняя граница — та линия, выше которой должен встать снэкбар. Живёт одна на активити,
 * переиспользуется и пересчитывается на каждый показ. `INVISIBLE` (не `GONE`) — вью должна
 * участвовать в разметке, чтобы у неё были координаты, но при этом не ловить касания.
 */
private fun View.ensureBottomInsetSpacer(): View? {
    val content = rootView?.findViewById<View>(android.R.id.content) as? FrameLayout ?: return null
    val height = transientMessageBottomOffsetPx()
    if (height <= 0) return null

    val existing = content.findViewWithTag<View>(BOTTOM_INSET_SPACER_TAG)
    val spacer = existing ?: View(context).apply {
        tag = BOTTOM_INSET_SPACER_TAG
        visibility = View.INVISIBLE
        isClickable = false
        isFocusable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        content.addView(this)
    }
    spacer.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM)
    return spacer
}

private const val BOTTOM_INSET_SPACER_TAG = "forpda_snackbar_bottom_inset_spacer"

private fun View.isImeVisible(): Boolean =
        ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

private fun View.transientMessageBottomOffsetPx(): Int {
    val rootInsets = ViewCompat.getRootWindowInsets(this)
    val navigationBottom = rootInsets
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?.coerceAtLeast(0)
            ?: 0
    val imeBottom = rootInsets
            ?.takeIf { it.isVisible(WindowInsetsCompat.Type.ime()) }
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom
            ?: 0
    val safeSpacing = (8f * resources.displayMetrics.density).toInt()
    val bottomChrome = rootView
            ?.findViewById<View>(R.id.bottomMenuRecycler)
            ?.takeIf { it.isShown }
            ?.let { recycler ->
                max(recycler.height, recycler.layoutParams?.height ?: 0)
            }
            ?: 0

    return if (imeBottom > 0) {
        max(imeBottom, bottomChrome + navigationBottom) + safeSpacing
    } else {
        bottomChrome + navigationBottom + safeSpacing
    }
}
