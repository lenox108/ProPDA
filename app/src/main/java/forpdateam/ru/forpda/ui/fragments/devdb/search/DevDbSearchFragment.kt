package forpdateam.ru.forpda.ui.fragments.devdb.search

import forpdateam.ru.forpda.common.dpToPx
import forpdateam.ru.forpda.databinding.FragmentBrandBinding
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.appcompat.widget.SearchView
import android.view.*
import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.entity.remote.devdb.Brand
import forpdateam.ru.forpda.presentation.devdb.search.DevDbSearchUiEvent
import forpdateam.ru.forpda.presentation.devdb.search.DevDbSearchViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.search.applyToolbarSearchPlateChrome
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesAdapter
import timber.log.Timber
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
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

/**
 * Created by radiationx on 09.11.17.
 */

@AndroidEntryPoint
class DevDbSearchFragment : TabFragment(), BaseAdapter.OnItemClickListener<Brand.DeviceItem> {
    @Inject lateinit var notesRepository: NotesRepository
    private var _searchBinding: FragmentBrandBinding? = null
    private val searchBinding get() = checkNotNull(_searchBinding) { "Binding accessed after onDestroyView" }

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private lateinit var adapter: DevicesAdapter
    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recyclerView: AutoFitRecyclerView
    private var searchView: SearchView? = null
    private var searchMenuItem: MenuItem? = null
    private val dialogMenu = DynamicDialogMenu<DevDbSearchFragment, Brand.DeviceItem>()

    private val presenter: DevDbSearchViewModel by viewModels()

    init {
        configuration.defaultTitle = "Поиск устройств"
    }

    override fun useTopBarRoundedBottomCorners(): Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _searchBinding = FragmentBrandBinding.inflate(inflater, fragmentContent, true)
        refreshLayout = searchBinding.swipeRefreshList
        recyclerView = searchBinding.baseList
        contentController.setMainRefresh(refreshLayout)
        return viewFragment
    }

    override fun onDestroyViewBinding() {
        _searchBinding = null
        super.onDestroyViewBinding()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListsBackground()
        refreshLayoutStyle(refreshLayout)
        clearToolbarScrollFlags()
        ensureOpaquePinnedToolbarUnderlay()
        refreshLayout.setOnRefreshListener { presenter.refresh() }

        adapter = DevicesAdapter()
        recyclerView.setColumnWidth(requireContext().dpToPx(144))
        recyclerView.adapter = adapter
        try {
            val gridLayoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.GridLayoutManager
            recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(gridLayoutManager, dp8))
        } catch (ex: Exception) {
            Timber.e(ex, "DevDbSearchFragment setup error")
        }
        tuneListRecyclerView(recyclerView)

        adapter.setItemClickListener(this)

        val toolbarSearchView = searchView ?: SearchView(requireContext()).also { searchView = it }

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
        if (searchManager != null) {
            toolbarSearchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
        }
        toolbarSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                presenter.search(query)
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })

        toolbarSearchView.queryHint = getString(R.string.search_keywords)
        toolbarSearchView.maxWidth = Int.MAX_VALUE
        toolbarSearchView.applyToolbarSearchPlateChrome()

        val searchEditFrame = toolbarSearchView.findViewById<LinearLayout>(R.id.search_edit_frame) ?: throw IllegalStateException("searchEditFrame not found")
        val params = searchEditFrame.layoutParams as? LinearLayout.LayoutParams ?: throw IllegalStateException("params not LinearLayout.LayoutParams")
        params.leftMargin = 0

        val searchSrcText = toolbarSearchView.findViewById<View>(R.id.search_src_text) ?: throw IllegalStateException("searchSrcText not found")
        searchSrcText.setPadding(0, searchSrcText.paddingTop, 0, searchSrcText.paddingBottom)

        searchMenuItem?.expandActionView()

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

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        toolbar.inflateMenu(R.menu.qms_contacts_menu)
        searchMenuItem = menu.findItem(R.id.action_search)
        searchView = searchMenuItem?.actionView as? SearchView ?: SearchView(requireContext()).also {
            searchMenuItem?.actionView = it
        }
        searchView?.setIconifiedByDefault(true)
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

    private fun handleUiEvent(event: DevDbSearchUiEvent) {
        when (event) {
            is DevDbSearchUiEvent.ShowData -> showData(event.brand, event.query)
            is DevDbSearchUiEvent.ShowCreateNote -> showCreateNote(event.title, event.url)
        }
    }

    private fun showData(data: Brand, query: String) {
        setTitle("Поиск $query")
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
            show(requireContext(), this@DevDbSearchFragment, item)
        }
        return false
    }

}
