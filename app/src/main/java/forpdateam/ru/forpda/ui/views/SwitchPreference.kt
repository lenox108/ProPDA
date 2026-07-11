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
        (holder.itemView.findViewById<View>(R.id.switchWidget) as? com.google.android.material.materialswitch.MaterialSwitch)?.let { sw ->
            sw.alpha = alpha
            // androidx.preference can't resolve ?attr/* inside a ColorStateList at inflation, and the widget
            // context here IS the themed settings context — so resolve the palette-aware colours at bind time.
            //
            // ON colour comes from colorAccent, NOT colorPrimary: in the neutral «System style» theme
            // colorPrimary is literally #FFFFFF (white) and in reading palettes it's a pale surface tone
            // (sepia = #FFF3D7), so a colorPrimary track was invisible. colorAccent is the real accent in every
            // mode — neutral grey by default, the chosen palette/Material You colour otherwise, a vivid brown
            // for sepia — so the ON state always shows colour (user: «у переключателей практически нет цвета»).
            val accent = com.google.android.material.color.MaterialColors.getColor(
                    sw, androidx.appcompat.R.attr.colorAccent)
            val surface = com.google.android.material.color.MaterialColors.getColor(
                    sw, com.google.android.material.R.attr.colorSurface)
            val onSurface = com.google.android.material.color.MaterialColors.getColor(
                    sw, com.google.android.material.R.attr.colorOnSurface)
            // OFF track/thumb: opaque greys blended over the actual card surface so they stay clearly visible
            // on light AND dark palettes (a low-alpha grey vanished on pale sepia/white cards).
            val offTrack = androidx.core.graphics.ColorUtils.blendARGB(surface, onSurface, 0.26f)
            val offThumb = androidx.core.graphics.ColorUtils.blendARGB(surface, onSurface, 0.55f)
            // ON thumb: black/white by accent luminance so it always contrasts with the accent track.
            val onThumb = if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5)
                0xFF1B1B1B.toInt() else 0xFFFFFFFF.toInt()
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(
                    accent,   // ON  → accent track (opaque)
                    offTrack, // OFF → visible neutral grey
            ))
            sw.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(
                    onThumb,  // ON  → contrast thumb on the accent track
                    offThumb, // OFF → darker grey thumb, clearly reads as «off»
            ))
            // Drop the M3 track outline: our track is already a filled, opaque colour in both states, and the
            // default outline (colorOutline) is redundant / muddies the OFF grey.
            sw.trackDecorationTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT)
        }
    }

    companion object {
        private const val DISABLED_ALPHA = 0.45f
    }
}
