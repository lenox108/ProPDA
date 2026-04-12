package forpdateam.ru.forpda.ui

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object EdgeToEdge {
    /**
     * Единая политика по проекту: используем decorFitsSystemWindows=true (как в MainActivity),
     * и при необходимости добавляем padding под системные бары на конкретный корневой view.
     */
    @JvmStatic
    fun apply(activity: Activity, root: View, padTop: Boolean = true, padBottom: Boolean = false) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
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
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}

