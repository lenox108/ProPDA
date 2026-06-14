package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val REFRESH_SCROLL_TAG = "RefreshScroll"

/**
 * Controlled bottom-edge refresh gesture for topic WebView.
 *
 * The controller only arms from the real bottom edge and starts consuming touch events after
 * a clear upward vertical gesture, so regular scrolling and horizontal page swipes stay intact.
 */
class BottomRefreshGestureController(
        private val target: ExtendedWebView,
        private val canRefresh: () -> Boolean,
        private val isHatOpen: () -> Boolean,
        private val onProgress: (progress: Float, canRelease: Boolean, active: Boolean) -> Unit,
        private val onRefresh: () -> Unit
) : View.OnTouchListener {

    var isEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) reset("disabled")
        }

    private val density = target.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(target.context).scaledTouchSlop
    private val captureDistancePx = max(touchSlop * 3f, 48f * density)
    private val fullProgressDistancePx = 220f * density
    private val triggerDistancePx = fullProgressDistancePx
    private val bottomTolerancePx = 16f * density
    private val verticalDominanceRatio = 1.5f
    private val maxReleaseVelocityPx = 1450f * density
    private val minControlledDurationMs = 260L
    private val refreshCooldownMs = 1400L

    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var state = State.Idle
    private var blocked = false
    private var triggeredRefresh = false
    private var lastRefreshAt = 0L
    private var lastLoggedProgress = -1
    private var velocityTracker: VelocityTracker? = null

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (!isEnabled) {
            onProgress(0f, false, false)
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset("down")
                downX = event.x
                downY = event.y
                downAt = SystemClock.uptimeMillis()
                val atBottom = isAtBottom()
                val hatOpen = isHatOpen()
                val refreshAllowed = canRefresh()
                blocked = event.pointerCount > 1 ||
                        shouldIgnoreStart() ||
                        hatOpen ||
                        !atBottom ||
                        isInRefreshCooldown() ||
                        !refreshAllowed
                state = if (blocked) State.Blocked else State.Tracking
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                if (BuildConfig.DEBUG) {
                    Log.i(
                            "BottomRefresh",
                            "gesture down t=$downAt enabled=$isEnabled y=${target.scrollY} max=${currentMaxScrollPx()} atBottom=$atBottom hatOpen=$hatOpen canRefresh=$refreshAllowed blocked=$blocked capture=$captureDistancePx trigger=$triggerDistancePx fullProgress=$fullProgressDistancePx minDuration=$minControlledDurationMs maxVelocity=$maxReleaseVelocityPx refreshing=${target.isUserScrollActive()}"
                    )
                }
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                blocked = true
                state = State.Blocked
                return state == State.Captured
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (isHatOpen()) {
                    block("hatMove")
                    return false
                }
                if (blocked || event.pointerCount != 1) return false

                val distance = upwardDistance(event)
                val horizontal = abs(event.x - downX)
                if (distance <= 0f) {
                    if (state == State.Captured) reset("reverse")
                    return false
                }
                if (distance < captureDistancePx) return false
                if (distance < horizontal * verticalDominanceRatio) {
                    block("horizontal")
                    return false
                }
                if (!isAtBottom()) {
                    block("leftBottom")
                    return false
                }

                state = State.Captured
                target.parent?.requestDisallowInterceptTouchEvent(true)
                val progress = gestureProgress(distance)
                onProgress(progress, progress >= 1f, true)
                logProgress(distance)
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                val shouldRefresh = state == State.Captured &&
                        !blocked &&
                        !isHatOpen() &&
                        isReleasePastThreshold(event) &&
                        !isInRefreshCooldown() &&
                        canRefresh()
                val releaseDistance = upwardDistance(event)
                val progress = gestureProgress(releaseDistance)
                if (BuildConfig.DEBUG) {
                    Log.i(
                            REFRESH_SCROLL_TAG,
                            "bottomGesture release t=${SystemClock.uptimeMillis()} refresh=$shouldRefresh distance=$releaseDistance progress=$progress y=${target.scrollY} max=${currentMaxScrollPx()} velocity=${currentVelocityY()} duration=${SystemClock.uptimeMillis() - downAt}"
                    )
                }
                reset("up")
                if (shouldRefresh) {
                    lastRefreshAt = System.currentTimeMillis()
                    triggeredRefresh = true
                    onProgress(0f, false, false)
                    target.post {
                        if (BuildConfig.DEBUG) {
                            Log.i(
                                    REFRESH_SCROLL_TAG,
                                    "bottomGesture trigger t=${SystemClock.uptimeMillis()} y=${target.scrollY} max=${currentMaxScrollPx()}"
                            )
                        }
                        onRefresh()
                    }
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> reset("cancel")
        }
        return false
    }

    fun cancelFromHatOpen() {
        block("hatOpen")
        reset("hatOpen")
    }

    fun isTracking(): Boolean = state == State.Tracking || state == State.Captured

    private fun shouldIgnoreStart(): Boolean {
        if (target.isActionModeActive()) return true
        return when (target.hitTestResult?.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.EDIT_TEXT_TYPE -> true
            else -> false
        }
    }

    private fun upwardDistance(event: MotionEvent): Float = downY - event.y

    private fun isReleasePastThreshold(event: MotionEvent): Boolean {
        val distance = upwardDistance(event)
        if (gestureProgress(distance) < 1f) return false
        velocityTracker?.computeCurrentVelocity(1000)
        val velocityY = velocityTracker?.yVelocity ?: 0f
        val duration = SystemClock.uptimeMillis() - downAt
        return abs(velocityY) <= maxReleaseVelocityPx && duration >= minControlledDurationMs
    }

    private fun logProgress(distance: Float) {
        val progress = gestureProgress(distance)
        val bucket = (progress * 4f).toInt()
        if (bucket == lastLoggedProgress) return
        lastLoggedProgress = bucket
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "bottomGesture progress=$progress distance=$distance threshold=$triggerDistancePx y=${target.scrollY} max=${currentMaxScrollPx()}"
            )
        }
    }

    private fun gestureProgress(distance: Float): Float {
        return min(1f, max(0f, distance / fullProgressDistancePx))
    }

    private fun isAtBottom(): Boolean {
        val contentHeightPx = target.contentHeight * target.scale
        val viewportHeightPx = target.height.toFloat()
        if (contentHeightPx <= 0f || viewportHeightPx <= 0f) return false
        return target.scrollY + viewportHeightPx >= contentHeightPx - bottomTolerancePx
    }

    private fun currentMaxScrollPx(): Int {
        return ((target.contentHeight * target.scale).toInt() - target.height).coerceAtLeast(0)
    }

    private fun currentVelocityY(): Float {
        velocityTracker?.computeCurrentVelocity(1000)
        return velocityTracker?.yVelocity ?: 0f
    }

    private fun isInRefreshCooldown(): Boolean {
        return System.currentTimeMillis() - lastRefreshAt < refreshCooldownMs
    }

    private fun block(reason: String) {
        if (!blocked) {
            if (BuildConfig.DEBUG) Log.i("BottomRefresh", "gesture blocked reason=$reason y=${target.scrollY} max=${currentMaxScrollPx()}")
        }
        blocked = true
        state = State.Blocked
        onProgress(0f, false, false)
    }

    private fun reset(reason: String) {
        if (state == State.Captured) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "bottomGesture reset reason=$reason y=${target.scrollY}")
        }
        target.parent?.requestDisallowInterceptTouchEvent(false)
        velocityTracker?.recycle()
        velocityTracker = null
        state = State.Idle
        blocked = false
        triggeredRefresh = false
        lastLoggedProgress = -1
        onProgress(0f, false, false)
    }

    private enum class State {
        Idle,
        Tracking,
        Captured,
        Blocked
    }
}
