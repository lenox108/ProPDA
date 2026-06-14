package forpdateam.ru.forpda.ui.fragments.devdb.brand

import forpdateam.ru.forpda.common.dpToPx
import android.graphics.Rect
import android.os.Bundle
import com.google.android.material.appbar.AppBarLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.presentation.devdb.devices.DevicesUiEvent
import forpdateam.ru.forpda.presentation.devdb.devices.DevicesViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerTopScroller
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.messagepanel.AutoFitRecyclerView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Created by radiationx on 08.08.17.
 */

@AndroidEntryPoint
class DevicesFragment : TabFragment(), BaseAdapter.OnItemClickListener<Brand.DeviceItem>, TabTopScroller {

    @Inject lateinit var notesRepository: NotesRepository

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recyclerView: AutoFitRecyclerView
    private lateinit var adapter: DevicesAdapter
    private val dialogMenu = DynamicDialogMenu<DevicesFragment, Brand.DeviceItem>()

    private var listScrollY = 0
    private var appBarOffset = 0

    private lateinit var topScroller: RecyclerTopScroller


    private val presenter: DevicesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_brand)
        arguments?.apply {
            presenter.categoryId = getString(ARG_CATEGORY_ID, null)
            presenter.brandId = getString(ARG_BRAND_ID, null)
        }
    }

    override fun useTopBarRoundedBottomCorners(): Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_brand)
        refreshLayout = findViewById(R.id.swipe_refresh_list) as? androidx.swiperefreshlayout.widget.SwipeRefreshLayout ?: throw IllegalStateException("SwipeRefreshLayout not found")
        recyclerView = findViewById(R.id.base_list) as? AutoFitRecyclerView ?: throw IllegalStateException("AutoFitRecyclerView not found")
        contentController.setMainRefresh(refreshLayout)
        clearToolbarScrollFlags()
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListsBackground()
        ensureOpaquePinnedToolbarUnderlay()
        refreshLayoutStyle(refreshLayout)
        refreshLayout.setOnRefreshListener { presenter.loadBrand() }

        adapter = DevicesAdapter()
        adapter.setItemClickListener(this)
        recyclerView.setColumnWidth(requireContext().dpToPx(144))
        recyclerView.adapter = adapter
        tuneListRecyclerView(recyclerView)
        try {
            val gridLayoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.GridLayoutManager
            recyclerView.addItemDecoration(SpacingItemDecoration(gridLayoutManager, dp8))
        } catch (ex: Exception) {
            Timber.e(ex, "DevicesFragment setup error")
        }

        dialogMenu.apply {
            addItem(getString(R.string.copy_link)) { _, data ->
                presenter.copyLink(data)
            }
            addItem(getString(R.string.share)) { _, data ->
                presenter.shareLink(data)
            }
            addItem(getString(R.string.create_note)) { _, data ->
                presenter.createNote(data)
            }
        }

        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                listScrollY = recyclerView.computeVerticalScrollOffset()
                updateToolbarShadow()
            }
        })

        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, offset ->
            appBarOffset = offset
            updateToolbarShadow()
        })

        topScroller = RecyclerTopScroller(recyclerView, appBarLayout)

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun isShadowVisible(): Boolean {
        return true || appBarOffset != 0 || listScrollY > 0
    }

    override fun toggleScrollTop() {
        if (!::topScroller.isInitialized) return
        topScroller.toggleScrollTop()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.fragment_title_device_search)
                .setIcon(R.drawable.ic_toolbar_search)
                .setOnMenuItemClickListener {
                    presenter.openSearch()
                    false
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        refreshLayout.isRefreshing = isRefreshing
                    }
                }
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: DevicesUiEvent) {
        when (event) {
            is DevicesUiEvent.ShowData -> showData(event.brand)
            is DevicesUiEvent.ShowCreateNote -> showCreateNote(event.title, event.url)
        }
    }

    private fun showData(data: Brand) {
        setTitle(data.title)
        setTabTitle("${data.catTitle} ${data.title}")
        setSubtitle(data.catTitle)
        adapter.addAll(data.devices)
    }

    private fun showCreateNote(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(context, title, url, notesRepository)
    }

    override fun onItemClick(item: Brand.DeviceItem) {
        presenter.openDevice(item)
    }

    override fun onItemLongClick(item: Brand.DeviceItem): Boolean {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(requireContext(), this@DevicesFragment, item)
        }
        return false
    }

    class SpacingItemDecoration : androidx.recyclerview.widget.RecyclerView.ItemDecoration {
        private var spanCount = 1
        private var fullWidth = false
        private val includeEdge = true
        private var spacing: Int = 0
        private var manager: androidx.recyclerview.widget.GridLayoutManager? = null

        constructor(manager: androidx.recyclerview.widget.GridLayoutManager, spacing: Int) {
            this.spacing = spacing
            this.manager = manager
        }

        constructor(spacing: Int) {
            this.spacing = spacing
        }

        constructor(spacing: Int, fullWidth: Boolean) {
            this.spacing = spacing
            this.fullWidth = fullWidth
        }


        override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
            manager?.also {
                spanCount = it.spanCount
            }

            val position = parent.getChildAdapterPosition(view) // item position
            val column = position % spanCount // item column

            if (includeEdge) {
                if (!fullWidth) {
                    outRect.left = spacing - column * spacing / spanCount // spacing - column * ((1f / spanCount) * spacing)
                    outRect.right = (column + 1) * spacing / spanCount // (column + 1) * ((1f / spanCount) * spacing)
                }
                if (position < spanCount) { // top edge
                    outRect.top = spacing
                }
                outRect.bottom = spacing // item bottom
            } else {
                if (!fullWidth) {
                    outRect.left = column * spacing / spanCount // column * ((1f / spanCount) * spacing)
                    outRect.right = spacing - (column + 1) * spacing / spanCount // spacing - (column + 1) * ((1f /    spanCount) * spacing)
                }
                if (position >= spanCount) {
                    outRect.top = spacing // item top
                }
            }
        }
    }

    companion object {
        const val ARG_CATEGORY_ID = "CATEGORY_ID"
        const val ARG_BRAND_ID = "BRAND_ID"
    }
}
