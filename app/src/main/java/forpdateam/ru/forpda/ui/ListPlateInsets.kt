package forpdateam.ru.forpda.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import androidx.annotation.AttrRes
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import androidx.annotation.Dimension
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr

internal fun Context.getDimensionFromAttr(@AttrRes attr: Int): Float {
    val typedValue = TypedValue()
    return if (theme.resolveAttribute(attr, typedValue, true)) {
        if (typedValue.type == TypedValue.TYPE_DIMENSION) {
            typedValue.getDimension(resources.displayMetrics)
        } else if (typedValue.type in TypedValue.TYPE_FIRST_INT..TypedValue.TYPE_LAST_INT) {
            typedValue.data.toFloat()
        } else {
            0f
        }
    } else {
        0f
    }
}

/**
 * Grouped list rows using the same rounded shapes as settings ([R.drawable.pref_plate_*]).
 */
enum class ListPlateSegment {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST,
}

fun listPlateSegment(prevInGroup: Boolean, nextInGroup: Boolean): ListPlateSegment = when {
    !prevInGroup && !nextInGroup -> ListPlateSegment.SINGLE
    !prevInGroup && nextInGroup -> ListPlateSegment.FIRST
    prevInGroup && nextInGroup -> ListPlateSegment.MIDDLE
    else -> ListPlateSegment.LAST
}

@DrawableRes
fun drawableResForListPlate(segment: ListPlateSegment): Int = when (segment) {
    ListPlateSegment.SINGLE -> R.drawable.pref_plate_single
    ListPlateSegment.FIRST -> R.drawable.pref_plate_top
    ListPlateSegment.MIDDLE -> R.drawable.pref_plate_middle
    ListPlateSegment.LAST -> R.drawable.pref_plate_bottom
}

fun View.resolveSelectableItemBackground(): android.graphics.drawable.Drawable? {
    val tv = TypedValue()
    if (!context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) return null
    return ContextCompat.getDrawable(context, tv.resourceId)
}

/**
 * @param gapBeforeGroupPx vertical gap above the first row of a plate group (after a header / page top).
 * @param gapAfterGroupPx vertical gap below the last row of a plate group (before next header).
 */
fun View.applyListRowPlate(
        segment: ListPlateSegment,
        horizontalInsetPx: Int,
        @Dimension(unit = Dimension.PX) gapBeforeGroupPx: Int,
        @Dimension(unit = Dimension.PX) gapAfterGroupPx: Int,
        ensureSelectableForeground: Boolean,
) {
    setBackgroundResource(drawableResForListPlate(segment))
    outlineProvider = ViewOutlineProvider.BACKGROUND
    clipToOutline = true
    if (ensureSelectableForeground && foreground == null) {
        foreground = resolveSelectableItemBackground()
    }
    updateLayoutParams<ViewGroup.MarginLayoutParams> {
        marginStart = horizontalInsetPx
        marginEnd = horizontalInsetPx
        topMargin = gapBeforeGroupPx
        bottomMargin = gapAfterGroupPx
    }
}

enum class TopAppBarShapeStyle {
    /** Во всю ширину; только нижние углы (как M3 bar под статус-баром). */
    FULL_WIDTH_BOTTOM_ROUNDED,
    /** Во всю ширину; без скругления (нижний край прямой). */
    FULL_WIDTH_RECT,
    /** Узкая плашка с боковыми inset — все четыре угла, заметное скругление. */
    INSET_PLAQUE,
    /** Узкая плашка с боковыми inset — скруглён только верх, низ стыкуется с контентом без серых углов. */
    INSET_PLAQUE_TOP_ROUNDED,
    /** Узкая плашка с боковыми inset — прямоугольник (радиус 0). */
    INSET_PLAQUE_RECT,
}

fun resolveTopAppBarShapeStyle(
        useHorizontalInset: Boolean,
        roundedCorners: Boolean,
        roundedBottomCorners: Boolean,
): TopAppBarShapeStyle = when {
    useHorizontalInset && roundedCorners && roundedBottomCorners -> TopAppBarShapeStyle.INSET_PLAQUE
    useHorizontalInset && roundedCorners && !roundedBottomCorners -> TopAppBarShapeStyle.INSET_PLAQUE_TOP_ROUNDED
    useHorizontalInset && !roundedCorners -> TopAppBarShapeStyle.INSET_PLAQUE_RECT
    !useHorizontalInset && roundedCorners -> TopAppBarShapeStyle.FULL_WIDTH_BOTTOM_ROUNDED
    else -> TopAppBarShapeStyle.FULL_WIDTH_RECT
}

/**
 * Общая форма верхней панели: радиус как у [pref_plate_*].
 */
fun Context.createTopAppBarShapeDrawable(
        surfaceColor: Int,
        style: TopAppBarShapeStyle,
        strokeWidthAttr: Int = R.attr.list_plate_stroke_width,
        strokeColorAttr: Int = R.attr.list_plate_stroke_color,
        cornerRadius: Float = resources.getDimension(R.dimen.card_corner_radius),
        drawStroke: Boolean = true,
): MaterialShapeDrawable {
    val radius = cornerRadius
    val shapeModel = when (style) {
        TopAppBarShapeStyle.FULL_WIDTH_BOTTOM_ROUNDED ->
            ShapeAppearanceModel.builder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, 0f)
                    .setTopRightCorner(CornerFamily.ROUNDED, 0f)
                    .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
                    .setBottomRightCorner(CornerFamily.ROUNDED, radius)
                    .build()
        TopAppBarShapeStyle.FULL_WIDTH_RECT ->
            ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build()
        TopAppBarShapeStyle.INSET_PLAQUE ->
            ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, radius)
                    .build()
        TopAppBarShapeStyle.INSET_PLAQUE_TOP_ROUNDED ->
            ShapeAppearanceModel.builder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                    .setTopRightCorner(CornerFamily.ROUNDED, radius)
                    .setBottomLeftCorner(CornerFamily.ROUNDED, 0f)
                    .setBottomRightCorner(CornerFamily.ROUNDED, 0f)
                    .build()
        TopAppBarShapeStyle.INSET_PLAQUE_RECT ->
            ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build()
    }
    return MaterialShapeDrawable(shapeModel).apply {
        fillColor = ColorStateList.valueOf(surfaceColor)
        val strokeWidth = if (drawStroke) getDimensionFromAttr(strokeWidthAttr) else 0f
        if (strokeWidth > 0f) {
            setStroke(strokeWidth, getColorFromAttr(strokeColorAttr))
        }
        // No view/drawable elevation: it draws a gray hairline under the rounded top bar against list content.
        elevation = 0f
    }
}

/**
 * Full-width top bars (settings action bar, тема WebView): нижние углы только.
 */
fun Context.createFullWidthTopAppBarShapeDrawable(surfaceColor: Int): MaterialShapeDrawable =
        createTopAppBarShapeDrawable(surfaceColor, TopAppBarShapeStyle.FULL_WIDTH_BOTTOM_ROUNDED)

/**
 * Нижний BottomDrawer / полоска вкладок: скругление только сверху ([R.attr.bottom_nav_bar_shape_appearance]),
 * низ прямой — edge-to-edge с системной навигацией. Цвет задаёт вызывающий код (обычно M3 `?attr/colorSurface` у нижнего листа).
 */
fun Context.createBottomNavBarSurfaceShapeDrawable(
        surfaceColor: Int,
        @AttrRes shapeAppearanceAttrRes: Int = R.attr.bottom_nav_bar_shape_appearance,
): MaterialShapeDrawable {
    val tv = TypedValue()
    check(theme.resolveAttribute(shapeAppearanceAttrRes, tv, true)) {
        "Theme attribute bottom_nav_bar_shape_appearance not resolved"
    }
    val shapeModel = ShapeAppearanceModel.builder(this, tv.resourceId, 0).build()
    return MaterialShapeDrawable(shapeModel).apply {
        fillColor = ColorStateList.valueOf(surfaceColor)
        elevation = 0f
    }
}

/**
 * [AppBarLayout]: фон-плашка со скруглением (или без — см. [roundedCorners]).
 * @param useHorizontalInset узкая плашка с отступами [R.dimen.top_bar_plaque_horizontal_inset];
 * полная ширина — сплошной бар ([TopAppBarShapeStyle.FULL_WIDTH_BOTTOM_ROUNDED] или прямой при [roundedCorners] == false).
 * @param roundedCorners если false — радиус 0 для выбранного режима; если true — M3-плашка.
 * @param roundedBottomCorners если false — низ плашки прямой для экранов, где она должна стыковаться с контентом.
 */
fun AppBarLayout.applyTopBarPlaqueChrome(
        useHorizontalInset: Boolean = false,
        roundedCorners: Boolean = true,
        roundedBottomCorners: Boolean = roundedCorners,
        surfaceColorAttr: Int = R.attr.chrome_plane_background,
) {
    val ctx = context
    val usesMainToolbarSurface = surfaceColorAttr == R.attr.main_toolbar_accent_surface
    // Плоский хром главных разделов идёт через ChromeCanvas: под Material You плашка
    // тонируется обоями в единый тон с полотном; вне MY — ровно значение атрибута.
    val fill = if (usesMainToolbarSurface) {
        ctx.chromeCanvasColor(surfaceColorAttr)
    } else {
        ctx.getColorFromAttr(surfaceColorAttr)
    }
    val style = resolveTopAppBarShapeStyle(useHorizontalInset, roundedCorners, roundedBottomCorners)
    background = ctx.createTopAppBarShapeDrawable(
            fill,
            style,
            strokeWidthAttr = if (usesMainToolbarSurface) {
                R.attr.main_toolbar_stroke_width
            } else {
                R.attr.list_plate_stroke_width
            },
            strokeColorAttr = if (usesMainToolbarSurface) {
                R.attr.main_toolbar_stroke_color
            } else {
                R.attr.list_plate_stroke_color
            },
            drawStroke = !usesMainToolbarSurface,
    )
    if (useHorizontalInset) {
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    } else {
        clipToOutline = false
    }
    stateListAnimator = null
    targetElevation = 0f
    ViewCompat.setElevation(this, 0f)
    ViewCompat.setTranslationZ(this, 0f)
    val insetPx = if (useHorizontalInset) {
        ctx.resources.getDimensionPixelSize(R.dimen.top_bar_plaque_horizontal_inset)
    } else {
        0
    }
    updateLayoutParams<CoordinatorLayout.LayoutParams> {
        marginStart = insetPx
        marginEnd = insetPx
        topMargin = 0
    }
}
