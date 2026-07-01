package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences.Main.AccentPalette
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Диалог выбора курируемой акцент-палитры («смена цвета») — грид цветных кружков.
 * Универсален (работает на всех API): свотчи — заранее сгенерированные
 * `@color/accent_*` ресурсы, без wallpaper-зависимости.
 */
object AccentPickerDialog {

    private data class Entry(
            val palette: AccentPalette,
            @StringRes val title: Int,
            @ColorRes val swatch: Int,
    )

    // Порядок = порядок в гриде. NEUTRAL первым (текущий бренд).
    private val entries = listOf(
            Entry(AccentPalette.NEUTRAL, R.string.accent_neutral, R.color.light_colorAccent),
            Entry(AccentPalette.BLUE, R.string.accent_blue, R.color.accent_blue_primary),
            Entry(AccentPalette.INDIGO, R.string.accent_indigo, R.color.accent_indigo_primary),
            Entry(AccentPalette.VIOLET, R.string.accent_violet, R.color.accent_violet_primary),
            Entry(AccentPalette.PURPLE, R.string.accent_purple, R.color.accent_purple_primary),
            Entry(AccentPalette.PINK, R.string.accent_pink, R.color.accent_pink_primary),
            Entry(AccentPalette.RED, R.string.accent_red, R.color.accent_red_primary),
            Entry(AccentPalette.DEEPORANGE, R.string.accent_deeporange, R.color.accent_deeporange_primary),
            Entry(AccentPalette.ORANGE, R.string.accent_orange, R.color.accent_orange_primary),
            Entry(AccentPalette.AMBER, R.string.accent_amber, R.color.accent_amber_primary),
            Entry(AccentPalette.GREEN, R.string.accent_green, R.color.accent_green_primary),
            Entry(AccentPalette.TEAL, R.string.accent_teal, R.color.accent_teal_primary),
            Entry(AccentPalette.CYAN, R.string.accent_cyan, R.color.accent_cyan_primary),
    )

    /** Заголовок палитры для summary настройки. */
    @StringRes
    fun titleRes(palette: AccentPalette): Int =
            entries.firstOrNull { it.palette == palette }?.title ?: R.string.accent_neutral

    fun show(context: Context, current: AccentPalette, onPick: (AccentPalette) -> Unit) {
        val columns = 4
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()

        val grid = GridLayout(context).apply {
            columnCount = columns
            val pad = px(12)
            setPadding(pad, px(8), pad, px(8))
        }

        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val ringColor = context.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)

        var dialogRef: androidx.appcompat.app.AlertDialog? = null

        entries.forEach { entry ->
            val fill = ContextCompat.getColor(context, entry.swatch)
            val cell = FrameLayout(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = px(64)
                    height = px(72)
                }
            }
            val dot = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(px(44), px(44), Gravity.CENTER_HORIZONTAL or Gravity.TOP)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(fill)
                    if (entry.palette == current) {
                        setStroke(px(3), ringColor)
                    } else {
                        // тонкая обводка для светлых свотчей на светлом фоне
                        setStroke(px(1), Color.argb(40, 0, 0, 0))
                    }
                }
                contentDescription = context.getString(entry.title)
            }
            val label = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                )
                text = context.getString(entry.title)
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 1
                setTextColor(onSurface)
            }
            cell.addView(dot)
            cell.addView(label)
            cell.setOnClickListener {
                onPick(entry.palette)
                dialogRef?.dismiss()
            }
            grid.addView(cell)
        }

        val scroll = android.widget.ScrollView(context).apply { addView(grid) }

        dialogRef = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.accent_dialog_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }
}
