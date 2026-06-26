package forpdateam.ru.forpda.ui.views

/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.animation.Interpolator
import androidx.core.view.ViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.viewpager.widget.ViewPager
import forpdateam.ru.forpda.R
import java.util.Arrays


/**
 * Created by David Pacioianu on 11/11/15.
 */
class InkPageIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle), ViewPager.OnPageChangeListener,
    View.OnAttachStateChangeListener {

    // defaults
    companion object {
        private const val DEFAULT_DOT_SIZE = 8                      // dp
        private const val DEFAULT_GAP = 12                          // dp
        private const val DEFAULT_ANIM_DURATION = 400               // ms
        private const val DEFAULT_UNSELECTED_COLOUR = 0x80ffffff.toInt()    // 50% white
        private const val DEFAULT_SELECTED_COLOUR = 0xffffffff.toInt()      // 100% white

        // constants
        private const val INVALID_FRACTION = -1f
        private const val MINIMAL_REVEAL = 0.00001f
    }

    // configurable attributes
    private var dotDiameter: Int = 0
    private var gap: Int = 0
    private var animDuration: Long = 0
    private var unselectedColour: Int = 0
    private var selectedColour: Int = 0

    // derived from attributes
    private var dotRadius: Float = 0f
    private var halfDotRadius: Float = 0f
    private var animHalfDuration: Long = 0
    private var dotTopY: Float = 0f
    private var dotCenterY: Float = 0f
    private var dotBottomY: Float = 0f

    // ViewPager
    private var viewPager: ViewPager? = null

    // state
    private var pageCount: Int = 0
    private var currentPage: Int = 0
    private var previousPage: Int = 0
    private var selectedDotX: Float = 0f
    private var selectedDotInPosition: Boolean = true
    private var dotCenterX: FloatArray? = null
    private var joiningFractions: FloatArray = FloatArray(0)
    private var retreatingJoinX1: Float = INVALID_FRACTION
    private var retreatingJoinX2: Float = INVALID_FRACTION
    private var dotRevealFractions: FloatArray = FloatArray(0)
    private var isAttachedToWindow: Boolean = false
    private var pageChanging: Boolean = false

    // drawing
    private val unselectedPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var combinedUnselectedPath: Path = Path()
    private val unselectedDotPath: Path = Path()
    private val unselectedDotLeftPath: Path = Path()
    private val unselectedDotRightPath: Path = Path()
    private val rectF: RectF = RectF()

    // animation
    private var moveAnimation: ValueAnimator? = null
    private var joiningAnimationSet: AnimatorSet? = null
    private var retreatAnimation: PendingRetreatAnimator? = null
    private var revealAnimations: Array<PendingRevealAnimator?>? = null
    private val interpolator: Interpolator = FastOutSlowInInterpolator()

    // working values for beziers
    internal var endX1: Float = 0f
    internal var endY1: Float = 0f
    internal var endX2: Float = 0f
    internal var endY2: Float = 0f
    internal var controlX1: Float = 0f
    internal var controlY1: Float = 0f
    internal var controlX2: Float = 0f
    internal var controlY2: Float = 0f

    init {
        val density = context.resources.displayMetrics.density.toInt()

        val a = getContext().obtainStyledAttributes(attrs, R.styleable.InkPageIndicator, defStyle, 0)

        dotDiameter = a.getDimensionPixelSize(R.styleable.InkPageIndicator_dotDiameter,
            DEFAULT_DOT_SIZE * density)
        dotRadius = dotDiameter / 2f
        halfDotRadius = dotRadius / 2f
        gap = a.getDimensionPixelSize(R.styleable.InkPageIndicator_dotGap,
            DEFAULT_GAP * density)
        animDuration = a.getInteger(R.styleable.InkPageIndicator_animationDuration,
            DEFAULT_ANIM_DURATION).toLong()
        animHalfDuration = animDuration / 2
        unselectedColour = a.getColor(R.styleable.InkPageIndicator_pageIndicatorColor,
            DEFAULT_UNSELECTED_COLOUR)
        selectedColour = a.getColor(R.styleable.InkPageIndicator_currentPageIndicatorColor,
            DEFAULT_SELECTED_COLOUR)

        a.recycle()

        unselectedPaint.color = unselectedColour
        selectedPaint.color = selectedColour

        addOnAttachStateChangeListener(this)
    }

    fun setViewPager(viewPager: ViewPager) {
        this.viewPager = viewPager
        viewPager.addOnPageChangeListener(this)
        setPageCount(viewPager.adapter?.count ?: 0)
        viewPager.adapter?.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                setPageCount(this@InkPageIndicator.viewPager?.adapter?.count ?: 0)
            }
        })
        setCurrentPageImmediate()
    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        if (isAttachedToWindow) {
            var fraction = positionOffset
            val currentPosition = if (pageChanging) previousPage else currentPage
            var leftDotPosition = position
            if (currentPosition != position) {
                fraction = 1f - positionOffset
                if (fraction == 1f) {
                    leftDotPosition = Math.min(currentPosition, position)
                }
            }
            setJoiningFraction(leftDotPosition, fraction)
        }
    }

    override fun onPageSelected(position: Int) {
        if (isAttachedToWindow) {
            setSelectedPage(position)
        } else {
            setCurrentPageImmediate()
        }
    }

    override fun onPageScrollStateChanged(state: Int) {
        // nothing to do
    }

    private fun setPageCount(pages: Int) {
        pageCount = pages
        resetState()
        requestLayout()
    }

    private fun calculateDotPositions(width: Int, height: Int) {
        val left = paddingLeft
        val top = paddingTop
        val right = width - paddingRight
        val bottom = height - paddingBottom

        val requiredWidth = getRequiredWidth()
        val startLeft = left + (right - left - requiredWidth) / 2f + dotRadius

        val centers = FloatArray(pageCount)
        for (i in 0 until pageCount) {
            centers[i] = startLeft + i * (dotDiameter + gap)
        }
        dotCenterX = centers
        dotTopY = top.toFloat()
        dotCenterY = top + dotRadius
        dotBottomY = (top + dotDiameter).toFloat()

        setCurrentPageImmediate()
    }

    private fun setCurrentPageImmediate() {
        currentPage = viewPager?.currentItem ?: 0
        val centers = dotCenterX
        val anim = moveAnimation
        if (centers != null && centers.isNotEmpty() && (anim == null || !anim.isStarted)
            && currentPage in centers.indices
        ) {
            selectedDotX = centers[currentPage]
        }
    }

    private fun resetState() {
        joiningFractions = FloatArray(pageCount - 1)
        Arrays.fill(joiningFractions, 0f)
        dotRevealFractions = FloatArray(pageCount)
        Arrays.fill(dotRevealFractions, 0f)
        retreatingJoinX1 = INVALID_FRACTION
        retreatingJoinX2 = INVALID_FRACTION
        selectedDotInPosition = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = getDesiredHeight()
        val height: Int = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> Math.min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }

        val desiredWidth = getDesiredWidth()
        val width: Int = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> Math.min(desiredWidth, MeasureSpec.getSize(widthMeasureSpec))
            else -> desiredWidth
        }
        setMeasuredDimension(width, height)
        calculateDotPositions(width, height)
    }

    private fun getDesiredHeight(): Int = paddingTop + dotDiameter + paddingBottom

    private fun getRequiredWidth(): Int = pageCount * dotDiameter + (pageCount - 1) * gap

    private fun getDesiredWidth(): Int = paddingLeft + getRequiredWidth() + paddingRight

    override fun onViewAttachedToWindow(view: View) {
        isAttachedToWindow = true
    }

    override fun onViewDetachedFromWindow(view: View) {
        isAttachedToWindow = false
    }

    override fun onDraw(canvas: Canvas) {
        if (viewPager == null || pageCount == 0) return
        // dotCenterX is populated in calculateDotPositions() during onMeasure(); guard
        // against a draw pass that races ahead of measurement to avoid an NPE.
        if (dotCenterX == null) return
        drawUnselected(canvas)
        drawSelected(canvas)
    }

    private fun drawUnselected(canvas: Canvas) {
        val centers = dotCenterX ?: return
        combinedUnselectedPath.rewind()

        for (page in 0 until pageCount) {
            val nextXIndex = if (page == pageCount - 1) page else page + 1
            val unselectedPath = getUnselectedPath(page,
                centers[page],
                centers[nextXIndex],
                if (page == pageCount - 1) INVALID_FRACTION else joiningFractions[page],
                dotRevealFractions[page])
            combinedUnselectedPath.addPath(unselectedPath)
        }
        if (retreatingJoinX1 != INVALID_FRACTION) {
            val retreatingJoinPath = getRetreatingJoinPath()
            combinedUnselectedPath.addPath(retreatingJoinPath)
        }

        canvas.drawPath(combinedUnselectedPath, unselectedPaint)
    }

    private fun getUnselectedPath(
        page: Int,
        centerX: Float,
        nextCenterX: Float,
        joiningFraction: Float,
        dotRevealFraction: Float
    ): Path {
        unselectedDotPath.rewind()

        if ((joiningFraction == 0f || joiningFraction == INVALID_FRACTION)
            && dotRevealFraction == 0f
            && !(page == currentPage && selectedDotInPosition)
        ) {
            // case #1 – At rest
            val centerForPage = dotCenterX?.getOrNull(page) ?: centerX
            unselectedDotPath.addCircle(centerForPage, dotCenterY, dotRadius, Path.Direction.CW)
        }

        if (joiningFraction > 0f && joiningFraction <= 0.5f
            && retreatingJoinX1 == INVALID_FRACTION
        ) {
            // case #2 – Joining neighbour, still separate
            unselectedDotLeftPath.rewind()
            unselectedDotLeftPath.moveTo(centerX, dotBottomY)
            rectF.set(centerX - dotRadius, dotTopY, centerX + dotRadius, dotBottomY)
            unselectedDotLeftPath.arcTo(rectF, 90f, 180f, true)

            endX1 = centerX + dotRadius + joiningFraction * gap
            endY1 = dotCenterY
            controlX1 = centerX + halfDotRadius
            controlY1 = dotTopY
            controlX2 = endX1
            controlY2 = endY1 - halfDotRadius
            unselectedDotLeftPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX1, endY1)

            endX2 = centerX
            endY2 = dotBottomY
            controlX1 = endX1
            controlY1 = endY1 + halfDotRadius
            controlX2 = centerX + halfDotRadius
            controlY2 = dotBottomY
            unselectedDotLeftPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX2, endY2)

            unselectedDotPath.addPath(unselectedDotLeftPath)

            unselectedDotRightPath.rewind()
            unselectedDotRightPath.moveTo(nextCenterX, dotBottomY)
            rectF.set(nextCenterX - dotRadius, dotTopY, nextCenterX + dotRadius, dotBottomY)
            unselectedDotRightPath.arcTo(rectF, 90f, -180f, true)

            endX1 = nextCenterX - dotRadius - joiningFraction * gap
            endY1 = dotCenterY
            controlX1 = nextCenterX - halfDotRadius
            controlY1 = dotTopY
            controlX2 = endX1
            controlY2 = endY1 - halfDotRadius
            unselectedDotRightPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX1, endY1)

            endX2 = nextCenterX
            endY2 = dotBottomY
            controlX1 = endX1
            controlY1 = endY1 + halfDotRadius
            controlX2 = endX2 - halfDotRadius
            controlY2 = dotBottomY
            unselectedDotRightPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX2, endY2)
            unselectedDotPath.addPath(unselectedDotRightPath)
        }

        if (joiningFraction > 0.5f && joiningFraction < 1f
            && retreatingJoinX1 == INVALID_FRACTION
        ) {
            // case #3 – Joining neighbour, combined curved
            val adjustedFraction = (joiningFraction - 0.2f) * 1.25f

            unselectedDotPath.moveTo(centerX, dotBottomY)
            rectF.set(centerX - dotRadius, dotTopY, centerX + dotRadius, dotBottomY)
            unselectedDotPath.arcTo(rectF, 90f, 180f, true)

            endX1 = centerX + dotRadius + gap / 2f
            endY1 = dotCenterY - adjustedFraction * dotRadius
            controlX1 = endX1 - adjustedFraction * dotRadius
            controlY1 = dotTopY
            controlX2 = endX1 - (1 - adjustedFraction) * dotRadius
            controlY2 = endY1
            unselectedDotPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX1, endY1)

            endX2 = nextCenterX
            endY2 = dotTopY
            controlX1 = endX1 + (1 - adjustedFraction) * dotRadius
            controlY1 = endY1
            controlX2 = endX1 + adjustedFraction * dotRadius
            controlY2 = dotTopY
            unselectedDotPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX2, endY2)

            rectF.set(nextCenterX - dotRadius, dotTopY, nextCenterX + dotRadius, dotBottomY)
            unselectedDotPath.arcTo(rectF, 270f, 180f, true)

            endY1 = dotCenterY + adjustedFraction * dotRadius
            controlX1 = endX1 + adjustedFraction * dotRadius
            controlY1 = dotBottomY
            controlX2 = endX1 + (1 - adjustedFraction) * dotRadius
            controlY2 = endY1
            unselectedDotPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX1, endY1)

            endX2 = centerX
            endY2 = dotBottomY
            controlX1 = endX1 - (1 - adjustedFraction) * dotRadius
            controlY1 = endY1
            controlX2 = endX1 - adjustedFraction * dotRadius
            controlY2 = endY2
            unselectedDotPath.cubicTo(controlX1, controlY1, controlX2, controlY2, endX2, endY2)
        }

        if (joiningFraction == 1f && retreatingJoinX1 == INVALID_FRACTION) {
            // case #4 Joining neighbour, combined straight
            rectF.set(centerX - dotRadius, dotTopY, nextCenterX + dotRadius, dotBottomY)
            unselectedDotPath.addRoundRect(rectF, dotRadius, dotRadius, Path.Direction.CW)
        }

        if (dotRevealFraction > MINIMAL_REVEAL) {
            // case #6 – previously hidden dot revealing
            unselectedDotPath.addCircle(centerX, dotCenterY, dotRevealFraction * dotRadius, Path.Direction.CW)
        }

        return unselectedDotPath
    }

    private fun getRetreatingJoinPath(): Path {
        unselectedDotPath.rewind()
        rectF.set(retreatingJoinX1, dotTopY, retreatingJoinX2, dotBottomY)
        unselectedDotPath.addRoundRect(rectF, dotRadius, dotRadius, Path.Direction.CW)
        return unselectedDotPath
    }

    private fun drawSelected(canvas: Canvas) {
        canvas.drawCircle(selectedDotX, dotCenterY, dotRadius, selectedPaint)
    }

    private fun setSelectedPage(now: Int) {
        if (now == currentPage) return
        // Selection animations rely on measured dot positions; bail out until measured.
        val centers = dotCenterX ?: return
        if (now !in centers.indices) return

        pageChanging = true
        previousPage = currentPage
        currentPage = now
        val steps = Math.abs(now - previousPage)

        if (steps > 1) {
            if (now > previousPage) {
                for (i in 0 until steps) {
                    setJoiningFraction(previousPage + i, 1f)
                }
            } else {
                for (i in -1 downTo -steps + 1) {
                    setJoiningFraction(previousPage + i, 1f)
                }
            }
        }

        moveAnimation = createMoveSelectedAnimator(centers[now], previousPage, now, steps)
        moveAnimation!!.start()
    }

    private fun createMoveSelectedAnimator(moveTo: Float, was: Int, now: Int, steps: Int): ValueAnimator {
        val moveSelected = ValueAnimator.ofFloat(selectedDotX, moveTo)

        retreatAnimation = PendingRetreatAnimator(was, now, steps,
            if (now > was)
                RightwardStartPredicate(moveTo - (moveTo - selectedDotX) * 0.25f)
            else
                LeftwardStartPredicate(moveTo + (selectedDotX - moveTo) * 0.25f))
        retreatAnimation!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                resetState()
                pageChanging = false
            }
        })
        moveSelected.addUpdateListener { valueAnimator ->
            selectedDotX = valueAnimator.animatedValue as Float
            retreatAnimation!!.startIfNecessary(selectedDotX)
            ViewCompat.postInvalidateOnAnimation(this@InkPageIndicator)
        }
        moveSelected.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                selectedDotInPosition = false
            }

            override fun onAnimationEnd(animation: Animator) {
                selectedDotInPosition = true
            }
        })
        moveSelected.startDelay = if (selectedDotInPosition) animDuration / 4 else 0
        moveSelected.duration = animDuration * 3 / 4
        moveSelected.interpolator = interpolator
        return moveSelected
    }

    private fun setJoiningFraction(leftDot: Int, fraction: Float) {
        if (leftDot < joiningFractions.size) {
            joiningFractions[leftDot] = fraction
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    private fun clearJoiningFractions() {
        Arrays.fill(joiningFractions, 0f)
        ViewCompat.postInvalidateOnAnimation(this)
    }

    private fun setDotRevealFraction(dot: Int, fraction: Float) {
        if (dot < dotRevealFractions.size) {
            dotRevealFractions[dot] = fraction
        }
        ViewCompat.postInvalidateOnAnimation(this)
    }

    private fun cancelJoiningAnimations() {
        if (joiningAnimationSet != null && joiningAnimationSet!!.isRunning) {
            joiningAnimationSet!!.cancel()
        }
    }

    /**
     * A [ValueAnimator] that starts once a given predicate returns true.
     */
    abstract inner class PendingStartAnimator(predicate: StartPredicate) : ValueAnimator() {
        protected var hasStarted: Boolean = false
        protected var predicate: StartPredicate = predicate

        fun startIfNecessary(currentValue: Float) {
            if (!hasStarted && predicate.shouldStart(currentValue)) {
                start()
                hasStarted = true
            }
        }
    }

    /**
     * An Animator that shows and then shrinks a retreating join between the previous and newly
     * selected pages.
     */
    inner class PendingRetreatAnimator(was: Int, now: Int, steps: Int, predicate: StartPredicate) : PendingStartAnimator(predicate) {
        init {
            duration = animHalfDuration
            setInterpolator(interpolator)

            // Positions are guaranteed non-null here because this animator is only
            // constructed from setSelectedPage(), which bails out when dotCenterX is null.
            val centers = dotCenterX ?: FloatArray(pageCount)

            val initialX1 = if (now > was) Math.min(centers[was], selectedDotX) - dotRadius
            else centers[now] - dotRadius
            val finalX1 = centers[now] - dotRadius
            val initialX2 = if (now > was) centers[now] + dotRadius
            else Math.max(centers[was], selectedDotX) + dotRadius
            val finalX2 = centers[now] + dotRadius

            revealAnimations = arrayOfNulls(steps)
            val dotsToHide = IntArray(steps)
            if (initialX1 != finalX1) { // rightward retreat
                setFloatValues(initialX1, finalX1)
                for (i in 0 until steps) {
                    revealAnimations!![i] = PendingRevealAnimator(was + i,
                        RightwardStartPredicate(centers[was + i]))
                    dotsToHide[i] = was + i
                }
                addUpdateListener { valueAnimator ->
                    retreatingJoinX1 = valueAnimator.animatedValue as Float
                    ViewCompat.postInvalidateOnAnimation(this@InkPageIndicator)
                    for (pendingReveal in revealAnimations!!) {
                        pendingReveal?.startIfNecessary(retreatingJoinX1)
                    }
                }
            } else { // leftward retreat
                setFloatValues(initialX2, finalX2)
                for (i in 0 until steps) {
                    revealAnimations!![i] = PendingRevealAnimator(was - i,
                        LeftwardStartPredicate(centers[was - i]))
                    dotsToHide[i] = was - i
                }
                addUpdateListener { valueAnimator ->
                    retreatingJoinX2 = valueAnimator.animatedValue as Float
                    ViewCompat.postInvalidateOnAnimation(this@InkPageIndicator)
                    for (pendingReveal in revealAnimations!!) {
                        pendingReveal?.startIfNecessary(retreatingJoinX2)
                    }
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    cancelJoiningAnimations()
                    clearJoiningFractions()
                    for (dot in dotsToHide) {
                        setDotRevealFraction(dot, MINIMAL_REVEAL)
                    }
                    retreatingJoinX1 = initialX1
                    retreatingJoinX2 = initialX2
                    ViewCompat.postInvalidateOnAnimation(this@InkPageIndicator)
                }

                override fun onAnimationEnd(animation: Animator) {
                    retreatingJoinX1 = INVALID_FRACTION
                    retreatingJoinX2 = INVALID_FRACTION
                    ViewCompat.postInvalidateOnAnimation(this@InkPageIndicator)
                }
            })
        }
    }

    /**
     * An Animator that animates a given dot's revealFraction i.e. scales it up
     */
    inner class PendingRevealAnimator(private val dot: Int, predicate: StartPredicate) : PendingStartAnimator(predicate) {
        init {
            setFloatValues(MINIMAL_REVEAL, 1f)
            duration = animHalfDuration
            setInterpolator(interpolator)
            addUpdateListener { valueAnimator ->
                setDotRevealFraction(dot, valueAnimator.animatedValue as Float)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    setDotRevealFraction(dot, 0f)
                    ViewCompat.postInvalidateOnAnimation(this@InkPageIndicator)
                }
            })
        }
    }

    /**
     * A predicate used to start an animation when a test passes
     */
    abstract inner class StartPredicate(protected var thresholdValue: Float) {
        abstract fun shouldStart(currentValue: Float): Boolean
    }

    /**
     * A predicate used to start an animation when a given value is greater than a threshold
     */
    inner class RightwardStartPredicate(thresholdValue: Float) : StartPredicate(thresholdValue) {
        override fun shouldStart(currentValue: Float): Boolean = currentValue > thresholdValue
    }

    /**
     * A predicate used to start an animation then a given value is less than a threshold
     */
    inner class LeftwardStartPredicate(thresholdValue: Float) : StartPredicate(thresholdValue) {
        override fun shouldStart(currentValue: Float): Boolean = currentValue < thresholdValue
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)
        currentPage = savedState.currentPage
        requestLayout()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        if (superState == null) {
            return Bundle().apply { putInt("currentPage", currentPage) }
        }
        val savedState = SavedState(superState)
        savedState.currentPage = currentPage
        return savedState
    }

    internal class SavedState : BaseSavedState {
        var currentPage: Int = 0

        constructor(superState: Parcelable) : super(superState)

        private constructor(`in`: Parcel) : super(`in`) {
            currentPage = `in`.readInt()
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeInt(currentPage)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState = SavedState(`in`)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }
}
