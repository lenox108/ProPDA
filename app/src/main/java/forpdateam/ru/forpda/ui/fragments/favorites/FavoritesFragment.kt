package forpdateam.ru.forpda.ui.fragments.favorites

import android.app.Dialog
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.MenuItemCompat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.presentation.TabRouter
import javax.inject.Inject

import androidx.fragment.app.viewModels

import java.util.Arrays

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import forpdateam.ru.forpda.presentation.favorites.FavoritesUiEvent
import forpdateam.ru.forpda.presentation.favorites.FavoritesViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedAdapter
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.pagination.PaginationHelper
import forpdateam.ru.forpda.databinding.FavoriteSortingBinding
import forpdateam.ru.forpda.ui.fragments.search.applyToolbarSearchPlateChrome
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 22.09.16.
 */

@AndroidEntryPoint
class FavoritesFragment : RecyclerFragment() {

    @Inject lateinit var router: TabRouter

    private lateinit var dialogMenu: DynamicDialogMenu<FavoritesFragment, FavItem>
    private lateinit var adapter: FavoritesAdapter

    private lateinit var paginationHelper: PaginationHelper

    private var currentSortDialog: BottomSheetDialog? = null
    private var currentSorting: Sorting = Sorting("", "")
    private var searchMenuItem: MenuItem? = null
    private var markAllReadMenuItem: MenuItem? = null
    private var markAllReadProgressDialog: AlertDialog? = null
    private var markAllReadProgressText: TextView? = null

    private val presenter: FavoritesViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private val paginationListener = object : PaginationHelper.PaginationListener {
        override fun onTabSelected(tab: TabLayout.Tab): Boolean {
            return refreshLayout.isRefreshing
        }

        override fun onSelectedPage(pageNumber: Int) {
            presenter.loadFavorites(pageNumber)
        }
    }

    private val adapterListener = object : BaseSectionedAdapter.OnItemClickListener<FavItem> {
        override fun onItemClick(item: FavItem) {
            presenter.onItemClick(item)
        }

        override fun onItemLongClick(item: FavItem): Boolean {
            presenter.onItemLongClick(item)
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_favorite)
    }

    private fun getPinText(b: Boolean): CharSequence {
        return getString(if (b) R.string.fav_unpin else R.string.fav_pin)
    }

    private fun getSubText(subTypeIndex: Int): CharSequence {
        return String.format("%s (%s)", getString(R.string.fav_change_subscribe_type), getSubNames(requireContext())[subTypeIndex])
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        paginationHelper = PaginationHelper(requireActivity(), dimensionsProvider)
        paginationHelper.addInToolbar(
                inflater = inflater,
                target = toolbarLayout,
                enablePadding = configuration.fitSystemWindow,
                surfaceColorAttr = R.attr.main_toolbar_accent_surface,
        )
        contentController.setFirstLoad(false)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.copy_link)) { _, data ->
                presenter.copyLink(data)
            }
            addItem(getString(R.string.attachments)) { _, data ->
                presenter.openAttachments(data)
            }
            addItem(getString(R.string.open_theme_forum)) { _, data ->
                presenter.openForum(data)
            }
            addItem(getString(R.string.fav_change_subscribe_type)) { _, data ->
                presenter.showSubscribeDialog(data)
            }
            addItem(getPinText(false)) { _, data ->
                presenter.changeFav(FavoritesApi.ACTION_EDIT_PIN_STATE, if (data.isPin) "unpin" else "pin", data.favId)
            }
            addItem(getString(R.string.delete)) { _, data ->
                presenter.changeFav(FavoritesApi.ACTION_DELETE, null, data.favId)
            }
            addItem(getString(R.string.fav_mute_notifications)) { _, data ->
                presenter.toggleTopicMute(data)
            }
        }


        refreshLayout.setOnRefreshListener { presenter.refresh() }

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        adapter = FavoritesAdapter()
        adapter.paginationFooterBinder = { container ->
            paginationHelper.addInList(layoutInflater, container)
        }
        adapter.setOnItemClickListener(adapterListener)
        adapter.setOnItemDisplayListener { presenter.onItemDisplayed(it) }
        adapter.setOnItemTouchDownListener { presenter.onItemTouchDown(it) }
        recyclerView.adapter = adapter

        paginationHelper.setListener(paginationListener)

        presenter.start()
        observeViewModel()
        presenter.refresh()
    }

    override fun useCompactToolbarPaginationChrome(): Boolean = true

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        addSearchItem(menu)
        val item = menu.add(Menu.NONE, R.id.menu_fav_mark_all_read, Menu.NONE, getString(R.string.fav_mark_all_read))
                .setIcon(R.drawable.ic_toolbar_done)
                .setOnMenuItemClickListener {
                    hideKeyboard()
                    openMarkAllFavoritesReadConfirmDialog()
                    false
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        markAllReadMenuItem = item
        MenuItemCompat.setContentDescription(item, getString(R.string.fav_mark_all_read))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            MenuItemCompat.setTooltipText(item, getString(R.string.fav_mark_all_read))
        }
        menu.add(R.string.sorting_title)
                .setIcon(R.drawable.ic_toolbar_sort)
                .setOnMenuItemClickListener {
                    hideKeyboard()
                    val dialogBinding = FavoriteSortingBinding.inflate(LayoutInflater.from(requireContext()))
                    selectSortingInto(dialogBinding, currentSorting)
                    dialogBinding.sortingApply.setOnClickListener {
                        val key = when (dialogBinding.sortingKey.checkedRadioButtonId) {
                            R.id.sorting_key_last_post -> Sorting.Companion.Key.LAST_POST
                            R.id.sorting_key_title -> Sorting.Companion.Key.TITLE
                            else -> ""
                        }
                        val order = when (dialogBinding.sortingOrder.checkedRadioButtonId) {
                            R.id.sorting_order_asc -> Sorting.Companion.Order.ASC
                            R.id.sorting_order_desc -> Sorting.Companion.Order.DESC
                            else -> ""
                        }
                        viewLifecycleOwner.lifecycleScope.launch {
                            presenter.updateSorting(key, order)
                        }
                        currentSortDialog?.dismiss()
                    }
                    dialogBinding.sortingReset.setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            presenter.updateSorting("", "")
                        }
                        currentSortDialog?.dismiss()
                    }
                    currentSortDialog = BottomSheetDialog(requireContext())
                    currentSortDialog?.setOnShowListener { dialog1 ->
                        (dialog1 as Dialog).window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    }
                    currentSortDialog?.setContentView(dialogBinding.root)
                    currentSortDialog?.show()
                    false
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun addSearchItem(menu: Menu) {
        val item = menu.add(Menu.NONE, R.id.action_favorites_search, Menu.NONE, getString(R.string.search))
                .setIcon(R.drawable.ic_toolbar_search)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
        searchMenuItem = item

        val searchView = SearchView(requireContext())
        item.actionView = searchView
        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
        searchView.setSearchableInfo(searchManager?.getSearchableInfo(activity?.componentName))
        searchView.setIconifiedByDefault(true)
        searchView.queryHint = getString(R.string.favorites_search_hint)
        searchView.maxWidth = Int.MAX_VALUE
        searchView.applyToolbarSearchPlateChrome()
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                hideKeyboard()
                presenter.searchLocal(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                presenter.searchLocal(newText)
                return true
            }
        })
        item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                presenter.searchLocal("")
                return true
            }
        })
        MenuItemCompat.setContentDescription(item, getString(R.string.search))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            MenuItemCompat.setTooltipText(item, getString(R.string.search))
        }
    }

    private fun openMarkAllFavoritesReadConfirmDialog() {
        val ctx = context ?: return
        val count = presenter.getMarkAllFavoritesReadCount()
        if (count <= 0) {
            showSnackbar(R.string.fav_mark_all_read_nothing)
            return
        }
        MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.fav_mark_all_read_title)
                .setMessage(getString(R.string.fav_mark_all_read_confirm, count))
                .setPositiveButton(R.string.fav_mark_all_read_button) { _, _ ->
                    presenter.markAllFavoritesRead()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
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
                    presenter.markAllReadRunning.collect { isRunning ->
                        markAllReadMenuItem?.isEnabled = !isRunning
                        if (!isRunning) {
                            dismissMarkAllReadProgressDialog()
                        }
                    }
                }
                launch {
                    presenter.sortingFlow.collect { sorting ->
                        currentSorting = sorting
                        adapter.setSorting(sorting)
                    }
                }
                launch {
                    presenter.displayedItems.collect { items ->
                        if (items != null) {
                            onShowFavorite(items)
                        }
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

    private fun handleUiEvent(event: FavoritesUiEvent) {
        when (event) {
            is FavoritesUiEvent.InitSorting -> {
                currentSorting = event.sorting
                adapter.setSorting(event.sorting)
            }
            is FavoritesUiEvent.SetShowDot -> adapter.setShowDot(event.show)
            is FavoritesUiEvent.SetShowUnreadIndicators -> adapter.setShowUnreadIndicators(event.show)
            is FavoritesUiEvent.SetUnreadTop -> adapter.setUnreadTop(event.unreadTop)
            is FavoritesUiEvent.OnShowFavorite -> Unit
            is FavoritesUiEvent.OnLoadFavorites -> onLoadFavorites(event.data)
            is FavoritesUiEvent.OnMarkAllReadProgress -> onMarkAllReadProgress(event.progress.processed, event.progress.total)
            is FavoritesUiEvent.OnMarkAllRead -> onMarkAllRead(event.result.successCount, event.result.failCount)
            is FavoritesUiEvent.ShowItemDialogMenu -> showItemDialogMenu(event.item)
            is FavoritesUiEvent.OnChangeFav -> onChangeFav(event.result)
            is FavoritesUiEvent.ShowSubscribeDialog -> showSubscribeDialog(event.item)
            is FavoritesUiEvent.OnToggleMute -> showSnackbar(
                    if (event.nowMuted) R.string.fav_notifications_muted else R.string.fav_notifications_unmuted
            )
            is FavoritesUiEvent.ShowLoadError -> showLoadError(event.message)
            is FavoritesUiEvent.ShowNeedAuth -> Utils.showNeedAuthDialog(requireContext(), router)
        }
    }

    private fun onMarkAllReadProgress(processed: Int, total: Int) {
        if (markAllReadProgressDialog?.isShowing != true) {
            showMarkAllReadProgressDialog(processed, total)
        } else {
            markAllReadProgressText?.text = getString(R.string.fav_mark_all_read_progress, processed, total)
        }
    }

    private fun onMarkAllRead(successCount: Int, failCount: Int) {
        dismissMarkAllReadProgressDialog()
        val message = if (failCount > 0) {
            getString(R.string.fav_mark_all_read_result_with_errors, successCount, failCount)
        } else {
            getString(R.string.fav_mark_all_read_result, successCount)
        }
        showSnackbar(message)
        // Confirm server/cache state after the queued operation settles.
        presenter.refresh()
    }

    private fun showMarkAllReadProgressDialog(processed: Int, total: Int) {
        val ctx = context ?: return
        val padding = (24 * resources.displayMetrics.density).toInt()
        val progressText = TextView(ctx).apply {
            text = getString(R.string.fav_mark_all_read_progress, processed, total)
        }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padding, padding / 2, padding, padding / 2)
            addView(CircularProgressIndicator(ctx).apply { isIndeterminate = true })
            addView(progressText, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = padding / 2
            })
        }
        markAllReadProgressText = progressText
        markAllReadProgressDialog = MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.fav_mark_all_read_title)
                .setView(content)
                .setCancelable(false)
                .showWithStyledButtons()
    }

    private fun dismissMarkAllReadProgressDialog() {
        markAllReadProgressDialog?.dismiss()
        markAllReadProgressDialog = null
        markAllReadProgressText = null
    }

    private fun onLoadFavorites(data: FavData) {
        contentController.hideContent(ContentController.TAG_ERROR)
        currentSorting = data.sorting
        paginationHelper.updatePagination(data.pagination)
        clearToolbarPaginationSubtitle()
        listScrollTop()
    }

    private fun onShowFavorite(items: List<FavItem>) {
        if (items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_star)
                        .setTitle(R.string.funny_favorites_nodata_title)
                        .setDesc(R.string.funny_favorites_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        // Delay bindItems to prevent RecyclerView crash during layout
        recyclerView.post {
            adapter.bindItems(items)
        }
    }

    private fun showLoadError(message: String?) {
        if (adapter.itemCount > 0) {
            showSnackbar(message ?: getString(R.string.error_occurred))
            return
        }
        contentController.hideContent(ContentController.TAG_NO_DATA)
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_toolbar_refresh)
                    .setTitle(R.string.funny_favorites_error_title)
                    .setDesc(R.string.funny_favorites_error_desc)
                    .addAction(R.string.retry) { presenter.refresh() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.showContent(ContentController.TAG_ERROR)
        recyclerView.post { adapter.bindItems(emptyList()) }
        message?.let { showSnackbar(it) }
    }

    override fun onBackPressed(): Boolean {
        val item = searchMenuItem
        return if (item?.isActionViewExpanded == true) {
            item.collapseActionView()
            hideKeyboard()
            true
        } else {
            super.onBackPressed()
        }
    }

    private fun selectSortingInto(binding: FavoriteSortingBinding, sorting: Sorting) {
        val keyId = when (sorting.key) {
            Sorting.Companion.Key.LAST_POST -> R.id.sorting_key_last_post
            Sorting.Companion.Key.TITLE -> R.id.sorting_key_title
            else -> R.id.sorting_key_last_post
        }
        val orderId = when (sorting.order) {
            Sorting.Companion.Order.ASC -> R.id.sorting_order_asc
            Sorting.Companion.Order.DESC -> R.id.sorting_order_desc
            else -> R.id.sorting_order_asc
        }
        binding.sortingKey.check(keyId)
        binding.sortingOrder.check(orderId)
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissMarkAllReadProgressDialog()
        paginationHelper.destroy()
        if (::adapter.isInitialized) adapter.release()
    }

    private fun onChangeFav(result: Boolean) {
        if (result) {
            showSnackbar(R.string.action_complete)
        } else {
            showSnackbar(R.string.error_occurred)
        }
    }

    private fun showSubscribeDialog(item: FavItem) {
        val subTypeIndex = Arrays.asList(*FavoritesApi.SUB_TYPES).indexOf(item.subType)
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setSingleChoiceItems(getSubNames(requireContext()), subTypeIndex) { dialog, which ->
                    presenter.changeFav(FavoritesApi.ACTION_EDIT_SUB_TYPE, FavoritesApi.SUB_TYPES[which], item.favId)
                    dialog.dismiss()
                }
                .showWithStyledButtons()
    }

    private fun showItemDialogMenu(item: FavItem) {
        dialogMenu.apply {
            disallowAll()
            allow(0)
            if (!item.isForum) {
                allow(1)
                allow(2)
            }
            allow(3)
            allow(4)
            allow(5)
            // Меню mute доступно только для тем (не форумов).
            if (!item.isForum) allow(6)

            val index = containsIndex(getPinText(!item.isPin))
            if (index != -1)
                changeTitle(index, getPinText(item.isPin))

            val subTypeIndex = Arrays.asList(*FavoritesApi.SUB_TYPES).indexOf(item.subType)
            changeTitle(3, getSubText(subTypeIndex))

            // Переписываем заголовок mute-пункта в зависимости от текущего состояния.
            if (!item.isForum) {
                val muted = presenter.isTopicMuted(item)
                changeTitle(
                        6,
                        getString(
                                if (muted) R.string.fav_unmute_notifications
                                else R.string.fav_mute_notifications
                        )
                )
            }

            show(requireContext(), this@FavoritesFragment, item)
        }
    }

    companion object {
        @JvmStatic
        fun getSubNames(context: android.content.Context): Array<CharSequence> {
            return arrayOf(
                    context.getString(R.string.fav_subscribe_none),
                    context.getString(R.string.fav_subscribe_delayed),
                    context.getString(R.string.fav_subscribe_immediate),
                    context.getString(R.string.fav_subscribe_daily),
                    context.getString(R.string.fav_subscribe_weekly),
                    context.getString(R.string.fav_subscribe_pinned)
            )
        }
    }
}
