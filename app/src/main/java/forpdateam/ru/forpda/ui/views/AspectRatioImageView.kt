package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import forpdateam.ru.forpda.R

/**
 * Created by radiationx on 26.08.17.
 */
class AspectRatioImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {
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
        val widthMode = View.MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = View.MeasureSpec.getSize(widthMeasureSpec)
        val width = when {
            widthMode == View.MeasureSpec.EXACTLY -> widthSize
            widthMode == View.MeasureSpec.AT_MOST && widthSize > 0 -> widthSize
            measuredWidth > 0 -> measuredWidth
            else -> widthSize
        }
        val height = Math.min(width * aspectRatio, maxHeight.toFloat())
        setMeasuredDimension(width, height.toInt())
    }

    fun setAspectRatio(aspectRatio: Float) {
        this.aspectRatio = aspectRatio
        requestLayout()
    }
}
