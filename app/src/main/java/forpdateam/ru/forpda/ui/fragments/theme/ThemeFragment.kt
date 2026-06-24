package forpdateam.ru.forpda.ui.fragments.theme

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import javax.inject.Inject
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.databinding.NewMessageNotificationBinding
import forpdateam.ru.forpda.databinding.FragmentThemeBinding
import android.annotation.SuppressLint
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.text.Editable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.shape.MaterialShapeDrawable
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.appcompat.widget.Toolbar
import timber.log.Timber
import android.util.TypedValue
import android.util.Log
import android.view.*
import android.view.Choreographer
import android.widget.*
import androidx.appcompat.app.AlertDialog
import forpdateam.ru.forpda.BuildConfig
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.dp40
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.theme.ThemeViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.createTopAppBarShapeDrawable
import forpdateam.ru.forpda.ui.resolveTopAppBarShapeStyle
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.views.FabOnScroll
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import forpdateam.ru.forpda.ui.views.pagination.PaginationHelper
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.presentation.theme.ThemeToolbarTitlePolicy
import forpdateam.ru.forpda.presentation.theme.TopicLoadingIndicatorPolicy
import forpdateam.ru.forpda.presentation.theme.TopicPostDensityPolicy
import forpdateam.ru.forpda.presentation.theme.TopicTopChromePaddingPolicy
import forpdateam.ru.forpda.presentation.theme.isToolbarAutoHideEnabled
import forpdateam.ru.forpda.presentation.theme.shouldShowTopicToolbarBack
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher

/**
 * Created by radiationx on 20.10.16.
 */
private const val EDIT_POST_DRAFT_SYNC_TAG = "EditPostDraftSync"
private const val REFRESH_SCROLL_TAG = "RefreshScroll"
private const val THEME_LOAD_JANK_TAG = "ThemeLoadJank"
private const val TOPIC_STATUS_BAR_UNDERLAY_TAG = "topic_status_bar_underlay"

@AndroidEntryPoint
abstract class ThemeFragment : TabFragment() {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var router: TabRouter

    /** Шапка темы (WebView и др.) — прежнее скругление плашки. */
    override fun useTopBarRoundedCorners(): Boolean = true

    /** Topic toolbar and top pagination are one compact pinned header. */
    override fun useCompactToolbarPaginationChrome(): Boolean = true

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface


    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        uploadFiles(FilePickHelper.onActivityResult(requireContext(), data))
    }

    // Nullable + view-scoped: created in onViewCreated() with the view
    // lifecycle scope and cleared in onDestroyViewBinding(). Constructing it from
    // onCreate() touched viewLifecycleOwner before the view existed and crashed
    // (Can't access the Fragment View's LifecycleOwner ... when getView() is null).
    protected var dialogsHelper: ThemeDialogsHelper_V2? = null

    protected lateinit var toggleMessagePanelItem: MenuItem
    protected lateinit var copyLinkMenuItem: MenuItem
    protected lateinit var searchOnPageMenuItem: MenuItem
    protected lateinit var searchInThemeMenuItem: MenuItem
    protected lateinit var pollToolbarMenuItem: MenuItem
    protected lateinit var refreshToolbarMenuItem: MenuItem
    protected lateinit var hatToolbarMenuItem: MenuItem
    protected lateinit var refreshPageMenuItem: MenuItem
    protected lateinit var pollOverflowMenuItem: MenuItem
    protected lateinit var searchPostsMenuItem: MenuItem
    protected lateinit var deleteFavoritesMenuItem: MenuItem
    protected lateinit var addFavoritesMenuItem: MenuItem
    protected lateinit var openForumMenuItem: MenuItem

    protected lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var paginationHelper: PaginationHelper
    private var compactToolbarDivider: View? = null
    private var previousTopicStatusBarColor: Int? = null
    private var previousTopicStatusBarContrastEnforced: Boolean? = null
    private var previousFragmentsContainerBackground: android.graphics.drawable.Drawable? = null
    private var isTopicPaginationPanelEnabled = false
    private var currentTopicScrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
    private var currentTopicPostDensity = AppPreferences.Main.TopicPostDensity.COMFORTABLE
    private var currentTopicToolbarBehavior = AppPreferences.Main.TopicToolbarBehavior.PINNED
    private var lastTopicSubtitle: String? = null
    private var lastToolbarRefreshRequestedAt = 0L
    private var lastAppliedRefreshingState: Boolean? = null
    protected var isSmartScrollButtonEnabled = true
    protected var fabBehavior: FabOnScroll? = null
    private var regularTopicToolbarTitleSizePx = 0f
    private var regularTopicToolbarSubtitleSizePx = 0f
    private var isToolbarAutoHideActive = false
    private var isToolbarAutoHideContentOverlayEnabled = false

    private var searchOnPageBar: ViewGroup? = null
    private var searchOnPageField: AppCompatEditText? = null
    private var isSearchOnPageBarVisible = false
    private val themeLoadFrameWatcher = object : Choreographer.FrameCallback {
        private var lastFrameNanos: Long = 0L

        override fun doFrame(frameTimeNanos: Long) {
            if (!themeLoadFrameWatchActive) return
            if (lastFrameNanos != 0L) {
                val gapMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L
                if (gapMs >= 32L) {
                    val bucket = when {
                        gapMs >= 64L -> "64+"
                        gapMs >= 48L -> "48+"
                        else -> "32+"
                    }
                    Log.w(
                            THEME_LOAD_JANK_TAG,
                            "frameGap bucket=$bucket gapMs=$gapMs visibleRefreshing=${isThemeRefreshingForJankLog()} swipe=${if (::refreshLayout.isInitialized) refreshLayout.isRefreshing else false} t=${SystemClock.uptimeMillis()}"
                    )
                }
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun reset() {
            lastFrameNanos = 0L
        }
    }
    private var themeLoadFrameWatchActive = false

    /**
     * Hook for "Поиск на странице" to forward queries to the actual content implementation.
     * Base class is UI-only; subclasses (e.g. WebView-based) should override.
     */
    protected open fun onSearchOnPageTextChanged(text: String) = Unit
    protected open fun onSearchOnPageNext(next: Boolean) = Unit
    protected open fun onSearchOnPageClear() = Unit
    protected open fun onSearchOnPageOpened() = Unit
    protected open fun onPollToolbarClick() = Unit
    protected open fun onHatToolbarClick() = Unit
    protected open fun onToolbarAutoHideEnabledChanged(enabled: Boolean) = Unit
    protected open fun onThemeRefreshRequested(source: String) {
        presenter.reload()
    }
    protected open fun shouldShowHatToolbarButton(): Boolean = false

    private fun closeSearchOnPageIfExpanded() {
        if (!isSearchOnPageBarVisible) return
        expandTopicToolbarForInteractiveMode()
        searchOnPageField?.setText("")
        searchOnPageField?.clearFocus()
        onSearchOnPageClear()
        searchOnPageBar?.visibility = View.GONE
        isSearchOnPageBarVisible = false
        hideKeyboard()
        applyToolbarAutoHide()
    }

    protected fun expandTopicToolbarForInteractiveMode() {
        if (isToolbarAutoHideActive) {
            appBarLayout.setExpanded(true, true)
        }
        applyToolbarAutoHide()
    }

    lateinit var messagePanel: MessagePanel
        protected set
    var attachmentsPopup: AttachmentsPopup? = null
        protected set
    private var messagePanelDraftMirror = ""
    private var clearMessagePanelTextDialog: AlertDialog? = null

    private val uploadQueue: ArrayDeque<Pair<List<RequestFile>, List<AttachmentItem>>> = ArrayDeque()
    private var uploadInProgress = false

    private var _themeBinding: FragmentThemeBinding? = null
    private val themeBinding get() = checkNotNull(_themeBinding) { "Binding accessed after onDestroyView" }
    private var notificationBinding: NewMessageNotificationBinding? = null
    private val notificationTitle: TextView? get() = notificationBinding?.title
    private val notificationButton: ImageButton? get() = notificationBinding?.icon
    private val notificationView: View? get() = notificationBinding?.root

    protected val presenter: ThemeViewModel by viewModels()

    fun onEventNew(event: TabNotification) {
        if (BuildConfig.DEBUG) {
            Timber.d("onEventNew")
        }
        notificationView?.visibility = View.VISIBLE
    }

    fun onEventRead(event: TabNotification) {
        if (BuildConfig.DEBUG) {
            Timber.d("onEventRead")
        }
        notificationView?.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            presenter.initThemeUrl(getString(TabFragment.ARG_TAB, ""))
            val unreadUrl = getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_UNREAD_URL_FROM_LIST, null)
            val unreadPostId = getInt(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST, 0)
            val lastReadUrl = getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_LAST_READ_URL_FROM_LIST, null)
            val inspectorMarkedUnread = getBoolean(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_INSPECTOR_MARKED_UNREAD, false)
            val source = getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_TOPIC_OPEN_SOURCE, "theme_tab")
            val openIntent = getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_TOPIC_OPEN_INTENT, "fresh")
            presenter.initTopicOpenHints(
                    forpdateam.ru.forpda.common.TopicOpenListHints(
                            unreadUrlFromList = unreadUrl,
                            unreadPostIdFromList = unreadPostId.takeIf { it > 0 },
                            topicMarkedUnread = !unreadUrl.isNullOrBlank() || unreadPostId > 0,
                            inspectorMarkedUnread = inspectorMarkedUnread,
                            lastReadUrlFromList = lastReadUrl
                    ),
                    sourceScreen = source
            )
            presenter.setTopicOpenIntent(openIntent)
        }
    }

    override fun initFabBehavior() {
        val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
        val behavior = FabOnScroll(fab.context, null)
        fabBehavior = behavior
        params.behavior = behavior
        params.gravity = Gravity.BOTTOM or Gravity.END
        fab.requestLayout()
        // Выше панели ввода (8dp), чтобы FAB не уходил под карточку при IME/оверлее WebView.
        ViewCompat.setElevation(fab, 12f * resources.displayMetrics.density)
    }


    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        initFabBehavior()
        _themeBinding = FragmentThemeBinding.inflate(inflater, fragmentContent, true)
        refreshLayout = themeBinding.swipeRefreshList
        messagePanel = MessagePanel(requireContext(), fragmentContainer, messagePanelHost, false, mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder)
        messagePanelHost.background = null
        coordinatorLayout.updateLayoutParams<RelativeLayout.LayoutParams> {
            removeRule(RelativeLayout.ABOVE)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        }
        messagePanelHost.bringToFront()
        paginationHelper = PaginationHelper(requireActivity(), dimensionsProvider)
        isTopicPaginationPanelEnabled = mainPreferencesHolder.getTopicPaginationPanelEnabled()
        currentTopicScrollMode = mainPreferencesHolder.getTopicScrollMode()
        currentTopicPostDensity = mainPreferencesHolder.getTopicPostDensity()
        currentTopicToolbarBehavior = mainPreferencesHolder.getTopicToolbarBehavior()
        paginationHelper.addInToolbar(
                inflater = inflater,
                target = toolbarLayout,
                enablePadding = configuration.fitSystemWindow,
                showSinglePageInToolbar = true,
                flatHeader = true,
                toolbarTopOffsetPx = topicToolbarHeightPx(currentTopicPostDensity),
                toolbarElevationPx = resources.getDimension(
                        R.dimen.dp0
                ),
                surfaceColorAttr = R.attr.main_toolbar_accent_surface,
        )
        paginationHelper.setSelectPageInputOnLongClickEnabled(true)
        paginationHelper.setToolbarPaginationEnabled(isTopicPaginationPanelEnabled)
        applyTopicPaginationPanelState()

        notificationBinding = NewMessageNotificationBinding.inflate(inflater, fragmentContent, false).also {
            fragmentContent.addView(it.root)
            it.root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        contentController.setMainRefresh(refreshLayout)

        return viewFragment
    }

    override fun onDestroyViewBinding() {
        stopThemeLoadFrameWatcher("destroyView")
        restoreTopicStatusBarColor()
        compactToolbarDivider = null
        notificationBinding = null
        clearMessagePanelTextDialog?.dismiss()
        clearMessagePanelTextDialog = null
        dialogsHelper = null
        _themeBinding = null
        super.onDestroyViewBinding()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Размер шрифта WebView задаёт ThemeFragmentWeb после создания webView (иначе lateinit webView).

        dialogsHelper = ThemeDialogsHelper_V2(
                requireContext(),
                authHolder,
                otherPreferencesHolder,
                topicPreferencesHolder,
                scope = viewLifecycleOwner.lifecycleScope
        )

        notificationButton?.setColorFilter(requireContext().getColorFromAttr(R.attr.contrast_text_color), PorterDuff.Mode.SRC_ATOP)
        notificationTitle?.text = getString(R.string.new_message_notification_title)
        notificationView?.visibility = View.GONE
        notificationButton?.setOnClickListener { notificationView?.visibility = View.GONE }
        notificationView
                ?.setOnClickListener {
                    presenter.loadNewPosts()
                    notificationBinding?.root?.visibility = View.GONE
                }

        // Не включаем MessagePanelBehavior: при IME adjustResize и смене высоты AppBar translationY
        // уводит панель ввода под клавиатуру (Fleksy и др.).
        messagePanel.disableBehavior()
        installMessagePanelDraftMirror()
        messagePanel.addSendOnClickListener { sendMessage() }
        messagePanel.setClearMessageClickListener { requestClearMessagePanelText() }
        messagePanel.sendButton?.setOnLongClickListener {
            openFullscreenEditorFromMessagePanel("sendLongClick")
            true
        }
        messagePanel.fullButton?.visibility = View.VISIBLE
        messagePanel.fullButton?.setOnClickListener { openFullscreenEditorFromMessagePanel("fullButton") }
        messagePanel.hideButton?.visibility = View.VISIBLE
        messagePanel.hideButton?.setOnClickListener { hideMessagePanel() }
        attachmentsPopup = messagePanel.attachmentsPopup
        attachmentsPopup?.setAddOnClickListener { tryPickFile() }
        attachmentsPopup?.setDeleteOnClickListener { removeFiles() }
        attachmentsPopup?.setRetryUploadListener(object : AttachmentsPopup.OnRetryUploadListener {
            override fun onRetry(files: List<RequestFile>, pending: List<AttachmentItem>) {
                enqueueUpload(files, pending)
            }
        })

        // Наблюдение StateFlow для MVVM (BX4, BX6, NX2)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { presenter.setRefreshing.collect { setRefreshing(it) } }
                launch { presenter.setMessageRefreshing.collect { messagePanel.setProgressState(it) } }
                launch { presenter.onMessageSent.collect { onMessageSent() } }
                launch { presenter.onAddToFavorite.collect { onAddToFavorite(it) } }
                launch { presenter.onDeleteFromFavorite.collect { onDeleteFromFavorite(it) } }
                launch {
                    presenter.topicToolbarClearSignal.collect { signal ->
                        if (signal > 0L) {
                            clearTopicToolbarForLoading()
                        }
                    }
                }
                launch { presenter.onLoadData.collect { onLoadData(it) } }
                launch { presenter.updateView.collect { updateView(it) } }
            }
        }

        paginationHelper.setListener(object : PaginationHelper.PaginationListener {
            override fun onTabSelected(tab: TabLayout.Tab): Boolean {
                // true = не обрабатывать клик (раньше при isRefreshing стрелки «пропадали» и не работала навигация)
                return false
            }

            override fun onSelectedPage(pageNumber: Int) {
                presenter.loadPage(pageNumber)
            }

            override fun onLastPageSelected(pagination: Pagination): Boolean {
                presenter.loadLastPageAndScrollToBottom()
                return true
            }
        })

        setupCompactTopicToolbar()

        fab.size = FloatingActionButton.SIZE_MINI
        isSmartScrollButtonEnabled = mainPreferencesHolder.getScrollButtonEnabled()
        fab.visibility = if (isSmartScrollButtonEnabled) View.VISIBLE else View.GONE
        fab.scaleX = 0.0f
        fab.scaleY = 0.0f
        fab.alpha = 0.0f
        fab.isEnabled = false
        fab.isClickable = false
        fab.isFocusable = false

        refreshLayoutStyle(refreshLayout)
        refreshLayout.setOnRefreshListener {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "topSwipe onRefresh refreshing=${refreshLayout.isRefreshing} appBarY=${appBarLayout.translationY}")
            onThemeRefreshRequested("topSwipe")
        }


        if (mainPreferencesHolder.getEditorDefaultHidden()) {
            hideMessagePanel()
        } else {
            showMessagePanel(false)
        }
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        applyTopicToolbarNavigation()
        applyTopicSystemBarChrome()
        view?.post {
            if (isAdded) {
                applyTopicToolbarNavigation()
                applyTopicSystemBarChrome()
            }
        }
        if (::messagePanel.isInitialized) {
            messagePanel.onResume()
        }
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        restoreTopicStatusBarColor()
        if (::messagePanel.isInitialized) {
            messagePanel.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::messagePanel.isInitialized) {
            messagePanel.onDestroy()
        }
        paginationHelper.destroy()
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        if (::messagePanel.isInitialized) {
            messagePanel.hidePopupWindows()
        }
    }

    override fun onToolbarNavigationClick(): Boolean {
        if (super.onToolbarNavigationClick()) {
            return true
        }
        return (activity as? forpdateam.ru.forpda.ui.activities.MainActivity)
                ?.selectTopicOriginOrParent(tag) == true
    }

    override fun onBackPressed(): Boolean {
        super.onBackPressed()

        if (::messagePanel.isInitialized && messagePanel.onBackPressed()) {
            return true
        }

        if (attachmentsPopup?.dismiss() == true) {
            return true
        }

        if (isSearchOnPageBarVisible) {
            closeSearchOnPageIfExpanded()
            return true
        }

        presenter.setSkipNextUnreadJumpAfterTabSwitch(true)
        if (presenter.onBackPressed()) {
            return true
        }

        if (resolveMessagePanelDraft().isNotEmpty() || !messagePanel.attachments.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.editpost_lose_changes)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        presenter.exit()
                    }
                    .setNegativeButton(R.string.no, null)
                    .showWithStyledButtons()
            return true
        }

        return false
    }


    override fun setRefreshing(isRefreshing: Boolean) {
        if (lastAppliedRefreshingState == isRefreshing) {
            if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "setRefreshing skipDuplicate value=$isRefreshing")
            return
        }
        lastAppliedRefreshingState = isRefreshing
        if (BuildConfig.DEBUG) {
            if (isRefreshing) {
                startThemeLoadFrameWatcher()
            } else {
                stopThemeLoadFrameWatcher("setRefreshingFalse")
            }
        }
        super.setRefreshing(isRefreshing)
        if (BuildConfig.DEBUG) Log.i(REFRESH_SCROLL_TAG, "setRefreshing value=$isRefreshing swipe=${if (::refreshLayout.isInitialized) refreshLayout.isRefreshing else false}")
        refreshToolbarMenuItems(!isRefreshing)
        syncTopicToolbarLoadingState(isRefreshing)
    }

    /**
     * Single source of truth for the topic loading indicator: the content-area indicator (skeleton
     * overlay / centered [contentProgress] for the initial load, swipe-refresh for a refresh) is the
     * only indicator. The toolbar progress double-rendered with the content indicator, so it stays
     * hidden for topics (see [TopicLoadingIndicatorPolicy]).
     */
    protected open fun syncTopicToolbarLoadingState(isRefreshing: Boolean) {
        val showToolbar = TopicLoadingIndicatorPolicy.showsToolbarProgress(isRefreshing, presenter.isPageLoaded())
        toolbarProgress.visibility = if (showToolbar) View.VISIBLE else View.INVISIBLE
    }

    protected fun stopNativeRefreshBeforeWebRender(page: ThemePage) {
        if (!::refreshLayout.isInitialized) return
        val wasSwipeRefreshing = refreshLayout.isRefreshing
        val wasContentProgressVisible = contentProgress.visibility == View.VISIBLE
        if (!wasSwipeRefreshing && !wasContentProgressVisible) return
        if (BuildConfig.DEBUG) {
            Log.i(
                    REFRESH_SCROLL_TAG,
                    "stopNativeRefreshBeforeWebRender topic=${page.id} html=${page.html?.length ?: 0} swipe=$wasSwipeRefreshing contentProgress=$wasContentProgressVisible t=${SystemClock.uptimeMillis()}"
            )
        }
        super.setRefreshing(false)
        lastAppliedRefreshingState = false
        refreshToolbarMenuItems(true)
        if (BuildConfig.DEBUG) {
            stopThemeLoadFrameWatcher("beforeWebRender")
        }
    }

    protected fun styleThemeProgressIndicators() {
        val progressTint = requireContext().getColorFromAttr(R.attr.colorAccent)
        val progressTintList = ColorStateList.valueOf(progressTint)
        contentProgress.indeterminateTintList = progressTintList
        toolbarProgress.indeterminateTintList = progressTintList
        refreshLayout.setProgressBackgroundColorSchemeColor(requireContext().getColorFromAttr(R.attr.background_for_cards))
        refreshLayout.setColorSchemeColors(
                progressTint,
                requireContext().getColorFromAttr(R.attr.link_color),
                requireContext().getColorFromAttr(R.attr.default_text_color)
        )
    }

    private fun syncTopicToolbarPaginationOffset(toolbarHeight: Int) {
        paginationHelper.setToolbarTopOffsetPx(toolbarHeight)
    }

    /** Bottom edge of native topic chrome (toolbar + pagination) in window coordinates. */
    protected fun measureTopicChromeBottomInWindow(stabilizeAutoHideTranslation: Boolean = false): Int {
        val loc = IntArray(2)
        var bottom = 0
        if (appBarLayout.height > 0) {
            appBarLayout.getLocationInWindow(loc)
            bottom = maxOf(
                    bottom,
                    if (stabilizeAutoHideTranslation) {
                        TopicTopChromePaddingPolicy.expandedChromeBottomWindowY(
                                chromeWindowY = loc[1],
                                chromeHeight = appBarLayout.height,
                                translationY = appBarLayout.translationY,
                        )
                    } else {
                        loc[1] + appBarLayout.height
                    }
            )
        }
        if (::paginationHelper.isInitialized) {
            paginationHelper.getToolbarPaginationView()?.let { tabs ->
                if (tabs.visibility == View.VISIBLE && tabs.height > 0) {
                    tabs.getLocationInWindow(loc)
                    bottom = maxOf(bottom, loc[1] + tabs.height)
                }
            }
        }
        return bottom
    }

    /**
     * Toolbar vertically centers [ActionMenuView] but defaults custom children to TOP.
     * Title + page subtitle must use the same vertical center as action icons.
     */
    private fun applyTopicToolbarContentAlignment() {
        for (index in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(index)
            if (child is androidx.appcompat.widget.ActionMenuView) continue
            child.updateLayoutParams<Toolbar.LayoutParams> {
                gravity = Gravity.CENTER_VERTICAL
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            break
        }
        titlesWrapper.updateLayoutParams<LinearLayout.LayoutParams> {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun startThemeLoadFrameWatcher() {
        if (themeLoadFrameWatchActive) return
        themeLoadFrameWatchActive = true
        themeLoadFrameWatcher.reset()
        Choreographer.getInstance().postFrameCallback(themeLoadFrameWatcher)
        Log.i(THEME_LOAD_JANK_TAG, "watchStart t=${SystemClock.uptimeMillis()}")
    }

    private fun stopThemeLoadFrameWatcher(reason: String) {
        if (!themeLoadFrameWatchActive) return
        themeLoadFrameWatchActive = false
        Choreographer.getInstance().removeFrameCallback(themeLoadFrameWatcher)
        Log.i(THEME_LOAD_JANK_TAG, "watchStop reason=$reason t=${SystemClock.uptimeMillis()}")
    }

    private fun isThemeRefreshingForJankLog(): Boolean {
        return presenter.isRefreshing()
    }

    fun onLoadData(newPage: ThemePage) {
        updateView(newPage)
    }

    /**
     * Cross-topic navigation: drop previous topic title, page counter, and pagination chrome
     * until [updateView] applies the newly loaded page.
     */
    fun clearTopicToolbarForLoading() {
        syncTopicToolbarLoadingState(presenter.isRefreshing())
        lastTopicSubtitle = ""
        setTitle("")
        setSubtitle(null)
        clearToolbarPaginationSubtitle()
        if (::paginationHelper.isInitialized) {
            paginationHelper.updatePagination(
                    Pagination().apply {
                        current = 0
                        all = 0
                    }
            )
            applyTopicPaginationPanelState()
        }
        updateHatToolbarVisibility(false)
    }

    /**
     * Toolbar title/subtitle/pagination must update even when [ThemeFragmentWeb] skips a duplicate
     * WebView render (e.g. after [clearTopicToolbarForLoading]).
     */
    protected fun applyTopicToolbarState(page: ThemePage) {
        if (!::messagePanel.isInitialized) return
        if (!presenter.shouldApplyToolbarPage(page)) return
        contentController.hideContent(ContentController.TAG_ERROR)
        paginationHelper.updatePagination(page.pagination)
        applyTopicPaginationPanelState()

        val resolvedTitle = ThemeToolbarTitlePolicy.resolveForToolbar(
                page = page,
                sessionTitle = presenter.getSessionTopicTitle(),
                argTitle = arguments?.getString(ARG_TITLE),
                currentTitle = toolbarTitleView.text?.toString() ?: getTitle(),
        ).ifBlank {
            // Last resort for a same-topic page whose HTML omitted the title and whose async
            // first-page fetch lost a session race: recover the durable per-topic label.
            presenter.getLastKnownTopicTitleForToolbar(page).orEmpty()
        }
        if (resolvedTitle.isNotEmpty()) {
            setTitle(resolvedTitle)
            presenter.rememberSessionTopicTitleFromFragment(resolvedTitle)
            setTabTitle(String.format(getString(R.string.fragment_tab_title_theme), resolvedTitle))
        }
        setTopicSubtitle("${page.pagination.current} / ${page.pagination.all}")

        refreshToolbarMenuItems(true)
    }

    open fun updateView(page: ThemePage) {
        applyTopicToolbarState(page)
    }

    protected fun showThemeLoadErrorState(message: String?) {
        if (presenter.isPageLoaded()) {
            showSnackbar(message ?: getString(R.string.error_occurred))
            return
        }
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_toolbar_refresh)
                    .setTitle(R.string.funny_theme_error_title)
                    .setDesc(R.string.funny_theme_error_desc)
                    .addAction(R.string.retry) { presenter.reload() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.showContent(ContentController.TAG_ERROR)
        message?.let { showSnackbar(it) }
    }

    protected fun updateTopicScrollModeSubtitle(mode: AppPreferences.Main.TopicScrollMode) {
        currentTopicScrollMode = mode
        applyTopicSubtitle()
    }

    fun updateTopicPostDensityChrome(density: AppPreferences.Main.TopicPostDensity) {
        currentTopicPostDensity = density
        applyTopicToolbarDensityChrome()
    }

    fun updateTopicToolbarBehaviorChrome(behavior: AppPreferences.Main.TopicToolbarBehavior) {
        currentTopicToolbarBehavior = behavior
        applyToolbarAutoHide()
    }

    private fun setTopicSubtitle(subtitle: String) {
        lastTopicSubtitle = subtitle
        applyTopicSubtitle()
    }

    private fun applyTopicSubtitle() {
        setSubtitle(
                if (shouldShowTopicSubtitle()) {
                    lastTopicSubtitle
                } else {
                    null
                }
        )
    }

    private fun shouldShowTopicSubtitle(): Boolean {
        return !isTopicPaginationPanelEnabled
    }

    protected fun updateTopicPaginationPanelState(isEnabled: Boolean) {
        isTopicPaginationPanelEnabled = isEnabled
        if (::paginationHelper.isInitialized) {
            paginationHelper.setToolbarPaginationEnabled(isEnabled)
            applyTopicPaginationPanelState()
        }
        applyTopicSubtitle()
    }

    private fun applyTopicPaginationPanelState() {
        if (!::paginationHelper.isInitialized) return
        paginationHelper.getToolbarPaginationView()?.let { panel ->
            panel.animate().cancel()
            panel.alpha = 1f
            panel.translationY = 0f
            panel.visibility = if (paginationHelper.shouldShowToolbarPagination()) View.VISIBLE else View.GONE
            panel.isClickable = true
            panel.isFocusable = true
        }
    }

    protected fun updateHatToolbarVisibility(visible: Boolean) {
        if (::hatToolbarMenuItem.isInitialized) {
            hatToolbarMenuItem.isVisible = visible && shouldShowHatToolbarButton()
            hatToolbarMenuItem.isEnabled = hatToolbarMenuItem.isVisible
        }
    }

    private fun setupCompactTopicToolbar() {
        regularTopicToolbarTitleSizePx = toolbarTitleView.textSize
        regularTopicToolbarSubtitleSizePx = toolbarSubtitleView.textSize
        toolbarTitleView.ellipsize = android.text.TextUtils.TruncateAt.END
        toolbarTitleView.setHorizontallyScrolling(false)
        toolbarSubtitleView.ellipsize = android.text.TextUtils.TruncateAt.END
        toolbarSubtitleView.maxLines = 1
        titlesWrapper.minimumWidth = 0
        toolbar.contentInsetEndWithActions = 0
        applyTopicToolbarNavigation()
        applyTopicToolbarDensityChrome()
        if (::pollToolbarMenuItem.isInitialized) {
            compactActionItem(pollToolbarMenuItem) { onPollToolbarClick() }
        }
        if (::refreshToolbarMenuItem.isInitialized) {
            compactActionItem(refreshToolbarMenuItem) { onToolbarRefreshClicked() }
        }
        if (::hatToolbarMenuItem.isInitialized) {
            compactActionItem(hatToolbarMenuItem) { onHatToolbarClick() }
        }
        if (::toggleMessagePanelItem.isInitialized) {
            compactActionItem(toggleMessagePanelItem) {
                if (!authHolder.get().isAuth()) {
                    Utils.showNeedAuthDialog(requireContext(), router)
                } else {
                    toggleMessagePanel()
                }
            }
        }
    }

    private fun applyTopicToolbarNavigation() {
        val mainActivity = activity as? MainActivity ?: return
        val toolbarStartInsetPx = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
        val tabController = mainActivity.tabNavigator.tabController
        val currentTag = tag
        val previousDestination = tabController.getParentScreenKey(currentTag)
        val hasParent = tabController.getParentTag(currentTag) != null
        val canCloseThemeChain = mainActivity.tabNavigator.canCloseThemeChainToOrigin(currentTag)
        val showBack = shouldShowTopicToolbarBack(
                tabCount = tabController.getList().size,
                isMenuTab = configuration.isMenu,
                hasParent = hasParent,
                canCloseThemeChain = canCloseThemeChain,
        )
        if (showBack) {
            toolbar.setNavigationOnClickListener {
                val handled = onToolbarNavigationClick()
                logTopicToolbarBackstack(
                        toolbarBackVisible = true,
                        navigationIconSet = toolbar.navigationIcon != null,
                        backClickHandledBy = if (handled) "topic_parent" else "activity_remove_tab",
                        previousDestination = previousDestination
                )
                if (!handled) {
                    mainActivity.removeTabListener.invoke(it)
                }
            }
            toolbar.setNavigationIcon(R.drawable.ic_toolbar_arrow_back)
            toolbar.navigationContentDescription = getString(R.string.close_tab)
            toolbar.contentInsetStartWithNavigation = 0
            toolbar.setContentInsetsRelative(0, 0)
            tintToolbarIcons()
        } else {
            toolbar.navigationIcon = null
            toolbar.contentInsetStartWithNavigation = toolbarStartInsetPx
            toolbar.setContentInsetsRelative(toolbarStartInsetPx, 0)
        }
        logTopicToolbarBackstack(
                toolbarBackVisible = showBack,
                navigationIconSet = toolbar.navigationIcon != null,
                backClickHandledBy = null,
                previousDestination = previousDestination
        )
        applyTopicToolbarContentAlignment()
    }

    private fun logTopicToolbarBackstack(
            toolbarBackVisible: Boolean,
            navigationIconSet: Boolean,
            backClickHandledBy: String?,
            previousDestination: String?
    ) {
        FpdaDebugLog.log(
                FpdaDebugLog.TAG_NAV_BACKSTACK,
                "toolbar_nav_state",
                mapOf(
                        "screen" to "Topic",
                        "destination" to javaClass.simpleName,
                        "previousDestination" to previousDestination,
                        "canNavigateBack" to toolbarBackVisible,
                        "toolbarBackVisible" to toolbarBackVisible,
                        "navigationIconSet" to navigationIconSet,
                        "toolbarMode" to currentTopicPostDensity.name,
                        "openSource" to configuration.isMenu,
                        "backClickHandledBy" to backClickHandledBy
                )
        )
    }

    private fun applyTopicToolbarDensityChrome() {
        val compact = currentTopicPostDensity != AppPreferences.Main.TopicPostDensity.COMFORTABLE
        val superCompact = currentTopicPostDensity == AppPreferences.Main.TopicPostDensity.SUPER_COMPACT
        val toolbarHeight = topicToolbarHeightPx(currentTopicPostDensity)
        paginationHelper.setToolbarFlatHeader(true)
        toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
            height = toolbarHeight
        }
        toolbar.minimumHeight = toolbarHeight
        toolbarLayout.minimumHeight = toolbarHeight
        appBarLayout.minimumHeight = toolbarHeight
        syncTopicToolbarPaginationOffset(toolbarHeight)

        titlesWrapper.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginEnd = if (compact) resources.getDimensionPixelSize(R.dimen.dp4) else resources.getDimensionPixelSize(R.dimen.dp8)
        }
        titlesWrapper.setPadding(0, if (compact) 1.dpPx() else 0, 0, if (compact) 1.dpPx() else 0)

        val titleSizePx = when (currentTopicPostDensity) {
            AppPreferences.Main.TopicPostDensity.SUPER_COMPACT -> 14f.spPx()
            AppPreferences.Main.TopicPostDensity.COMPACT -> 15f.spPx()
            else -> regularTopicToolbarTitleSizePx
        }
        val subtitleSizePx = when (currentTopicPostDensity) {
            AppPreferences.Main.TopicPostDensity.SUPER_COMPACT -> 10f.spPx()
            AppPreferences.Main.TopicPostDensity.COMPACT -> 11f.spPx()
            else -> regularTopicToolbarSubtitleSizePx
        }
        toolbarTitleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSizePx)
        toolbarSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, subtitleSizePx)
        toolbarSubtitleView.includeFontPadding = !compact
        toolbarTitleView.includeFontPadding = !compact
        toolbarSubtitleView.alpha = if (superCompact) 0.78f else if (compact) 0.86f else 1f
        applyTopicToolbarTextChrome(compact)
        val toolbarElevation = resources.getDimension(R.dimen.dp0)
        paginationHelper.setToolbarElevationPx(toolbarElevation)
        listOf(appBarLayout, toolbarLayout, toolbar).forEach { headerView ->
            ViewCompat.setElevation(headerView, 0f)
            ViewCompat.setTranslationZ(headerView, 0f)
        }

        applyTopicToolbarShape(flat = true)
        applyTopicSystemBarChrome()
        view?.post {
            if (isAdded) {
                applyTopicSystemBarChrome()
            }
        }
        applyCompactToolbarDivider(compact)
        applyToolbarAutoHide()
        applyTopicToolbarContentAlignment()
        toolbar.requestLayout()
        toolbarLayout.requestLayout()
        appBarLayout.requestLayout()
    }

    private fun topicToolbarHeightPx(density: AppPreferences.Main.TopicPostDensity): Int =
            resources.getDimensionPixelSize(TopicPostDensityPolicy.toolbarHeightDimenRes(density))

    private fun applyToolbarAutoHide() {
        val enabled = isToolbarAutoHideEnabled(currentTopicToolbarBehavior) &&
                !isSearchOnPageBarVisible &&
                ::messagePanel.isInitialized &&
                messagePanel.visibility != View.VISIBLE
        if (isToolbarAutoHideActive != enabled) {
            isToolbarAutoHideActive = enabled
            clearToolbarScrollFlags()
            applyToolbarAutoHideContentOverlay(enabled)
            if (!enabled) {
                appBarLayout.setExpanded(true, false)
            }
        }
        // Subclasses (ThemeFragmentWeb) may initialize scroll controller after super.onViewCreated;
        // always sync so late-bound controllers receive the current enabled state.
        onToolbarAutoHideEnabledChanged(enabled)
    }

    private fun applyToolbarAutoHideContentOverlay(enabled: Boolean) {
        if (isToolbarAutoHideContentOverlayEnabled == enabled) return
        isToolbarAutoHideContentOverlayEnabled = enabled
        val params = fragmentContent.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        val targetBehavior = if (enabled) null else AppBarLayout.ScrollingViewBehavior()
        val currentBehavior = params.behavior
        val sameBehavior = if (enabled) {
            currentBehavior == null
        } else {
            currentBehavior is AppBarLayout.ScrollingViewBehavior
        }
        if (!sameBehavior) {
            params.behavior = targetBehavior
            fragmentContent.layoutParams = params
        }
        fragmentContent.translationY = 0f
        preLpShadow.translationY = 0f
    }

    private fun applyTopicToolbarTextChrome(compact: Boolean) {
        val titleColor = requireContext().getColorFromAttr(
                R.attr.icon_toolbar
        )
        val subtitleColor = requireContext().getColorFromAttr(
                R.attr.icon_toolbar
        )
        toolbarTitleView.setTextColor(titleColor)
        toolbarSubtitleView.setTextColor(subtitleColor)
        tintTopicToolbarActionViews()
        tintToolbarIcons()
        tintMenuItems(toolbar.menu)
    }

    private fun applyTopicToolbarShape(flat: Boolean) {
        val hasPagination = paginationHelper.getToolbarPaginationView()?.parent === toolbarLayout
        val radius = resources.getDimension(R.dimen.dp8)
        val background = createTopicToolbarShapeDrawable(radius)
        val surfaceColor = resolveTopicToolbarSurfaceColor()

        if (hasPagination && flat) {
            appBarLayout.background = ColorDrawable(surfaceColor)
            appBarLayout.outlineProvider = ViewOutlineProvider.BOUNDS
            appBarLayout.clipToOutline = false
            toolbarLayout.background = ColorDrawable(surfaceColor)
            toolbarLayout.clipToOutline = false
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            toolbar.clipToOutline = false
            val underlay = toolbarLayout.findViewWithTag<View>(R.id.toolbar_opaque_underlay)
            underlay?.background = ColorDrawable(surfaceColor)
        } else if (hasPagination) {
            appBarLayout.background = background
            appBarLayout.outlineProvider = ViewOutlineProvider.BACKGROUND
            appBarLayout.clipToOutline = useTopBarHorizontalPlaqueInset()
            toolbarLayout.setBackgroundColor(Color.TRANSPARENT)
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            toolbar.clipToOutline = false
            val underlay = toolbarLayout.findViewWithTag<View>(R.id.toolbar_opaque_underlay)
            underlay?.background = createTopicToolbarShapeDrawable(radius)
        } else {
            appBarLayout.background = createTopicToolbarShapeDrawable(radius)
            appBarLayout.outlineProvider = ViewOutlineProvider.BACKGROUND
            appBarLayout.clipToOutline = useTopBarHorizontalPlaqueInset()
            toolbarLayout.setBackgroundColor(Color.TRANSPARENT)
            toolbar.background = background
            toolbar.clipToOutline = useTopBarHorizontalPlaqueInset()
        }
        toolbar.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun applyTopicSystemBarChrome() {
        val surfaceColor = resolveTopicToolbarSurfaceColor()
        val window = requireActivity().window
        if (previousTopicStatusBarColor == null) {
            previousTopicStatusBarColor = window.statusBarColor
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousTopicStatusBarContrastEnforced == null) {
            previousTopicStatusBarContrastEnforced = window.isStatusBarContrastEnforced
        }

        appBarLayout.background = ColorDrawable(surfaceColor)
        toolbarLayout.background = ColorDrawable(surfaceColor)
        toolbarLayout.setContentScrimColor(surfaceColor)
        toolbarLayout.setStatusBarScrimColor(surfaceColor)
        toolbar.setBackgroundColor(Color.TRANSPARENT)
        toolbarLayout.findViewWithTag<View>(R.id.toolbar_opaque_underlay)?.background = ColorDrawable(surfaceColor)
        applyTopicStatusBarUnderlay(surfaceColor)
        SystemBarAppearance.syncStatusBar(requireActivity(), surfaceColor)
    }

    private fun restoreTopicStatusBarColor() {
        removeTopicStatusBarUnderlay()
        restoreFragmentsContainerBackground()
        val color = previousTopicStatusBarColor
        activity?.window?.let { window ->
            if (color != null) {
                window.statusBarColor = color
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                previousTopicStatusBarContrastEnforced?.let { window.isStatusBarContrastEnforced = it }
            }
        }
        activity?.let(SystemBarAppearance::syncStatusBarIconContrast)
        previousTopicStatusBarColor = null
        previousTopicStatusBarContrastEnforced = null
    }

    private fun resolveTopicToolbarSurfaceColor(): Int =
            requireContext().getColorFromAttr(topBarSurfaceColorAttr())

    private fun applyTopicStatusBarUnderlay(surfaceColor: Int) {
        val fragmentsContainer = activity?.findViewById<ViewGroup>(R.id.fragments_container) ?: return
        if (previousFragmentsContainerBackground == null) {
            previousFragmentsContainerBackground = fragmentsContainer.background
        }
        fragmentsContainer.setBackgroundColor(surfaceColor)

        val container = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        val topInset = maxOf(fragmentsContainer.paddingTop, dimensionsProvider.getDimensions().statusBar)
        if (topInset <= 0) {
            removeTopicStatusBarUnderlay()
            return
        }
        val underlay = container.findViewWithTag<View>(TOPIC_STATUS_BAR_UNDERLAY_TAG)
                ?: View(requireContext()).apply {
                    tag = TOPIC_STATUS_BAR_UNDERLAY_TAG
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    container.addView(this)
                }
        underlay.setBackgroundColor(surfaceColor)
        underlay.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                topInset
        ).apply {
            gravity = Gravity.TOP
        }
        underlay.bringToFront()
    }

    private fun removeTopicStatusBarUnderlay() {
        val content = activity?.findViewById<ViewGroup>(android.R.id.content)
        content?.findViewWithTag<View>(TOPIC_STATUS_BAR_UNDERLAY_TAG)?.let { underlay ->
            content.removeView(underlay)
        }
        val oldContainer = activity?.findViewById<ViewGroup>(R.id.fragments_container)
        oldContainer?.findViewWithTag<View>(TOPIC_STATUS_BAR_UNDERLAY_TAG)?.let { underlay ->
            oldContainer.removeView(underlay)
        }
    }

    private fun restoreFragmentsContainerBackground() {
        val background = previousFragmentsContainerBackground ?: return
        activity?.findViewById<ViewGroup>(R.id.fragments_container)?.background = background
        previousFragmentsContainerBackground = null
    }

    private fun applyCompactToolbarDivider(compact: Boolean) {
        val divider = compactToolbarDivider ?: View(requireContext()).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = CollapsingToolbarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(R.dimen.divider_thin)
            ).apply {
                gravity = Gravity.BOTTOM
                collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
            }
            toolbarLayout.addView(this)
            compactToolbarDivider = this
        }
        divider.setBackgroundColor(requireContext().getColorFromAttr(R.attr.divider_line))
        divider.alpha = if (compact) 0.45f else 0f
        divider.visibility = if (compact) View.VISIBLE else View.GONE
        divider.bringToFront()
    }

    private fun createTopicToolbarShapeDrawable(radius: Float): MaterialShapeDrawable {
        val usesMainToolbarSurface = topBarSurfaceColorAttr() == R.attr.main_toolbar_accent_surface
        val style = resolveTopAppBarShapeStyle(
                useHorizontalInset = useTopBarHorizontalPlaqueInset(),
                roundedCorners = useTopBarRoundedCorners(),
                roundedBottomCorners = true,
        )
        return requireContext().createTopAppBarShapeDrawable(
                surfaceColor = requireContext().getColorFromAttr(topBarSurfaceColorAttr()),
                style = style,
                strokeWidthAttr = if (usesMainToolbarSurface) R.attr.main_toolbar_stroke_width else R.attr.list_plate_stroke_width,
                strokeColorAttr = if (usesMainToolbarSurface) R.attr.main_toolbar_stroke_color else R.attr.list_plate_stroke_color,
                cornerRadius = radius,
        )
    }

    private fun Float.spPx(): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)

    private fun Int.dpPx(): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    private fun compactActionItem(item: MenuItem, onClick: () -> Unit) {
        val icon = item.icon ?: return
        val iconColor = requireContext().getColorFromAttr(R.attr.icon_toolbar)
        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.actionBarItemBackground, outValue, true)
        val button = AppCompatImageButton(requireContext()).apply {
            setImageDrawable(icon.mutate().apply { setTint(iconColor) })
            imageTintList = ColorStateList.valueOf(iconColor)
            setBackgroundResource(outValue.resourceId)
            contentDescription = item.title
            TooltipCompat.setTooltipText(this, item.title)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp16 / 2, dp16 / 2, dp16 / 2, dp16 / 2)
            minimumWidth = dp40
            minimumHeight = dp40
            layoutParams = Toolbar.LayoutParams(dp40, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { onClick() }
        }
        item.actionView = button
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun refreshCompactActionIcon(item: MenuItem) {
        val iconColor = requireContext().getColorFromAttr(R.attr.icon_toolbar)
        (item.actionView as? ImageButton)?.apply {
            setImageDrawable(item.icon?.mutate()?.apply { setTint(iconColor) })
            imageTintList = ColorStateList.valueOf(iconColor)
        }
    }

    private fun tintTopicToolbarActionViews() {
        val iconColor = requireContext().getColorFromAttr(R.attr.icon_toolbar)
        for (i in 0 until toolbar.menu.size()) {
            val item = toolbar.menu.getItem(i)
            (item.actionView as? ImageButton)?.apply {
                imageTintList = ColorStateList.valueOf(iconColor)
                item.icon?.mutate()?.setTint(iconColor)
            }
        }
    }

    fun updateVisiblePage(pageNumber: Int, allPages: Int, perPage: Int, isForum: Boolean) {
        if (!::messagePanel.isInitialized) return
        val toolbarPageBefore = lastTopicSubtitle
        paginationHelper.updatePagination(Pagination().apply {
            current = pageNumber
            all = allPages
            this.perPage = perPage
            this.isForum = isForum
        })
        setTopicSubtitle("$pageNumber / $allPages")
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                    "ThemePageSync",
                    "visiblePageDetected current=$pageNumber total=$allPages toolbarPageBefore=$toolbarPageBefore toolbarPageAfter=$lastTopicSubtitle"
            )
        }
    }

    private fun toggleMessagePanel() {
        if (messagePanel.visibility == View.VISIBLE) {
            hideMessagePanel()
        } else {
            showMessagePanel(true)
        }
    }

    private fun showMessagePanel(showKeyboard: Boolean) {
        expandTopicToolbarForInteractiveMode()
        if (messagePanel.visibility != View.VISIBLE) {
            messagePanel.visibility = View.VISIBLE
            applyToolbarAutoHide()
            if (showKeyboard) {
                messagePanel.show()
            }
            messagePanel.heightChangeListener?.onChangedHeight(messagePanel.lastHeight)
            toggleMessagePanelItem.icon = requireContext().getVecDrawable(R.drawable.ic_toolbar_transcribe_close)
            refreshCompactActionIcon(toggleMessagePanelItem)
        }
        if (showKeyboard) {
            //messagePanel.getMessageField().setSelection(messagePanel.getMessageField().length());
            messagePanel.messageField?.requestFocus()
            messagePanel.messageField?.let { showKeyboard(it) }
        }
    }

    private fun hideMessagePanel() {
        messagePanel.hideImeFromEditor()
        messagePanel.visibility = View.GONE
        messagePanel.hidePopupWindows()
        hideKeyboard()
        messagePanel.heightChangeListener?.onChangedHeight(0)
        toggleMessagePanelItem.icon = requireContext().getVecDrawable(R.drawable.ic_toolbar_create)
        refreshCompactActionIcon(toggleMessagePanelItem)
        applyToolbarAutoHide()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        toggleMessagePanelItem = menu
                .add(Menu.NONE, R.id.action_theme_reply, Menu.NONE, R.string.reply)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_create))
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

        addSearchOnPageItem(menu)

        pollToolbarMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_poll, Menu.NONE, R.string.poll)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_poll_box))
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)

        refreshToolbarMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_refresh_toolbar, Menu.NONE, R.string.theme_refresh_page)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_refresh))
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        refreshToolbarMenuItem.isVisible = false

        hatToolbarMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_hat, Menu.NONE, R.string.hat)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_info))
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        hatToolbarMenuItem.isVisible = false

        refreshPageMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_refresh_page, Menu.NONE, R.string.theme_refresh_page)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        pollOverflowMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_poll_overflow, Menu.NONE, R.string.poll)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        copyLinkMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_copy_link, Menu.NONE, R.string.copy_link)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        menu
                .add(Menu.NONE, R.id.action_theme_search_on_page_overflow, Menu.NONE, R.string.search_in_page)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        searchInThemeMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_search_in_topic, Menu.NONE, R.string.search_in_theme)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        searchPostsMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_search_my_posts, Menu.NONE, R.string.search_my_posts)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        deleteFavoritesMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_delete_favorite, Menu.NONE, R.string.delete_from_favorites)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()
        addFavoritesMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_add_favorite, Menu.NONE, R.string.add_to_favorites)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()
        openForumMenuItem = menu
                .add(Menu.NONE, R.id.action_theme_open_forum, Menu.NONE, R.string.open_theme_forum)
                .setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
                .setShowAsActionNever()

        toolbar.setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
        refreshToolbarMenuItems(false)
    }

    private fun handleTopicToolbarAction(itemId: Int): Boolean {
        when (itemId) {
            R.id.action_theme_reply -> {
                if (!authHolder.get().isAuth()) {
                    Utils.showNeedAuthDialog(requireContext(), router)
                } else {
                    toggleMessagePanel()
                }
            }
            R.id.action_theme_poll, R.id.action_theme_poll_overflow -> onPollToolbarClick()
            R.id.action_theme_hat -> onHatToolbarClick()
            R.id.action_theme_refresh_page, R.id.action_theme_refresh_toolbar -> onToolbarRefreshClicked()
            R.id.action_theme_copy_link -> presenter.copyLink()
            R.id.action_theme_search_on_page_overflow, R.id.action_search -> openSearchOnPageBar()
            R.id.action_theme_search_in_topic -> {
                closeSearchOnPageIfExpanded()
                presenter.openSearch()
            }
            R.id.action_theme_search_my_posts -> {
                closeSearchOnPageIfExpanded()
                presenter.openSearchMyPosts()
            }
            R.id.action_theme_delete_favorite -> presenter.onClickDeleteInFav()
            R.id.action_theme_add_favorite -> presenter.onClickAddInFav()
            R.id.action_theme_open_forum -> presenter.openForum()
            else -> return false
        }
        return true
    }

    private fun onToolbarRefreshClicked() {
        val now = SystemClock.uptimeMillis()
        if (now - lastToolbarRefreshRequestedAt < 700L) return
        lastToolbarRefreshRequestedAt = now
        onThemeRefreshRequested("toolbarRefresh")
    }

    private fun MenuItem.setShowAsActionNever(): MenuItem = apply {
        setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }

    override fun refreshToolbarMenuItems(enable: Boolean) {
        super.refreshToolbarMenuItems(enable)
        if (enable) {
            val pageNotNull = presenter.isPageLoaded()
            val hasPoll = pageNotNull && presenter.hasPoll()
            val hasTopicHat = pageNotNull && presenter.hasTopicHat()
            val toolbarMenu = ThemeToolbarMenuPolicy.resolve(
                    pageLoaded = pageNotNull,
                    hasPoll = hasPoll,
                    hasTopicHat = hasTopicHat,
                    hatToolbarEnabled = shouldShowHatToolbarButton(),
            )

            toggleMessagePanelItem.isEnabled = true
            refreshToolbarMenuItem.isEnabled = toolbarMenu.showCompactRefresh
            refreshToolbarMenuItem.isVisible = toolbarMenu.showCompactRefresh
            refreshPageMenuItem.isEnabled = toolbarMenu.showOverflowRefresh
            refreshPageMenuItem.isVisible = toolbarMenu.showOverflowRefresh
            pollOverflowMenuItem.isEnabled = toolbarMenu.showOverflowPoll
            pollOverflowMenuItem.isVisible = toolbarMenu.showOverflowPoll
            copyLinkMenuItem.isEnabled = pageNotNull
            searchInThemeMenuItem.isEnabled = pageNotNull
            searchPostsMenuItem.isEnabled = pageNotNull
            searchOnPageMenuItem.isEnabled = pageNotNull
            pollToolbarMenuItem.isEnabled = toolbarMenu.showCompactPoll
            pollToolbarMenuItem.isVisible = toolbarMenu.showCompactPoll
            hatToolbarMenuItem.isEnabled = toolbarMenu.showCompactHat
            hatToolbarMenuItem.isVisible = toolbarMenu.showCompactHat
            deleteFavoritesMenuItem.isEnabled = pageNotNull
            addFavoritesMenuItem.isEnabled = pageNotNull
            if (pageNotNull) {
                if (presenter.isInFavorites()) {
                    deleteFavoritesMenuItem.isVisible = true
                    addFavoritesMenuItem.isVisible = false
                } else {
                    deleteFavoritesMenuItem.isVisible = false
                    addFavoritesMenuItem.isVisible = true
                }
            }
            openForumMenuItem.isEnabled = pageNotNull
        } else {
            toggleMessagePanelItem.isEnabled = false
            refreshToolbarMenuItem.isEnabled = false
            refreshToolbarMenuItem.isVisible = false
            refreshPageMenuItem.isEnabled = false
            refreshPageMenuItem.isVisible = false
            pollOverflowMenuItem.isEnabled = false
            pollOverflowMenuItem.isVisible = false
            copyLinkMenuItem.isEnabled = false
            searchInThemeMenuItem.isEnabled = false
            searchPostsMenuItem.isEnabled = false
            searchOnPageMenuItem.isEnabled = false
            pollToolbarMenuItem.isEnabled = false
            pollToolbarMenuItem.isVisible = false
            hatToolbarMenuItem.isEnabled = false
            hatToolbarMenuItem.isVisible = false
            deleteFavoritesMenuItem.isEnabled = false
            addFavoritesMenuItem.isEnabled = false
            deleteFavoritesMenuItem.isVisible = false
            addFavoritesMenuItem.isVisible = false
            openForumMenuItem.isEnabled = false
        }
        if (!authHolder.get().isAuth()) {
            deleteFavoritesMenuItem.isVisible = false
            addFavoritesMenuItem.isVisible = false
            searchPostsMenuItem.isEnabled = false
            hideMessagePanel()
        }
    }

    private fun addSearchOnPageItem(menu: Menu) {
        toolbar.inflateMenu(R.menu.theme_search_menu)
        searchOnPageMenuItem = menu.findItem(R.id.action_search)
        searchOnPageMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        searchOnPageMenuItem.isVisible = false
        searchOnPageMenuItem.setOnMenuItemClickListener { handleTopicToolbarAction(it.itemId) }
        ensureSearchOnPageBar()
    }

    private fun ensureSearchOnPageBar(): ViewGroup {
        searchOnPageBar?.let { return it }

        val itemSize = resources.getDimensionPixelSize(R.dimen.dp40)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.dp4)
        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.actionBarItemBackground, outValue, true)

        val bar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = android.graphics.drawable.ColorDrawable(requireContext().getColorFromAttr(R.attr.chrome_plane_background))
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            layoutParams = AppBarLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val field = AppCompatEditText(requireContext()).apply {
            hint = getString(R.string.search_in_page)
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextColor(requireContext().getColorFromAttr(R.attr.default_text_color))
            setHintTextColor(requireContext().getColorFromAttr(R.attr.second_text_color))
            background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            minHeight = itemSize
            setPadding(0, 0, resources.getDimensionPixelSize(R.dimen.dp8), 0)
            layoutParams = LinearLayout.LayoutParams(0, itemSize, 1f)
            addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val q = s.toString().trim()
                    if (q.isEmpty()) {
                        onSearchOnPageClear()
                    } else {
                        onSearchOnPageTextChanged(q)
                    }
                }
            })
        }

        fun searchBarButton(iconRes: Int, description: CharSequence?, onClick: () -> Unit): AppCompatImageButton {
            return AppCompatImageButton(requireContext()).apply {
                setImageDrawable(requireContext().getVecDrawable(iconRes))
                setBackgroundResource(outValue.resourceId)
                contentDescription = description
                TooltipCompat.setTooltipText(this, description)
                scaleType = ImageView.ScaleType.CENTER
                layoutParams = LinearLayout.LayoutParams(itemSize, itemSize)
                setOnClickListener { onClick() }
            }
        }

        bar.addView(field)
        bar.addView(searchBarButton(R.drawable.ic_toolbar_search_prev, getString(R.string.search_in_page)) {
            onSearchOnPageNext(false)
        })
        bar.addView(searchBarButton(R.drawable.ic_toolbar_search_next, getString(R.string.search_in_page)) {
            onSearchOnPageNext(true)
        })
        bar.addView(searchBarButton(R.drawable.ic_close, getString(R.string.close)) {
            closeSearchOnPageIfExpanded()
        })

        appBarLayout.addView(bar)
        searchOnPageBar = bar
        searchOnPageField = field
        return bar
    }

    protected fun openSearchOnPageBar() {
        expandTopicToolbarForInteractiveMode()
        ensureSearchOnPageBar().visibility = View.VISIBLE
        isSearchOnPageBarVisible = true
        applyToolbarAutoHide()
        onSearchOnPageOpened()
        searchOnPageField?.post {
            searchOnPageField?.requestFocus()
            searchOnPageField?.let { showKeyboard(it) }
        }
    }

    fun showAddInFavDialog(page: ThemePage) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setItems(FavoritesFragment.getSubNames(requireContext())) { _, which ->
                    presenter.addTopicToFavorite(page.id, FavoritesApi.SUB_TYPES[which])
                }
                .showWithStyledButtons()
    }

    fun showDeleteInFavDialog(page: ThemePage) {
        if (page.favId == 0) {
            showSnackbar(R.string.fav_delete_error_id_not_found)
        }
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.fav_ask_delete)
                .setPositiveButton(R.string.ok) { _, _ ->
                    presenter.deleteTopicFromFavorite(page.favId)
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    fun onAddToFavorite(result: Boolean) {
        showSnackbar(if (result) getString(R.string.favorites_added) else getString(R.string.error_occurred))
        refreshToolbarMenuItems(true)
    }

    fun onDeleteFromFavorite(result: Boolean) {
        showSnackbar(getString(if (result) R.string.favorite_theme_deleted else R.string.error))
        refreshToolbarMenuItems(true)
    }


    /*
     *
     * EDIT POST FUNCTIONS
     *
     * */

    fun syncEditPost(data: EditPostSyncData) {
        messagePanelDraftMirror = data.message.orEmpty()
        messagePanel.setText(data.message)
        messagePanel.messageField?.setSelection(data.selectionStart, data.selectionEnd)
        data.attachments?.also { attachmentsPopup?.setAttachments(it) }
    }

    private fun sendMessage() {
        hideKeyboard()
        presenter.sendMessage(resolveMessagePanelDraft(), messagePanel.attachments.toMutableList())
    }

    fun onMessageSent() {
        messagePanel.clearAttachments()
        messagePanel.clearMessage()
        messagePanelDraftMirror = ""
        if (mainPreferencesHolder.getEditorDefaultHidden()) {
            hideMessagePanel()
        }
    }

    fun setMessageRefreshing(isRefreshing: Boolean) {
        messagePanel.setProgressState(isRefreshing)
    }

    private fun tryPickFile() {
        pickFileLauncher.launch(FilePickHelper.pickFile(false))
    }

    private fun installMessagePanelDraftMirror() {
        messagePanel.messageField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                messagePanelDraftMirror = s.toString()
            }
        })
    }

    private fun resolveMessagePanelDraft(): String {
        val fieldText = messagePanel.message
        return if (fieldText.isNotEmpty()) fieldText else messagePanelDraftMirror
    }

    private fun requestClearMessagePanelText() {
        if (!isAdded || view == null || !::messagePanel.isInitialized) return
        if (messagePanel.messageField.visibility != View.VISIBLE || messagePanel.isInputBlocked()) return
        if (resolveMessagePanelDraft().isBlank()) return
        if (clearMessagePanelTextDialog?.isShowing == true) return

        clearMessagePanelTextDialog = MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.editor_clear_text_confirm_message)
                .setPositiveButton(R.string.editor_clear_text_confirm_positive) { _, _ ->
                    clearMessagePanelTextConfirmed()
                }
                .setNegativeButton(R.string.editor_clear_text_confirm_negative, null)
                .showWithStyledButtons()
                .also { dialog ->
                    dialog.setOnDismissListener {
                        if (clearMessagePanelTextDialog === dialog) {
                            clearMessagePanelTextDialog = null
                        }
                    }
                }
    }

    private fun clearMessagePanelTextConfirmed() {
        if (!isAdded || view == null || !::messagePanel.isInitialized) return
        messagePanelDraftMirror = ""
        messagePanel.clearMessage()
    }

    private fun openFullscreenEditorFromMessagePanel(source: String) {
        val fieldText = messagePanel.message
        val draft = if (fieldText.isNotEmpty()) fieldText else messagePanelDraftMirror
        val selectionRange = messagePanel.selectionRange
        Timber.d(
            EDIT_POST_DRAFT_SYNC_TAG,
            "sourceClick len=${fieldText.length}" +
                " mirror=${messagePanelDraftMirror.length}" +
                " resolved=${draft.length}" +
                " source=$source" +
                " selection=${selectionRange[0]}..${selectionRange[1]}" +
                " attachments=${messagePanel.attachments.size}"
        )
        presenter.openEditPostForm(draft, messagePanel.attachments.toMutableList(), selectionRange)
    }

    fun uploadFiles(files: List<RequestFile>) {
        val pending = attachmentsPopup?.preUploadFiles(files) ?: emptyList()
        attachmentsPopup?.revealDuringUploadPreview()
        enqueueUpload(files, pending)
    }

    private fun enqueueUpload(files: List<RequestFile>, pending: List<AttachmentItem>) {
        uploadQueue.addLast(files to pending)
        pumpUploadQueue()
    }

    private fun pumpUploadQueue() {
        if (uploadInProgress) return
        val next = uploadQueue.firstOrNull() ?: return
        uploadInProgress = true
        presenter.uploadFiles(next.first, next.second)
    }

    private fun removeFiles() {
        attachmentsPopup?.preDeleteFiles()
        val selectedFiles = attachmentsPopup?.getSelected() ?: emptyList()
        presenter.deleteFiles(selectedFiles)
    }

    fun onUploadFiles(items: List<AttachmentItem>) {
        attachmentsPopup?.onUploadFiles(items)
        uploadInProgress = false
        if (uploadQueue.isNotEmpty()) uploadQueue.removeFirst()
        pumpUploadQueue()
    }

    fun onDeleteFiles(items: List<AttachmentItem>) {
        attachmentsPopup?.onDeleteFiles(items)
    }

    /*
     *
     * Post functions
     *
     * */

    fun showNoteCreate(title: String, url: String) {
        NotesAddPopup.showCreateBookmarkDialog(requireContext(), title, url, notesRepository)
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

    fun selectPageInput() {
        paginationHelper.selectPageInputDialog()
    }


    fun insertText(text: String) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        messagePanel.insertText(text)
        showMessagePanel(true)
    }

    open fun editPost(post: IBaseForumPost) {
        presenter.openEditPostForm(post.id, post.body)
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
        dialogsHelper?.showPostMenu(presenter, post, currentTopicPostDensity)
    }

    fun reportPost(post: IBaseForumPost) {
        dialogsHelper?.tryReportPost(presenter, post)
    }

    fun deletePost(post: IBaseForumPost) {
        dialogsHelper?.deletePost(presenter, post)
    }

    fun votePost(post: IBaseForumPost, type: Boolean) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        dialogsHelper?.votePost(presenter, post, type)
    }

    fun showChangeReputation(post: IBaseForumPost, type: Boolean) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        dialogsHelper?.changeReputation(presenter, post, type)
    }

    companion object {
        //Указывают на произведенное действие: переход назад, обновление, обычный переход по ссылке
        private val LOG_TAG = ThemeFragment::class.java.simpleName
    }
}
