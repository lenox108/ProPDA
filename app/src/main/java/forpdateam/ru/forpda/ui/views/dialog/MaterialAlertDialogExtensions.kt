@file:JvmName("MaterialAlertDialogHelper")

package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.R as AppCompatR
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import timber.log.Timber

/**
 * Тема alertDialogTheme не всегда задаёт контраст кнопок в тёмной теме — красим явно.
 * [android.util.TypedValue.data] для ссылки на цвет — не ARGB; через [android.content.res.TypedArray] цвет резолвится верно.
 */
fun MaterialAlertDialogBuilder.showWithStyledButtons(compact: Boolean = true): AlertDialog {
    val dialog = create()
    dialog.applyForPdaSurface()
    // Отключаем системную анимацию окна: свою (плавное появление уже в нужном размере) делаем сами,
    // иначе системный scale/переезд окна при ресайзе виден как «прыжок».
    if (compact) dialog.window?.setWindowAnimations(0)
    dialog.setOnShowListener {
        dialog.applyForPdaMaterialStyle()
        if (compact) dialog.applyCompactWidthAnimated()
    }
    dialog.show()
    return dialog
}

/**
 * Компактная ширина БЕЗ «прыжка»: закрепить ширину окна можно только после show() (это неминуемо
 * двигает окно), поэтому прячем карточку (decor alpha=0), делаем ресайз, ДОЖИДАЕМСЯ завершения
 * перелэйаута (OnGlobalLayout, а не один post — иначе показ ловит окно ещё «слева») и только тогда
 * плавно проявляем — уже в финальном размере и по центру. Затемнение фона (dim) — атрибут окна,
 * оно живёт отдельно и не мигает. Вызывать в onShow (панели уже построены и измеримы).
 */
fun AlertDialog.applyCompactWidthAnimated() {
    val decor = window?.decorView ?: return
    decor.alpha = 0f
    shrinkWidthToContent()
    decor.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            decor.viewTreeObserver.removeOnGlobalLayoutListener(this)
            decor.animate().alpha(1f).setDuration(140).start()
        }
    })
}

/**
 * Ужимает ширину окна диалога до ширины его контента. AlertController иначе держит ширину по
 * windowMinWidthMinor/Major (~90–95% экрана), из-за чего диалоги выглядят растянутыми.
 *
 * Меряем НЕ сырую панель целиком (ListView под UNSPECIFIED отдаёт негодную ширину), а по компонентам:
 * заголовок (topPanel), кнопки (buttonPanel) и тело — для списков это максимум ширины пунктов адаптера,
 * иначе contentPanel. Всё в UNSPECIFIED, где match_parent ведёт себя как wrap и отдаёт натуральную
 * ширину. Затем ставим окну конкретную ширину в px — фиксированный размер окна перебивает внутренний
 * минимум. Зажимаем в 300dp..92% экрана: не уже удобного для поля ввода и не шире экрана для длинного
 * контента. Если замер невалиден (0) — ширину не трогаем (безопасный фолбэк).
 */
private fun AlertDialog.shrinkWidthToContent() {
    val w = window ?: return
    val decor = w.decorView
    val dm = context.resources.displayMetrics

    fun naturalWidth(v: View?): Int {
        v ?: return 0
        v.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return v.measuredWidth
    }

    val titleWidth = naturalWidth(decor.findViewById(AppCompatR.id.topPanel))
    val buttonsWidth = naturalWidth(decor.findViewById(AppCompatR.id.buttonPanel))

    val lv = listView
    val adapter = lv?.adapter
    val bodyWidth = if (lv != null && adapter != null && adapter.count > 0) {
        // ListView сам по себе меряется криво — меряем ширину пунктов адаптера напрямую.
        var itemMax = 0
        for (i in 0 until minOf(adapter.count, 50)) {
            itemMax = maxOf(itemMax, naturalWidth(adapter.getView(i, null, lv)))
        }
        itemMax + lv.paddingLeft + lv.paddingRight
    } else {
        // contentPanel — для message-диалогов; customPanel — для setView (меню, поля ввода и т.п.).
        maxOf(
            naturalWidth(decor.findViewById(AppCompatR.id.contentPanel)),
            naturalWidth(decor.findViewById(AppCompatR.id.customPanel))
        )
    }

    val measured = maxOf(titleWidth, buttonsWidth, bodyWidth)
    if (measured <= 0) return
    val minPx = (300 * dm.density).toInt()
    val maxPx = (dm.widthPixels * 0.92f).toInt()
    val target = (measured + (16 * dm.density).toInt()).coerceIn(minPx, maxPx)

    // Закрепить ширину можно только через атрибуты окна (панель/до-show не «прилипают»). Явно ставим
    // gravity=CENTER, иначе при ресайзе узкое окно прилипает к левому краю. Плавный показ (скрытие на
    // время ресайза) делает вызывающий код — здесь только выставляем финальные размер/позицию.
    w.attributes = w.attributes.apply {
        width = target
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        gravity = Gravity.CENTER
    }
}

fun AlertDialog.applyForPdaMaterialStyle() {
    applyForPdaSurface()
    applyForPdaButtonColors()
    applyForPdaFontMode()
}

fun AlertDialog.applyForPdaSurface() {
    val surface = resolveColor(context, R.attr.colorSurface, 0xff000000.toInt())
    val outline = resolveColor(context, R.attr.colorOutline, surface)
    val background = createDialogSurface(context, surface, outline)

    window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
    window?.setDimAmount(0.32f)
    listView?.setDialogPanelBackground()
    window?.decorView?.apply {
        setDialogPanelBackground()
        findViewById<View>(AppCompatR.id.parentPanel)?.background = background
    }
}

private fun AlertDialog.applyForPdaButtonColors() {
    val c = resolveLinkColor(context)
    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(c)
    getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(c)
    getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(c)
}

private fun AlertDialog.applyForPdaFontMode() {
    val font = ResourcesCompat.getFont(context, R.font.forpda_roboto)
    val family = resolveString(context, R.attr.fontFamily)
    if (family == null || font == null || family == "sans-serif") return
    if (BuildConfig.DEBUG) {
        Timber.d("nativeFontFamilyApplied=%s dialog=true", family)
    }
    window?.decorView?.applyTypeface(font)
}

private fun View.applyTypeface(typeface: Typeface) {
    when (this) {
        is TextView -> setTypeface(typeface, this.typeface?.style ?: Typeface.NORMAL)
        is ViewGroup -> for (i in 0 until childCount) {
            getChildAt(i).applyTypeface(typeface)
        }
    }
}

private fun createDialogSurface(context: Context, surface: Int, outline: Int): Drawable {
    val density = context.resources.displayMetrics.density
    val shape = ShapeAppearanceModel.builder()
            .setAllCornerSizes(28f * density)
            .build()

    return MaterialShapeDrawable(shape).apply {
        fillColor = ColorStateList.valueOf(surface)
        strokeColor = ColorStateList.valueOf(outline)
        strokeWidth = if (outline == surface) 0f else density
        initializeElevationOverlay(context)
    }
}

private fun View.setDialogPanelBackground() {
    when (this) {
        is ListView -> {
            background = ColorDrawable(android.graphics.Color.TRANSPARENT)
            cacheColorHint = android.graphics.Color.TRANSPARENT
        }
        is ViewGroup -> {
            if (background is ColorDrawable) {
                background = ColorDrawable(android.graphics.Color.TRANSPARENT)
            }
            for (i in 0 until childCount) {
                getChildAt(i).setDialogPanelBackground()
            }
        }
    }
}

private fun resolveLinkColor(context: Context): Int {
    return resolveColor(context, com.google.android.material.R.attr.colorSecondary, 0xff2177af.toInt())
}

private fun resolveColor(context: Context, @AttrRes attr: Int, fallback: Int): Int {
    val a = context.obtainStyledAttributes(intArrayOf(attr))
    val color = a.getColor(0, fallback)
    a.recycle()
    return color
}

private fun resolveString(context: Context, @AttrRes attr: Int): String? {
    val a = context.obtainStyledAttributes(intArrayOf(attr))
    val value = a.getString(0)
    a.recycle()
    return value
}
