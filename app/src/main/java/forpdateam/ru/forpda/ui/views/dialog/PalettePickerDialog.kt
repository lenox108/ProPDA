package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences.Main.UiPalette
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Диалог выбора «Цветов интерфейса» (UiPalette) с ВИЗУАЛЬНЫМ ПРЕВЬЮ: каждая
 * палитра нарисована карточкой в СВОИХ цветах (бумага + текст + акцент), а не
 * строкой списка — цвет читаемости виден до применения. Тап по карточке
 * применяет палитру (как раньше делал ListPreference).
 *
 * Для читающих палитр (Sepia/SepiaBlue/Minimal) показывается их светлая
 * «бумажная» сигнатура — узнаваемый образ палитры независимо от текущего
 * ночного режима. Для SYSTEM берутся живые M3-роли текущей темы (следует за
 * светлой/тёмной/AMOLED и акцентом).
 */
object PalettePickerDialog {

    private data class Entry(
            val palette: UiPalette,
            @StringRes val title: Int,
            // Светлая сигнатура читающих палитр; для SYSTEM игнорируется (живой резолв).
            val bg: Int,
            val text: Int,
            val text2: Int,
            val accent: Int,
    )

    private val entries = listOf(
            Entry(UiPalette.SYSTEM, R.string.pref_value_ui_palette_system, 0, 0, 0, 0),
            Entry(UiPalette.MINIMAL_READER, R.string.pref_value_ui_palette_minimal_reader,
                    0xFFFCFBF8.toInt(), 0xFF1E1E1E.toInt(), 0xFF6F6B63.toInt(), 0xFF7C8FA1.toInt()),
            Entry(UiPalette.SEPIA_READING, R.string.pref_value_ui_palette_sepia_reading,
                    0xFFFFF8E7.toInt(), 0xFF3A2E22.toInt(), 0xFF7A6A58.toInt(), 0xFF8A5A2B.toInt()),
            Entry(UiPalette.SEPIA_BLUE, R.string.pref_value_ui_palette_sepia_blue,
                    0xFFFFF9EE.toInt(), 0xFF2F2A23.toInt(), 0xFF766B5D.toInt(), 0xFF4F7896.toInt()),
    )

    fun show(context: Context, current: UiPalette, onPick: (UiPalette) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()

        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val selectedRing = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)

        fun colorsFor(e: Entry): Entry {
            if (e.palette != UiPalette.SYSTEM) return e
            return e.copy(
                    bg = context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest),
                    text = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface),
                    text2 = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant),
                    accent = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary),
            )
        }

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(8))
        }

        entries.forEach { raw ->
            val e = colorsFor(raw)
            val selected = raw.palette == current

            val title = TextView(context).apply {
                text = context.getString(raw.title)
                setTextColor(e.text)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            }
            val sample = TextView(context).apply {
                text = context.getString(R.string.palette_preview_sample)
                setTextColor(e.text2)
                textSize = 13f
                setPadding(0, px(2), 0, px(8))
            }
            val accentChip = TextView(context).apply {
                text = context.getString(R.string.palette_preview_link)
                setTextColor(if (isLight(e.accent)) Color.BLACK else Color.WHITE)
                textSize = 12f
                setPadding(px(12), px(4), px(12), px(4))
                background = GradientDrawable().apply {
                    cornerRadius = px(14).toFloat(); setColor(e.accent)
                }
            }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16), px(14), px(16), px(14))
                background = GradientDrawable().apply {
                    cornerRadius = px(16).toFloat()
                    setColor(e.bg)
                    setStroke(if (selected) px(3) else px(1), if (selected) selectedRing else outline)
                }
                addView(title)
                addView(sample)
                addView(accentChip, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
                isClickable = true
                setOnClickListener {
                    dialog.dismiss()
                    onPick(raw.palette)
                }
            }
            list.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6); bottomMargin = px(6)
            })
        }

        val content = ScrollView(context).apply { addView(list) }

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_ui_palette)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    /** Достаточный ли цвет светлый, чтобы класть тёмный текст поверх. */
    private fun isLight(color: Int): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return (0.299 * r + 0.587 * g + 0.114 * b) > 150
    }
}
