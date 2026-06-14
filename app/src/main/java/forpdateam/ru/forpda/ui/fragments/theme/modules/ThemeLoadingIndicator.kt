package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr

class ThemeLoadingIndicator(
        private val context: Context,
        private val fragmentContent: ViewGroup,
        private val contentProgress: View,
        private val dp8: Int,
        private val dp16: Int,
        private val screenWidthPx: () -> Int
) : ThemeUiModule {

    private companion object {
        const val MIN_VISIBLE_MS = 160L
        const val FADE_OUT_MS = 90L
    }

    private var skeletonView: ViewGroup? = null
    private var hidePosted = false
    private var hideRunnable: Runnable? = null
    private var isShowing = false
    private var shownAtMs = 0L

    override fun init() = Unit

    fun show() {
        hidePosted = false
        hideContentProgress()
        val skeleton = skeletonView ?: createLoadingSkeletonView().also {
            fragmentContent.addView(
                    it,
                    FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            )
            skeletonView = it
        }
        hideRunnable?.let { skeleton.removeCallbacks(it) }
        hideRunnable = null
        val wasVisible = skeleton.visibility == View.VISIBLE && isShowing
        if (!wasVisible) {
            shownAtMs = SystemClock.uptimeMillis()
            isShowing = true
        }
        if (skeleton.visibility != View.VISIBLE) {
            skeleton.visibility = View.VISIBLE
        }
        if (skeleton.alpha != 1f) {
            skeleton.animate().cancel()
            skeleton.alpha = 1f
        }
        bringSkeletonToFrontIfNeeded(skeleton)
    }

    fun hide() {
        hideContentProgress()
        if (hidePosted) return
        hidePosted = true
        skeletonView?.let { skeleton ->
            val runnable = Runnable {
                hidePosted = false
                hideRunnable = null
                if (!isShowing && skeleton.visibility != View.VISIBLE) return@Runnable
                isShowing = false
                skeleton.animate().cancel()
                skeleton.animate()
                        .alpha(0f)
                        .setDuration(FADE_OUT_MS)
                        .withEndAction { skeleton.visibility = View.GONE }
                        .start()
            }
            hideRunnable = runnable
            val elapsedVisibleMs = SystemClock.uptimeMillis() - shownAtMs
            val hideDelayMs = (MIN_VISIBLE_MS - elapsedVisibleMs).coerceAtLeast(0L)
            if (hideDelayMs > 0L) {
                skeleton.postDelayed(runnable, hideDelayMs)
            } else {
                skeleton.post(runnable)
            }
        } ?: run {
            hidePosted = false
        }
    }

    override fun dispose() {
        skeletonView?.let { skeleton ->
            hideRunnable?.let { skeleton.removeCallbacks(it) }
            skeleton.animate().cancel()
            (skeleton.parent as? ViewGroup)?.removeView(skeleton)
        }
        skeletonView = null
        hidePosted = false
        hideRunnable = null
        isShowing = false
        shownAtMs = 0L
    }

    private fun hideContentProgress() {
        if (contentProgress.visibility != View.GONE) {
            contentProgress.visibility = View.GONE
        }
    }

    private fun bringSkeletonToFrontIfNeeded(skeleton: View) {
        val parent = skeleton.parent as? ViewGroup ?: return
        if (parent.indexOfChild(skeleton) != parent.childCount - 1) {
            skeleton.bringToFront()
        }
    }

    private fun createLoadingSkeletonView(): ViewGroup {
        val background = context.getColorFromAttr(R.attr.background_for_lists)
        val card = context.getColorFromAttr(R.attr.cards_background)
        val muted = context.getColorFromAttr(R.attr.divider_line)
        val lineColor = blendColors(card, muted, 0.42f)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp8, dp8, dp8, dp8)
            isClickable = false
            repeat(4) { index ->
                addView(
                        createSkeletonCard(card, lineColor),
                        LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = if (index == 3) 0 else dp8
                        }
                )
            }
        }
    }

    private fun createSkeletonCard(cardColor: Int, lineColor: Int): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp16.toFloat()
            }
            setPadding(dp16, dp16, dp16, dp16)
        }
        card.addView(createSkeletonLine(lineColor, 0.42f, dp16))
        card.addView(createSkeletonLine(lineColor, 0.82f, dp8))
        card.addView(createSkeletonLine(lineColor, 0.68f, dp8))
        card.addView(createSkeletonLine(lineColor, 0.52f, dp8))
        return card
    }

    private fun createSkeletonLine(color: Int, widthFraction: Float, topMargin: Int): View {
        return View(context).apply {
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp8.toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                    (screenWidthPx() * widthFraction).toInt().coerceAtLeast(dp16),
                    dp8
            ).apply {
                if (topMargin > 0) this.topMargin = topMargin
            }
        }
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val safeRatio = ratio.coerceIn(0f, 1f)
        val inverse = 1f - safeRatio
        return Color.argb(
                (Color.alpha(from) * inverse + Color.alpha(to) * safeRatio).toInt(),
                (Color.red(from) * inverse + Color.red(to) * safeRatio).toInt(),
                (Color.green(from) * inverse + Color.green(to) * safeRatio).toInt(),
                (Color.blue(from) * inverse + Color.blue(to) * safeRatio).toInt()
        )
    }
}
