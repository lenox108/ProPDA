package forpdateam.ru.forpda.ui.fragments.search

import javax.inject.Inject
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.showSnackbar
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ViewTreeObserver
import android.os.Bundle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.widget.ActionMenuView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SearchView
import com.google.android.material.textfield.TextInputEditText
import timber.log.Timber
import android.view.*
import android.widget.*
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.FragmentSearchBinding
import forpdateam.ru.forpda.databinding.SearchSettingsBinding
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.search.SearchViewModel
import forpdateam.ru.forpda.ui.fragments.search.SearchQueryHelper
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.views.*
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.pagination.PaginationHelper
import forpdateam.ru.forpda.ui.views.tooltip.CustomTooltip
import org.json.JSONObject
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.MenuMapper
import forpdateam.ru.forpda.model.data.remote.api.search.SearchParser
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter

/**
 * Created by radiationx on 29.01.17.
 *
 * Fragment для поиска по форуму.
 *
 * JS Bridge обоснование:
 * - SearchJsInterface: используется для навигации по результатам поиска и взаимодействием с UI поиска.
 * - Контент: результаты поиска с сервера 4pda.to (доверенный контент).
 * - Профиль безопасности: TRUSTED_STATIC_ARTICLE (JS для локального шаблона, IBase запрещён, только SearchJsInterface).
 */
@AndroidEntryPoint
class SearchFragment : TabFragment(), BaseAdapter.OnItemClickListener<SearchItem> {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var clipboardHelper: ClipboardHelper


    private var _searchBinding: FragmentSearchBinding? = null
    private val searchBinding get() = checkNotNull(_searchBinding) { "Binding accessed after onDestroyView" }
    private var _settingsBinding: SearchSettingsBinding? = null
    private val settingsBinding get() = checkNotNull(_settingsBinding) { "Binding accessed after onDestroyView" }

    // Legacy field accessors for search settings dialog
    private val searchSettingsView: ViewGroup get() = settingsBinding.root
    private val nickBlock: ViewGroup get() = settingsBinding.searchNickBlock
    private val resourceBlock: ViewGroup get() = settingsBinding.searchResourceBlock
    private val resultBlock: ViewGroup get() = settingsBinding.searchResultBlock
    private val sortBlock: ViewGroup get() = settingsBinding.searchSortBlock
    private val sourceBlock: ViewGroup get() = settingsBinding.searchSourceBlock
    private val resourceLayout: TextInputLayout get() = settingsBinding.searchResourceBlock
    private val resultLayout: TextInputLayout get() = settingsBinding.searchResultBlock
    private val sortLayout: TextInputLayout get() = settingsBinding.searchSortBlock
    private val sourceLayout: TextInputLayout get() = settingsBinding.searchSourceBlock
    private val nickField: TextView get() = settingsBinding.searchNickField
    private val submitButton: Button get() = settingsBinding.searchSubmit
    private val saveSettingsButton: Button get() = settingsBinding.searchSaveSettings


    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private val adapter = SearchAdapter()
    private var fabBehavior: FabOnScroll? = null


    private lateinit var paginationHelper: PaginationHelper
    private lateinit var dialogMenu: DynamicDialogMenu<SearchFragment, IBaseForumPost>
    private lateinit var searchQueryHelper: SearchQueryHelper


    private var searchView: SearchView? = null
    private var searchItem: MenuItem? = null
    private lateinit var settingsDialog: BottomSheetDialog
    private var tooltip: CustomTooltip? = null

    private lateinit var settingsMenuItem: MenuItem


    private val presenter: SearchViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_search)
        arguments?.apply {
            presenter.initSearchSettings(
                    getString(TabFragment.ARG_TAB),
                    getString(TabFragment.ARG_SUBTITLE),
            )
        }
    }

    fun updateScrollButtonState(isEnabled: Boolean) {
        fab.visibility = View.VISIBLE
    }

    override fun initFabBehavior() {
        val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val behavior = FabOnScroll(fab.context)
        // Search FAB only scrolls to the first result → keep a fixed ↑ icon (no scroll-driven ↓/↑ flip),
        // otherwise a down arrow appears while the action jumps to the top.
        behavior.flipIconOnScroll = false
        fabBehavior = behavior
        params.behavior = behavior
        params.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        fab.requestLayout()
    }

    @SuppressLint("JavascriptInterface")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        initFabBehavior()

        _searchBinding = FragmentSearchBinding.inflate(inflater, fragmentContent, true)
        refreshLayout = searchBinding.swipeRefreshList
        _settingsBinding = SearchSettingsBinding.inflate(inflater, null, false)

        recyclerView = androidx.recyclerview.widget.RecyclerView(requireContext())
        recyclerView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        refreshLayout.addView(recyclerView)

        paginationHelper = PaginationHelper(requireActivity(), dimensionsProvider)
        paginationHelper.addInToolbar(
                inflater = inflater,
                target = toolbarLayout,
                enablePadding = configuration.fitSystemWindow,
                surfaceColorAttr = R.attr.main_toolbar_accent_surface,
        )

        contentController.setMainRefresh(refreshLayout)
        return viewFragment
    }

    override fun onDestroyViewBinding() {
        _searchBinding = null
        _settingsBinding = null
        super.onDestroyViewBinding()
    }

    override fun useCompactToolbarPaginationChrome(): Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()
        ensureOpaquePinnedToolbarUnderlay()

        // Initialize BottomSheetDialog with STATE_EXPANDED to prevent IME clipping
        settingsDialog = BottomSheetDialog(requireContext())
        settingsDialog.setOnShowListener {
            val bottomSheet = settingsDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            settingsDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        // Initialize searchQueryHelper first before using it
        searchQueryHelper = SearchQueryHelper(
                context = requireContext(),
                onSearchSubmit = { query, nick ->
                    presenter.search(query, nick)
                    settingsDialog.dismiss()
                },
                onSettingsUpdate = { field, position ->
                    presenter.updateSettings(field, position)
                },
                onSettingsSave = {
                    lifecycleScope.launch { presenter.saveSettings() }
                }
        )

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.topic_to_begin)) { _, data ->
                presenter.openTopicBegin(data)
            }
            addItem(getString(R.string.topic_newposts)) { _, data ->
                presenter.openTopicNew(data)
            }
            addItem(getString(R.string.topic_lastposts)) { _, data ->
                presenter.openTopicLast(data)
            }
            addItem(getString(R.string.copy_link)) { _, data ->
                presenter.copyLink(data)
            }
            addItem(getString(R.string.open_theme_forum)) { _, data ->
                presenter.openForum(data)
            }
            addItem(getString(R.string.add_to_favorites)) { _, data ->
                presenter.onClickAddInFav(data)
            }
        }


        // Результаты рендерятся нативным RecyclerView (WebView удалён): FAB прокручивает список наверх.
        fab.setImageDrawable(fab.context.getVecDrawable(R.drawable.ic_arrow_up))
        fab.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
            fabBehavior?.resetHideTimer(fab)
        }

        fab.size = FloatingActionButton.SIZE_MINI
        fab.visibility = View.VISIBLE
        fab.scaleX = 0.0f
        fab.scaleY = 0.0f
        fab.alpha = 0.0f

        setListsBackground()

        paginationHelper.setListener(object : PaginationHelper.PaginationListener {
            override fun onTabSelected(tab: TabLayout.Tab): Boolean {
                return refreshLayout.isRefreshing
            }

            override fun onSelectedPage(pageNumber: Int) {
                presenter.search(pageNumber)
            }
        })

        //searchSettingsView.setVisibility(View.GONE);
        //dialog.setPeekHeight(App.getKeyboardHeight());
        //dialog.getWindow().getDecorView().setFitsSystemWindows(true);


        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
        val toolbarSearchView = searchView
        if (toolbarSearchView != null && searchManager != null) {
            searchQueryHelper.setupSearchView(toolbarSearchView, searchManager, activity?.componentName)
        }
        toolbarSearchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                presenter.search(query, nickField.text.toString())
                // Consume (true): we've handled the search ourselves. Returning false let the framework ALSO
                // dispatch ACTION_SEARCH to the searchable-activity (setSearchableInfo is set) — a redundant
                // second search / focus churn.
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })

        toolbarSearchView?.let { searchQueryHelper.setupSubmitButton(submitButton, it, nickField) }
        searchQueryHelper.setupSaveButton(saveSettingsButton)
        //recyclerView.setHasFixedSize(true);
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(dp8, true))
        recyclerView.adapter = adapter
        // Native forum-post-search rendering (Фаза 7: WebView-движок тем удалён). Post-body hits are
        // rendered by [SearchPostBodyRenderer] into the RecyclerView instead of the legacy ExtendedWebView.
        adapter.bodyRenderer = SearchPostBodyRenderer(linkHandler) { urls, index ->
            if (urls.isNotEmpty()) {
                forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
                        .startActivity(requireContext(), ArrayList(urls), index.coerceIn(0, urls.size - 1))
            }
        }
        tuneListRecyclerView(recyclerView)
        refreshLayoutStyle(refreshLayout)
        refreshLayoutLongTrigger(refreshLayout)
        refreshLayout.setOnRefreshListener { presenter.refreshData() }
        adapter.setOnItemClickListener(this)

        lifecycleScope.launch {
            if (otherPreferencesHolder.getTooltipSearchSettings()) {
                for (toolbarChildIndex in 0 until toolbar.childCount) {
                    val childView = toolbar.getChildAt(toolbarChildIndex)
                    if (childView is ActionMenuView) {
                        for (menuChildIndex in 0 until childView.childCount) {
                            try {
                                val itemView = childView.getChildAt(menuChildIndex) as ActionMenuItemView
                                if (settingsMenuItem === itemView.itemData) {
                                    tooltip = CustomTooltip(
                                        requireContext(),
                                        itemView,
                                        getString(R.string.tooltip_search_settings),
                                        Gravity.BOTTOM
                                    ).apply {
                                        show()
                                    }
                                    break
                                }
                            } catch (ignore: ClassCastException) {
                            }

                        }
                        break
                    }
                }
                otherPreferencesHolder.setTooltipSearchSettings(false)
            }
        }

        presenter.start()
        observeViewModel()
    }


    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.copy_link)
                .setOnMenuItemClickListener {
                    presenter.copyLink()
                    false
                }
        toolbar.inflateMenu(R.menu.qms_contacts_menu)

        settingsMenuItem = menu.add(R.string.settings)
                .setIcon(R.drawable.ic_toolbar_tune)
                .setOnMenuItemClickListener {
                    if (searchSettingsView.parent != null && searchSettingsView.parent is ViewGroup) {
                        (searchSettingsView.parent as ViewGroup).removeView(searchSettingsView)
                    }
                    settingsDialog.setContentView(searchSettingsView)
                    settingsDialog.show()
                    presenter.emitFillSettings()
                    false
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

        searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem?.actionView as? SearchView ?: SearchView(requireContext()).also {
            searchItem?.actionView = it
        }
        searchView?.setIconifiedByDefault(true)
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        searchItem?.expandActionView()
        // onPauseOrHide() сворачивает action-view (collapseActionView очищает текст SearchView), поэтому при
        // возврате на экран поиска (жест «назад» из открытого результата) поле показывало только плейсхолдер
        // «Keywords», хотя результаты в списке правильные. Возвращаем набранный запрос из ViewModel. post{}
        // — т.к. expandActionView() внутри вызывает onActionViewExpanded → setQuery("") уже после нашего
        // вызова; false — не сабмитим повторно, список результатов не трогаем.
        val query = presenter.currentQuery()
        if (query.isNotEmpty()) {
            searchView?.post { searchView?.setQuery(query, false) }
        }
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        hideKeyboard()
        searchItem?.collapseActionView()
        // Clear focus from search view to prevent IME issues when returning
        searchView?.clearFocus()
    }

    override fun onBackPressed(): Boolean {
        tooltip?.also {
            if (it.isShowing) {
                it.dismiss()
                return true
            }
        }
        if (::settingsDialog.isInitialized && settingsDialog.isShowing) {
            settingsDialog.dismiss()
            return true
        }
        if (searchItem?.isActionViewExpanded == true) {
            searchItem?.collapseActionView()
            searchView?.clearFocus()
            hideKeyboard()
            return true
        }
        return super.onBackPressed()
    }

    // Read-only зеркало onBackPressed: перехватываем «назад» пока открыт tooltip,
    // диалог настроек или раскрыт поиск (см. hasBackHandling в TabFragment).
    override fun hasBackHandling(): Boolean =
            (tooltip?.isShowing == true) ||
                    (::settingsDialog.isInitialized && settingsDialog.isShowing) ||
                    (searchItem?.isActionViewExpanded == true)

    fun showAddInFavDialog(item: IBaseForumPost) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setItems(FavoritesFragment.getSubNames(requireContext())) { _, which ->
                    presenter.addTopicToFavorite(item.topicId, FavoritesApi.SUB_TYPES[which])
                }
                .showWithStyledButtons()
    }

    fun onAddToFavorite(result: Boolean) {
        showSnackbar(if (result) getString(R.string.favorites_added) else getString(R.string.error_occurred))
        refreshToolbarMenuItems(true)
    }

    fun fillSettingsData(settings: SearchSettings, fields: Map<String, List<String>>) {
        searchQueryHelper.setupSpinners(resourceLayout, resultLayout, sortLayout, sourceLayout, fields)
        searchView?.let {
            searchQueryHelper.fillSettingsData(it, nickField, resourceLayout, resultLayout, sortLayout, sourceLayout, settings, fields)
        }
    }

    fun setNewsMode() {
        searchQueryHelper.setNewsMode(nickBlock, resultBlock, sortBlock, sourceBlock)
    }

    fun setForumMode() {
        searchQueryHelper.setForumMode(nickBlock, resultBlock, sortBlock, sourceBlock)
    }

    fun onStartSearch(settings: SearchSettings) {
        hideKeyboard()
        setTitle(searchQueryHelper.buildSearchTitle(settings, presenter.scopedForumTitleForUi()))
    }

    fun showData(searchResult: SearchResult) {
        contentController.hideContent(ContentController.TAG_ERROR)
        setRefreshing(false)
        // NB: no scroll-to-top / hideKeyboard here. showData is driven by the [currentData] StateFlow, which
        // REPLAYS on every view re-create (e.g. returning to the results via Back) — resetting the scroll
        // there threw the user back to the top of the list. Those side effects moved to the one-shot ShowData
        // event (fired only for a genuinely new search / page), see [handleUiEvent].
        if (BuildConfig.DEBUG) {
            Timber.d("SEARCH SIZE %d", searchResult.items.size)
        }
        if (searchResult.items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_search)
                        .setTitle(R.string.funny_search_nodata_title)
                        .setDesc(R.string.funny_search_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        // Фаза 7: forum-post-search results are rendered NATIVELY (RecyclerView + [SearchPostBodyRenderer])
        // like every other result type — the legacy ExtendedWebView path was removed. [postMode] switches the
        // adapter to the full-post layout for «поиск по сообщениям форума» (rich body), plain cards otherwise.
        val newPostMode = searchResult.settings?.result == SearchSettings.RESULT_POSTS.first
                && searchResult.settings?.resourceType == SearchSettings.RESOURCE_FORUM.first
        val modeChanged = adapter.postMode != newPostMode
        adapter.postMode = newPostMode
        adapter.addAll(searchResult.items, true)
        if (modeChanged) adapter.notifyDataSetChanged()

        paginationHelper.updatePagination(searchResult.pagination)
        clearToolbarPaginationSubtitle()
    }


    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        paginationHelper.destroy()
    }

    override fun onItemClick(item: SearchItem) {
        presenter.onItemClick(item)
    }

    override fun onItemLongClick(item: SearchItem): Boolean {
        presenter.onItemLongClick(item)
        return false
    }

    fun showItemDialogMenu(item: SearchItem, settings: SearchSettings) {
        dialogMenu.apply {
            disallowAll()
            if (settings.resourceType == SearchSettings.RESOURCE_NEWS.first) {
                allow(3)
            } else {
                allowAll()
            }
            show(requireContext(), this@SearchFragment, item)
        }
    }


    /* JS PRESENTER */

    fun showNoteCreate(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(requireContext(), title, url, notesRepository)
    }

    fun firstPage() {
        paginationHelper.firstPage()
    }

    fun prevPage() {
        paginationHelper.prevPage()
    }

    fun nextPage() {
        paginationHelper.nextPage()
    }

    fun lastPage() {
        paginationHelper.lastPage()
    }

    fun selectPage() {
        paginationHelper.selectPageDialog()
    }


    fun toast(text: String) {
        showSnackbar(text)
    }

    fun log(text: String) {
        val maxLogSize = 1000
        for (i in 0..text.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > text.length) text.length else end
            Timber.v(text.substring(start, end))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        setRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.currentData.collect { data ->
                        if (data != null) {
                            showData(data)
                        }
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: forpdateam.ru.forpda.presentation.search.SearchUiEvent) {
        when (event) {
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.UpdateScrollButtonState -> updateScrollButtonState(event.enabled)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.FillSettingsData -> fillSettingsData(event.settings, event.fields)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.OnStartSearch -> onStartSearch(event.settings)
            // One-shot (SharedFlow, not replayed): a genuinely new search / page landed → jump to the top and
            // drop the keyboard. Rendering itself is handled by the [currentData] collector (see observeViewModel).
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowData -> {
                recyclerView.scrollToPosition(0)
                hideKeyboard()
            }
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowLoadError -> showLoadError(event.message)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowInitialState -> showInitialState()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.SetNewsMode -> setNewsMode()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.SetForumMode -> setForumMode()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowItemDialogMenu -> showItemDialogMenu(event.item, event.settings)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowAddInFavDialog -> showAddInFavDialog(event.item)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.OnAddToFavorite -> onAddToFavorite(event.result)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.FirstPage -> firstPage()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.PrevPage -> prevPage()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.NextPage -> nextPage()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.LastPage -> lastPage()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.SelectPage -> selectPage()
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowNoteCreate -> showNoteCreate(event.title, event.url)
            // Defensive fallback for any event without a UI action (поиск рендерится нативно, тап → открыть пост).
            else -> Unit
        }
    }

    private fun showInitialState() {
        contentController.hideContent(ContentController.TAG_ERROR)
        if (!contentController.contains(ContentController.TAG_NO_DATA)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_search)
                    .setTitle(R.string.funny_search_initial_title)
                    .setDesc(R.string.funny_search_initial_desc)
            contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
        }
        contentController.showContent(ContentController.TAG_NO_DATA)
    }

    private fun showLoadError(message: String?) {
        val hasData = presenter.currentData.value != null && adapter.itemCount > 0
        if (hasData) {
            showSnackbar(message ?: getString(R.string.error_occurred))
            return
        }
        contentController.hideContent(ContentController.TAG_NO_DATA)
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_search)
                    .setTitle(R.string.funny_search_error_title)
                    .setDesc(R.string.funny_search_error_desc)
                    .addAction(R.string.retry) { presenter.refreshData() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.showContent(ContentController.TAG_ERROR)
        adapter.clear()
        showSnackbar(message ?: getString(R.string.error_occurred))
    }

    companion object {
        private val LOG_TAG = SearchFragment::class.java.simpleName
    }

}
