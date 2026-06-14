package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import forpdateam.ru.forpda.BuildConfig
import kotlin.math.abs

class ThemeToolbarScrollController(
        private val appBarLayout: View,
        private val linkedTranslationViews: List<View> = emptyList(),
        private val shouldStayVisible: () -> Boolean
) {
    enum class State {
        VISIBLE,
        HIDDEN,
        ANIMATING_TO_VISIBLE,
        ANIMATING_TO_HIDDEN
    }

    private val density = appBarLayout.resources.displayMetrics.density
    private val hideThresholdPx = 24f * density
    private val showThresholdPx = 12f * density
    private val cooldownMs = 200L
    private val interpolator = DecelerateInterpolator()
    private var cumulativeDownPx = 0f
    private var cumulativeUpPx = 0f
    private var lastTransitionAt = 0L
    private var animationGeneration = 0
    private var enabled = false
    private var bound = false
    private var pendingHideAfterLayout = false
    var state: State = State.VISIBLE
        private set

    fun bind() {
        bound = true
        reset()
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            reset()
        }
    }

    fun onScroll(scrollY: Int, oldScrollY: Int) {
        onScroll(scrollY, oldScrollY, userScroll = false)
    }

    fun onScroll(scrollY: Int, oldScrollY: Int, userScroll: Boolean) {
        if (!bound || !enabled) {
            if (BuildConfig.DEBUG && scrollY != oldScrollY) {
                Log.w(TAG, "onScroll ignored bound=$bound enabled=$enabled y=$scrollY")
            }
            return
        }
        if (shouldStayVisible()) {
            show(force = true)
            resetAccumulatedScroll()
            return
        }
        if (scrollY <= 0) {
            show(force = true)
            resetAccumulatedScroll()
            return
        }

        val delta = scrollY - oldScrollY
        if (delta == 0) return
        if (delta > 0) {
            if (userScroll) {
                hide(force = true)
                resetAccumulatedScroll()
                return
            }
            cumulativeDownPx += delta
            cumulativeUpPx = 0f
            if (cumulativeDownPx > hideThresholdPx) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "hide threshold reached down=${cumulativeDownPx.toInt()} y=$scrollY state=$state")
                }
                hide(force = false)
                resetAccumulatedScroll()
            }
        } else {
            cumulativeUpPx += abs(delta)
            cumulativeDownPx = 0f
            if (cumulativeUpPx > showThresholdPx) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "show threshold reached up=${cumulativeUpPx.toInt()} y=$scrollY state=$state")
                }
                show(force = false)
                resetAccumulatedScroll()
            }
        }
    }

    fun show(force: Boolean = false) {
        animateTo(0f, State.ANIMATING_TO_VISIBLE, State.VISIBLE, force)
    }

    /** Synchronous restore after overlay chrome (topic hat) — avoids stuck hidden state after rapid toggles. */
    fun forceVisible() {
        reset()
    }

    fun hide(force: Boolean = false) {
        val height = resolveAppBarHeightPx()
        if (height <= 0f) {
            if (force) return
            pendingHideAfterLayout = true
            appBarLayout.post { applyPendingHideAfterLayout() }
            return
        }
        pendingHideAfterLayout = false
        val offset = -height
        animateTo(offset, State.ANIMATING_TO_HIDDEN, State.HIDDEN, force)
    }

    private fun applyPendingHideAfterLayout() {
        if (!pendingHideAfterLayout || !bound || !enabled) return
        if (shouldStayVisible()) {
            pendingHideAfterLayout = false
            return
        }
        val height = resolveAppBarHeightPx()
        if (height <= 0f) return
        pendingHideAfterLayout = false
        animateTo(-height, State.ANIMATING_TO_HIDDEN, State.HIDDEN, force = false)
    }

    private fun resolveAppBarHeightPx(): Float {
        val laidOutHeight = appBarLayout.height
        if (laidOutHeight > 0) return laidOutHeight.toFloat()
        val measuredHeight = appBarLayout.measuredHeight
        if (measuredHeight > 0) return measuredHeight.toFloat()
        return 0f
    }

    fun reset() {
        resetAccumulatedScroll()
        cancelAnimations()
        pendingHideAfterLayout = false
        setTranslation(0f)
        state = State.VISIBLE
        lastTransitionAt = 0L
        animationGeneration++
    }

    fun dispose() {
        enabled = false
        bound = false
        reset()
    }

    private fun animateTo(targetTranslationY: Float, animatingState: State, finalState: State, force: Boolean) {
        if (appBarLayout.translationY == targetTranslationY) {
            state = finalState
            linkedTranslationViews.forEach { view ->
                view.translationY = targetTranslationY
            }
            return
        }
        if (state == finalState) return
        if (state == animatingState) {
            val reversing = (animatingState == State.ANIMATING_TO_VISIBLE && finalState == State.HIDDEN) ||
                    (animatingState == State.ANIMATING_TO_HIDDEN && finalState == State.VISIBLE)
            if (!reversing && !force) return
            cancelAnimations()
            animationGeneration++
        }

        val now = SystemClock.uptimeMillis()
        if (!force && now - lastTransitionAt < cooldownMs) return

        lastTransitionAt = now
        val generation = ++animationGeneration
        appBarLayout.animate().cancel()
        state = animatingState
        appBarLayout.animate()
                .translationY(targetTranslationY)
                .setDuration(200L)
                .setInterpolator(interpolator)
                .withLayer()
                .setUpdateListener { setLinkedTranslation(appBarLayout.translationY) }
                .setListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled && generation == animationGeneration) {
                            state = finalState
                            setLinkedTranslation(targetTranslationY)
                        }
                    }
                })
                .start()
    }

    private fun cancelAnimations() {
        appBarLayout.animate().cancel()
    }

    private fun setTranslation(value: Float) {
        appBarLayout.translationY = value
        setLinkedTranslation(value)
    }

    private fun setLinkedTranslation(value: Float) {
        linkedTranslationViews.forEach { view ->
            view.translationY = value
        }
    }

    private fun resetAccumulatedScroll() {
        cumulativeDownPx = 0f
        cumulativeUpPx = 0f
    }

    companion object {
        private const val TAG = "ToolbarAutoHide"
    }
}
