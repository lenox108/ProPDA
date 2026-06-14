@file:JvmName("MaterialAlertDialogHelper")

package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
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
fun MaterialAlertDialogBuilder.showWithStyledButtons(): AlertDialog {
    val dialog = create()
    dialog.applyForPdaSurface()
    dialog.setOnShowListener {
        dialog.applyForPdaMaterialStyle()
    }
    dialog.show()
    return dialog
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
    return resolveColor(context, R.attr.link_color, 0xff2177af.toInt())
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
