package forpdateam.ru.forpda.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.util.DisplayMetrics
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import android.content.res.TypedArray

/**
 * Extension-функции для Context, заменяющие статические утилиты из App.
 *
 * Java-совместимость обеспечена через @JvmStatic-обёртки в App.companion.
 */

@ColorInt
fun Context.getColorFromAttr(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    if (!theme.resolveAttribute(attr, typedValue, true)) return Color.RED
    if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
        return typedValue.data
    }
    // Атрибут резолвится не в сырой цвет, а в файл-ресурс (ColorStateList-
    // селектор — напр., lStar-цвета Material You DarkFloor или динамические
    // палитры Material на API 31-33): TypedValue.data тут НЕ цвет. Грузим
    // через TypedArray, который умеет селекторы (вернёт default-цвет).
    val ta = theme.obtainStyledAttributes(intArrayOf(attr))
    try {
        return ta.getColor(0, Color.RED)
    } finally {
        ta.recycle()
    }
}

@DrawableRes
fun Context.getDrawableResAttr(@AttrRes attr: Int): Int {
    val typedArray: TypedArray = theme.obtainStyledAttributes(intArrayOf(attr))
    val resourceId = typedArray.getResourceId(0, 0)
    typedArray.recycle()
    return resourceId
}

fun Context.getDrawableAttr(@AttrRes attr: Int): Drawable? {
    return AppCompatResources.getDrawable(this, getDrawableResAttr(attr))
}

fun Context.getVecDrawable(@DrawableRes id: Int): Drawable {
    val drawable = AppCompatResources.getDrawable(this, id)
    require(
        drawable is androidx.vectordrawable.graphics.drawable.VectorDrawableCompat ||
        drawable is VectorDrawable
    ) {
        "Drawable must be a vector drawable"
    }
    return drawable as Drawable
}

fun Context.getToolBarHeight(): Int {
    val attrs = intArrayOf(androidx.appcompat.R.attr.actionBarSize)
    val typedArray = obtainStyledAttributes(attrs)
    val height = typedArray.getDimensionPixelSize(0, -1)
    typedArray.recycle()
    return height
}

fun Context.dpToPx(dp: Int): Int {
    val displayMetrics = resources.displayMetrics
    return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
}
