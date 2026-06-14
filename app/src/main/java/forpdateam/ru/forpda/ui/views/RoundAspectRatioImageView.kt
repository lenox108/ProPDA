package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import com.makeramen.roundedimageview.RoundedImageView
import forpdateam.ru.forpda.R

/**
 * Created by radiationx on 26.08.17.
 */
class RoundAspectRatioImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RoundedImageView(context, attrs, defStyle) {
    private var aspectRatio: Float = 1.0f

    init {
        if (attrs != null) {
            init(attrs)
        }
    }

    private fun init(attrs: AttributeSet) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AspectRatio)
        aspectRatio = typedArray.getFloat(R.styleable.AspectRatio_aspectRatio, 1f)
        typedArray.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val height = Math.min(measuredWidth * aspectRatio, maxHeight.toFloat())
        setMeasuredDimension(widthMeasureSpec, height.toInt())
    }

    fun setAspectRatio(aspectRatio: Float) {
        this.aspectRatio = aspectRatio
        requestLayout()
    }
}
