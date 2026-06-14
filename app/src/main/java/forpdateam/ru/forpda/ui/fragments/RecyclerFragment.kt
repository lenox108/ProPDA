package forpdateam.ru.forpda.ui.fragments

import android.os.Bundle
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope

import forpdateam.ru.forpda.R

/**
 * Created by radiationx on 14.08.17.
 */

abstract class RecyclerFragment : TabFragment(), TabTopScroller {
    protected lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    protected lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView

    private var listScrollY = 0
    private var appBarOffset = 0

    private lateinit var topScroller: RecyclerTopScroller

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_base_list)
        refreshLayout = findViewById(R.id.swipe_refresh_list) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout ?: throw IllegalStateException("refreshLayout not found")
        recyclerView = findViewById(R.id.base_list) as? androidx.recyclerview.widget.RecyclerView ?: throw IllegalStateException("recyclerView not found")
        contentController.setMainRefresh(refreshLayout)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pinStaticOpaqueToolbar()
        setListsBackground()
        tuneListRecyclerView(recyclerView)
        refreshLayoutStyle(refreshLayout)
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                listScrollY = recyclerView.computeVerticalScrollOffset()
                updateToolbarShadow()
            }
        })

        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, i ->
            appBarOffset = i
            updateToolbarShadow()
        })

        topScroller = RecyclerTopScroller(recyclerView, appBarLayout)
    }

    override fun isShadowVisible(): Boolean {
        return appBarOffset != 0 || listScrollY > 0
    }

    protected fun listScrollTop() {
        lifecycleScope.launch {
            delay(225)
            recyclerView.smoothScrollToPosition(0)
        }
    }

    override fun toggleScrollTop() {
        if (!::topScroller.isInitialized) return
        topScroller.toggleScrollTop()
    }
}
