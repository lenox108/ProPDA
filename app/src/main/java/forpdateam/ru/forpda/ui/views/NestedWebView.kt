package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.core.view.MotionEventCompat
import androidx.core.view.NestedScrollingChild2
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat

/*
* Обработка событий аккуратно слизана с RecyclerView с некоторыми доработками.
*/
open class NestedWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.webViewStyle
) : WebView(context, attrs, defStyleAttr), NestedScrollingChild2 {
    private val mScrollOffset = IntArray(2)
    private val mScrollConsumed = IntArray(2)
    private val mNestedOffsets = IntArray(2)
    private val mChildHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)
    private val mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var mLastTouchX: Int = 0
    private var mLastTouchY: Int = 0
    private var mScrollState: Int = SCROLL_STATE_IDLE

    private val longClickListener: OnLongClickListener = OnLongClickListener { true }
    private var scrollGestureActive: Boolean = false
    /** True while WebView fling/momentum continues after the finger lifts. */
    private var scrollInertiaActive: Boolean = false
    /**
     * Set from [markUserTouchForScroll] when outer touch routing (bottom refresh, page swipe)
     * prevents [onTouchEvent] from observing a user drag on some WebView/OEM builds.
     */
    private var userTouchScrollActive: Boolean = false
    /**
     * Uptime of the last GENUINE user touch that began a scroll (ACTION_DOWN / outer touch routing).
     * Unlike [isUserScrollActive], this is NEVER set by the programmatic [onScrollChanged] path, so a
     * post-open content reflow (hat strip / hybrid prepend) does not look like a user scroll here.
     */
    var lastUserTouchScrollAtMs: Long = 0L
        private set
    private val deferredUntilScrollIdle = ArrayList<() -> Unit>()
    private val endScrollInertiaRunnable = Runnable {
        scrollInertiaActive = false
        userTouchScrollActive = false
    }

    /**
     * Set when the user touches/drags the content after a programmatic scroll-to-bottom
     * pass started. Stays true until [beginAutoScrollToBottom] resets it, so a multi-stage
     * auto-scroll (QMS open) stops yanking the viewport the moment the user takes control.
     */
    private var autoScrollUserInterrupt: Boolean = false

    init {
        // КРИТИЧНО: NestedScrollingChildHelper.isNestedScrollingEnabled по умолчанию false.
        // Без явного включения dispatchNestedPreScroll/Scroll возвращает false и AppBar/FAB не реагируют.
        isNestedScrollingEnabled = true
    }

    private fun changeLongClickable(enable: Boolean) {
        setOnLongClickListener(if (enable) null else longClickListener)
        isLongClickable = enable
        isHapticFeedbackEnabled = enable
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val vtev = MotionEvent.obtain(e)
        val action = MotionEventCompat.getActionMasked(e)

        if (action == MotionEvent.ACTION_DOWN) {
            mNestedOffsets[0] = 0
            mNestedOffsets[1] = 0
        }
        vtev.offsetLocation(mNestedOffsets[0].toFloat(), mNestedOffsets[1].toFloat())

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mLastTouchX = (e.x + 0.5f).toInt()
                mLastTouchY = (e.y + 0.5f).toInt()
                // Any touch after an auto-scroll pass began means the user is taking control.
                autoScrollUserInterrupt = true
                markUserTouchForScroll()

                var nestedScrollAxis = ViewCompat.SCROLL_AXIS_NONE
                nestedScrollAxis = nestedScrollAxis or ViewCompat.SCROLL_AXIS_HORIZONTAL
                nestedScrollAxis = nestedScrollAxis or ViewCompat.SCROLL_AXIS_VERTICAL
                startNestedScroll(nestedScrollAxis, ViewCompat.TYPE_TOUCH)
                val handled = super.onTouchEvent(vtev)
                vtev.recycle()
                return handled
            }

            MotionEvent.ACTION_MOVE -> {
                val x = (e.x + 0.5f).toInt()
                val y = (e.y + 0.5f).toInt()
                var dx = mLastTouchX - x
                var dy = mLastTouchY - y

                if (mScrollState == SCROLL_STATE_IDLE) {
                    if (Math.abs(dx) < mTouchSlop && Math.abs(dy) < mTouchSlop) {
                        vtev.recycle()
                        return true
                    }
                }

                mScrollConsumed[0] = 0
                mScrollConsumed[1] = 0
                mScrollOffset[0] = 0
                mScrollOffset[1] = 0
                val preScrollConsumed = dispatchNestedPreScroll(dx, dy, mScrollConsumed, mScrollOffset, ViewCompat.TYPE_TOUCH)

                if (preScrollConsumed) {
                    dx -= mScrollConsumed[0]
                    dy -= mScrollConsumed[1]
                    vtev.offsetLocation(mScrollOffset[0].toFloat(), mScrollOffset[1].toFloat())
                    mNestedOffsets[0] += mScrollOffset[0]
                    mNestedOffsets[1] += mScrollOffset[1]
                }

                mLastTouchX = x - mScrollOffset[0]
                mLastTouchY = y - mScrollOffset[1]

                if (preScrollConsumed) {
                    setScrollState(SCROLL_STATE_NESTED_SCROLL)
                }

                if (dy != 0) {
                    setScrollState(SCROLL_STATE_SCROLL)
                }
                if (mScrollConsumed[1] != 0) {
                    vtev.offsetLocation(0f, mScrollConsumed[1].toFloat())
                }
                val oldScrollY = scrollY
                val handled = super.onTouchEvent(vtev)
                val scrolledY = scrollY - oldScrollY
                // Сообщаем родителю о фактически потреблённом скролле + остатке (dyUnconsumed),
                // чтобы AppBarLayout/FAB могли реагировать на скролл.
                mScrollOffset[0] = 0
                mScrollOffset[1] = 0
                val scrollConsumed = dispatchNestedScroll(0, scrolledY, 0, dy - scrolledY, mScrollOffset, ViewCompat.TYPE_TOUCH)
                if (scrollConsumed) {
                    mLastTouchX -= mScrollOffset[0]
                    mLastTouchY -= mScrollOffset[1]
                    vtev.offsetLocation(mScrollOffset[0].toFloat(), mScrollOffset[1].toFloat())
                    mNestedOffsets[0] += mScrollOffset[0]
                    mNestedOffsets[1] += mScrollOffset[1]
                }

                if (mScrollState != SCROLL_STATE_IDLE) {
                    if (isLongClickable) {
                        changeLongClickable(false)
                    }
                }
                vtev.recycle()
                return handled
            }

            MotionEvent.ACTION_UP -> {
                val handled = super.onTouchEvent(vtev)
                resetTouch()
                changeLongClickable(true)
                vtev.recycle()
                return handled
            }

            MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(vtev)
                resetTouch()
                changeLongClickable(true)
                vtev.recycle()
                return handled
            }
        }
        vtev.recycle()
        return super.onTouchEvent(e)
    }

    private fun resetTouch() {
        val wasScrolling = mScrollState == SCROLL_STATE_SCROLL || mScrollState == SCROLL_STATE_NESTED_SCROLL
        stopNestedScroll(ViewCompat.TYPE_TOUCH)
        setScrollState(SCROLL_STATE_IDLE)
        if (wasScrolling) {
            beginScrollInertiaTracking()
        }
        runDeferredUntilScrollIdle()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (t != oldt) {
            // OEM WebViews may scroll without delivering touch events to this view.
            beginScrollInertiaTracking()
        }
    }

    /** Call from outer [View.OnTouchListener] when the WebView scrolls under user control. */
    fun markUserTouchForScroll() {
        userTouchScrollActive = true
        lastUserTouchScrollAtMs = SystemClock.uptimeMillis()
        beginScrollInertiaTracking()
    }

    private fun beginScrollInertiaTracking() {
        scrollInertiaActive = true
        removeCallbacks(endScrollInertiaRunnable)
        postDelayed(endScrollInertiaRunnable, SCROLL_INERTIA_IDLE_MS)
    }

    private fun setScrollState(state: Int) {
        if (state == mScrollState) return
        mScrollState = state
        scrollGestureActive = state != SCROLL_STATE_IDLE
        if (!scrollGestureActive) {
            runDeferredUntilScrollIdle()
        }
    }

    fun runWhenScrollIdle(action: () -> Unit) {
        if (!scrollGestureActive) {
            action()
            return
        }
        deferredUntilScrollIdle.clear()
        deferredUntilScrollIdle.add(action)
    }

    fun isUserScrollActive(): Boolean =
            scrollGestureActive || scrollInertiaActive || userTouchScrollActive

    /** Marks the start of a programmatic scroll-to-bottom pass, clearing any prior user interrupt. */
    fun beginAutoScrollToBottom() {
        autoScrollUserInterrupt = false
    }

    /** True while a programmatic scroll-to-bottom should stand down for the user. */
    fun isAutoScrollSuppressedByUser(): Boolean = scrollGestureActive || autoScrollUserInterrupt

    private fun runDeferredUntilScrollIdle() {
        if (deferredUntilScrollIdle.isEmpty()) return
        val actions = ArrayList(deferredUntilScrollIdle)
        deferredUntilScrollIdle.clear()
        actions.forEach { it.invoke() }
    }

    override fun setNestedScrollingEnabled(enabled: Boolean) {
        mChildHelper.isNestedScrollingEnabled = enabled
    }

    override fun isNestedScrollingEnabled(): Boolean = mChildHelper.isNestedScrollingEnabled

    override fun startNestedScroll(axes: Int): Boolean = startNestedScroll(axes, ViewCompat.TYPE_TOUCH)

    override fun startNestedScroll(axes: Int, type: Int): Boolean = mChildHelper.startNestedScroll(axes, type)

    override fun stopNestedScroll() = stopNestedScroll(ViewCompat.TYPE_TOUCH)

    override fun stopNestedScroll(type: Int) = mChildHelper.stopNestedScroll(type)

    override fun hasNestedScrollingParent(): Boolean = hasNestedScrollingParent(ViewCompat.TYPE_TOUCH)

    override fun hasNestedScrollingParent(type: Int): Boolean = mChildHelper.hasNestedScrollingParent(type)

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedScroll(
        dxConsumed: Int, dyConsumed: Int,
        dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type)

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?
    ): Boolean = dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH)

    override fun dispatchNestedPreScroll(
        dx: Int, dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
        type: Int
    ): Boolean = mChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        mChildHelper.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean =
        mChildHelper.dispatchNestedPreFling(velocityX, velocityY)

    companion object {
        const val SCROLL_STATE_IDLE = 0
        const val SCROLL_STATE_NESTED_SCROLL = 1
        const val SCROLL_STATE_SCROLL = 2
        private const val SCROLL_INERTIA_IDLE_MS = 450L
    }
}
