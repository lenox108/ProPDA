package forpdateam.ru.forpda.ui.fragments.other

import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem

internal enum class OtherMenuPlateRow {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST,
}

internal fun otherMenuPlateRow(items: List<ListItem>, position: Int, groupPredicate: (ListItem) -> Boolean): OtherMenuPlateRow {
    if (position !in items.indices || !groupPredicate(items[position])) {
        return OtherMenuPlateRow.SINGLE
    }
    val prevSame = items.getOrNull(position - 1)?.let(groupPredicate) == true
    val nextSame = items.getOrNull(position + 1)?.let(groupPredicate) == true
    return when {
        !prevSame && !nextSame -> OtherMenuPlateRow.SINGLE
        !prevSame && nextSame -> OtherMenuPlateRow.FIRST
        prevSame && nextSame -> OtherMenuPlateRow.MIDDLE
        else -> OtherMenuPlateRow.LAST
    }
}

/**
 * Plate backgrounds for the menu. Unlike the shared `pref_plate_*` set, the middle/bottom rows here draw
 * NO top edge: the settings plates paint one stroke per row, which made every seam inside a group show a
 * full-bleed hairline. A menu plate reads as one M3 card, and rows are separated by the inset divider the
 * row itself draws (see `otherContinueDivider`).
 */
@DrawableRes
internal fun drawableForPlateRow(row: OtherMenuPlateRow): Int = when (row) {
    OtherMenuPlateRow.SINGLE -> R.drawable.pref_plate_single
    OtherMenuPlateRow.FIRST -> R.drawable.pref_plate_top
    OtherMenuPlateRow.MIDDLE -> R.drawable.other_menu_plate_middle
    OtherMenuPlateRow.LAST -> R.drawable.other_menu_plate_bottom
}

internal fun View.applyOtherMenuPlateMarginsAndBackground(
        items: List<ListItem>,
        position: Int,
        groupPredicate: (ListItem) -> Boolean,
        bottomDivider: View?,
) {
    val isMenuRow = position in items.indices && groupPredicate(items[position])
    val row = if (isMenuRow) OtherMenuPlateRow.SINGLE else otherMenuPlateRow(items, position, groupPredicate)
    setBackgroundResource(drawableForPlateRow(row))
    // No inner dividers inside the plate groups: spacing + plate backgrounds provide enough separation.
    bottomDivider?.visibility = View.GONE
    val h = resources.getDimensionPixelSize(R.dimen.other_menu_plate_horizontal_margin)
    val gap = resources.getDimensionPixelSize(R.dimen.other_menu_plate_vertical_gap)
    updateLayoutParams<RecyclerView.LayoutParams> {
        leftMargin = h
        rightMargin = h
        topMargin = if (isMenuRow) gap / 2 else 0
        bottomMargin = if (isMenuRow) gap / 2 else 0
    }
}
