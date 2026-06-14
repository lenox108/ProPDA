package com.robohorse.pagerbullet

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemViewPagerBinding

/**
 * Вендоринг robohorse/PagerBullet (артефакт с JCenter больше не резолвится).
 */
class PagerBullet : FrameLayout {

    companion object {
        private const val DIGIT_PATTERN = "[^0-9.]"
        private const val DEFAULT_INDICATOR_OFFSET_VALUE = 20

        @JvmStatic
        fun wrapTintDrawable(sourceDrawable: Drawable, color: Int): Drawable {
            if (color != 0) {
                val wrapDrawable = DrawableCompat.wrap(sourceDrawable)
                DrawableCompat.setTint(wrapDrawable, color)
                wrapDrawable.setBounds(0, 0, wrapDrawable.intrinsicWidth, wrapDrawable.intrinsicHeight)
                return wrapDrawable
            }
            return sourceDrawable
        }
    }

    private var offset = DEFAULT_INDICATOR_OFFSET_VALUE
    private lateinit var viewPager: ViewPager
    private lateinit var textIndicator: TextView
    private lateinit var layoutIndicator: LinearLayout
    private lateinit var indicatorContainer: View
    private var activeColorTint: Int = 0
    private var inactiveColorTint: Int = 0

    constructor(context: Context) : super(context) { init(context) }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context); setAttributes(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context); setAttributes(context, attrs)
    }

    private fun setAttributes(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PagerBullet)
        var heightValue = typedArray.getString(R.styleable.PagerBullet_panelHeightInDp)
        if (heightValue != null) {
            heightValue = heightValue.replace(Regex(DIGIT_PATTERN), "")
            val height = heightValue.toFloat()
            val params = indicatorContainer.layoutParams as LayoutParams
            params.height = Math.round(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, height, resources.displayMetrics
                )
            )
            indicatorContainer.requestLayout()
        }
        typedArray.recycle()
    }

    fun setIndicatorTintColorScheme(activeColorTint: Int, inactiveColorTint: Int) {
        this.activeColorTint = activeColorTint
        this.inactiveColorTint = inactiveColorTint
        invalidateBullets()
    }

    fun setTextSeparatorOffset(offset: Int) { this.offset = offset }

    fun setAdapter(adapter: PagerAdapter) {
        viewPager.adapter = adapter
        invalidateBullets(adapter)
    }

    fun setCurrentItem(position: Int) { setCurrentItem(position, true) }

    fun setCurrentItem(position: Int, smoothScroll: Boolean) {
        viewPager.setCurrentItem(position, smoothScroll)
        setIndicatorItem(position)
    }

    fun getCurrentItem(): Int = viewPager.currentItem

    fun getViewPager(): ViewPager = viewPager

    fun addOnPageChangeListener(onPageChangeListener: ViewPager.OnPageChangeListener) {
        viewPager.addOnPageChangeListener(onPageChangeListener)
    }

    fun invalidateBullets() {
        val adapter = viewPager.adapter
        if (adapter != null) invalidateBullets(adapter)
    }

    fun invalidateBullets(adapter: PagerAdapter) {
        val hasSep = hasSeparator()
        textIndicator.visibility = if (hasSep) VISIBLE else INVISIBLE
        layoutIndicator.visibility = if (hasSep) INVISIBLE else VISIBLE
        if (!hasSep) initIndicator(adapter.count)
        setIndicatorItem(viewPager.currentItem)
    }

    fun setIndicatorVisibility(visibility: Boolean) {
        indicatorContainer.visibility = if (visibility) VISIBLE else INVISIBLE
    }

    private fun init(context: Context) {
        val binding = ItemViewPagerBinding.inflate(LayoutInflater.from(context), this, true)
        indicatorContainer = binding.root.findViewById(R.id.pagerBulletIndicatorContainer)
        textIndicator = binding.root.findViewById(R.id.pagerBulletIndicatorText)
        layoutIndicator = binding.root.findViewById(R.id.pagerBulletIndicator)
        viewPager = binding.viewPagerBullet
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) { setIndicatorItem(position) }
            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

    private fun initIndicator(count: Int) {
        layoutIndicator.removeAllViews()
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val margin = Math.round(context.resources.getDimension(R.dimen.pager_bullet_indicator_dot_margin))
        params.setMargins(margin, 0, margin, 0)
        val drawableInactive = ContextCompat.getDrawable(context, R.drawable.inactive_dot)
        for (i in 0 until count) {
            val imageView = ImageView(context)
            imageView.setImageDrawable(drawableInactive)
            layoutIndicator.addView(imageView, params)
        }
    }

    private fun setIndicatorItem(index: Int) {
        if (!hasSeparator()) setItemBullet(index) else setItemText(index)
    }

    private fun hasSeparator(): Boolean {
        val adapter = viewPager.adapter
        return adapter != null && adapter.count > offset
    }

    private fun setItemText(index: Int) {
        val adapter = viewPager.adapter
        if (adapter != null) {
            val count = adapter.count
            textIndicator.text = String.format(
                context.getString(R.string.pager_bullet_separator),
                (index + 1).toString(), count.toString()
            )
        }
    }

    private fun setItemBullet(selectedPosition: Int) {
        var drawableInactive = ContextCompat.getDrawable(context, R.drawable.inactive_dot)!!
        drawableInactive = wrapTintDrawable(drawableInactive, inactiveColorTint)
        var drawableActive = ContextCompat.getDrawable(context, R.drawable.active_dot)!!
        drawableActive = wrapTintDrawable(drawableActive, activeColorTint)
        val indicatorItemsCount = layoutIndicator.childCount
        for (position in 0 until indicatorItemsCount) {
            val imageView = layoutIndicator.getChildAt(position) as ImageView
            imageView.setImageDrawable(if (position != selectedPosition) drawableInactive else drawableActive)
        }
    }
}
