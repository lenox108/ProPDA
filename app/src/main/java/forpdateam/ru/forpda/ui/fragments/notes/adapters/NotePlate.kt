package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.view.View
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.listPlateSegment
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteFolderListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteListItem

/**
 * Закладки рисуются той же «плашкой», что избранное/ответы/QMS (см. [applyListRowPlate]):
 * подряд идущие строки (папки и заметки) склеиваются в один скруглённый блок с волосяными
 * разделителями, а зазор появляется только на границах группы. Группа рвётся на заголовке
 * секции и на любых не-строчных элементах (инфо-плашка, разделитель).
 */
private fun isPlateRow(item: ListItem?): Boolean =
        item is NoteListItem || item is NoteFolderListItem

internal fun notePlateSegment(items: List<ListItem>, position: Int): ListPlateSegment =
        listPlateSegment(
                prevInGroup = isPlateRow(items.getOrNull(position - 1)),
                nextInGroup = isPlateRow(items.getOrNull(position + 1))
        )

/** Фон-плашка + боковые инсеты + вертикальные зазоры на границах группы. */
internal fun View.applyNotePlate(segment: ListPlateSegment) {
    val res = resources
    val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
    val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
    val first = segment == ListPlateSegment.FIRST || segment == ListPlateSegment.SINGLE
    val last = segment == ListPlateSegment.LAST || segment == ListPlateSegment.SINGLE
    applyListRowPlate(
            segment,
            inset,
            if (first) gap else 0,
            if (last) gap else 0,
            ensureSelectableForeground = false,
    )
}
