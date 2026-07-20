package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.R as MaterialR
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
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
        // AppCompatResources грузит и vector, и bitmap. Раньше был getVecDrawable с
        // require(vector) — падал IllegalArgumentException на растровых заглушках
        // (напр. ic_notify_mention = PNG в состоянии «нет тем»). Тинт ниже работает
        // и на растре, поэтому вектор здесь не обязателен.
        binding.funnyImage.setImageDrawable(AppCompatResources.getDrawable(context, resId))
        // Muted M3 empty-state illustration: следует за палитрой/акцентом.
        binding.funnyImage.imageTintList = ColorStateList.valueOf(
                context.getColorFromAttr(MaterialR.attr.colorOnSurfaceVariant)
        )
    }

    fun setTitle(@StringRes resId: Int): FunnyContent = apply {
        binding.funnyTitle.setText(resId)
        binding.funnyTitle.visibility = VISIBLE
    }

    fun setDesc(@StringRes resId: Int): FunnyContent = apply {
        binding.funnyDesc.setText(resId)
        binding.funnyDesc.visibility = VISIBLE
    }

    /** Описание произвольным текстом — например, реальная причина ошибки из ErrorHandler. */
    fun setDesc(text: CharSequence): FunnyContent = apply {
        binding.funnyDesc.text = text
        binding.funnyDesc.visibility = VISIBLE
    }

    fun addAction(@StringRes textResId: Int, listener: View.OnClickListener): FunnyContent = apply {
        val button = MaterialButton(context, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
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
