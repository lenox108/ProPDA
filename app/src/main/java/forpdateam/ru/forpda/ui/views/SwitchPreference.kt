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
        holder.itemView.findViewById<View>(R.id.switchWidget)?.alpha = alpha
    }

    companion object {
        private const val DISABLED_ALPHA = 0.45f
    }
}
