package forpdateam.ru.forpda.ui.activities.imageviewer

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.annotation.IntDef
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs

/**
 * Created by radiationx on 24.05.17.
 */
class PullBackLayout : FrameLayout {

    companion object {
        const val DIRECTION_UP = 1
        const val DIRECTION_DOWN = 1 shl 1
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(value = [DIRECTION_UP, DIRECTION_DOWN], flag = true)
    annotation class Direction

    private val dragger: ViewDragHelper
    private val minimumFlingVelocity: Int

    @Direction
    private var direction: Int = DIRECTION_UP or DIRECTION_DOWN

    @Nullable
    private var callback: Callback? = null

    constructor(context: Context) : super(context) {
        dragger = ViewDragHelper.create(this, 1f / 8f, ViewDragCallback())
        minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        dragger = ViewDragHelper.create(this, 1f / 8f, ViewDragCallback())
        minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        dragger = ViewDragHelper.create(this, 1f / 8f, ViewDragCallback())
        minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    }

    @Direction
    fun getDirection(): Int = direction

    fun setDirection(@Direction direction: Int) { this.direction = direction }

    fun setCallback(@Nullable callback: Callback?) { this.callback = callback }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try { dragger.shouldInterceptTouchEvent(ev) } catch (e: Exception) { false }
    }

    override fun onTouchEvent(@NonNull event: MotionEvent): Boolean {
        return try { dragger.processTouchEvent(event); true } catch (e: Exception) { false }
    }

    override fun computeScroll() {
        if (dragger.continueSettling(true)) ViewCompat.postInvalidateOnAnimation(this)
    }

    private fun onPullStart() { callback?.onPullStart() }
    private fun onPull(@Direction direction: Int, progress: Float) { callback?.onPull(direction, progress) }
    private fun onPullCancel(@Direction direction: Int) { callback?.onPullCancel(direction) }
    private fun onPullComplete(@Direction direction: Int) { callback?.onPullComplete(direction) }

    private fun reset() { dragger.settleCapturedViewAt(0, 0); invalidate() }

    interface Callback {
        fun onPullStart()
        fun onPull(@Direction direction: Int, progress: Float)
        fun onPullCancel(@Direction direction: Int)
        fun onPullComplete(@Direction direction: Int)
    }

    private inner class ViewDragCallback : ViewDragHelper.Callback() {
        override fun tryCaptureView(child: View, pointerId: Int): Boolean = true
        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int = 0

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return if (direction and (DIRECTION_UP or DIRECTION_DOWN) != 0) top
            else if (direction and DIRECTION_UP != 0) minOf(0, top)
            else if (direction and DIRECTION_DOWN != 0) maxOf(0, top)
            else 0
        }

        override fun getViewHorizontalDragRange(child: View): Int = 0

        override fun getViewVerticalDragRange(child: View): Int {
            return if (direction == 0) 0
            else if (direction and (DIRECTION_UP or DIRECTION_DOWN) != 0) height * 2
            else height
        }

        override fun onViewCaptured(capturedChild: View, activePointerId: Int) { onPullStart() }

        override fun onViewPositionChanged(changedView: View, left: Int, top: Int, dx: Int, dy: Int) {
            if (top > 0) onPull(DIRECTION_DOWN, top.toFloat() / height.toFloat())
            else if (top < 0) onPull(DIRECTION_UP, -top.toFloat() / height.toFloat())
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val top = releasedChild.top
            val slop = if (abs(yvel) > minimumFlingVelocity) height / 6 else height / 3
            if (top > 0) {
                if (top > slop) onPullComplete(DIRECTION_DOWN)
                else { onPullCancel(DIRECTION_DOWN); reset() }
            } else if (top < 0) {
                if (top < -slop) onPullComplete(DIRECTION_UP)
                else { onPullCancel(DIRECTION_UP); reset() }
            }
        }
    }
}
