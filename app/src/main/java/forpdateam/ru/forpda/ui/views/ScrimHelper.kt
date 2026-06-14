package forpdateam.ru.forpda.ui.views

import android.view.ViewTreeObserver
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

/**
 * Хелпер для отслеживания состояния scrim в CollapsingToolbarLayout.
 * 
 * Улучшения в Kotlin-версии:
 * - Функциональный тип для listener
 * - Упрощенная логика с if-expression
 */
class ScrimHelper(
    private val appBarLayout: AppBarLayout,
    private val toolbarLayout: CollapsingToolbarLayout
) {
    private var scrimListener: ScrimListener? = null
    private var scrim = true
    private var firstOffsetReceived = false

    init {
        appBarLayout.addOnOffsetChangedListener { appBar, verticalOffset ->
            scrimListener?.let { listener ->
                firstOffsetReceived = true
                val totalRange = appBar.totalScrollRange
                val isScrimVisible = if (totalRange == 0) {
                    true
                } else {
                    appBar.height + verticalOffset <= toolbarLayout.scrimVisibleHeightTrigger
                }
                if (isScrimVisible != scrim) {
                    scrim = isScrimVisible
                    listener.onScrimChanged(isScrimVisible)
                }
            }
        }
    }

    fun setScrimListener(listener: ScrimListener?) {
        this.scrimListener = listener
    }

    fun checkInitialState() {
        scrimListener?.let { listener ->
            if (firstOffsetReceived) return
            try {
                appBarLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try {
                            appBarLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            if (firstOffsetReceived) return
                            val totalRange = appBarLayout.totalScrollRange
                            val isCollapsed = totalRange == 0
                            if (isCollapsed != scrim) {
                                scrim = isCollapsed
                                listener.onScrimChanged(isCollapsed)
                            }
                        } catch (e: Exception) {
                            // Ignore errors in onGlobalLayout to prevent crashes
                        }
                    }
                })
            } catch (e: Exception) {
                // Ignore errors when adding listener
            }
        }
    }

    /**
     * Функциональный интерфейс для отслеживания изменений scrim.
     */
    fun interface ScrimListener {
        fun onScrimChanged(scrim: Boolean)
    }
}
