package forpdateam.ru.forpda.ui.views

import forpdateam.ru.forpda.common.getVecDrawable
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.FunnyContentBinding

/**
 * View для отображения заглушек (funny content).
 * 
 * Улучшения в Kotlin-версии:
 * - lazy инициализация view
 * - apply/with для fluent interface
 */
class FunnyContent(context: Context) : RelativeLayout(context) {
    
    private val binding: FunnyContentBinding = FunnyContentBinding.inflate(LayoutInflater.from(context), this, true)

    init {
    }

    fun setImage(@DrawableRes resId: Int): FunnyContent = apply {
        binding.funnyImage.setImageDrawable(context.getVecDrawable(resId))
    }

    fun setTitle(@StringRes resId: Int): FunnyContent = apply {
        binding.funnyTitle.setText(resId)
        binding.funnyTitle.visibility = VISIBLE
    }

    fun setDesc(@StringRes resId: Int): FunnyContent = apply {
        binding.funnyDesc.setText(resId)
        binding.funnyDesc.visibility = VISIBLE
    }

    fun addAction(@StringRes textResId: Int, listener: View.OnClickListener): FunnyContent = apply {
        val button = Button(context).apply {
            setText(textResId)
            setOnClickListener(listener)
        }
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(BELOW, binding.funnyDesc.id)
            addRule(CENTER_HORIZONTAL)
            topMargin = resources.getDimensionPixelSize(R.dimen.dp24)
        }
        addView(button, params)
    }

    /*
    fun setTitle(text: String): FunnyContent = apply {
        title.text = text
        title.visibility = VISIBLE
    }

    fun setDesc(text: String): FunnyContent = apply {
        desc.text = text
        desc.visibility = VISIBLE
    }
    */
}
