package forpdateam.ru.forpda.ui.views.dialog

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences.Main.ThemeMode
import forpdateam.ru.forpda.common.getColorFromAttr
import kotlin.math.roundToInt

/**
 * Диалог выбора «Темы приложения» (ThemeMode) с ВИЗУАЛЬНЫМ ПРЕВЬЮ: каждый режим
 * показан панелью(ями) реальной подложки — светлая / тёмная / чёрная AMOLED, а
 * системные режимы двумя панелями сразу («светлая | тёмная»), чтобы «следует за
 * системой» читалось наглядно. Тап по карточке применяет режим (тот же путь
 * рестарта, что делал ListPreference).
 */
object ThemeModePickerDialog {

    // Абсолютные подложки/текст режимов (не зависят от текущей темы — светлая
    // всегда светлая). Нейтрали M3.
    private const val LIGHT_BG = 0xFFFFFFFF.toInt()
    private const val LIGHT_TX = 0xFF1A1C1E.toInt()
    private const val DARK_BG = 0xFF1B1B1F.toInt()
    private const val DARK_TX = 0xFFE3E2E6.toInt()
    private const val AMOLED_BG = 0xFF000000.toInt()
    private const val AMOLED_TX = 0xFFE3E2E6.toInt()

    private data class Pane(val bg: Int, val tx: Int)

    private data class Entry(
            val mode: ThemeMode,
            @StringRes val title: Int,
            @StringRes val subtitle: Int,
            val panes: List<Pane>,
            // Absolute card background/text for a SINGLE-mode entry, so the whole «Светлая» card is light,
            // «Тёмная» dark, AMOLED black — a mini-mockup of the mode, independent of the CURRENT app theme
            // (fixes «светлая тема выглядит тёмной»). null → the neutral themed surface (the SYSTEM entries,
            // which show two panes light|dark and shouldn't commit to one background).
            val card: Pane?,
    )

    private val light = Pane(LIGHT_BG, LIGHT_TX)
    private val dark = Pane(DARK_BG, DARK_TX)
    private val amoled = Pane(AMOLED_BG, AMOLED_TX)

    private val entries = listOf(
            Entry(ThemeMode.SYSTEM, R.string.pref_value_theme_mode_system,
                    R.string.pref_summary_theme_mode_system, listOf(light, dark), card = null),
            Entry(ThemeMode.LIGHT, R.string.pref_value_theme_mode_light,
                    R.string.pref_summary_theme_mode_light, listOf(light), card = light),
            Entry(ThemeMode.DARK, R.string.pref_value_theme_mode_dark,
                    R.string.pref_summary_theme_mode_dark, listOf(dark), card = dark),
            Entry(ThemeMode.AMOLED, R.string.pref_value_theme_mode_amoled,
                    R.string.pref_summary_theme_mode_amoled, listOf(amoled), card = amoled),
            Entry(ThemeMode.SYSTEM_AMOLED, R.string.pref_value_theme_mode_system_amoled,
                    R.string.pref_summary_theme_mode_system_amoled, listOf(light, amoled), card = null),
    )

    fun show(context: Context, current: ThemeMode, onPick: (ThemeMode) -> Unit) {
        val dp = context.resources.displayMetrics.density
        fun px(v: Int) = (v * dp).roundToInt()

        val outline = context.getColorFromAttr(com.google.android.material.R.attr.colorOutline)
        val selectedRing = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val accent = context.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary)
        val onSurface = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val onSurfaceVar = context.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)

        lateinit var dialog: androidx.appcompat.app.AlertDialog

        fun makePane(p: Pane): View = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = px(10).toFloat(); setColor(p.bg)
                // Subtle outline so the preview panel stays delineated even when the card behind it is the
                // same colour (single-mode cards below paint their own background with the pane's colour).
                setStroke(px(1), androidx.core.graphics.ColorUtils.setAlphaComponent(p.tx, 38))
            }
            addView(TextView(context).apply {
                text = "Aa"
                setTextColor(p.tx)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER)
            })
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(accent) }
                layoutParams = FrameLayout.LayoutParams(px(10), px(10),
                        Gravity.TOP or Gravity.END).apply { topMargin = px(8); marginEnd = px(8) }
            })
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(4), px(16), px(8))
        }

        entries.forEach { e ->
            val selected = e.mode == current

            val paneRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                e.panes.forEachIndexed { i, p ->
                    if (i > 0) addView(View(context), LinearLayout.LayoutParams(px(8), 1))
                    addView(makePane(p), LinearLayout.LayoutParams(0, px(56), 1f))
                }
            }
            // Single-mode entries paint the whole card in their OWN background/text; SYSTEM entries stay on
            // the neutral themed surface (they show two panes, so committing to one background would mislead).
            val cardBg = e.card?.bg ?: context.getColorFromAttr(
                    com.google.android.material.R.attr.colorSurfaceContainerLow)
            val cardTitleColor = e.card?.tx ?: onSurface
            val cardSubtitleColor = e.card?.let {
                androidx.core.graphics.ColorUtils.setAlphaComponent(it.tx, 0xB3)
            } ?: onSurfaceVar
            val cardOutline = e.card?.let {
                androidx.core.graphics.ColorUtils.setAlphaComponent(it.tx, 40)
            } ?: outline
            val title = TextView(context).apply {
                text = context.getString(e.title)
                setTextColor(cardTitleColor)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, px(10), 0, 0)
            }
            val subtitle = TextView(context).apply {
                text = context.getString(e.subtitle)
                setTextColor(cardSubtitleColor)
                textSize = 12f
                setPadding(0, px(2), 0, 0)
            }
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(14), px(14), px(14), px(14))
                background = GradientDrawable().apply {
                    cornerRadius = px(16).toFloat()
                    setColor(cardBg)
                    setStroke(if (selected) px(3) else px(1), if (selected) selectedRing else cardOutline)
                }
                addView(paneRow)
                addView(title)
                addView(subtitle)
                isClickable = true
                setOnClickListener { dialog.dismiss(); onPick(e.mode) }
            }
            list.addView(card, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(6); bottomMargin = px(6)
            })
        }

        dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_theme_mode)
                .setView(ScrollView(context).apply { addView(list) })
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }
}
