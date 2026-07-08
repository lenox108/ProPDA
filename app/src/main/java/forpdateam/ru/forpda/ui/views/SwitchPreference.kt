package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.PreferenceViewHolder
import forpdateam.ru.forpda.R

/**
 * Created by radiationx on 26.07.17.
 */

/*
* Исправляет самопроизвольные переключения настроек в киткате.
* Пи*дец, да.
* */
class SwitchPreference : SwitchPreferenceCompat {
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context) : super(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val alpha = if (isEnabled) 1f else DISABLED_ALPHA
        holder.itemView.findViewById<View>(android.R.id.title)?.alpha = alpha
        holder.itemView.findViewById<View>(android.R.id.summary)?.alpha = alpha
        (holder.itemView.findViewById<View>(R.id.switchWidget) as? androidx.appcompat.widget.SwitchCompat)?.let { sw ->
            sw.alpha = alpha
            // The widget's XML trackTint is a LITERAL green (#4CAF50) because androidx.preference can't
            // resolve ?attr/* inside a ColorStateList at inflation. Here the view IS attached to the themed
            // settings context, so resolve the real accent (colorPrimary = the chosen palette accent, or the
            // Material You wallpaper colour) at bind time — otherwise every toggle stays green while the rest
            // of the UI follows the accent (user: «цвета путаются, особенно в Material You»).
            val accent = com.google.android.material.color.MaterialColors.getColor(
                    sw, androidx.appcompat.R.attr.colorPrimary)
            val onSurface = com.google.android.material.color.MaterialColors.getColor(
                    sw, com.google.android.material.R.attr.colorOnSurface)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(
                    accent,                                                              // ON  → accent track (full)
                    androidx.core.graphics.ColorUtils.setAlphaComponent(onSurface, 0x4D), // OFF → adaptive grey
            ))
        }
    }

    companion object {
        private const val DISABLED_ALPHA = 0.45f
    }
}
