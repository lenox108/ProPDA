package forpdateam.ru.forpda.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdge {
    /**
     * Единая политика по проекту: edge-to-edge (decorFitsSystemWindows=false) во всех Activity.
     * Padding под системные бары добавляется вручную на конкретный корневой view.
     */
    @JvmStatic
    fun apply(
            activity: Activity,
            root: View,
            padTop: Boolean = true,
            padBottom: Boolean = false,
            topUnderlayColor: Int? = null,
            topUnderlayTag: String? = null
    ) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        val topUnderlay = if (topUnderlayColor != null && topUnderlayTag != null) {
            ensureTopUnderlay(activity, topUnderlayTag).apply {
                setBackgroundColor(topUnderlayColor)
            }
        } else {
            null
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nb = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val top = if (padTop) sb.top else 0
            val bottom = if (padBottom) nb.bottom else 0
            val newTop = baseTop + top
            val newBottom = baseBottom + bottom
            if (v.paddingLeft != baseLeft || v.paddingTop != newTop || v.paddingRight != baseRight || v.paddingBottom != newBottom) {
                v.setPadding(baseLeft, newTop, baseRight, newBottom)
            }
            topUnderlay?.let { underlay ->
                underlay.visibility = if (sb.top > 0) View.VISIBLE else View.GONE
                underlay.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        sb.top
                )
                underlay.bringToFront()
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun ensureTopUnderlay(activity: Activity, tag: String): View {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        return content.findViewWithTag<View>(tag)
                ?: View(activity).apply {
                    this.tag = tag
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    content.addView(this)
                }
    }
}

