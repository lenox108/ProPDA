package forpdateam.ru.forpda.ui.views

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Для управления дополнительными вьюхами, когда нет данных и т.д.
 * 
 * Улучшения в Kotlin-версии:
 * - MutableMap вместо HashMap
 * - Smart cast для SwipeRefreshLayout
 * - Упрощенная работа с visibility
 */
class ContentController(
    private var additionalRefresh: View?,
    private val additionalContent: ViewGroup,
    private var mainContent: ViewGroup?
) {
    
    companion object {
        const val TAG_NO_DATA = "NO_DATA"
        const val TAG_ERROR = "ERROR"
    }

    private var mainRefresh: View? = null
    private var firstLoad = true
    private val contents = mutableMapOf<Any, View>()

    fun setMainRefresh(mainRefresh: View?) {
        this.mainRefresh = mainRefresh
    }

    fun contains(tag: Any): Boolean = contents[tag] != null

    fun addContent(content: View, tag: Any): View {
        var view = contents[tag]
        if (view == null) {
            view = content
            view.visibility = View.GONE
            contents[tag] = view
            additionalContent.addView(view, 0)
        }
        return view
    }

    fun addContent(context: Context, @LayoutRes id: Int, tag: Any): View {
        var view = contents[tag]
        if (view == null) {
            view = View.inflate(context, id, null)
            view?.visibility = View.GONE
            view?.let {
                contents[tag] = it
                additionalContent.addView(it, 0)
            }
        }
        return view ?: throw IllegalStateException("Failed to inflate view for tag=$tag")
    }

    fun showContent(tag: Any) {
        contents[tag]?.visibility = View.VISIBLE
        // mainContent?.visibility = View.GONE
    }

    fun hideContent(tag: Any) {
        contents[tag]?.visibility = View.GONE
        // mainContent?.visibility = View.VISIBLE
    }

    fun startRefreshing() {
        if (firstLoad) {
            mainContent?.visibility = View.INVISIBLE
            additionalRefresh?.visibility = View.VISIBLE
        } else {
            (mainRefresh as? SwipeRefreshLayout)?.isRefreshing = true
        }
    }

    fun stopRefreshing() {
        if (firstLoad) {
            mainContent?.visibility = View.VISIBLE
            additionalRefresh?.visibility = View.GONE
            firstLoad = false
        } else {
            (mainRefresh as? SwipeRefreshLayout)?.isRefreshing = false
        }
    }

    fun setFirstLoad(b: Boolean) {
        firstLoad = b
    }

    fun destroy() {
        additionalRefresh = null
        mainContent = null
        mainRefresh = null
        contents.clear()
    }
}
