package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.AppFontMode
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Диалог выбора «Шрифта приложения» (AppFontMode) с ВИЗУАЛЬНЫМ ПРЕВЬЮ: каждая
 * карточка рендерит образец текста РЕАЛЬНОЙ гарнитурой (Roboto / Inter /
 * Source Sans 3 / Open Sans / системный), чтобы шрифт был виден до применения —
 * шрифты как раз выбирают глазами. Тап по карточке применяет режим (тот же путь
 * recreate, что раньше).
 */
object FontPickerDialog {

    private data class Entry(val mode: AppFontMode, @StringRes val title: Int, @FontRes val font: Int)

    // font = 0 для системного (Typeface.DEFAULT).
    private val entries = listOf(
            Entry(AppFontMode.SYSTEM, R.string.pref_value_app_font_system, 0),
            Entry(AppFontMode.ROBOTO, R.string.pref_value_app_font_roboto, R.font.forpda_roboto),
            Entry(AppFontMode.INTER, R.string.pref_value_app_font_inter, R.font.forpda_inter),
            Entry(AppFontMode.SOURCE_SANS_3, R.string.pref_value_app_font_source_sans_3, R.font.forpda_source_sans_3),
            Entry(AppFontMode.OPEN_SANS, R.string.pref_value_app_font_open_sans, R.font.forpda_open_sans),
    )

    fun show(context: Context, current: AppFontMode, onPick: (AppFontMode) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()

        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val selectedRing = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val cardBg = context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow)

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(8))
        }

        entries.forEach { e ->
            val selected = e.mode == current
            val face = if (e.font == 0) Typeface.DEFAULT
            else runCatching { ResourcesCompat.getFont(context, e.font) }.getOrNull() ?: Typeface.DEFAULT

            val sample = TextView(context).apply {
                text = context.getString(R.string.font_preview_sample)
                setTextColor(onSurface)
                textSize = 19f
                typeface = face
            }
            val label = TextView(context).apply {
                text = context.getString(e.title)
                setTextColor(onSurfaceVar)
                textSize = 12f
                setPadding(0, px(4), 0, 0)
            }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(16), px(14), px(16), px(14))
                background = GradientDrawable().apply {
                    cornerRadius = px(16).toFloat()
                    setColor(cardBg)
                    setStroke(if (selected) px(3) else px(1), if (selected) selectedRing else outline)
                }
                addView(sample)
                addView(label)
                isClickable = true
                setOnClickListener { dialog.dismiss(); onPick(e.mode) }
            }
            list.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6); bottomMargin = px(6)
            })
        }

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_app_font)
                .setView(ScrollView(context).apply { addView(list) })
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }
}
