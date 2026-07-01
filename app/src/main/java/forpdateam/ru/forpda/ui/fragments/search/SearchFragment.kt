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
import android.webkit.WebView
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
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.databinding.FragmentSearchBinding
import forpdateam.ru.forpda.databinding.SearchSettingsBinding
import forpdateam.ru.forpda.common.extractPostBodyHtml
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.search.SearchViewModel
import forpdateam.ru.forpda.presentation.search.SearchJsInterface
import forpdateam.ru.forpda.common.webview.WebViewLoadDispatchPolicy
import forpdateam.ru.forpda.common.webview.WebViewRenderController
import forpdateam.ru.forpda.common.webview.WebViewRenderSession
import forpdateam.ru.forpda.diagnostic.WebViewRenderDiagnostics
import forpdateam.ru.forpda.presentation.search.SearchWebRenderPolicy
import forpdateam.ru.forpda.ui.fragments.search.SearchQueryHelper
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.fragments.theme.ThemeDialogsHelper_V2
import forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb
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
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
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
class SearchFragment : TabFragment(), ExtendedWebView.JsLifeCycleListener, BaseAdapter.OnItemClickListener<SearchItem> {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var avatarRepository: AvatarRepository


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


    private lateinit var webView: ExtendedWebView
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private val adapter = SearchAdapter()
    private var webViewClient: CustomWebViewClient? = null
    private var webChromeClient: CustomWebChromeClient? = null
    private var fabBehavior: FabOnScroll? = null


    private lateinit var paginationHelper: PaginationHelper
    private lateinit var dialogMenu: DynamicDialogMenu<SearchFragment, IBaseForumPost>
    private lateinit var searchQueryHelper: SearchQueryHelper


    private var searchView: SearchView? = null
    private var searchItem: MenuItem? = null
    private lateinit var settingsDialog: BottomSheetDialog
    private var tooltip: CustomTooltip? = null

    private lateinit var settingsMenuItem: MenuItem


    private lateinit var jsInterface: SearchJsInterface

    // Nullable + view-scoped: created in onViewCreated() with the view lifecycle
    // scope and cleared in onDestroyView(). Constructing it from onCreate() touched
    // viewLifecycleOwner before the view existed and crashed.
    private var dialogsHelper: ThemeDialogsHelper_V2? = null

    private var pendingSearchHtml: String? = null
    private var pendingSearchHtmlHash: Int = 0
    private var searchRenderGeneration = 0
    private var searchLoadDispatched = false
    private var searchDomConfirmedGeneration = 0
    private var searchBlankRetryCount = 0
    private var searchLayoutDispatchGeneration = -1
    private var searchLayoutDispatchListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var searchBlankVerifyRunnable: Runnable? = null

    /**
     * Additive shared-controller mirror (Phase 9). Runs alongside the existing
     * [searchRenderGeneration] / [WebViewLoadDispatchPolicy] system without changing any dispatch
     * decision — diagnostics only. The existing per-feature logic stays authoritative.
     */
    private val sharedRenderController = WebViewRenderController()
    private var sharedRenderSession: WebViewRenderSession? = null

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

    fun updateShowAvatarState(isShow: Boolean) {
        webView.evalJs("updateShowAvatarState($isShow)")
    }

    fun updateTypeAvatarState(isCircle: Boolean) {
        webView.evalJs("updateTypeAvatarState($isCircle)")
    }

    fun updateScrollButtonState(isEnabled: Boolean) {
        fab.visibility = View.VISIBLE
    }

    fun setFontSize(size: Int) {
        webView.setRelativeFontSize(size)
    }

    private fun setAppFontMode(mode: forpdateam.ru.forpda.ui.AppFontMode) {
        webView.setAppFontMode(mode)
    }

    override fun initFabBehavior() {
        val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val behavior = FabOnScroll(fab.context)
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

        webView = ExtendedWebView(requireContext()).also {
            it.systemLinkHandler = systemLinkHandler
            it.init(WebViewSecurityProfile.TRUSTED_STATIC_ARTICLE)
        }
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                linkHandler,
                systemLinkHandler,
                router,
                clipboardHelper
        ))
        attachWebView(webView)
        recyclerView = androidx.recyclerview.widget.RecyclerView(requireContext())
        recyclerView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        webView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
        jsInterface = SearchJsInterface(presenter)
        dialogsHelper = ThemeDialogsHelper_V2(
                requireContext(),
                authHolder,
                otherPreferencesHolder,
                topicPreferencesHolder,
                scope = viewLifecycleOwner.lifecycleScope,
                enableForumBlacklistMenu = false
        )
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


        var currentFabDirection = ExtendedWebView.DIRECTION_DOWN
        fab.setOnClickListener {
            if (currentFabDirection == ExtendedWebView.DIRECTION_DOWN) {
                webView.pageDown(true)
            } else if (currentFabDirection == ExtendedWebView.DIRECTION_UP) {
                webView.pageUp(true)
            }
            fabBehavior?.resetHideTimer(fab)
        }
        webView.setOnDirectionListener(object : ExtendedWebView.OnDirectionListener {
            override fun onDirectionChanged(direction: Int) {
                currentFabDirection = direction
                if (direction == ExtendedWebView.DIRECTION_DOWN) {
                    fab.setImageDrawable(fab.context.getVecDrawable(R.drawable.ic_arrow_down))
                } else if (direction == ExtendedWebView.DIRECTION_UP) {
                    fab.setImageDrawable(fab.context.getVecDrawable(R.drawable.ic_arrow_up))
                }
            }
        })

        webView.setJsLifeCycleListener(this)
        webView.addJavascriptInterface(jsInterface, SEARCH_JS_INTERFACE)
        webView.setRelativeFontSize(mainPreferencesHolder.getWebViewFontSize())

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
                return false
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

    fun setStyleType(type: String) {
        webView.evalJs("changeStyleType(\"$type\")")
    }

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

    private fun startSearch() {
        searchView?.let { searchQueryHelper.setupSubmitButton(submitButton, it, nickField) }
    }

    fun onStartSearch(settings: SearchSettings) {
        hideKeyboard()
        setTitle(searchQueryHelper.buildSearchTitle(settings, presenter.scopedForumTitleForUi()))
    }

    fun showData(searchResult: SearchResult) {
        contentController.hideContent(ContentController.TAG_ERROR)
        setRefreshing(false)
        recyclerView.scrollToPosition(0)
        hideKeyboard()
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
        if (
                searchResult.settings?.result == SearchSettings.RESULT_POSTS.first
                && searchResult.settings?.resourceType == SearchSettings.RESOURCE_FORUM.first
        ) {
            for (i in 0 until refreshLayout.childCount) {
                if (refreshLayout.getChildAt(i) is androidx.recyclerview.widget.RecyclerView) {
                    refreshLayout.removeViewAt(i)
                    fixTargetView()
                    break
                }
            }
            if (refreshLayout.childCount <= 1) {
                refreshLayout.addView(webView)
                Timber.d("add webview")
            }
            val client = webViewClient ?: SearchWebViewClient().also { webViewClient = it }
            webView.webViewClient = client
            val chrome = webChromeClient ?: CustomWebChromeClient().also { webChromeClient = it }
            webView.webChromeClient = chrome
            if (BuildConfig.DEBUG) {
                Timber.d("SEARCH SHOW WEBVIEW")
            }
            queueSearchHtmlLoad(searchResult.html ?: "")
        } else {
            for (i in 0 until refreshLayout.childCount) {
                if (refreshLayout.getChildAt(i) is ExtendedWebView) {
                    refreshLayout.removeViewAt(i)
                    fixTargetView()
                }
            }
            if (refreshLayout.childCount <= 1) {
                fab.visibility = View.GONE
                refreshLayout.addView(recyclerView)
                Timber.d("add recyclerview")
            }
            if (BuildConfig.DEBUG) {
                Timber.d("SEARCH SHOW RECYCLERVIEW")
            }
            adapter.addAll(searchResult.items, true)
        }

        paginationHelper.updatePagination(searchResult.pagination)
        clearToolbarPaginationSubtitle()
    }


    //Поле mTarget это вьюха, от которой зависит обработка движений
    private fun fixTargetView() {
        try {
            val field = refreshLayout.javaClass.getDeclaredField("mTarget")
            field.isAccessible = true
            field.set(refreshLayout, null)
            field.isAccessible = false
        } catch (e: NoSuchFieldException) {
            Timber.e(e, "SwipeRefreshLayout reflection error")
        } catch (e: IllegalAccessException) {
            Timber.e(e, "SwipeRefreshLayout reflection error")
        }

    }

    override fun onDestroyView() {
        cancelSearchBlankVerify()
        detachSearchLayoutDispatchListener()
        unregisterForContextMenu(webView)
        if (::jsInterface.isInitialized) jsInterface.cancel()
        webView.removeJavascriptInterface(SEARCH_JS_INTERFACE)
        webView.setJsLifeCycleListener(null)
        webView.endWork()
        pendingSearchHtml = null
        pendingSearchHtmlHash = 0
        searchLoadDispatched = false
        sharedRenderController.cleanup()
        sharedRenderSession = null
        dialogsHelper = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        paginationHelper.destroy()
    }

    override fun onDomContentComplete(actions: ArrayList<String>) {
        setRefreshing(false)
        val generation = searchRenderGeneration
        cancelSearchBlankVerify()
        searchBlankVerifyRunnable = Runnable { verifySearchRender(generation, "dom_content_complete") }
        searchBlankVerifyRunnable?.let { webView.postDelayed(it, 48L) }
    }

    override fun onPageComplete(actions: ArrayList<String>) {
        actions.add("window.scrollTo(0, 0);")
    }

    private fun searchLoadSnapshot(): WebViewLoadDispatchPolicy.Snapshot =
            WebViewLoadDispatchPolicy.Snapshot(
                    pendingTargetId = searchRenderGeneration,
                    pendingContentHash = pendingSearchHtmlHash,
                    loadDispatched = searchLoadDispatched,
                    requestGeneration = searchRenderGeneration,
                    domConfirmedGeneration = searchDomConfirmedGeneration,
            )

    private fun queueSearchHtmlLoad(html: String) {
        if (html.isBlank()) return
        val htmlHash = html.hashCode()
        if (SearchWebRenderPolicy.isDuplicateQueuedHtml(htmlHash, pendingSearchHtmlHash, searchRenderGeneration)) {
            if (WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
                            force = false,
                            targetId = searchRenderGeneration,
                            contentHash = htmlHash,
                            snapshot = searchLoadSnapshot(),
                    )
            ) {
                return
            }
            scheduleSearchHtmlLoad()
            return
        }
        pendingSearchHtml = html
        pendingSearchHtmlHash = htmlHash
        searchRenderGeneration++
        searchLoadDispatched = false
        searchDomConfirmedGeneration = 0
        searchBlankRetryCount = 0
        val sharedSession = sharedRenderController.beginRender(
                owner = WebViewRenderSession.Owner.SEARCH,
                targetId = searchRenderGeneration,
                contentHash = htmlHash,
        )
        sharedRenderSession = sharedSession
        if (BuildConfig.DEBUG) {
            WebViewRenderDiagnostics.log(
                    sharedSession,
                    WebViewRenderDiagnostics.Event.RENDER_REQUESTED,
                    mapOf("searchRenderGeneration" to searchRenderGeneration),
            )
        }
        scheduleSearchHtmlLoad()
    }

    private fun scheduleSearchHtmlLoad() {
        if (!::webView.isInitialized) return
        webView.post { dispatchSearchHtmlLoad(force = false) }
    }

    private fun dispatchSearchHtmlLoad(force: Boolean) {
        if (!::webView.isInitialized || !isAdded || view == null) return
        val html = pendingSearchHtml ?: return
        val generation = searchRenderGeneration
        if (WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
                        force = force,
                        targetId = generation,
                        contentHash = pendingSearchHtmlHash,
                        snapshot = searchLoadSnapshot(),
                )
        ) {
            return
        }
        if (SearchWebRenderPolicy.shouldDeferHtmlLoad(webView.width, webView.height)) {
            scheduleSearchLayoutDispatch(generation)
            return
        }
        detachSearchLayoutDispatchListener()
        searchLoadDispatched = true
        if (BuildConfig.DEBUG) {
            Timber.d(
                    "SEARCH load htmlLen=%d webView=%dx%d generation=%d force=%s",
                    html.length,
                    webView.width,
                    webView.height,
                    generation,
                    force
            )
        }
        webView.loadDataWithBaseURL(
                SearchWebRenderPolicy.SEARCH_BASE_URL,
                html,
                "text/html",
                "utf-8",
                null
        )
        sharedRenderSession?.let { session ->
            if (session.targetId == generation) {
                sharedRenderController.markLoadDispatched(session)
                if (BuildConfig.DEBUG) {
                    WebViewRenderDiagnostics.log(session, WebViewRenderDiagnostics.Event.LOAD_DISPATCHED)
                }
            }
        }
    }

    private fun scheduleSearchLayoutDispatch(generation: Int) {
        if (!::webView.isInitialized || generation != searchRenderGeneration || searchLoadDispatched) return
        if (!SearchWebRenderPolicy.shouldDeferHtmlLoad(webView.width, webView.height)) {
            dispatchSearchHtmlLoad(force = false)
            return
        }
        if (searchLayoutDispatchGeneration == generation && searchLayoutDispatchListener != null) {
            return
        }
        detachSearchLayoutDispatchListener()
        searchLayoutDispatchGeneration = generation
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!::webView.isInitialized ||
                    generation != searchRenderGeneration ||
                    searchLoadDispatched ||
                    !isAdded ||
                    view == null
            ) {
                detachSearchLayoutDispatchListener()
                return@OnGlobalLayoutListener
            }
            if (SearchWebRenderPolicy.shouldDeferHtmlLoad(webView.width, webView.height)) {
                return@OnGlobalLayoutListener
            }
            detachSearchLayoutDispatchListener()
            dispatchSearchHtmlLoad(force = false)
        }
        searchLayoutDispatchListener = listener
        webView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun detachSearchLayoutDispatchListener() {
        val listener = searchLayoutDispatchListener ?: return
        if (::webView.isInitialized) {
            val observer = webView.viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(listener)
            }
        }
        searchLayoutDispatchListener = null
        searchLayoutDispatchGeneration = -1
    }

    private fun cancelSearchBlankVerify() {
        val runnable = searchBlankVerifyRunnable ?: return
        if (::webView.isInitialized) {
            webView.removeCallbacks(runnable)
        }
        searchBlankVerifyRunnable = null
    }

    private fun verifySearchRender(generation: Int, source: String) {
        if (!::webView.isInitialized || !isAdded || view == null || generation != searchRenderGeneration) {
            return
        }
        val script = """
            (function(){
                var posts=document.querySelectorAll('.post_container[data-post-id]').length;
                return JSON.stringify({posts:posts,scrollHeight:Math.max(document.documentElement.scrollHeight||0,document.body.scrollHeight||0)});
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (!isAdded || view == null || generation != searchRenderGeneration) return@evaluateJavascript
            val raw = result
                    ?.let { runCatching { JSONObject("{\"v\":$it}").optString("v") }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }
            val domPostCount = runCatching {
                JSONObject(raw ?: "{}").optInt("posts")
            }.getOrDefault(0)
            val contentHeight = webView.contentHeight
            if (SearchWebRenderPolicy.isBodyVisible(contentHeight, domPostCount)) {
                searchDomConfirmedGeneration = generation
                searchBlankRetryCount = 0
                sharedRenderSession?.let { session ->
                    if (session.targetId == generation) {
                        sharedRenderController.markDomConfirmed(session)
                        if (BuildConfig.DEBUG) {
                            WebViewRenderDiagnostics.log(
                                    session,
                                    WebViewRenderDiagnostics.Event.DOM_CONFIRMED,
                                    mapOf("source" to source, "posts" to domPostCount),
                            )
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    Timber.d(
                            "SEARCH render confirmed source=%s generation=%d contentHeight=%d posts=%d",
                            source,
                            generation,
                            contentHeight,
                            domPostCount
                    )
                }
                return@evaluateJavascript
            }
            handleSearchBlankBody(generation, source, contentHeight, domPostCount)
        }
    }

    private fun handleSearchBlankBody(
            generation: Int,
            source: String,
            contentHeight: Int,
            domPostCount: Int,
    ) {
        if (generation != searchRenderGeneration || !isAdded || view == null) return
        searchBlankRetryCount++
        if (BuildConfig.DEBUG) {
            Timber.w(
                    "SEARCH blank body source=%s generation=%d retry=%d contentHeight=%d posts=%d",
                    source,
                    generation,
                    searchBlankRetryCount,
                    contentHeight,
                    domPostCount
            )
        }
        when (SearchWebRenderPolicy.blankRecoveryDecision(searchBlankRetryCount)) {
            SearchWebRenderPolicy.BlankRecovery.RERENDER_CACHED -> {
                searchLoadDispatched = false
                webView.post { dispatchSearchHtmlLoad(force = true) }
            }
            SearchWebRenderPolicy.BlankRecovery.REFETCH -> presenter.refreshData()
            SearchWebRenderPolicy.BlankRecovery.GIVE_UP -> {
                showSnackbar(getString(R.string.error_occurred))
            }
        }
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

    fun deletePostUi(post: IBaseForumPost) {
        webView.evalJs("onDeletePostClick(" + post.id + ");")
    }

    fun openAnchorDialog(post: IBaseForumPost, anchorName: String) {
        dialogsHelper?.openAnchorDialog(presenter, post, anchorName)
    }

    fun openSpoilerLinkDialog(post: IBaseForumPost, spoilNumber: String) {
        dialogsHelper?.openSpoilerLinkDialog(presenter, post, spoilNumber)
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

    fun showUserMenu(post: IBaseForumPost) {
        dialogsHelper?.showUserMenu(presenter, post)
    }

    fun showReputationMenu(post: IBaseForumPost) {
        dialogsHelper?.showReputationMenu(presenter, post)
    }

    fun showPostMenu(post: IBaseForumPost) {
        dialogsHelper?.showPostMenu(presenter, post)
    }

    fun reportPost(post: IBaseForumPost) {
        dialogsHelper?.tryReportPost(presenter, post)
    }

    fun deletePost(post: IBaseForumPost) {
        dialogsHelper?.deletePost(presenter, post)
    }

    fun votePost(post: IBaseForumPost, type: Boolean) {
        dialogsHelper?.votePost(presenter, post, type)
    }

    fun showChangeReputation(post: IBaseForumPost, type: Boolean) {
        dialogsHelper?.changeReputation(presenter, post, type)
    }

    fun editPost(post: IBaseForumPost) {
        webView.extractPostBodyHtml(post.id) { domHtml ->
            presenter.openEditPostForm(post.id, domHtml)
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
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.UpdateShowAvatarState -> updateShowAvatarState(event.show)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.UpdateTypeAvatarState -> updateTypeAvatarState(event.circle)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.UpdateScrollButtonState -> updateScrollButtonState(event.enabled)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.SetFontSize -> setFontSize(event.size)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.SetAppFontMode -> setAppFontMode(event.mode)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.SetStyleType -> setStyleType(event.styleType)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.FillSettingsData -> fillSettingsData(event.settings, event.fields)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.OnStartSearch -> onStartSearch(event.settings)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowData -> showData(event.data)
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
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowUserMenu -> showUserMenu(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowReputationMenu -> showReputationMenu(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowPostMenu -> showPostMenu(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ReportPost -> reportPost(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.DeletePost -> deletePost(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.EditPost -> editPost(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.VotePost -> votePost(event.post, event.type)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.OpenSpoilerLinkDialog -> openSpoilerLinkDialog(event.post, event.spoilNumber)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.OpenAnchorDialog -> openAnchorDialog(event.post, event.name)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.Log -> log(event.text)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowChangeReputation -> showChangeReputation(event.post, event.type)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.DeletePostUi -> deletePostUi(event.post)
            is forpdateam.ru.forpda.presentation.search.SearchUiEvent.ShowNoteCreate -> showNoteCreate(event.title, event.url)
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
        val hasData = presenter.currentData.value != null && (adapter.itemCount > 0 || refreshLayout.indexOfChild(webView) >= 0)
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
        const val SEARCH_JS_INTERFACE = "IThemePresenter"
    }

    private inner class SearchWebViewClient : CustomWebViewClient(avatarRepository, linkHandler, systemLinkHandler) {
        override fun handleUri(view: WebView, uri: android.net.Uri): Boolean {
            // Let parent handle file downloads first
            if (super.handleUri(view, uri)) {
                return true // File download handled
            }
            // Handle forum links through the app
            val url = uri.toString()
            if (uri.getQueryParameter("act") == "search") {
                uri.getQueryParameter("st")?.toIntOrNull()?.let { st ->
                    presenter.onSearchPageClick(st)
                    return true
                }
            }
            if (SiteUrls.isSiteUri(uri)) {
                presenter.onOpenLink(url)
                return true
            }
            return false
        }
    }

}
