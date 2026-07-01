package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences.Main.AccentPalette
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Диалог выбора акцент-палитры («смена цвета») с ЖИВЫМ ПРЕВЬЮ: тап по свотчу
 * обновляет мини-карточку (кнопка + ссылка + чип в выбранном цвете на surface),
 * применение — по кнопке OK. Свотчи и превью генерятся тем же алгоритмом
 * (TonalSpot/Vibrant из seed), что и нативная тема — учитывают выбранную
 * насыщенность и светлую/тёмную тему. Универсально на всех API.
 */
object AccentPickerDialog {

    private data class Entry(val palette: AccentPalette, @StringRes val title: Int, val seed: Int?)

    // seed = null для NEUTRAL (нет генерации). Порядок = порядок в гриде.
    private val entries = listOf(
            Entry(AccentPalette.NEUTRAL, R.string.accent_neutral, null),
            Entry(AccentPalette.BLUE, R.string.accent_blue, 0xFF0B57D0.toInt()),
            Entry(AccentPalette.INDIGO, R.string.accent_indigo, 0xFF4355B9.toInt()),
            Entry(AccentPalette.VIOLET, R.string.accent_violet, 0xFF7B4FCF.toInt()),
            Entry(AccentPalette.PURPLE, R.string.accent_purple, 0xFF9C27B0.toInt()),
            Entry(AccentPalette.PINK, R.string.accent_pink, 0xFFC2185B.toInt()),
            Entry(AccentPalette.RED, R.string.accent_red, 0xFFD32F2F.toInt()),
            Entry(AccentPalette.DEEPORANGE, R.string.accent_deeporange, 0xFFE64A19.toInt()),
            Entry(AccentPalette.ORANGE, R.string.accent_orange, 0xFFEF6C00.toInt()),
            Entry(AccentPalette.AMBER, R.string.accent_amber, 0xFFFF8F00.toInt()),
            Entry(AccentPalette.GREEN, R.string.accent_green, 0xFF2E7D32.toInt()),
            Entry(AccentPalette.TEAL, R.string.accent_teal, 0xFF00796B.toInt()),
            Entry(AccentPalette.CYAN, R.string.accent_cyan, 0xFF0097A7.toInt()),
    )

    @StringRes
    fun titleRes(palette: AccentPalette): Int = when (palette) {
        AccentPalette.CUSTOM -> R.string.accent_custom
        else -> entries.firstOrNull { it.palette == palette }?.title ?: R.string.accent_neutral
    }

    private data class Roles(val primary: Int, val onPrimary: Int, val container: Int, val onContainer: Int)

    fun show(
            context: Context,
            current: AccentPalette,
            currentCustomColor: Int,
            vibrant: Boolean,
            onPick: (AccentPalette, Int?) -> Unit,
    ) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)

        fun rolesFor(palette: AccentPalette, customColor: Int): Roles {
            val seed = when (palette) {
                AccentPalette.NEUTRAL -> return Roles(
                        context.getColorFromAttr(androidx.appcompat.R.attr.colorAccent),
                        if (isDark) Color.BLACK else Color.WHITE,
                        context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh),
                        onSurface)
                AccentPalette.CUSTOM -> customColor
                else -> entries.first { it.palette == palette }.seed!!
            }
            val scheme = if (vibrant) SchemeVibrant(Hct.fromInt(seed), isDark, 0.0)
            else SchemeTonalSpot(Hct.fromInt(seed), isDark, 0.0)
            val m = MaterialDynamicColors()
            return Roles(m.primary().getArgb(scheme), m.onPrimary().getArgb(scheme),
                    m.primaryContainer().getArgb(scheme), m.onPrimaryContainer().getArgb(scheme))
        }

        // Изменяемый выбор (превью до применения).
        var selected = current
        var selectedCustom = currentCustomColor

        // --- Превью-карточка: кнопка + ссылка + чип ---
        val previewButton = TextView(context).apply {
            text = context.getString(R.string.accent_preview_button)
            textSize = 13f
            setPadding(px(16), px(8), px(16), px(8))
            gravity = Gravity.CENTER
        }
        val previewLink = TextView(context).apply {
            text = context.getString(R.string.accent_preview_link)
            textSize = 14f
            paint.isUnderlineText = true
            setPadding(px(4), px(8), px(4), px(8))
        }
        val previewChip = TextView(context).apply {
            text = context.getString(R.string.accent_preview_chip)
            textSize = 13f
            setPadding(px(14), px(6), px(14), px(6))
        }
        fun renderPreview() {
            val r = rolesFor(selected, selectedCustom)
            previewButton.setTextColor(r.onPrimary)
            previewButton.background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat(); setColor(r.primary)
            }
            previewLink.setTextColor(r.primary)
            previewChip.setTextColor(r.onContainer)
            previewChip.background = GradientDrawable().apply {
                cornerRadius = px(16).toFloat(); setColor(r.container); setStroke(px(1), outline)
            }
        }
        val previewRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(px(20), px(8), px(20), px(12))
            addView(previewButton)
            addView(View(context), LinearLayout.LayoutParams(px(12), 1))
            addView(previewLink)
            addView(View(context), LinearLayout.LayoutParams(px(12), 1))
            addView(previewChip)
        }

        // --- Грид свотчей ---
        val grid = GridLayout(context).apply {
            columnCount = 4
            val pad = px(12)
            setPadding(pad, px(4), pad, px(8))
        }
        val dots = mutableMapOf<AccentPalette, View>()
        fun refreshRings() {
            val ringColor = rolesFor(selected, selectedCustom).primary
            dots.forEach { (palette, dot) ->
                val fill = if (palette == AccentPalette.CUSTOM) rolesFor(AccentPalette.CUSTOM, selectedCustom).primary
                else rolesFor(palette, selectedCustom).primary
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(fill)
                    if (palette == selected) setStroke(px(3), ringColor) else setStroke(px(1), Color.argb(40, 0, 0, 0))
                }
            }
        }

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        fun makeCell(palette: AccentPalette, title: String, onClick: () -> Unit) {
            val cell = FrameLayout(context).apply {
                layoutParams = GridLayout.LayoutParams().apply { width = px(64); height = px(72) }
            }
            val dot = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(px(44), px(44), Gravity.CENTER_HORIZONTAL or Gravity.TOP)
                contentDescription = title
            }
            val label = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                text = title; textSize = 10f; gravity = Gravity.CENTER; maxLines = 1; setTextColor(onSurface)
            }
            cell.addView(dot); cell.addView(label)
            cell.setOnClickListener { onClick() }
            dots[palette] = dot
            grid.addView(cell)
        }

        entries.forEach { entry ->
            makeCell(entry.palette, context.getString(entry.title)) {
                selected = entry.palette
                renderPreview(); refreshRings()
            }
        }
        makeCell(AccentPalette.CUSTOM, context.getString(R.string.accent_custom)) {
            showHueDialog(context, selectedCustom) { seed ->
                selected = AccentPalette.CUSTOM
                selectedCustom = seed
                renderPreview(); refreshRings()
            }
        }

        renderPreview(); refreshRings()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(previewRow)
            addView(android.widget.ScrollView(context).apply { addView(grid) })
        }

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.accent_dialog_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onPick(selected, if (selected == AccentPalette.CUSTOM) selectedCustom else null)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    /** Ползунок оттенка (0–360°) для произвольного seed + живое превью. */
    private fun showHueDialog(context: Context, initial: Int, onChosen: (Int) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()
        val initialHsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        var hue = initialHsv[0]
        fun seedForHue(h: Float): Int = Color.HSVToColor(floatArrayOf(h, 0.85f, 0.85f))

        val preview = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(px(72), px(72), Gravity.CENTER_HORIZONTAL).apply {
                topMargin = px(8); bottomMargin = px(16)
            }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(seedForHue(hue)) }
        }
        val slider = SeekBar(context).apply { max = 360; progress = hue.roundToInt().coerceIn(0, 360) }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = px(20); setPadding(pad, px(12), pad, 0)
            addView(preview); addView(slider)
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                hue = progress.toFloat()
                (preview.background as GradientDrawable).setColor(seedForHue(hue))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.accent_custom)
                .setView(container)
                .setPositiveButton(android.R.string.ok) { _, _ -> onChosen(seedForHue(hue)) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }
}
