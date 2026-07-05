package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import forpdateam.ru.forpda.common.getColorFromAttr
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import android.app.Activity
import android.text.Editable
import androidx.activity.result.contract.ActivityResultContracts
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeTabHost
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.attachments.AttachmentsPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Native RecyclerView topic renderer (roadmap `native-topic-renderer.md`, Фаза 1), behind
 * the [forpdateam.ru.forpda.ui.navigation.TabHelper.useNativeTopicRenderer] flag.
 *
 * This first slice deliberately loads a single page directly through [ThemeApi] — the real
 * network + [forpdateam.ru.forpda.model.data.remote.api.theme.ThemeParser] layer the plan
 * says to REUSE and NOT touch — instead of wiring the WebView-coupled [ThemeViewModel] state
 * machine. That proves the native rendering pipeline end-to-end (real 4pda HTML → parser →
 * posts → [PostBodyRenderer] → native views) on device. Pagination / infinite-scroll / the
 * full presenter contract are later Фаза-1/3 steps; this is the fail-fast, verify-early
 * shell the roadmap asks for.
 *
 * Implements [ThemeTabHost] so the [forpdateam.ru.forpda.ui.navigation.TabNavigator] can reuse
 * this tab and switch topics (e.g. tapping an in-topic link to a DIFFERENT topic) exactly as it
 * does for the WebView engine.
 */
@AndroidEntryPoint
class NativeTopicFragment : RecyclerFragment(), ThemeTabHost, TopicPostsAdapter.PostActionListener {

    @Inject
    lateinit var themeApi: ThemeApi

    @Inject
    lateinit var editPostApi: forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi

    @Inject
    lateinit var reputationApi: forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi

    @Inject
    lateinit var linkHandler: ILinkHandler

    @Inject
    lateinit var eventsRepository: forpdateam.ru.forpda.model.repository.events.EventsRepository

    @Inject
    lateinit var themeUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase

    @Inject
    lateinit var mainPreferencesHolder: forpdateam.ru.forpda.model.preferences.MainPreferencesHolder

    @Inject
    lateinit var otherPreferencesHolder: forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder

    /** Reuses the WebView editor's send/upload/delete network logic (no ViewModel needed). */
    @Inject
    lateinit var editorUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase

    /** Logged-in context — drives the footer 👍/👎 visibility (parity with the WebView). */
    @Inject
    lateinit var authHolder: forpdateam.ru.forpda.model.AuthHolder

    /** Reuses the WebView toolbar navigation actions (open forum / search in topic / my posts). */
    @Inject
    lateinit var navigationUseCase: forpdateam.ru.forpda.model.interactors.theme.ThemeNavigationUseCase

    // topicPreferencesHolder is provided by the TabFragment supertype.

    private val mapper = NativePostMapper()
    private val anchorResolver = NativeAnchorResolver()
    private val pagination = TopicPaginationController()
    private val postsAdapter by lazy { TopicPostsAdapter(linkHandler, this) }
    private val pollHeaderAdapter = PollHeaderAdapter(
            voteListener = PollHeaderAdapter.PollVoteListener { action, method, encodedForm ->
                submitPoll(action, method, encodedForm)
            },
    )

    /** Adapter positions are shifted by the poll header (0 or 1) — offset scroll targets by this. */
    private fun headerOffset(): Int = pollHeaderAdapter.itemCount

    /** The accumulated posts across all loaded pages (source of truth for the adapter). */
    private val loadedItems = ArrayList<NativePostItem>()

    /**
     * Post ids already scanned for mention-clearing this topic session, so a post scrolled past
     * (or re-bound during infinite scroll) is not re-fed to [EventsRepository]. Reset per topic load.
     */
    private val mentionScannedPostIds = HashSet<Int>()

    /** Fire the end-of-topic mark-read exactly once per topic load (reset on a fresh load). */
    private var markedTopicReadAtEnd = false

    /** The URL actually loaded into the list (may differ from ARG_TAB after a navigator switch). */
    private var loadedUrl: String? = null
    private var isLoadingNextPage = false
    private var isLoadingPrevPage = false

    /** Topic context for posting a reply (from the loaded page). */
    private var pageForumId = 0
    private var pageTopicId = 0
    private var pageSt = 0
    private var isSending = false

    /** Loaded-page flags driving the toolbar poll / hat icon visibility (see [refreshToolbarState]). */
    private var pageHasPoll = false
    private var pageHasHat = false
    /** Post id of the topic hat on the loaded page (rendered as a collapsed inline block on page 1). */
    private var topicHatPostId: Int? = null
    /** Inline hat collapse state — collapsed by default (the «Инфо» toolbar opens a popup instead). */
    private var hatCollapsed: Boolean = true
    /** The loaded page's poll (page 1) — the «Опрос» toolbar button shows it in a popup. */
    private var currentPoll: forpdateam.ru.forpda.entity.remote.theme.Poll? = null

    /** The full BBCode editor (formatting toolbar, smiles, attachments) — one-to-one with WebView. */
    private var messagePanel: MessagePanel? = null
    private var attachmentsPopup: AttachmentsPopup? = null
    /** Mirror of the editor field text, so a draft survives IME-driven view churn (cf. WebView). */
    private var messagePanelDraftMirror = ""
    private val uploadQueue: ArrayDeque<Pair<List<RequestFile>, List<AttachmentItem>>> = ArrayDeque()
    private var uploadInProgress = false
    /** Non-null when the editor is editing an existing post (else composing a new reply). */
    private var editingForm: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm? = null

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        uploadFiles(FilePickHelper.onActivityResult(requireContext(), data))
    }

    private var paginationBar: android.widget.LinearLayout? = null
    private var paginationLabel: TextView? = null

    private var searchBar: android.widget.LinearLayout? = null
    private var searchInput: android.widget.EditText? = null
    private var searchCountLabel: TextView? = null
    /** Adapter positions (poll-header-offset applied) of posts matching the current query, in order. */
    private val searchMatchPositions = ArrayList<Int>()
    private var currentMatchIndex = -1
    /** The 1-based page shown in the pagination bar (best-effort as the user scrolls / jumps). */
    private var barCurrentPage: Int = 1
    /** Set for an explicit page-jump so [applyInitialAnchor] lands on the page top, not unread/find. */
    private var pendingJumpToTop: Boolean = false

    /** «Умная кнопка темы» (FAB) state: enabled per pref; arrow follows the last scroll direction. */
    private var fabEnabled = false
    private var fabPointsDown = true

    override fun hasBackHandling(): Boolean =
            messagePanel?.visibility == View.VISIBLE || searchBar?.visibility == View.VISIBLE

    override fun onBackPressed(): Boolean {
        // Back closes the find-on-page bar / reply editor first, before leaving the topic.
        if (searchBar?.visibility == View.VISIBLE) {
            closeSearch()
            return true
        }
        // Let the panel dismiss its own BBCode/smiles popup before it hides entirely.
        if (messagePanel?.onBackPressed() == true) return true
        if (messagePanel?.visibility == View.VISIBLE) {
            hideMessagePanel()
            return true
        }
        return super.onBackPressed()
    }

    private val topicUrl: String
        get() = arguments?.getString(TabFragment.ARG_TAB).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }?.let { setTitle(it) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = androidx.recyclerview.widget.ConcatAdapter(pollHeaderAdapter, postsAdapter)
        // Bottom room for the CLASSIC-mode pagination bar is managed in updatePaginationBar().
        recyclerView.clipToPadding = false
        applyDisplaySettings()
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) maybeLoadNextPage()
                if (dy < 0) maybeLoadPrevPage()
                markVisiblePostsRead()
                maybeMarkTopicReadAtEnd()
                updateBarCurrentPageFromScroll()
                updateFabOnScroll(dy)
            }
        })
        val auth = authHolder.get()
        postsAdapter.setAuthContext(authorized = auth.isAuth(), memberId = auth.userId)
        installPageSwipeDetector()
        refreshLayout.setOnRefreshListener { loadTopic(loadedUrl ?: topicUrl) }
        setupMessagePanel()
        setupFab()
        setupToolbarMenu()
        applyToolbarAutoHide()
        loadTopic(topicUrl)
    }

    /**
     * «Умная кнопка темы» (setting «Умная кнопка темы»): a persistent mini FAB (parity with the
     * WebView, which keeps it on screen). A short tap scrolls ~a screen in the direction the user is
     * travelling (the arrow follows that direction); a LONG press opens the page-jump dialog. It
     * stays visible so the long press is always reachable. Hidden entirely when the pref is off.
     */
    private fun setupFab() {
        fabEnabled = mainPreferencesHolder.getScrollButtonEnabled()
        val dm = resources.displayMetrics
        // Bottom-end, lifted above the CLASSIC pagination bar so it never sits on top of it.
        (fab.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { lp ->
            lp.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
            val m = (16 * dm.density).toInt()
            lp.rightMargin = m
            lp.bottomMargin = ((if (isClassicMode()) 64 else 16) * dm.density).toInt()
            fab.layoutParams = lp
        }
        androidx.core.view.ViewCompat.setElevation(fab, 12f * dm.density)
        if (!fabEnabled) {
            fab.hide()
            return
        }
        fab.size = com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI
        fab.setImageResource(forpdateam.ru.forpda.R.drawable.ic_arrow_down)
        fab.isLongClickable = true
        fab.setOnClickListener { smartScrollTap() }
        fab.setOnLongClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showPagePicker()
            true
        }
        fab.show()
    }

    /** Point the FAB arrow along the current scroll direction (it stays visible, unlike a timed FAB). */
    private fun updateFabOnScroll(dy: Int) {
        if (!fabEnabled) return
        if (kotlin.math.abs(dy) < SCROLL_HIDE_THRESHOLD) return
        val down = dy > 0
        if (down != fabPointsDown) {
            fabPointsDown = down
            fab.setImageResource(
                    if (down) forpdateam.ru.forpda.R.drawable.ic_arrow_down
                    else forpdateam.ru.forpda.R.drawable.ic_arrow_up)
        }
        if (!fab.isShown) fab.show()
    }

    /** Short tap: scroll ~a screen in the arrow's direction (parity with the WebView pageUp/Down). */
    private fun smartScrollTap() {
        val step = (recyclerView.height * 0.85f).toInt().coerceAtLeast(1)
        recyclerView.smoothScrollBy(0, if (fabPointsDown) step else -step)
    }

    /**
     * Embeds the full WebView editor component ([MessagePanel]) one-to-one: BBCode formatting
     * toolbar, smiles, attachments and the send / clear / hide controls. Hidden until the user taps
     * «Написать», reply or quote. Send / upload / delete reuse [ThemeEditorUseCase] (the same
     * network logic the WebView editor drives through its ViewModel).
     */
    private fun setupMessagePanel() {
        if (messagePanel != null) return
        val panel = MessagePanel(
                requireContext(), fragmentContainer, messagePanelHost, false,
                mainPreferencesHolder, dimensionsProvider, otherPreferencesHolder,
        )
        panel.hostBaseBottomMarginProvider = { messagePanelBaseBottomMargin() }
        // The message-panel host sits over the fragment root, whose background is ?colorPrimary (blue).
        // The compact panel floats with margins, so that blue leaks around it as an unwanted border.
        // Paint the host with the list surface so the panel floats seamlessly (parity with WebView).
        messagePanelHost.setBackgroundColor(
                requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        panel.visibility = View.GONE
        // Behavior off: with IME adjustResize the AppBar translationY would push the panel under the
        // keyboard (matches the WebView fragment's disableBehavior()).
        panel.disableBehavior()
        panel.messageField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) { messagePanelDraftMirror = s.toString() }
        })
        panel.addSendOnClickListener { sendMessage() }
        panel.setClearMessageClickListener { messagePanel?.clearMessage(); messagePanelDraftMirror = "" }
        panel.hideButton?.visibility = View.VISIBLE
        panel.hideButton?.setOnClickListener { hideMessagePanel() }
        panel.fullButton?.visibility = View.GONE // fullscreen editor is a separate WebView-era screen
        attachmentsPopup = panel.attachmentsPopup
        attachmentsPopup?.setAddOnClickListener { pickFileLauncher.launch(FilePickHelper.pickFile(false)) }
        attachmentsPopup?.setDeleteOnClickListener { removeFiles() }
        attachmentsPopup?.setRetryUploadListener(object : AttachmentsPopup.OnRetryUploadListener {
            override fun onRetry(files: List<RequestFile>, pending: List<AttachmentItem>) {
                enqueueUpload(files, pending)
            }
        })
        messagePanel = panel
    }

    /**
     * Native top-toolbar (parity with the WebView theme toolbar): dedicated icon BUTTONS shown
     * always — «Написать» (pencil, opens the editor), «Опрос» (visible only with a poll), search,
     * «Обновить» and «Инфо» (topic hat, visible only when a hat exists) — plus an overflow with
     * page-jump / copy-link / open-forum. Icons match the WebView (ic_toolbar_create / ic_poll_box /
     * ic_toolbar_search / ic_toolbar_refresh / ic_info). The tab's [toolbar] comes from [TabFragment].
     */
    private fun setupToolbarMenu() {
        // Back navigation to leave the topic (parity with the WebView; system back is reused).
        toolbar.setNavigationIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_arrow_back)
        toolbar.navigationContentDescription = getString(forpdateam.ru.forpda.R.string.close_tab)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        // Overflow popup theme comes from the toolbar's app:popupTheme="?attr/popup_overlay" (fragment_base),
        // which resolves to a readable per-palette dropdown — no manual override needed.
        val menu = toolbar.menu
        menu.clear()
        menu.add(0, MENU_CREATE, 0, "Написать").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_create)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_POLL, 1, "Опрос").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_poll_box)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = false
        }
        menu.add(0, MENU_SEARCH, 2, "Найти на странице").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_search)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_REFRESH, 3, "Обновить").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_toolbar_refresh)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_HAT, 4, "Шапка темы").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_info)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = false
        }
        // «⋮» opens a compact top-sliding panel of extra actions (see showOverflowMenu) rather than the
        // toolbar's built-in overflow popup, which mis-renders with a transparent background on this theme.
        menu.add(0, MENU_OVERFLOW, 20, "Ещё").apply {
            setIcon(forpdateam.ru.forpda.R.drawable.ic_more_vert)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CREATE -> { toggleComposeEditor(); true }
                MENU_POLL -> { onPollToolbarClick(); true }
                MENU_SEARCH -> { toggleSearchBar(); true }
                MENU_REFRESH -> { loadTopic(loadedUrl ?: topicUrl); true }
                MENU_HAT -> { onHatToolbarClick(); true }
                MENU_OVERFLOW -> { showOverflowMenu(); true }
                else -> false
            }
        }
        refreshToolbarState()
    }

    /**
     * Toggle the poll / hat toolbar-icon visibility to match the loaded page (they are meaningless
     * on pages that carry neither). Mirrors the WebView's refreshToolbarMenuItems visibility gating.
     */
    private fun refreshToolbarState() {
        // Poll and page-search share one toolbar slot (WebView parity): a poll takes it when present, and
        // page-search then moves into the «⋮» overflow (see showOverflowMenu). No poll → show search.
        toolbar.menu.findItem(MENU_POLL)?.isVisible = pageHasPoll
        toolbar.menu.findItem(MENU_SEARCH)?.isVisible = !pageHasPoll
        toolbar.menu.findItem(MENU_HAT)?.isVisible = pageHasHat
    }

    /**
     * «Поведение тулбара» = HIDE_ON_SCROLL: let the AppBar scroll off with the list (standard
     * AppBarLayout scroll flags; the RecyclerView container already carries ScrollingViewBehavior).
     * Kept PINNED while the editor/search is open so those don't fight the collapsing bar. Re-read
     * on return since the pref may have changed.
     */
    private fun applyToolbarAutoHide() {
        val enabled = mainPreferencesHolder.getTopicToolbarBehavior() ==
                forpdateam.ru.forpda.common.Preferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL &&
                messagePanel?.visibility != View.VISIBLE &&
                searchBar?.visibility != View.VISIBLE
        val lp = toolbarLayout.layoutParams as? com.google.android.material.appbar.AppBarLayout.LayoutParams
                ?: return
        val flags = if (enabled) {
            com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS or
                    com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
        } else {
            0
        }
        if (lp.scrollFlags != flags) {
            lp.scrollFlags = flags
            toolbarLayout.layoutParams = lp
            if (!enabled) appBarLayout.setExpanded(true, false)
        }
    }

    /** Toolbar «Написать»: opens the empty compose editor (or closes it if already open). */
    private fun toggleComposeEditor() {
        if (messagePanel?.visibility == View.VISIBLE) {
            hideMessagePanel()
        } else {
            editingForm = null
            showMessagePanel(showKeyboard = true)
        }
    }

    /** Reveal the editor panel (and hide the pagination bar they share the bottom edge with). */
    private fun showMessagePanel(showKeyboard: Boolean) {
        val panel = messagePanel ?: return
        if (panel.visibility != View.VISIBLE) {
            panel.visibility = View.VISIBLE
            paginationBar?.visibility = View.GONE
            appBarLayout.setExpanded(true, true) // reveal toolbar for editing
            applyToolbarAutoHide() // pin the toolbar while composing
            if (showKeyboard) panel.show()
        }
        if (showKeyboard) {
            panel.messageField.requestFocus()
            showKeyboard(panel.messageField)
        }
    }

    /** Hide the editor panel, dismiss the keyboard/popups, and restore the pagination bar. */
    private fun hideMessagePanel() {
        val panel = messagePanel ?: return
        panel.hideImeFromEditor()
        panel.visibility = View.GONE
        panel.hidePopupWindows()
        hideKeyboard()
        editingForm = null
        updatePaginationBar()
        applyToolbarAutoHide() // restore auto-hide once the editor is closed
    }

    /** Toolbar «Опрос»: show the poll in a popup over the theme (parity with the WebView poll overlay). */
    private fun onPollToolbarClick() {
        val poll = currentPoll ?: return
        val ctx = requireContext()
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = PollHeaderAdapter(
                    voteListener = PollHeaderAdapter.PollVoteListener { action, method, form ->
                        submitPoll(action, method, form)
                    },
                    collapsible = false, // popup poll is always fully expanded
            ).also { it.setPoll(poll) }
        }
        showThemePopup("Опрос", rv)
    }

    /**
     * A top-anchored overlay panel showing [content], sliding DOWN from the top over the theme (used by
     * the «Инфо»/«Опрос» toolbar buttons — parity with the WebView hat/poll overlays, which drop down
     * from under the toolbar). Capped to most of the screen height; the [content] scrolls if taller.
     */
    private fun showThemePopup(title: String, content: View) {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val header = TextView(ctx).apply {
            text = title
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            setPadding(pad, pad, pad, pad / 2)
        }
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(ctx, forpdateam.ru.forpda.R.drawable.bg_theme_top_sheet)
            addView(header)
            addView(content, android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        presentTopSheet(root, scrollTarget = content)
    }

    /**
     * Present [root] as an overlay panel that drops down from directly under the toolbar (parity with the
     * WebView hat/poll/menu overlays, which sit at the top of the content area — the toolbar stays visible
     * and un-dimmed). Built in the fragment's own themed context so `?attr/colorSurface` resolves to the
     * active palette — unlike the toolbar's built-in overflow popup, whose nested theme mis-resolves it to
     * a transparent background. When [scrollTarget] is set and the panel would exceed the available height,
     * that view is clamped so it scrolls inside the panel instead. Returns the shown [Dialog].
     */
    private fun presentTopSheet(root: android.widget.LinearLayout, scrollTarget: View?): android.app.Dialog {
        val dialog = android.app.Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true) // tap the toolbar / the strip below the panel to dismiss
        // Anchor at the TOP of the content area (the list), i.e. below the toolbar AND any top pagination
        // bar, so the panel drops out from under the chrome — never covering it or clipping its own top.
        val loc = IntArray(2)
        recyclerView.getLocationOnScreen(loc)
        val topY = loc[1].coerceAtLeast(0)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            // No full-screen dim — the WebView overlay just covers the content below the toolbar.
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes = attributes.apply {
                gravity = android.view.Gravity.TOP
                y = topY
                windowAnimations = forpdateam.ru.forpda.R.style.ThemeTopSheetAnimation
            }
        }
        if (scrollTarget != null) {
            // Cap to ~82% of the content height so tall content scrolls inside AND a strip stays free at the
            // bottom for tap-to-dismiss; content still starts from its top.
            val maxH = ((resources.displayMetrics.heightPixels - topY) * 0.82).toInt().coerceAtLeast(0)
            scrollTarget.viewTreeObserver.addOnPreDrawListener(
                    object : android.view.ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            scrollTarget.viewTreeObserver.removeOnPreDrawListener(this)
                            val overflow = root.height - maxH
                            if (overflow > 0) {
                                scrollTarget.layoutParams = scrollTarget.layoutParams
                                        .apply { height = scrollTarget.height - overflow }
                                scrollTarget.requestLayout()
                            }
                            return true
                        }
                    })
        }
        dialog.show()
        return dialog
    }

    /**
     * The toolbar «⋮» overflow — a compact dropdown anchored under the «⋮» button (right side), parity
     * with the WebView toolbar menu. Uses a [ListPopupWindow] with an explicitly solid background so it
     * never renders transparent (the toolbar's built-in overflow mis-resolves `?attr/colorSurface`), and
     * drops out from under the toolbar without covering it.
     */
    private fun showOverflowMenu() {
        val ctx = requireContext()
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Скопировать ссылку" to {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("topic",
                        "https://4pda.to/forum/index.php?showtopic=$pageTopicId"))
                Toast.makeText(ctx, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
            })
            // Page-search lives here whenever a poll has taken its toolbar slot (see refreshToolbarState).
            if (pageHasPoll) add("Найти на странице" to { toggleSearchBar() })
            add("Найти в теме" to {
                if (pageTopicId > 0) navigationUseCase.openSearchInTopic(pageForumId, pageTopicId, nick = "")
            })
            add("Найти мои посты" to {
                if (pageTopicId > 0) navigationUseCase.openSearchMyPosts(pageTopicId, pageForumId)
            })
            add("Перейти на страницу" to { showPagePicker() })
            add("Открыть форум темы" to { if (pageForumId > 0) navigationUseCase.openForum(pageForumId) })
            add("Открыть в браузере" to {
                runCatching {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://4pda.to/forum/index.php?showtopic=$pageTopicId")))
                }
            })
        }
        val dm = resources.displayMetrics
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val surface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(surface)
            cornerRadius = 12 * dm.density
        }
        val popup = androidx.appcompat.widget.ListPopupWindow(ctx)
        popup.anchorView = toolbar.findViewById(MENU_OVERFLOW) ?: toolbar
        popup.setBackgroundDrawable(bg)
        popup.isModal = true
        popup.width = (240 * dm.density).toInt()
        popup.verticalOffset = (4 * dm.density).toInt()
        val hpad = (20 * dm.density).toInt()
        val vpad = (13 * dm.density).toInt()
        val adapter = object : android.widget.ArrayAdapter<String>(
                ctx, 0, actions.map { it.first }) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val tv = (convertView as? TextView) ?: TextView(ctx).apply {
                    setPadding(hpad, vpad, hpad, vpad)
                    textSize = 15f
                    setTextColor(onSurface)
                }
                tv.text = getItem(position)
                return tv
            }
        }
        popup.setAdapter(adapter)
        popup.setOnItemClickListener { _, _, position, _ ->
            popup.dismiss()
            actions[position].second()
        }
        popup.show()
    }

    /**
     * Submit a poll vote (from [PollHeaderAdapter]) via [ThemeApi.submitPoll] — the same server write
     * the WebView JS `submitThemePoll` performs. On success the page is reloaded so the poll re-renders
     * with results; we keep the view pinned to the top so the freshly-voted poll stays in sight.
     */
    private fun submitPoll(action: String, method: String, encodedForm: String) {
        if (isSending) return
        isSending = true
        Toast.makeText(requireContext(), "Отправка голоса…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.submitPoll(action, method, encodedForm) }
            }
            isSending = false
            if (view == null) return@launch
            result.onSuccess {
                Toast.makeText(requireContext(), "Голос учтён", Toast.LENGTH_SHORT).show()
                pendingJumpToTop = true // land on the poll (now showing results), not the unread anchor
                loadTopic(loadedUrl ?: topicUrl)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Не удалось проголосовать: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Toolbar «Инфо»: show the topic hat in a popup over the theme (parity with the WebView overlay). */
    private fun onHatToolbarClick() {
        val hatId = topicHatPostId ?: return
        val hatItem = loadedItems.firstOrNull { it.postId == hatId } ?: return
        val ctx = requireContext()
        // A throwaway adapter renders the hat post fully (no hat-collapse in the popup) with all its
        // spoilers/links, reusing the exact post rendering.
        val popupAdapter = TopicPostsAdapter(linkHandler, this)
        popupAdapter.setDisplaySettings(currentDisplaySettings())
        val auth = authHolder.get()
        popupAdapter.setAuthContext(auth.isAuth(), auth.userId)
        val rv = androidx.recyclerview.widget.RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = popupAdapter
        }
        popupAdapter.submitList(listOf(hatItem))
        showThemePopup("Шапка темы", rv)
    }

    /** Header tap on the hat post itself toggles its body (same session state as the toolbar «Инфо»). */
    override fun onToggleHat() {
        val hatId = topicHatPostId ?: return
        hatCollapsed = !hatCollapsed
        postsAdapter.setTopicHat(hatId, hatCollapsed)
    }

    /**
     * Optional horizontal-swipe page navigation (setting «Свайпы страниц», default OFF): a deliberate
     * left drag jumps to the next page, right to the previous. Intercepts the gesture only once
     * HORIZONTAL travel clearly dominates (so vertical scroll is never stolen); intercepting hands the
     * child view an ACTION_CANCEL, which is what prevents a link tap firing mid-swipe over hat/quote
     * links. Being opt-in, it can't regress the default reading experience.
     */
    private fun installPageSwipeDetector() {
        val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        val minDist = SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var claimed = false

            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                if (!mainPreferencesHolder.getTopicPageSwipeEnabled()) return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.x; downY = e.y; claimed = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val dy = e.y - downY
                        if (!claimed && kotlin.math.abs(dx) > touchSlop * 2 &&
                                kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f) {
                            claimed = true
                            return true // steal the gesture → child gets CANCEL (no link tap / scroll)
                        }
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent) {
                if (e.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    val dx = e.x - downX
                    if (kotlin.math.abs(dx) > minDist) {
                        if (dx < 0) jumpToPage(barCurrentPage + 1) else jumpToPage(barCurrentPage - 1)
                    }
                    claimed = false
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        })
    }

    /**
     * CLASSIC reading mode shows one page at a time with the bottom pagination bar (no infinite
     * scroll); HYBRID (default) is continuous infinite scroll with no bar. Mirrors the WebView
     * «Режим чтения тем» setting.
     */
    private fun isClassicMode(): Boolean =
            mainPreferencesHolder.getTopicScrollMode() ==
                    forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.CLASSIC

    /** Downward infinite scroll: when within [NEXT_PAGE_PREFETCH_DISTANCE] items of the end. */
    private fun maybeLoadNextPage() {
        if (isClassicMode()) return // classic mode navigates via the bottom bar, not infinite scroll
        if (isLoadingNextPage || !pagination.hasNextPage()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = lm.findLastVisibleItemPosition()
        if (lastVisible >= loadedItems.size - NEXT_PAGE_PREFETCH_DISTANCE) {
            loadNextPage()
        }
    }

    private fun loadNextPage() {
        val url = pagination.nextPageUrl() ?: return
        isLoadingNextPage = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            result.onSuccess { page ->
                val newItems = pagination.registerAndFilterNew(mapper.map(page.posts))
                pagination.onPageAppended(page.pagination.current, page.pagination)
                if (newItems.isNotEmpty()) {
                    loadedItems.addAll(newItems)
                    postsAdapter.submitList(loadedItems.toList())
                }
                updatePaginationBar() // totalPages may have grown

            }
            // On failure: silently stop; the user can pull-to-refresh. isLoadingNextPage resets so a
            // later scroll retries.
            isLoadingNextPage = false
        }
    }

    /** Upward infinite scroll: when the top of the list nears item 0 and a page above exists. */
    private fun maybeLoadPrevPage() {
        if (isClassicMode()) return // classic mode navigates via the bottom bar, not infinite scroll
        if (isLoadingPrevPage || !pagination.hasPrevPage()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        if (lm.findFirstVisibleItemPosition() <= PREV_PAGE_PREFETCH_DISTANCE) {
            loadPrevPage()
        }
    }

    private fun loadPrevPage() {
        val url = pagination.prevPageUrl() ?: return
        isLoadingPrevPage = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            var reArm = true
            result.onSuccess { page ->
                val newItems = pagination.registerAndFilterNew(mapper.map(page.posts))
                pagination.onPagePrepended(page.pagination.current)
                if (newItems.isNotEmpty()) {
                    prependPreservingPosition(newItems)
                    reArm = false // re-armed inside the submitList callback after the scroll is restored
                }
            }
            if (reArm) isLoadingPrevPage = false
        }
    }

    /**
     * Insert [newItems] at the top and keep the currently-visible post fixed on screen. The anchor
     * is the current first-visible post BY ID (not index) + its pixel offset, so the prepend never
     * makes the content jump under the user's finger.
     */
    private fun prependPreservingPosition(newItems: List<NativePostItem>) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val header = headerOffset()
        val anchorConcatPos = lm?.findFirstVisibleItemPosition() ?: header
        val anchorOffset = lm?.findViewByPosition(anchorConcatPos)?.top ?: 0
        // Concat position → post index (subtract the poll header, if any).
        val anchorPostId = loadedItems.getOrNull(anchorConcatPos - header)?.postId

        loadedItems.addAll(0, newItems)
        postsAdapter.submitList(loadedItems.toList()) {
            if (view != null) {
                val newIndex = anchorPostId
                        ?.let { id -> loadedItems.indexOfFirst { it.postId == id } }
                        ?.takeIf { it >= 0 }
                        ?: newItems.size
                (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(newIndex + header, anchorOffset)
            }
            isLoadingPrevPage = false
        }
    }

    // region ThemeTabHost — navigator-driven tab reuse / topic switch

    override fun getOpenTopicIdForReuse(): Int? {
        val url = loadedUrl ?: topicUrl
        return ThemeApi.extractTopicIdFromUrl(url)?.takeIf { it > 0 }
    }

    override fun loadThemeUrlFromNavigator(
            url: String,
            sourceScreen: String,
            openIntent: String,
            listHints: TopicOpenListHints?,
    ) {
        // The navigator has already written the new url/title into arguments; refresh the title
        // and reload the list for the new topic (or the new findpost within the same topic).
        arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }?.let { setTitle(it) }
        if (view != null) {
            loadTopic(url)
        }
        // If the view is not created yet, onViewCreated will load from the (already updated) args.
    }

    override fun onTabStackBecameCurrent() {
        // Load lazily if this tab became visible before it ever loaded (e.g. created hidden).
        if (loadedUrl == null && view != null) {
            loadTopic(topicUrl)
        }
        // The user may have changed font/avatar prefs while away — re-apply on return.
        if (view != null) {
            applyDisplaySettings()
            setupFab() // the «Умная кнопка темы» pref may have been toggled while away
            applyToolbarAutoHide() // the «Поведение тулбара» pref may have been toggled while away
        }
    }

    override fun onResume() {
        super.onResume()
        messagePanel?.onResume()
    }

    override fun onPause() {
        super.onPause()
        messagePanel?.onPause()
    }

    override fun onDestroyView() {
        messagePanel?.onDestroy()
        messagePanel = null
        attachmentsPopup = null
        super.onDestroyView()
    }

    override fun hideKeyboard() {
        super.hideKeyboard()
        messagePanel?.hidePopupWindows()
    }

    /**
     * Read the user's font-size / avatar prefs and push them into the adapter (parity with the
     * WebView path, which sets `defaultFontSize` + avatar CSS at load). Font size is an absolute
     * base (default 16); [PostDisplaySettings.textScale] is relative to that reference so the
     * default look is unchanged.
     */
    private fun applyDisplaySettings() {
        postsAdapter.setDisplaySettings(currentDisplaySettings())
    }

    /** Current post display prefs (font/avatars/density) — shared by the list and the hat popup. */
    private fun currentDisplaySettings() = TopicPostsAdapter.PostDisplaySettings(
            textScale = mainPreferencesHolder.getWebViewFontSize() / REFERENCE_FONT_SIZE,
            showAvatars = topicPreferencesHolder.getShowAvatars(),
            circleAvatars = topicPreferencesHolder.getCircleAvatars(),
            density = mainPreferencesHolder.getTopicPostDensity(),
    )

    override fun onRestoredAfterChildFragmentRemoved() {
        // Native list keeps its state/scroll across a covering child fragment — nothing to restore.
    }

    // endregion

    // region PostActionListener — write actions (authorised by the user)

    override fun onVote(item: NativePostItem, up: Boolean) {
        // Rating a post is a one-shot irreversible action → confirm first (per user request).
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (up) "Повысить репутацию поста?" else "Понизить репутацию поста?")
                .setMessage("Автор: ${item.nick.orEmpty()}")
                .setNegativeButton("Отмена", null)
                .setPositiveButton(if (up) "Повысить" else "Понизить") { _, _ -> performVote(item, up) }
                .show()
    }

    private fun performVote(item: NativePostItem, up: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.votePost(item.postId, up) }
            }
            if (view == null) return@launch
            result.onSuccess { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                // Optimistically nudge the visible rating so the change is reflected immediately.
                updateRatingOptimistically(item.postId, if (up) +1 else -1)
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: "Ошибка голосования", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateRatingOptimistically(postId: Int, delta: Int) {
        val idx = loadedItems.indexOfFirst { it.postId == postId }
        if (idx < 0) return
        val cur = loadedItems[idx]
        val newRating = ((cur.postRating?.replace("+", "")?.trim()?.toIntOrNull() ?: 0) + delta)
        // Voted once → can't vote again on that direction; drop both to avoid a second attempt.
        loadedItems[idx] = cur.copy(
                postRating = newRating.toString(),
                canPlusPostRating = false,
                canMinusPostRating = false,
        )
        postsAdapter.submitList(loadedItems.toList())
    }

    override fun onReply(item: NativePostItem) {
        insertIntoEditor("[snapback]${item.postId}[/snapback] [b]${item.nick},[/b] \n")
    }

    override fun onQuote(item: NativePostItem) {
        val date = item.date?.takeIf { it.isNotBlank() }?.let { " date=\"$it\"" } ?: ""
        insertIntoEditor("[quote name=\"${item.nick}\"$date post=${item.postId}]${'\n'}[/quote]${'\n'}")
    }

    override fun onQuoteSelection(item: NativePostItem, selectedText: String) {
        val d = item.date?.takeIf { it.isNotBlank() }?.let { " date=\"$it\"" } ?: ""
        insertIntoEditor("[quote name=\"${item.nick}\"$d post=${item.postId}]$selectedText[/quote]${'\n'}")
    }

    /** Insert BBCode/text into the panel field at the caret and reveal the editor (like the WebView). */
    private fun insertIntoEditor(text: String) {
        editingForm = null
        messagePanel?.insertText(text)
        showMessagePanel(showKeyboard = true)
    }

    override fun onEdit(item: NativePostItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val form = withContext(Dispatchers.IO) {
                runCatching { editPostApi.loadForm(item.postId) }
            }.getOrNull()
            if (view == null) return@launch
            if (form == null) {
                Toast.makeText(requireContext(), "Не удалось открыть пост для правки", Toast.LENGTH_SHORT).show()
                return@launch
            }
            editingForm = form
            val panel = messagePanel ?: return@launch
            panel.setText(form.message)
            messagePanelDraftMirror = form.message.orEmpty()
            form.attachments.takeIf { it.isNotEmpty() }?.let { attachmentsPopup?.setAttachments(it) }
            showMessagePanel(showKeyboard = true)
        }
    }

    override fun onDelete(item: NativePostItem) {
        // Destructive → confirm first (authorised, but irreversible).
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить сообщение?")
                .setMessage("Это действие необратимо.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить") { _, _ -> performDelete(item) }
                .show()
    }

    /**
     * The «⋮» post menu (parity with the WebView showPostMenu): reply / quote / copy-link / share /
     * author profile / report / edit / delete. Rendered as a solid MaterialAlertDialog list.
     */
    override fun onPostMenu(item: NativePostItem) {
        val postUrl = "https://4pda.to/forum/index.php?showtopic=$pageTopicId&view=findpost&p=${item.postId}"
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Ответить" to { onReply(item) })
            add("Цитировать" to { onQuote(item) })
            add("Копировать ссылку на сообщение" to {
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("post", postUrl))
                Toast.makeText(requireContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show()
            })
            add("Поделиться ссылкой на сообщение" to {
                runCatching {
                    startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, postUrl)
                            }, null))
                }
            })
            if (item.userId > 0) add("Профиль автора" to {
                linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.userId}", null)
            })
            if (item.canReport) add("Пожаловаться" to {
                linkHandler.handle("https://4pda.to/forum/index.php?act=report&p=${item.postId}", null)
            })
            if (item.canEdit) add("Изменить" to { onEdit(item) })
            if (item.canDelete) add("Удалить" to { onDelete(item) })
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.nick.orEmpty())
                .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
                .show()
    }

    override fun onReputation(item: NativePostItem) {
        if (item.userId <= 0) return
        val ctx = requireContext()
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (item.canPlusRep) options.add("Увеличить" to { showReputationChangeDialog(item, increase = true) })
        options.add("Посмотреть" to {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.userId}&tab=reputation", null)
        })
        if (item.canMinusRep) options.add("Уменьшить" to { showReputationChangeDialog(item, increase = false) })
        val labels = options.map { it.first }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Репутация ${item.nick.orEmpty()}")
                .setItems(labels) { _, which -> options[which].second() }
                .show()
    }

    private fun showReputationChangeDialog(item: NativePostItem, increase: Boolean) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply { hint = "Комментарий (необязательно)" }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("${if (increase) "Увеличить" else "Уменьшить"} репутацию ${item.nick.orEmpty()}")
                .setView(input)
                .setPositiveButton("OK") { _, _ -> performReputationChange(item, increase, input.text?.toString().orEmpty()) }
                .setNegativeButton("Отмена", null)
                .show()
    }

    private fun performReputationChange(item: NativePostItem, increase: Boolean, message: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { reputationApi.editReputation(item.postId, item.userId, increase, message) }.getOrDefault(false)
            }
            if (view == null) return@launch
            Toast.makeText(requireContext(),
                    if (ok) "Репутация изменена" else "Не удалось изменить репутацию",
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun performDelete(item: NativePostItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { themeApi.deletePost(item.postId) }.getOrDefault(false)
            }
            if (view == null) return@launch
            if (ok) {
                Toast.makeText(requireContext(), "Удалено", Toast.LENGTH_SHORT).show()
                loadedUrl?.let { loadTopic(it) }
            } else {
                Toast.makeText(requireContext(), "Не удалось удалить", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // region attachments — reuse the WebView editor's upload/delete pipeline via ThemeEditorUseCase

    /** Pre-shows placeholders for the picked files, then queues them for upload. */
    private fun uploadFiles(files: List<RequestFile>) {
        val pending = attachmentsPopup?.preUploadFiles(files) ?: emptyList()
        attachmentsPopup?.revealDuringUploadPreview()
        enqueueUpload(files, pending)
    }

    private fun enqueueUpload(files: List<RequestFile>, pending: List<AttachmentItem>) {
        uploadQueue.addLast(files to pending)
        pumpUploadQueue()
    }

    /** Serialise uploads (the popup shows one batch at a time), matching the WebView fragment. */
    private fun pumpUploadQueue() {
        if (uploadInProgress) return
        val next = uploadQueue.firstOrNull() ?: return
        uploadInProgress = true
        viewLifecycleOwner.lifecycleScope.launch {
            val result = editorUseCase.uploadFiles(0, next.first, next.second)
            if (view != null && result is ThemeEditorUseCase.UploadResult.Success) {
                attachmentsPopup?.onUploadFiles(result.items)
            }
            uploadInProgress = false
            if (uploadQueue.isNotEmpty()) uploadQueue.removeFirst()
            pumpUploadQueue()
        }
    }

    private fun removeFiles() {
        attachmentsPopup?.preDeleteFiles()
        val selected = attachmentsPopup?.getSelected() ?: emptyList()
        if (selected.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val result = editorUseCase.deleteFiles(0, selected)
            if (view != null && result is ThemeEditorUseCase.DeleteResult.Success) {
                attachmentsPopup?.onDeleteFiles(result.items)
            }
        }
    }

    // endregion

    /**
     * Lazily builds the bottom pagination bar «  ‹  N / M  ›  » — styled to match the WebView
     * `theme_bottom_pagination`: flat surface, bold `colorOnSurface` chevrons (NOT accent-blue),
     * no heavy divider. Tapping the label opens a page picker. CLASSIC mode only.
     */
    private fun ensurePaginationBar() {
        if (paginationBar != null) return
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val bar = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            // Match the POST-CARD colour (colorSurfaceContainer) so the bar blends seamlessly with the
            // content above — no visible seam/line — like the WebView's on-page bottom pagination.
            setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer))
            elevation = 0f
            visibility = View.GONE
        }
        fun navButton(label: String, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            val pv = (8 * dm.density).toInt()
            setPadding(0, pv, 0, pv)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = ctx.obtainStyledAttributes(intArrayOf(
                    android.R.attr.selectableItemBackgroundBorderless)).use { it.getDrawable(0) }
            setOnClickListener { onClick() }
        }
        val label = TextView(ctx).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(onSurface)
            val pv = (8 * dm.density).toInt()
            setPadding(0, pv, 0, pv)
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.45f)
            setOnClickListener { showPagePicker() }
        }
        bar.addView(navButton("«") { jumpToPage(1) })
        bar.addView(navButton("‹") { jumpToPage(barCurrentPage - 1) })
        bar.addView(label)
        bar.addView(navButton("›") { jumpToPage(barCurrentPage + 1) })
        bar.addView(navButton("»") { jumpToPage(pagination.totalPages) })
        coordinatorLayout.addView(bar, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = android.view.Gravity.BOTTOM })
        paginationBar = bar
        paginationLabel = label
    }

    /** Show/hide the find-on-page bar; hiding clears the query and match highlights. */
    private fun toggleSearchBar() {
        val bar = ensureSearchBar()
        if (bar.visibility == View.VISIBLE) {
            closeSearch()
        } else {
            bar.visibility = View.VISIBLE
            appBarLayout.setExpanded(true, true)
            applyToolbarAutoHide() // pin the toolbar while searching
            searchInput?.requestFocus()
            searchInput?.let { showKeyboard(it) }
        }
    }

    private fun closeSearch() {
        searchBar?.visibility = View.GONE
        searchInput?.setText("")
        postsAdapter.setSearchQuery("")
        searchMatchPositions.clear()
        currentMatchIndex = -1
        hideKeyboard()
        applyToolbarAutoHide() // restore auto-hide once search is closed
    }

    /** Lazily builds the find-on-page bar: [query] «k/N» ↑ ↓ ✕, pinned above the pagination bar. */
    private fun ensureSearchBar(): android.widget.LinearLayout {
        searchBar?.let { return it }
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val bar = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            val p = (4 * dm.density).toInt()
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        val input = android.widget.EditText(ctx).apply {
            hint = "Поиск по теме"
            textSize = 15f
            maxLines = 1
            isSingleLine = true
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) = onSearchQueryChanged(s?.toString().orEmpty())
            })
        }
        val count = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant))
            val ph = (8 * dm.density).toInt()
            setPadding(ph, 0, ph, 0)
        }
        fun iconBtn(label: String, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 18f
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            val ph = (10 * dm.density).toInt()
            val pv = (6 * dm.density).toInt()
            setPadding(ph, pv, ph, pv)
            setOnClickListener { onClick() }
        }
        bar.addView(input)
        bar.addView(count)
        bar.addView(iconBtn("↑") { stepMatch(-1) })
        bar.addView(iconBtn("↓") { stepMatch(1) })
        bar.addView(iconBtn("✕") { closeSearch() })
        // Sit above the pagination bar (which is ~52dp tall).
        coordinatorLayout.addView(bar, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            bottomMargin = (52 * dm.density).toInt()
        })
        searchBar = bar
        searchInput = input
        searchCountLabel = count
        return bar
    }

    private fun onSearchQueryChanged(query: String) {
        postsAdapter.setSearchQuery(query)
        searchMatchPositions.clear()
        currentMatchIndex = -1
        val q = query.trim()
        if (q.isNotBlank()) {
            val header = headerOffset()
            loadedItems.forEachIndexed { index, item ->
                if (postMatchesQuery(item, q)) searchMatchPositions.add(index + header)
            }
        }
        searchCountLabel?.text = if (q.isBlank()) "" else "${if (searchMatchPositions.isEmpty()) 0 else 1}/${searchMatchPositions.size}"
        if (searchMatchPositions.isNotEmpty()) {
            currentMatchIndex = 0
            scrollToMatch(0)
        }
    }

    /** Cycle to the previous (-1) / next (+1) matching post and scroll there. */
    private fun stepMatch(dir: Int) {
        if (searchMatchPositions.isEmpty()) return
        currentMatchIndex = (currentMatchIndex + dir + searchMatchPositions.size) % searchMatchPositions.size
        searchCountLabel?.text = "${currentMatchIndex + 1}/${searchMatchPositions.size}"
        scrollToMatch(currentMatchIndex)
    }

    private fun scrollToMatch(matchIndex: Int) {
        val pos = searchMatchPositions.getOrNull(matchIndex) ?: return
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
    }

    /** Does any of [item]'s text (body, nested quotes/spoilers, code, attachments) contain [q]? */
    private fun postMatchesQuery(item: NativePostItem, q: String): Boolean =
            item.blocks.any { blockPlainText(it).contains(q, ignoreCase = true) }

    private fun blockPlainText(block: BodyBlock): String = when (block) {
        is BodyBlock.Text -> android.text.Html.fromHtml(block.html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        is BodyBlock.Code -> block.text
        is BodyBlock.Quote -> block.inner.joinToString(" ") { blockPlainText(it) }
        is BodyBlock.Spoiler -> block.inner.joinToString(" ") { blockPlainText(it) }
        is BodyBlock.FileAttachment -> block.name
        is BodyBlock.Table -> block.rows.joinToString(" ") { row ->
            row.joinToString(" ") { android.text.Html.fromHtml(it, android.text.Html.FROM_HTML_MODE_COMPACT).toString() }
        }
        is BodyBlock.WebFallback -> android.text.Html.fromHtml(block.html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
        is BodyBlock.Image -> ""
    }

    /** Refresh the bar's «N / M» text and hide it entirely for single-page topics. */
    private fun updatePaginationBar() {
        if (!pagination.isInitialised) return
        ensurePaginationBar()
        val total = pagination.totalPages
        paginationLabel?.text = "$barCurrentPage / $total"
        // Top-toolbar subtitle mirrors the page position (parity with the WebView toolbar).
        setSubtitle(if (total > 1) "Страница $barCurrentPage из $total" else null)
        // The bottom pagination bar belongs to CLASSIC reading mode only; HYBRID (default) uses
        // continuous infinite scroll with no bar. Also hidden while the reply editor is open.
        paginationBar?.let { bar ->
            // Honour «Панель страниц темы» (getTopicPaginationPanelEnabled) — user can hide the bar
            // to rely on page swipes/FAB instead (parity with the WebView pagination panel toggle).
            val show = isClassicMode() && total > 1 && messagePanel?.visibility != View.VISIBLE &&
                    mainPreferencesHolder.getTopicPaginationPanelEnabled()
            bar.visibility = if (show) View.VISIBLE else View.GONE
            // Reserve bottom room for the bar only when it is shown (CLASSIC); HYBRID needs none.
            val bottomPad = if (show) (52 * resources.displayMetrics.density).toInt() else 0
            recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop,
                    recyclerView.paddingRight, bottomPad)
        }
    }

    /**
     * Best-effort «current page» for the bar from the first-visible post's index: page =
     * firstLoadedPage + floor(index / perPage). Server pages can overlap slightly so this is an
     * indicator, not authoritative — good enough to show where you are while scrolling.
     */
    private fun updateBarCurrentPageFromScroll() {
        if (!pagination.isInitialised || loadedItems.isEmpty()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = lm.findFirstVisibleItemPosition() - headerOffset()
        if (firstVisible < 0) return
        val page = (pagination.firstLoadedPage + firstVisible / pagination.perPage)
                .coerceIn(1, pagination.totalPages)
        if (page != barCurrentPage) {
            barCurrentPage = page
            updatePaginationBar()
        }
    }


    private fun jumpToPage(pageNumber: Int) {
        if (!pagination.isInitialised) return
        val target = pageNumber.coerceIn(1, pagination.totalPages)
        if (target == barCurrentPage && loadedItems.isNotEmpty()) return
        pendingJumpToTop = true
        barCurrentPage = target
        loadTopic(pagination.pageUrl(target))
    }

    private fun showPagePicker() {
        if (!pagination.isInitialised || pagination.totalPages <= 1) return
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 – ${pagination.totalPages}"
            setText(barCurrentPage.toString())
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Перейти на страницу")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    input.text?.toString()?.trim()?.toIntOrNull()?.let { jumpToPage(it) }
                }
                .setNegativeButton("Отмена", null)
                .show()
    }

    /** Resolve the field text, falling back to the mirror if the CodeEditor lost it to view churn. */
    private fun resolveMessagePanelDraft(): String {
        val field = messagePanel?.message.orEmpty()
        return if (field.isNotEmpty()) field else messagePanelDraftMirror
    }

    private fun sendMessage() {
        val panel = messagePanel ?: return
        val message = resolveMessagePanelDraft().trim()
        val attachments = panel.attachments.toMutableList()
        if ((message.isBlank() && attachments.isEmpty()) || isSending || pageTopicId <= 0) return
        isSending = true
        hideKeyboard()
        panel.setProgressState(true)
        viewLifecycleOwner.lifecycleScope.launch {
            // Editing an existing post reuses its loaded form (type=EDIT, postId set); a new reply
            // builds a fresh NEW_POST form from the current topic context. Attachments ride along.
            val form = editingForm?.apply { this.message = message }
                    ?: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm().apply {
                        type = forpdateam.ru.forpda.entity.remote.editpost.EditPostForm.TYPE_NEW_POST
                        forumId = pageForumId
                        topicId = pageTopicId
                        st = pageSt
                        this.message = message
                    }
            form.attachments.clear()
            form.attachments.addAll(attachments)
            val result = withContext(Dispatchers.IO) { runCatching { editPostApi.sendPost(form) } }
            isSending = false
            if (view == null) return@launch
            panel.setProgressState(false)
            result.onSuccess {
                panel.clearMessage()
                panel.clearAttachments()
                messagePanelDraftMirror = ""
                editingForm = null
                hideMessagePanel()
                Toast.makeText(requireContext(), "Отправлено", Toast.LENGTH_SHORT).show()
                loadedUrl?.let { loadTopic(it) }
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Ошибка отправки: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // endregion

    private fun loadTopic(url: String) {
        if (url.isBlank()) {
            refreshLayout.isRefreshing = false
            return
        }
        refreshLayout.isRefreshing = true
        isLoadingNextPage = false
        isLoadingPrevPage = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            refreshLayout.isRefreshing = false
            result.onSuccess { page ->
                loadedUrl = url
                pageForumId = page.forumId
                pageTopicId = page.id
                pageSt = page.st
                // The poll belongs to page 1 only — don't show it when jumping to a later page.
                val poll = if (page.pagination.current <= 1) page.poll else null
                pollHeaderAdapter.setPoll(poll)
                pageHasPoll = poll != null
                currentPoll = poll
                // Detect the topic hat synchronously with the SAME policy the WebView uses
                // (promoteTopicHatForHybridPage → TopicPrependedHatPolicy): on page 1 it's the first
                // post (4pda's «шапка» convention), on deep pages a server-prepended hat. No async
                // metadata subsystem needed.
                topicHatPostId = forpdateam.ru.forpda.presentation.theme.TopicPrependedHatPolicy
                        .detectPrependedHat(page)?.id?.takeIf { it > 0 }
                pageHasHat = topicHatPostId != null
                refreshToolbarState()
                val items = mapper.map(page.posts)
                val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: page.id
                pagination.reset(topicId, page.pagination, items)
                barCurrentPage = pagination.loadedPage
                loadedItems.clear()
                loadedItems.addAll(items)
                mentionScannedPostIds.clear()
                markedTopicReadAtEnd = false
                closeSearch() // matches from a previous page are stale after a reload
                updatePaginationBar()
                postsAdapter.setTopicHat(topicHatPostId, hatCollapsed)
                postsAdapter.submitList(loadedItems.toList()) {
                    if (view != null) applyInitialAnchor(page.anchorPostId, page.hasUnreadTarget, items)
                }
                enrichLoadedPage(page)
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Ошибка загрузки темы: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Deferred desktop/profile metadata merge (author post counts «💬 N» + real post-rating/rep
     * metadata that mobile HTML omits) — parity with the WebView ViewModel's mergeDesktopRatingsIntoPage,
     * which runs AFTER first paint. Re-maps the affected posts and patches them in [loadedItems] by
     * post id (safe even if infinite scroll grew the list meanwhile).
     */
    private fun enrichLoadedPage(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { themeApi.enrichPageMetadata(page, page.url.orEmpty()) }
            }
            if (view == null) return@launch
            val enrichedById = mapper.map(page.posts).associateBy { it.postId }
            var changed = false
            for (i in loadedItems.indices) {
                val updated = enrichedById[loadedItems[i].postId] ?: continue
                if (updated != loadedItems[i]) {
                    loadedItems[i] = updated
                    changed = true
                }
            }
            if (changed) postsAdapter.submitList(loadedItems.toList())
        }
    }

    private fun applyInitialAnchor(
            anchorPostId: String?,
            hasUnreadTarget: Boolean,
            items: List<NativePostItem>,
    ) {
        val ids = items.map { it.postId }
        val targetId = anchorPostId?.toIntOrNull()
        // An explicit page-jump lands on the first post of the requested page, ignoring the server's
        // unread/find anchor (cf. «go to page N lands on last post» — we force the page top).
        val jumpToTop = pendingJumpToTop
        pendingJumpToTop = false
        val request = when {
            jumpToTop -> AnchorRequest.Top
            hasUnreadTarget && targetId != null ->
                AnchorRequest.Post(targetId, AnchorRequest.Post.Reason.FIRST_UNREAD)
            targetId != null ->
                AnchorRequest.Post(targetId, AnchorRequest.Post.Reason.FIND_POST)
            else -> AnchorRequest.Top
        }
        when (val resolution = anchorResolver.resolve(ids, request)) {
            is AnchorResolution.Position -> {
                // For a fresh "to top" open, land on the very top (the poll header, if any, then #1),
                // matching the WebView which shows the poll first. For post targets, offset past the
                // poll header (adapter position 0) to the resolved POST.
                val target = if (request is AnchorRequest.Top) 0 else resolution.index + headerOffset()
                (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(target, 0)
                // Flash the resolved post once so the user sees where a link/find/unread open landed.
                if (request is AnchorRequest.Post) postsAdapter.requestHighlight(request.postId)
            }
            // PostNotLoaded / Empty: nothing to do — downward pagination will bring later pages in.
            else -> Unit
        }
        // Mentions on the initially-visible posts must clear even without a scroll gesture; and a
        // short topic that fully fits on screen (no scroll) still counts as read-to-end.
        recyclerView.post {
            markVisiblePostsRead()
            maybeMarkTopicReadAtEnd()
        }
    }

    /**
     * Clear «Ответы»/mentions for posts currently on screen — mirrors the WebView path's
     * [forpdateam.ru.forpda.presentation.theme.ThemeViewModel.onVisiblePageChanged] →
     * `onRenderedTopicPosts`. [EventsRepository.onTopicPostsRead] itself filters to this topic's
     * real mentions, so feeding it the visible post ids is safe and idempotent; the local
     * [mentionScannedPostIds] guard just avoids re-feeding posts already scanned this session.
     */
    private fun markVisiblePostsRead() {
        if (pageTopicId <= 0) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == androidx.recyclerview.widget.RecyclerView.NO_POSITION ||
                last == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            return
        }
        val header = headerOffset()
        val visibleIds = ArrayList<Int>()
        var hasNew = false
        for (pos in first..last) {
            val item = loadedItems.getOrNull(pos - header) ?: continue
            if (item.postId <= 0) continue
            visibleIds.add(item.postId)
            if (mentionScannedPostIds.add(item.postId)) hasNew = true
        }
        if (hasNew && visibleIds.isNotEmpty()) {
            eventsRepository.onTopicPostsRead(pageTopicId, visibleIds)
        }
    }

    /**
     * When the user scrolls to the very bottom of the LAST page, mark the whole topic read —
     * mirrors the WebView path's [forpdateam.ru.forpda.presentation.theme.ThemeViewModel]
     * `markTopicReadIfEndReached`. Fires once per topic load. The single chokepoint
     * [ThemeUseCase.markTopicRead] clears shade notifications, updates cross-screen state and
     * fires the server mark-read, so «прочитал до конца» reliably un-bolds the topic in favorites.
     */
    private fun maybeMarkTopicReadAtEnd() {
        if (markedTopicReadAtEnd || pageTopicId <= 0) return
        if (pagination.hasNextPage()) return // more pages below → not the end yet
        if (loadedItems.isEmpty()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val lastVisible = lm.findLastVisibleItemPosition()
        if (lastVisible == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
        val lastItemPosition = headerOffset() + loadedItems.size - 1
        if (lastVisible < lastItemPosition) return // bottom post not on screen yet
        markedTopicReadAtEnd = true
        themeUseCase.markTopicRead(pageTopicId, reason = "last_page_bottom_reached", source = "native")
    }

    private companion object {
        /** Start fetching the next page this many items before the current list end. */
        const val NEXT_PAGE_PREFETCH_DISTANCE = 3

        /** Start fetching the previous page when the first-visible item is within this of the top. */
        const val PREV_PAGE_PREFETCH_DISTANCE = 1

        /** Font-size pref value that maps to textScale 1.0 (matches the WebView default defaultFontSize). */
        const val REFERENCE_FONT_SIZE = 16f

        /** Minimum horizontal travel (dp) for a page-swipe drag to register on release. */
        const val SWIPE_MIN_DISTANCE_DP = 80f

        /** Per-frame scroll delta (px) beyond which the FAB direction arrow flips. */
        const val SCROLL_HIDE_THRESHOLD = 8

        private const val MENU_SEARCH = 0x4E01
        private const val MENU_REFRESH = 0x4E03
        private const val MENU_CREATE = 0x4E06
        private const val MENU_POLL = 0x4E07
        private const val MENU_HAT = 0x4E08
        private const val MENU_OVERFLOW = 0x4E0C
    }
}
