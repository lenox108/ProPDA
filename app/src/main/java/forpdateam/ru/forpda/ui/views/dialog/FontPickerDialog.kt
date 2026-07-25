package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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
 * Source Sans 3 / Open Sans / IBM Plex Sans / Golos Text / Literata /
 * Appetite Pro / Mayonez Italic / системный), чтобы шрифт был виден до применения —
 * шрифты как раз выбирают глазами. Тап по карточке применяет режим (тот же путь
 * recreate, что раньше).
 */
object FontPickerDialog {

    // monospace — рисовать превью платформенным monospace (Typeface.MONOSPACE).
    private data class Entry(
            val mode: AppFontMode,
            @StringRes val title: Int,
            @FontRes val font: Int,
            val monospace: Boolean = false,
    )

    // font = 0 для системного (Typeface.DEFAULT).
    private val entries = listOf(
            Entry(AppFontMode.SYSTEM, R.string.pref_value_app_font_system, 0),
            Entry(AppFontMode.ROBOTO, R.string.pref_value_app_font_roboto, R.font.forpda_roboto),
            Entry(AppFontMode.INTER, R.string.pref_value_app_font_inter, R.font.forpda_inter),
            Entry(AppFontMode.SOURCE_SANS_3, R.string.pref_value_app_font_source_sans_3, R.font.forpda_source_sans_3),
            Entry(AppFontMode.OPEN_SANS, R.string.pref_value_app_font_open_sans, R.font.forpda_open_sans),
            Entry(AppFontMode.IBM_PLEX_SANS, R.string.pref_value_app_font_ibm_plex_sans, R.font.forpda_ibm_plex_sans),
            Entry(AppFontMode.GOLOS_TEXT, R.string.pref_value_app_font_golos_text, R.font.forpda_golos_text),
            Entry(AppFontMode.LITERATA, R.string.pref_value_app_font_literata, R.font.forpda_literata),
            Entry(AppFontMode.APPETITE_PRO, R.string.pref_value_app_font_appetite_pro, R.font.forpda_appetite_pro),
            Entry(AppFontMode.MAYONEZ_ITALIC, R.string.pref_value_app_font_mayonez_italic, R.font.forpda_mayonez_italic),
            Entry(AppFontMode.ROBOTO_MONO, R.string.pref_value_app_font_roboto_mono, 0, monospace = true),
    )

    fun show(context: Context, current: AppFontMode, onPick: (AppFontMode) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()

        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val selectedRing = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val onPrimaryContainer = context.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimaryContainer)
        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val cardBg = context.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLow)
        val selectedBg = context.getColorFromAttr(com.google.android.material.R.attr.colorPrimaryContainer)

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(8))
        }

        val currentTitle = context.getString(
                entries.firstOrNull { it.mode == current }?.title
                        ?: R.string.pref_value_app_font_system
        )
        list.addView(TextView(context).apply {
            text = context.getString(R.string.font_picker_current_font, currentTitle)
            setTextColor(onPrimaryContainer)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(px(16), px(12), px(16), px(12))
            background = GradientDrawable().apply {
                cornerRadius = px(12).toFloat()
                setColor(selectedBg)
            }
        }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = px(4)
            bottomMargin = px(8)
        })

        entries.forEach { e ->
            val selected = e.mode == current
            val title = context.getString(e.title)
            val face = when {
                e.monospace -> Typeface.MONOSPACE
                e.font == 0 -> Typeface.DEFAULT
                else -> runCatching { ResourcesCompat.getFont(context, e.font) }.getOrNull() ?: Typeface.DEFAULT
            }

            val sample = TextView(context).apply {
                text = context.getString(R.string.font_preview_sample)
                setTextColor(onSurface)
                textSize = 19f
                typeface = face
            }
            val label = TextView(context).apply {
                text = title
                setTextColor(onSurfaceVar)
                textSize = 13f
                setPadding(0, px(4), 0, 0)
            }
            val selectionLabel = TextView(context).apply {
                text = if (selected) context.getString(R.string.font_picker_active) else ""
                setTextColor(selectedRing)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.END
                setPadding(px(8), px(4), 0, 0)
                visibility = if (selected) TextView.VISIBLE else TextView.GONE
            }
            val footer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(label, LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                ))
                addView(selectionLabel)
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
                addView(footer)
                isClickable = true
                isFocusable = true
                isSelected = selected
                contentDescription = if (selected) {
                    context.getString(R.string.font_picker_active_description, title)
                } else {
                    title
                }
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
                .showWithStyledButtons(compact = false)
    }
}
