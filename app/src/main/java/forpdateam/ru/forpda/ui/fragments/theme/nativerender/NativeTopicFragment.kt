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
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.interactors.theme.ThemeEditorUseCase
import androidx.core.view.ViewCompat
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.theme.ThemeToolbarTitlePolicy
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeTabHost
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
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

    /** For the «Создать заметку» post-menu action (parity with the WebView createNote). */
    @Inject
    lateinit var notesRepository: forpdateam.ru.forpda.model.repository.note.NotesRepository

    /**
     * Клиентская граница прочитанного (модель Discourse) — общая с WebView-движком. Ведём самый
     * дальний реально-виденный пост локально, чтобы при переоткрытии не сесть НИЖЕ непрочитанных,
     * когда серверный getnewpost/getlastpost уполз вниз (walk-down 4PDA/IPB). См.
     * [forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore].
     */
    @Inject
    lateinit var readBoundaryStore: forpdateam.ru.forpda.model.repository.theme.TopicReadBoundaryStore

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

    /** The last URL handed to [loadTopic] (set even before the load completes) — dedupes the navigator's
     *  redundant echo of the initial open against [onViewCreated]'s resolved load. */
    private var lastRequestedUrl: String? = null
    private var isLoadingNextPage = false
    private var isLoadingPrevPage = false
    /** True while auto-pulling previous pages to fill an under-filled last page (see maybeFillLastPage). */
    private var fillingLastPage = false

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
    /** The hat post id once learned from page 1 — used to strip the server-repeated copy off deep pages. */
    private var knownHatPostId: Int? = null
    /**
     * The hat post content for the toolbar «Инфо» popup — captured whenever the hat is seen on ANY page
     * (page 1 keeps it inline; deep pages strip the echoed copy but we still hold it here). Persisted for
     * the whole topic so the ⓘ button works on every page, matching the WebView (topic-level hat state).
     */
    private var toolbarHatItem: NativePostItem? = null
    /** Inline hat collapse state — collapsed by default (the «Инфо» toolbar opens a popup instead). */
    private var hatCollapsed: Boolean = true
    /** The topic's poll (parsed on page 1) — the «Опрос» toolbar button shows it in a popup. Cached at
     *  topic level so the button persists across pages once the poll page has been seen (WebView parity). */
    private var currentPoll: forpdateam.ru.forpda.entity.remote.theme.Poll? = null
    /** Topic id [currentPoll] belongs to — scopes the cached poll to the current topic. */
    private var cachedPollTopicId: Int? = null

    /** Popup showing the full topic title when the toolbar title is tapped (WebView parity). */
    private var topicTitlePopup: android.widget.PopupWindow? = null

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

    /** Set for «В конец темы» so [applyInitialAnchor] lands on the last post of the last page. */
    private var pendingJumpToBottom: Boolean = false

    /**
     * Клиентская граница прочитанного: разрешить резюм-на-границу только один раз за открытие темы —
     * на ПЕРВОЙ успешной загрузке. Взводится в [onViewCreated] перед первым [loadTopic], гасится сразу
     * после проверки, чтобы последующие загрузки (пагинация, переходы по страницам, findpost-резюм)
     * не запускали override повторно и не зациклили.
     */
    private var boundaryResumeArmed: Boolean = false

    /**
     * Restore-scroll «где остановился» / устойчивость состояния: пост и его пиксельный offset, на который
     * надо вернуться после пересоздания фрагмента (смерть процесса, восстановление FragmentManager,
     * пересоздание вью). Пишутся в [onSaveInstanceState], читаются в [onViewCreated], применяются один
     * раз в [applyInitialAnchor] вместо серверного якоря. 0 = восстанавливать нечего (свежее открытие).
     */
    private var pendingRestorePostId: Int = 0
    private var pendingRestoreOffset: Int = 0

    /**
     * In-tab navigation history for «Поведение кнопки Назад в темах» = HISTORY: each time the navigator
     * reuses this tab for a NEW url (a link to another post/topic), we push where we were (url + scroll
     * anchor) so Back returns there instead of closing the tab. Empty → Back leaves the tab as before.
     */
    private data class ThemeBackEntry(val url: String, val postId: Int, val offset: Int)
    private val themeBackStack = ArrayDeque<ThemeBackEntry>()

    /**
     * Post id the open anchored to the BOTTOM (last post of an already-read topic). The async metadata
     * enrichment (post counts / reputation) grows posts AFTER the anchor is applied, which would push the
     * last post's action buttons back below the fold — so [enrichLoadedPage] re-bottom-anchors while the
     * user still sits at the bottom. Cleared once the user scrolls up off the last post.
     */
    private var anchoredBottomPostId: Int? = null

    /**
     * Bottom-nav «chrome» (tab bar + system navigation) height in px, pushed in by MainActivity via
     * [onBottomChromePaddingChanged]. The list View measures full-height and overflows its clipped parent
     * by exactly this much, so a plain bottom-anchor buries the last post's action buttons behind the tab
     * bar. Reserved as the RecyclerView's bottom padding (clipToPadding=false → still scrolls edge-to-edge)
     * so resting content — the last post especially — clears the tab bar. Parity with the WebView spacer.
     */
    private var bottomNavChromePad = 0

    /** Whether the loaded content still has an unread anchor — drives the «К непрочитанному» menu item. */
    private var topicHasUnread: Boolean = false

    /** Reused WebView smart-navigation popup (page wheel + start/unread/end/enter-number). */
    private var smartNavMenu: forpdateam.ru.forpda.ui.views.SmartNavigationMenu? = null

    /** «Умная кнопка темы» (FAB) state: enabled per pref; arrow follows the last scroll direction. */
    private var fabEnabled = false
    private var fabPointsDown = true
    /** Auto-hide: the smart button appears on scroll and hides after this idle delay (WebView parity). */
    private val fabHideHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val fabHideRunnable = Runnable { if (fabEnabled && view != null) fab.hide() }

    override fun hasBackHandling(): Boolean =
            themeOverlay != null || messagePanel?.visibility == View.VISIBLE || searchBar?.visibility == View.VISIBLE ||
                    // История переходов внутри вкладки (HISTORY): «назад» должен вернуть по истории,
                    // а не выйти из приложения на корневой вкладке — иначе back-callback выключается.
                    (mainPreferencesHolder.getTopicBackBehavior() ==
                            forpdateam.ru.forpda.common.Preferences.Main.TopicBackBehavior.HISTORY &&
                            themeBackStack.isNotEmpty())

    override fun onBackPressed(): Boolean {
        // Back closes the hat/poll overlay first, then the find-on-page bar / reply editor, before leaving.
        if (dismissThemeOverlay()) return true
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
        // «Поведение кнопки Назад в темах» = HISTORY (по умолчанию): пройтись по истории переходов внутри
        // вкладки (ссылки на посты/темы) прежде чем закрыть вкладку. ORIGIN — сразу закрыть (как раньше).
        if (mainPreferencesHolder.getTopicBackBehavior() ==
                forpdateam.ru.forpda.common.Preferences.Main.TopicBackBehavior.HISTORY &&
                themeBackStack.isNotEmpty()) {
            val entry = themeBackStack.removeLast()
            if (entry.postId > 0) {
                pendingRestorePostId = entry.postId
                pendingRestoreOffset = entry.offset
            }
            boundaryResumeArmed = false // возврат в историю, не свежее открытие
            loadTopic(entry.url)
            return true
        }
        return super.onBackPressed()
    }

    private val topicUrl: String
        get() = arguments?.getString(TabFragment.ARG_TAB).orEmpty()

    /**
     * Flat, edge-to-edge top-bar chrome like the WebView theme screen ([ThemeFragment] does the same
     * override). Without it the topic toolbar defaults to [R.attr.chrome_plane_background] → non-flat
     * chrome, which draws a 1dp app-bar stroke that reads as an extra divider line under the toolbar
     * (very visible on the reading palettes, e.g. Sepia Blue's blue-grey stroke).
     */
    override fun topBarSurfaceColorAttr(): Int =
            forpdateam.ru.forpda.R.attr.main_toolbar_accent_surface

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }?.let { setTitle(it) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = androidx.recyclerview.widget.ConcatAdapter(pollHeaderAdapter, postsAdapter)
        // Bottom room for the CLASSIC-mode pagination bar is managed in updatePaginationBar().
        recyclerView.clipToPadding = false
        // Page tone UNDER the post cards: the lowest surface container, so each post (colorSurfaceContainer,
        // rounded, slightly elevated) reads as a distinct Material 3 card floating on the page — inter-card
        // gaps and the area below the last card share this one page tone.
        requireContext().getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest).let { page ->
            recyclerView.setBackgroundColor(page)
            refreshLayout.setBackgroundColor(page)
        }
        applyDisplaySettings()
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) maybeLoadNextPage()
                if (dy < 0) { maybeLoadPrevPage(); anchoredBottomPostId = null } // user scrolled up off the bottom anchor
                markVisiblePostsRead()
                maybeMarkTopicReadAtEnd()
                updateBarCurrentPageFromScroll()
                updateFabOnScroll(dy)
                if (smartNavMenu?.isShowing() == true) smartNavMenu?.dismiss()
            }
        })
        // The bottom-nav container padding lands a beat AFTER the first anchor (async window-inset pass),
        // shrinking the list. If we parked on the last post, that stale offset re-hides its action buttons
        // below the new fold — re-pin to the bottom whenever the list height actually changes while anchored.
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom && anchoredBottomPostId != null) {
                recyclerView.post { reanchorBottomAfterGrowth() }
            }
        }
        val auth = authHolder.get()
        postsAdapter.setAuthContext(authorized = auth.isAuth(), memberId = auth.userId)
        installPageSwipeDetector()
        installBottomRefreshDetector()
        refreshLayout.setOnRefreshListener { loadTopic(loadedUrl ?: topicUrl) }
        setupMessagePanel()
        // Lift the reply panel above the keyboard by reading the REAL ime inset on every window-inset pass
        // and setting the host's bottom margin directly. The shared DimensionHelper path lags/sticks on
        // some OEM builds (keyboard covered the editor; and a stale inset left an empty strip after the
        // keyboard closed) — reading Type.ime() here is exact for both show and dismiss. This fragment
        // opts out of the base DimensionHelper host-margin path (shouldApplyMessagePanelImeInsets=false).
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { _, insets ->
            applyMessagePanelImeMargin(insets)
            insets
        }
        setupFab()
        setupToolbarMenu()
        setupTitleTap()
        applyToolbarAutoHide()
        // Устойчивость состояния / restore-scroll: если фрагмент пересоздан (смерть процесса / восстановление
        // FragmentManager / пересоздание вью), в savedInstanceState лежит пост+offset, где стоял пользователь —
        // грузим ИМЕННО ту страницу (findpost) и садимся туда, а не на серверный якорь.
        val restorePostId = savedInstanceState?.getInt(STATE_RESTORE_POST_ID, 0) ?: 0
        if (restorePostId > 0) {
            pendingRestorePostId = restorePostId
            pendingRestoreOffset = savedInstanceState?.getInt(STATE_RESTORE_OFFSET, 0) ?: 0
            barCurrentPage = (savedInstanceState?.getInt(STATE_RESTORE_BAR_PAGE, 1) ?: 1).coerceAtLeast(1)
        }
        // Arm the client read-boundary resume only for a genuinely fresh open (nothing to restore).
        boundaryResumeArmed = restorePostId <= 0
        loadTopic(if (restorePostId > 0) buildRestoreUrl(restorePostId) else resolveInitialOpenUrl())
    }

    /** URL, ведущий на конкретный пост (findpost) для restore-scroll; фолбэк — обычное открытие. */
    private fun buildRestoreUrl(postId: Int): String {
        val topicId = ThemeApi.extractTopicIdFromUrl(topicUrl) ?: return resolveInitialOpenUrl()
        return forpdateam.ru.forpda.presentation.theme.TopicUnreadFindPostReloadPolicy
                .buildFindPostUrl(topicId, postId.toString())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Сохраняем первый видимый пост и его пиксельный offset — точную точку «где остановился», чтобы
        // пережить пересоздание фрагмента (смерть процесса / restore FragmentManager). Пустой список / нет
        // вью — сохранять нечего.
        if (view == null || pageTopicId <= 0 || loadedItems.isEmpty()) return
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
        val item = loadedItems.getOrNull(firstPos - headerOffset()) ?: return
        if (item.postId <= 0) return
        val top = lm.findViewByPosition(firstPos)?.top ?: 0
        outState.putInt(STATE_RESTORE_POST_ID, item.postId)
        outState.putInt(STATE_RESTORE_OFFSET, top)
        outState.putInt(STATE_RESTORE_BAR_PAGE, barCurrentPage)
    }

    /**
     * Apply the «При открытии темы» setting (Первая страница / Первое непрочитанное) to the initial URL,
     * exactly as the WebView ViewModel does via [TopicOpenTargetResolver]: with «Первое непрочитанное» and
     * an unread hint from the list, this yields a `view=getnewpost`/find-post URL so the topic opens on the
     * first unread post instead of always page 1. Only used for the very first load.
     */
    private fun resolveInitialOpenUrl(): String {
        val args = arguments ?: return topicUrl
        val context = forpdateam.ru.forpda.presentation.theme.TopicOpenContext(
                rawUrl = topicUrl,
                setting = mainPreferencesHolder.getTopicOpenTarget(),
                sourceScreen = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_TOPIC_OPEN_SOURCE)
                        ?: "unknown",
                sourceUrl = topicUrl,
                openIntentRaw = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_TOPIC_OPEN_INTENT),
                unreadUrlFromList = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_UNREAD_URL_FROM_LIST),
                unreadPostIdFromList = args.getInt(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_UNREAD_POST_ID_FROM_LIST)
                        .takeIf { it > 0 },
                inspectorMarkedUnread = args.getBoolean(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_INSPECTOR_MARKED_UNREAD),
                lastReadUrlFromList = args.getString(forpdateam.ru.forpda.presentation.Screen.Theme.ARG_LAST_READ_URL_FROM_LIST),
        )
        return runCatching {
            forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver.resolve(context).url
        }.getOrDefault(topicUrl)
    }

    /**
     * Resolve the open target for a navigator-driven open/switch (parity with [resolveInitialOpenUrl], but
     * fed from the navigator's explicit [listHints] rather than fragment arguments). Mirrors the WebView
     * presenter's loadUrl → the same getnewpost/findpost/page-1 decision, so the navigator never loads the
     * bare page-1 url.
     */
    private fun resolveNavigatorOpenUrl(
            rawUrl: String,
            sourceScreen: String,
            openIntent: String,
            listHints: TopicOpenListHints?,
    ): String {
        val context = forpdateam.ru.forpda.presentation.theme.TopicOpenContext(
                rawUrl = rawUrl,
                setting = mainPreferencesHolder.getTopicOpenTarget(),
                sourceScreen = sourceScreen,
                sourceUrl = rawUrl,
                openIntentRaw = openIntent,
                unreadUrlFromList = listHints?.unreadUrlFromList,
                unreadPostIdFromList = listHints?.unreadPostIdFromList?.takeIf { it > 0 },
                listTopicMarkedUnread = listHints?.topicMarkedUnread ?: false,
                inspectorMarkedUnread = listHints?.inspectorMarkedUnread ?: false,
                lastReadUrlFromList = listHints?.lastReadUrlFromList,
        )
        return runCatching {
            forpdateam.ru.forpda.presentation.theme.TopicOpenTargetResolver.resolve(context).url
        }.getOrDefault(rawUrl)
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
            // Lifted a bit higher off the bottom (WebView reference sits well above the tab bar); in
            // CLASSIC it must also clear the pagination bar, so it sits higher still.
            lp.bottomMargin = ((if (isClassicMode()) 120 else 72) * dm.density).toInt()
            fab.layoutParams = lp
        }
        androidx.core.view.ViewCompat.setElevation(fab, 12f * dm.density)
        if (!fabEnabled) {
            fab.hide()
            return
        }
        fab.size = com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI
        fab.setImageResource(forpdateam.ru.forpda.R.drawable.ic_arrow_down)
        // Follow the active palette / accent colour: resolve straight from the fragment's themed context so
        // the FAB is the palette accent (not a stale default blue). Icon uses the accent's contrast colour.
        val fabBg = requireContext().getColorFromAttr(androidx.appcompat.R.attr.colorAccent)
        val fabIcon = requireContext().getColorFromAttr(forpdateam.ru.forpda.R.attr.smart_nav_fab_icon)
        fab.backgroundTintList = android.content.res.ColorStateList.valueOf(fabBg)
        fab.imageTintList = android.content.res.ColorStateList.valueOf(fabIcon)
        fab.isLongClickable = true
        // Гасим АВТОМАТИЧЕСКИЙ haptic View (на long-press он давал второй, дублирующий buzz), а свой
        // одиночный отклик шлём через Haptic.perform() с FLAG_IGNORE_VIEW_SETTING — он обходит выключенный
        // флаг view, но уважает настройку «Тактильный отклик». Иначе после отключения флага вибрации не было.
        fab.isHapticFeedbackEnabled = false
        fab.setOnClickListener {
            forpdateam.ru.forpda.ui.Haptic.perform(it, android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            showFabTemporarily() // использование кнопки сбрасывает таймер авто-скрытия обратно на 2.5с
            smartScrollTap()
        }
        fab.setOnLongClickListener {
            forpdateam.ru.forpda.ui.Haptic.perform(it, android.view.HapticFeedbackConstants.LONG_PRESS)
            showFabTemporarily() // long-press тоже продлевает видимость кнопки
            showSmartNavMenu()
            true
        }
        // Hidden until the user scrolls (WebView parity): appears on any scroll, hides after idle.
        fab.hide()
    }

    /** Show the smart button and (re)arm its idle auto-hide. */
    private fun showFabTemporarily() {
        if (!fabEnabled) return
        if (!fab.isShown) fab.show()
        fabHideHandler.removeCallbacks(fabHideRunnable)
        fabHideHandler.postDelayed(fabHideRunnable, FAB_AUTO_HIDE_MS)
    }

    /** Reveal the FAB on any scroll (auto-hiding after idle) and point its arrow along the scroll direction. */
    private fun updateFabOnScroll(dy: Int) {
        if (!fabEnabled) return
        if (dy != 0) showFabTemporarily() // any scroll wakes the button and re-arms the 2.5s hide
        if (kotlin.math.abs(dy) < SCROLL_HIDE_THRESHOLD) return
        val down = dy > 0
        if (down != fabPointsDown) {
            fabPointsDown = down
            fab.setImageResource(
                    if (down) forpdateam.ru.forpda.R.drawable.ic_arrow_down
                    else forpdateam.ru.forpda.R.drawable.ic_arrow_up)
        }
    }

    /**
     * Short tap: flip exactly ONE FORUM page (perPage posts) forward/back in the arrow's direction — the
     * user wants a whole-page jump («перекинуть на 1 страницу»), not a one-screen nudge. Position-based so
     * it lands on the page boundary regardless of variable post heights; on reaching the loaded edge the
     * infinite scroll pulls the adjacent page in. Falls back to a one-viewport scroll if positions aren't
     * resolvable yet.
     */
    private fun smartScrollTap() {
        val lm = recyclerView.layoutManager as? LinearLayoutManager
        val total = recyclerView.adapter?.itemCount ?: 0
        val first = lm?.findFirstVisibleItemPosition() ?: androidx.recyclerview.widget.RecyclerView.NO_POSITION
        if (lm == null || total <= 0 || first == androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            val viewport = recyclerView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            recyclerView.smoothScrollBy(0, if (fabPointsDown) viewport else -viewport)
            return
        }
        val perPage = pagination.perPage.takeIf { it > 0 } ?: 20
        val target = (if (fabPointsDown) first + perPage else first - perPage).coerceIn(0, total - 1)
        lm.scrollToPositionWithOffset(target, 0)
        recyclerView.post { if (fabPointsDown) maybeLoadNextPage() else maybeLoadPrevPage() }
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
        panel.setClearMessageClickListener { confirmClearMessage() }
        panel.hideButton?.visibility = View.VISIBLE
        panel.hideButton?.setOnClickListener { hideMessagePanel() }
        panel.fullButton?.visibility = View.VISIBLE // «полноэкранный редактор» (parity with the WebView)
        panel.fullButton?.setOnClickListener { openFullscreenEditor() }
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
        // «Обновить» moved into the «Ещё» overflow (see showOverflowMenu): one fewer always-icon frees
        // toolbar width so the «N / M» page counter subtitle shows in full instead of truncating to «N / …».
        // Refresh is still reachable via the overflow, pull-to-refresh (CLASSIC) and the bottom-up gesture.
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
        // The icon drawables bake in DIFFERENT colour attrs (icon_base / icon_toolbar / colorOnSurface),
        // so the toolbar buttons rendered as visibly different colours. Force one uniform tint (matching
        // the back arrow) on the nav icon and every menu icon.
        val toolbarIconColor = requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        toolbar.navigationIcon?.mutate()?.setTint(toolbarIconColor)
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.mutate()?.setTint(toolbarIconColor)
        }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CREATE -> { toggleComposeEditor(); true }
                MENU_POLL -> { onPollToolbarClick(); true }
                MENU_SEARCH -> { toggleSearchBar(); true }
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
     * A tap on the toolbar title must show the FULL topic name in a popup (WebView parity) — not toggle
     * scroll-to-top/bottom. [TabFragment] wires the title strip to [TabTopScroller.toggleScrollTop] for
     * every tab; here we consume that touch on the wrapper and route the title's own click to the popup,
     * exactly as [ThemeFragmentWeb.consumeHeaderTouchGaps] does.
     */
    private fun setupTitleTap() {
        titlesWrapper.isClickable = true
        titlesWrapper.setOnTouchListener { _, _ -> true } // swallow the scroll-toggle click on the strip
        toolbarTitleView.apply {
            isClickable = true
            setOnClickListener { showFullTopicTitle() }
        }
    }

    /** Popup with the full (untruncated) topic title, anchored under the toolbar title (WebView parity). */
    private fun showFullTopicTitle() {
        val topicTitle = getTitle().trim()
        if (topicTitle.isEmpty()) return
        if (topicTitlePopup?.isShowing == true) return
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val dp8 = (8 * dm.density).toInt()
        val dp16 = (16 * dm.density).toInt()
        val contentView = androidx.appcompat.widget.AppCompatTextView(ctx).apply {
            text = topicTitle
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            gravity = android.view.Gravity.START
            maxLines = 4
            maxWidth = dm.widthPixels - dp16 * 2
            setPadding(dp16, dp8, dp16, dp8)
        }
        topicTitlePopup = android.widget.PopupWindow(
                contentView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
        ).apply {
            isOutsideTouchable = true
            elevation = 4 * dm.density
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
                cornerRadius = dp8.toFloat()
            })
            setOnDismissListener { if (topicTitlePopup === this) topicTitlePopup = null }
            showAsDropDown(toolbarTitleView, 0, dp8)
        }
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

    /**
     * Editor «полноэкранный редактор» button: hand the current inline draft off to the standalone
     * fullscreen editor screen (parity with the WebView). On close-without-post the draft is restored into
     * the inline panel; on a successful post the topic reloads to show the new message.
     */
    private fun openFullscreenEditor() {
        val panel = messagePanel ?: return
        val draft = panel.message.ifEmpty { messagePanelDraftMirror }
        val selection = panel.selectionRange
        navigationUseCase.openFullscreenEditor(
                forumId = pageForumId,
                topicId = pageTopicId,
                st = pageSt,
                themeName = arguments?.getString(TabFragment.ARG_TITLE),
                message = draft,
                attachments = panel.attachments.toList(),
                selectionStart = selection.getOrNull(0),
                selectionEnd = selection.getOrNull(1),
                onSync = { data ->
                    if (view == null) return@openFullscreenEditor
                    messagePanelDraftMirror = data.message.orEmpty()
                    showMessagePanel(showKeyboard = false)
                    messagePanel?.setText(data.message)
                    data.attachments?.let { attachmentsPopup?.setAttachments(it) }
                    messagePanel?.messageField?.let { field ->
                        val len = field.text?.length ?: 0
                        runCatching {
                            field.setSelection(
                                    data.selectionStart.coerceIn(0, len),
                                    data.selectionEnd.coerceIn(0, len))
                        }
                    }
                },
                onPosted = {
                    if (view == null) return@openFullscreenEditor
                    messagePanel?.clearMessage()
                    messagePanel?.clearAttachments()
                    messagePanelDraftMirror = ""
                    hideMessagePanel()
                    loadTopic(loadedUrl ?: topicUrl)
                },
        )
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
        // Force an inset pass so the host lifts above the keyboard right after the panel appears.
        ViewCompat.requestApplyInsets(fragmentContainer)
    }

    /** This fragment manages the reply-panel IME margin itself (see the fragmentContainer inset listener),
     *  so opt out of the base DimensionHelper-driven host margin which lags/sticks on some OEM keyboards. */
    override fun shouldApplyMessagePanelImeInsets(): Boolean = false

    /** Lift [messagePanelHost] to sit exactly above the keyboard from the REAL ime inset (0 when the
     *  keyboard is hidden or the panel is closed) — device-robust, unlike the lagging DimensionHelper. */
    private fun applyMessagePanelImeMargin(insets: androidx.core.view.WindowInsetsCompat) {
        val imeVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
        val imeBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
        val panelShown = messagePanel?.visibility == View.VISIBLE
        val target = if (panelShown && imeVisible) imeBottom else 0
        val lp = messagePanelHost.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: return
        if (lp.bottomMargin != target) {
            lp.bottomMargin = target
            messagePanelHost.layoutParams = lp
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
        if (dismissThemeOverlay()) return // toggle: second tap closes the poll overlay
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
    private fun showThemePopup(title: String?, content: View) {
        val ctx = requireContext()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = androidx.core.content.ContextCompat.getDrawable(ctx, forpdateam.ru.forpda.R.drawable.bg_theme_top_sheet)
            // A title header only when explicitly asked — the hat overlay omits it (the content is
            // self-evidently the topic hat, so «Шапка темы» just added a redundant strip).
            if (!title.isNullOrBlank()) {
                addView(TextView(ctx).apply {
                    text = title
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
                    setPadding(pad, pad, pad, pad / 2)
                })
            } else {
                setPadding(0, pad / 2, 0, 0) // small top inset so content isn't glued to the toolbar
            }
            addView(content, android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        presentTopSheet(root, scrollTarget = content)
    }

    /** The live hat/poll overlay panel (an in-layout view, not a Dialog), or null when none is shown. */
    private var themeOverlay: View? = null

    /** Indeterminate spinner shown while a hybrid neighbour page is loading (top for prev, bottom for next). */
    private var hybridLoadingBar: com.google.android.material.progressindicator.CircularProgressIndicator? = null

    /** Show the hybrid page-load spinner at the top (prev page) or bottom (next page) of the content area. */
    private fun showHybridLoading(atTop: Boolean) {
        if (view == null) return
        val bar = hybridLoadingBar ?: com.google.android.material.progressindicator.CircularProgressIndicator(requireContext()).apply {
            // Material 3 indeterminate spinner (parity with the rest of the app — Favorites/Profile/Auth),
            // themed with the accent; replaces the plain android.widget.ProgressBar.
            isIndeterminate = true
            indicatorSize = (30 * resources.displayMetrics.density).toInt()
            trackThickness = (3 * resources.displayMetrics.density).toInt()
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                    androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT)
            coordinatorLayout.addView(this)
        }.also { hybridLoadingBar = it }
        val dm = resources.displayMetrics
        (bar.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.let { lp ->
            lp.gravity = android.view.Gravity.CENTER_HORIZONTAL or
                    (if (atTop) android.view.Gravity.TOP else android.view.Gravity.BOTTOM)
            lp.topMargin = if (atTop) (appBarLayout.height + 12 * dm.density).toInt() else 0
            lp.bottomMargin = if (atTop) 0 else (24 * dm.density).toInt()
            bar.layoutParams = lp
        }
        bar.bringToFront()
        bar.visibility = View.VISIBLE
    }

    private fun hideHybridLoading() {
        if (!isLoadingNextPage && !isLoadingPrevPage) hybridLoadingBar?.visibility = View.GONE
    }

    /**
     * Present [root] as an overlay panel that slides down from directly under the toolbar (parity with the
     * WebView hat/poll/menu overlays, which are DOM elements at the top of the content area). Implemented
     * as a real child of the [coordinatorLayout] — positioned below the [appBarLayout] via
     * [AppBarLayout.ScrollingViewBehavior] and clipping its child — so the panel genuinely emerges from
     * *under* the toolbar with one smooth animation (the old Dialog approach flashed at the screen top
     * before repositioning). A tap on the free strip below the panel, or Back, dismisses it. When
     * [scrollTarget] is set and the panel would exceed the available height, that view is clamped so it
     * scrolls inside the panel instead.
     */
    private fun presentTopSheet(root: android.widget.LinearLayout, scrollTarget: View?) {
        dismissThemeOverlay()
        val ctx = requireContext()
        // Fills the content area (ScrollingViewBehavior offsets it under the toolbar) and clips its child,
        // so a child translated up by its own height is hidden behind the toolbar until it slides down.
        val container = android.widget.FrameLayout(ctx).apply {
            layoutParams = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT).apply {
                behavior = com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior()
            }
            clipChildren = true
            isClickable = true // tap the free strip below the panel to dismiss
            setOnClickListener { dismissThemeOverlay() }
        }
        root.isClickable = true // swallow taps on the panel itself so they don't dismiss
        container.addView(root, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP))
        coordinatorLayout.addView(container)
        themeOverlay = container
        appBarLayout.setExpanded(true, true) // ensure the toolbar is down so the panel drops from under it
        container.viewTreeObserver.addOnPreDrawListener(
                object : android.view.ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        container.viewTreeObserver.removeOnPreDrawListener(this)
                        // Cap to ~90% of the content height so tall content scrolls inside and a strip
                        // stays free below for tap-to-dismiss.
                        if (scrollTarget != null) {
                            val maxH = (container.height * 0.9f).toInt()
                            val overflow = root.height - maxH
                            if (overflow > 0) {
                                scrollTarget.layoutParams = scrollTarget.layoutParams
                                        .apply { height = scrollTarget.height - overflow }
                                scrollTarget.requestLayout()
                            }
                        }
                        root.translationY = -root.height.toFloat()
                        root.alpha = 0f
                        root.animate().translationY(0f).alpha(1f).setDuration(220)
                                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                        return true
                    }
                })
    }

    /** Slide the hat/poll overlay back up under the toolbar and remove it. Returns true if one was open. */
    private fun dismissThemeOverlay(): Boolean {
        val overlay = themeOverlay ?: return false
        themeOverlay = null
        val panel = (overlay as? android.view.ViewGroup)?.getChildAt(0)
        val remove = { (overlay.parent as? android.view.ViewGroup)?.removeView(overlay) }
        if (panel != null && panel.height > 0) {
            panel.animate().translationY(-panel.height.toFloat()).alpha(0f).setDuration(160)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction { remove() }.start()
        } else {
            remove()
        }
        return true
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
            add("Обновить" to { loadTopic(loadedUrl ?: topicUrl) })
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
        // Toggle: a second tap on the ⓘ button closes the overlay instead of reopening it.
        if (dismissThemeOverlay()) return
        // Prefer the live inline hat post (page 1); fall back to the captured topic-level hat so the popup
        // still works on deep pages where the echoed hat was stripped from the list.
        val hatItem = topicHatPostId?.let { id -> loadedItems.firstOrNull { it.postId == id } }
                ?: toolbarHatItem
                ?: return
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
        // No «Шапка темы» title/strip — opening it from the toolbar already makes the context clear.
        showThemePopup(title = null, content = rv)
    }

    /** Long-press a spoiler header → copy its deep link to the clipboard (parity with copySpoilerLink). */
    override fun onSpoilerCopyLink(item: NativePostItem, spoilNumber: Int) {
        val url = "https://4pda.to/forum/index.php?showtopic=${item.topicId}&act=findpost&pid=${item.postId}" +
                "&anchor=Spoil-${item.postId}-$spoilNumber"
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("spoiler", url))
        Toast.makeText(requireContext(), "Ссылка на спойлер скопирована", Toast.LENGTH_SHORT).show()
    }

    /** Tap an attachment image → open the swipeable image viewer over the post's whole gallery, starting
     *  on the tapped image (parity with the WebView's handleImageNavigation). */
    override fun onImageClick(galleryUrls: List<String>, index: Int) {
        if (galleryUrls.isEmpty()) return
        val start = index.coerceIn(0, galleryUrls.size - 1)
        forpdateam.ru.forpda.ui.activities.imageviewer.ImageViewerActivity
                .startActivity(requireContext(), ArrayList(galleryUrls), start)
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
    // ---- Gesture indicator overlay (parity with the WebView pull-to-refresh / page-swipe overlays) ----
    // A centered rounded surface card with a big direction glyph, a label and a horizontal progress bar,
    // shown WHILE the user performs the bottom-up refresh or the classic page-swipe gesture, so the gesture
    // is visible before release (previously the native engine gave no feedback until after release).
    private var gestureOverlay: android.widget.LinearLayout? = null
    private var gestureOverlayGlyph: TextView? = null
    private var gestureOverlayLabel: TextView? = null
    private var gestureOverlayProgress: android.widget.ProgressBar? = null

    private fun ensureGestureOverlay(): android.widget.LinearLayout {
        gestureOverlay?.let { return it }
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val glyph = TextView(ctx).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            textSize = 22f
            maxLines = 1
        }
        val label = TextView(ctx).apply {
            gravity = android.view.Gravity.CENTER
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            textSize = 12f
            maxLines = 1
        }
        val progress = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
            progressTintList = android.content.res.ColorStateList.valueOf(
                    ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                    ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant))
        }
        val overlay = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            alpha = 0f
            isClickable = false
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurface))
                cornerRadius = dp(16f).toFloat()
                setStroke(dp(1f).coerceAtLeast(1),
                        ctx.getColorFromAttr(com.google.android.material.R.attr.colorOutlineVariant))
            }
            androidx.core.view.ViewCompat.setElevation(this, dp(6f).toFloat())
            val wrap = android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            addView(glyph, android.widget.LinearLayout.LayoutParams(wrap, wrap))
            addView(label, android.widget.LinearLayout.LayoutParams(wrap, wrap).apply {
                topMargin = dp(4f); bottomMargin = dp(6f)
            })
            addView(progress, android.widget.LinearLayout.LayoutParams(dp(120f), dp(4f)))
        }
        // Attach to the coordinator (the FAB's parent), NOT fragment_content: the RecyclerView there
        // draws over a fragment_content sibling regardless of elevation, so the overlay stayed hidden.
        coordinatorLayout.addView(overlay, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.CENTER
        })
        gestureOverlay = overlay
        gestureOverlayGlyph = glyph
        gestureOverlayLabel = label
        gestureOverlayProgress = progress
        return overlay
    }

    /** Show/update the gesture indicator with a direction [glyph], a [label] and 0..1 [progress]. */
    private fun showGestureIndicator(glyph: String, label: String, progress: Float) {
        if (view == null) return
        val overlay = ensureGestureOverlay()
        val n = progress.coerceIn(0f, 1f)
        gestureOverlayGlyph?.text = glyph
        gestureOverlayLabel?.text = label
        gestureOverlayProgress?.progress = (n * 100f).toInt()
        overlay.visibility = View.VISIBLE
        overlay.alpha = (0.4f + n * 0.6f).coerceAtMost(1f)
        overlay.bringToFront()
    }

    private fun hideGestureIndicator() {
        gestureOverlay?.let {
            it.visibility = View.GONE
            it.alpha = 0f
            gestureOverlayProgress?.progress = 0
        }
    }

    private fun installPageSwipeDetector() {
        val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        val minDist = SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var claimed = false

            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                // Page swipes are a CLASSIC-only navigation (the setting itself says «доступно только в
                // классическом режиме»); in HYBRID you scroll, so never steal horizontal gestures there —
                // even if the stored flag is still true from a previous CLASSIC session.
                if (!isClassicMode() || !mainPreferencesHolder.getTopicPageSwipeEnabled()) return false
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
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = e.x - downX
                        val prog = (kotlin.math.abs(dx) / minDist).coerceIn(0f, 1f)
                        val armed = kotlin.math.abs(dx) >= minDist
                        // Finger left (dx<0) → NEXT page (→); finger right (dx>0) → PREVIOUS page (←).
                        if (dx < 0) {
                            showGestureIndicator("→", getString(if (armed)
                                    forpdateam.ru.forpda.R.string.theme_page_swipe_release_next
                                else forpdateam.ru.forpda.R.string.theme_page_swipe_pull_next), prog)
                        } else {
                            showGestureIndicator("←", getString(if (armed)
                                    forpdateam.ru.forpda.R.string.theme_page_swipe_release_previous
                                else forpdateam.ru.forpda.R.string.theme_page_swipe_pull_previous), prog)
                        }
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val dx = e.x - downX
                        if (kotlin.math.abs(dx) > minDist) {
                            if (dx < 0) jumpToPage(barCurrentPage + 1) else jumpToPage(barCurrentPage - 1)
                        }
                        claimed = false
                        hideGestureIndicator()
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        claimed = false
                        hideGestureIndicator()
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}
        })
    }

    /**
     * Bottom-edge pull-up refresh («Обновление свайпом снизу», default ON) — parity with the WebView's
     * BottomRefreshGestureController. Arms only at the TRUE bottom of the list (nothing more to scroll,
     * so it never fights hybrid next-page loading), captures after a clear controlled UPWARD drag (not a
     * fling, not a horizontal swipe), and on release past the threshold reloads the current page.
     */
    private fun installBottomRefreshDetector() {
        val vc = android.view.ViewConfiguration.get(requireContext())
        val touchSlop = vc.scaledTouchSlop
        val density = resources.displayMetrics.density
        val captureDist = kotlin.math.max(touchSlop * 3f, 48f * density)
        val triggerDist = 160f * density
        val maxReleaseVelocity = 1450f * density
        val minDurationMs = 240L
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var downAt = 0L
            private var captured = false
            private var blocked = false
            private var vt: android.view.VelocityTracker? = null

            override fun onInterceptTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent): Boolean {
                if (!mainPreferencesHolder.getTopicBottomRefreshGestureEnabled()) return false
                if (messagePanel?.visibility == View.VISIBLE || searchBar?.visibility == View.VISIBLE) return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        downX = e.x; downY = e.y; downAt = android.os.SystemClock.uptimeMillis()
                        captured = false
                        // Arm only when already at the very bottom (no more content below) and not mid-reload.
                        blocked = rv.canScrollVertically(1) || refreshLayout.isRefreshing || e.pointerCount > 1
                        vt = android.view.VelocityTracker.obtain().also { it.addMovement(e) }
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        vt?.addMovement(e)
                        if (blocked || e.pointerCount != 1) return false
                        val up = downY - e.y
                        val horiz = kotlin.math.abs(e.x - downX)
                        if (up <= 0f) return false
                        if (up < captureDist) return false
                        if (up < horiz * 1.5f) { blocked = true; return false } // horizontal → not ours
                        if (rv.canScrollVertically(1)) { blocked = true; return false } // left the bottom
                        captured = true
                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                return false
            }

            override fun onTouchEvent(rv: androidx.recyclerview.widget.RecyclerView, e: android.view.MotionEvent) {
                vt?.addMovement(e)
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_MOVE -> {
                        if (!captured) return
                        val up = downY - e.y
                        val prog = (up / triggerDist).coerceIn(0f, 1f)
                        showGestureIndicator("↑", getString(if (prog >= 1f)
                                forpdateam.ru.forpda.R.string.theme_bottom_refresh_release
                            else forpdateam.ru.forpda.R.string.theme_bottom_refresh_pull), prog)
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        val up = downY - e.y
                        vt?.computeCurrentVelocity(1000)
                        val vel = kotlin.math.abs(vt?.yVelocity ?: 0f)
                        val dur = android.os.SystemClock.uptimeMillis() - downAt
                        if (captured && up >= triggerDist && vel <= maxReleaseVelocity && dur >= minDurationMs &&
                                !refreshLayout.isRefreshing) {
                            refreshFromBottom()
                        }
                        hideGestureIndicator()
                        reset(rv)
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        hideGestureIndicator()
                        reset(rv)
                    }
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) {}

            private fun reset(rv: androidx.recyclerview.widget.RecyclerView) {
                rv.parent?.requestDisallowInterceptTouchEvent(false)
                vt?.recycle(); vt = null
                captured = false; blocked = false
            }
        })
    }

    /**
     * Bottom-up «обновление» gesture: the user is at the TRUE bottom of the topic (the gesture only
     * arms there), so reload the LAST loaded page — not [loadedUrl], which stays pinned to the entry
     * page even after infinite-scrolling down — and land back at the bottom. Without the
     * [pendingJumpToBottom] flag the fresh load's [applyInitialAnchor] falls to the page TOP, which is
     * the reported «после обновления кидает на первый пост последней страницы» bug.
     */
    private fun refreshFromBottom() {
        val url = if (pagination.isInitialised) pagination.pageUrl(pagination.loadedPage) else (loadedUrl ?: topicUrl)
        pendingJumpToBottom = true
        loadTopic(url)
    }

    /**
     * CLASSIC reading mode shows one page at a time with the bottom pagination bar (no infinite
     * scroll); HYBRID (default) is continuous infinite scroll with no bar. Mirrors the WebView
     * «Режим чтения тем» setting.
     */
    private fun isClassicMode(): Boolean =
            mainPreferencesHolder.getTopicScrollMode() ==
                    forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.CLASSIC

    /**
     * Handle the topic «шапка» (4pda echoes the topic's first post at the top of EVERY page):
     *  - real first page → detect the hat, remember its id, and RETURN it (rendered as a collapsible
     *    block on page 1 only);
     *  - deeper page → strip the repeated copy from [page].posts in place (so it never shows again) and
     *    return null. Mirrors the WebView's TopicPrependedHatPolicy.stripFromNonFirstPage.
     */
    private fun processHatForPage(page: forpdateam.ru.forpda.entity.remote.theme.ThemePage): Int? {
        val policy = forpdateam.ru.forpda.presentation.theme.TopicPrependedHatPolicy
        if (page.pagination.current <= 1) {
            val hatPost = policy.detectPrependedHat(page)?.takeIf { it.id > 0 }
            val hatId = hatPost?.id
            if (hatId != null) {
                knownHatPostId = hatId
                toolbarHatItem = mapper.map(hatPost) // hold the hat for the toolbar ⓘ popup on every page
            }
            return hatId
        }
        // Deep page: 4pda echoes the topic's FIRST post (the hat) at the very top of every page. Only ever
        // strip that LEADING post — never a middle one (the WebView policy's number/anchor heuristics can
        // mis-resolve here and delete the open's anchor target while the real hat, whose number the parser
        // leaves at the page's start index, survives — device log topic 1103268 p1350). Identify the
        // leading hat by, in order: the id learned from page 1, the server marker, or a structural signal —
        // the leading post is far OLDER (much smaller id) than the page's own posts.
        val posts = page.posts
        val first = posts.firstOrNull()?.takeIf { it.id > 0 } ?: return null
        val second = posts.getOrNull(1)?.takeIf { it.id > 0 }
        val leadGap = if (second != null) second.id.toLong() - first.id.toLong() else 0L
        // A typical intra-page gap between consecutive posts — the leading hat's gap must dwarf it, so a
        // merely-slow topic (large but uniform gaps) isn't mistaken for a hat.
        val typicalGap = posts.getOrNull(2)?.takeIf { it.id > 0 }
                ?.let { it.id.toLong() - (second?.id?.toLong() ?: it.id.toLong()) }
                ?.coerceAtLeast(1L) ?: 1L
        val structuralHat = leadGap > HAT_LEADING_ID_GAP && leadGap > typicalGap * HAT_LEADING_GAP_RATIO
        val isLeadingHat = knownHatPostId == first.id ||
                (page.prependedHatPostId > 0 && page.prependedHatPostId == first.id) ||
                structuralHat
        if (isLeadingHat) {
            if (knownHatPostId == null) knownHatPostId = first.id
            // Capture the echoed hat for the toolbar ⓘ popup BEFORE stripping it from the deep page, so the
            // button works even when the topic is opened directly on a deep page (never showing page 1).
            if (toolbarHatItem == null) toolbarHatItem = mapper.map(first)
            posts.removeAt(0)
        }
        return null
    }

    /** Tag [items] with the 1-based [pageNumber] they were loaded from (drives the «Страница N» dividers). */
    private fun tagPage(items: List<NativePostItem>, pageNumber: Int): List<NativePostItem> =
            if (pageNumber <= 0) items else items.map {
                if (it.pageNumber == pageNumber) it else it.copy(pageNumber = pageNumber)
            }

    /** Drop posts by forum-blacklisted authors (parity with the WebView, which hides their posts). Applied
     *  at load time so all the index-based anchor/pagination logic keeps working on the visible list. */
    private fun filterBlacklisted(items: List<NativePostItem>): List<NativePostItem> =
            items.filterNot { it.userId > 0 && themeUseCase.isForumBlacklisted(it.userId, it.nick) }

    /**
     * Submit [loadedItems] to the adapter with the «Страница N» divider labels recomputed over the whole
     * list first, so the label lives IN the item (DiffUtil then rebinds a page-boundary post when a
     * prepended page shifts it — a purely positional divider would go stale). The hat never gets one.
     */
    private fun submitPosts(commit: (() -> Unit)? = null) {
        var prevPage = 0
        val list = loadedItems.map { item ->
            val label = if (prevPage != 0 && item.pageNumber > 0 && item.pageNumber != prevPage &&
                    item.postId != topicHatPostId) {
                "Страница ${item.pageNumber}"
            } else {
                null
            }
            prevPage = item.pageNumber
            if (item.pageDividerLabel == label) item else item.copy(pageDividerLabel = label)
        }
        if (commit != null) postsAdapter.submitList(list, commit) else postsAdapter.submitList(list)
    }

    /**
     * Prefetch distance for hybrid infinite scroll: ~one viewport from the edge, mirroring the WebView's
     * pixel threshold (`height - (scrollTop + viewport) <= threshold`). Pixel-based (not item-count) so a
     * single very tall post can't leave the loader un-armed near the boundary.
     */
    private fun prefetchThresholdPx(): Int {
        // «Умная предзагрузка страниц» OFF → load only AT the edge (threshold 0), not a viewport ahead.
        if (!mainPreferencesHolder.getSmartPreload()) return 0
        return (recyclerView.height.coerceAtLeast(1) * HYBRID_PREFETCH_VIEWPORT_FRACTION).toInt()
    }

    /** Downward infinite scroll: when the content scrolled to within ~a viewport of the bottom. */
    private fun maybeLoadNextPage() {
        if (isClassicMode()) return // classic mode navigates via the bottom bar, not infinite scroll
        // Never run a next- and prev-page load at once — serialised so only ONE loading spinner shows.
        if (isLoadingNextPage || isLoadingPrevPage || !pagination.hasNextPage()) return
        val range = recyclerView.computeVerticalScrollRange()
        val offset = recyclerView.computeVerticalScrollOffset()
        val extent = recyclerView.computeVerticalScrollExtent()
        val distanceToBottom = range - (offset + extent)
        if (distanceToBottom <= prefetchThresholdPx()) loadNextPage()
    }

    private fun loadNextPage() {
        val url = pagination.nextPageUrl() ?: return
        isLoadingNextPage = true
        showHybridLoading(atTop = false)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            result.onSuccess { page ->
                processHatForPage(page) // strip the repeated hat 4pda echoes onto this deeper page
                val newItems = pagination.registerAndFilterNew(
                        filterBlacklisted(tagPage(mapper.map(page.posts), page.pagination.current)))
                pagination.onPageAppended(page.pagination.current, page.pagination)
                if (newItems.isNotEmpty()) {
                    loadedItems.addAll(newItems)
                    submitPosts {
                        // Chain another prefetch if the appended page still leaves the bottom underfilled
                        // (short pages / tall viewport), so reading forward never stalls at a page seam.
                        if (view != null) recyclerView.post { maybeLoadNextPage() }
                    }
                    // Enrich the appended page too (post ratings «ka_p» + 💬 counts live only in desktop
                    // HTML) — WebView parity: it defers a merge for every hybrid-appended page, not just the
                    // first. Without this, ratings/counts appeared only on the initially opened page.
                    enrichLoadedPage(page)
                }
                updatePaginationBar() // totalPages may have grown

            }
            // On failure: silently stop; the user can pull-to-refresh. isLoadingNextPage resets so a
            // later scroll retries.
            isLoadingNextPage = false
            hideHybridLoading()
        }
    }

    /** Upward infinite scroll: when the content scrolled to within ~a viewport of the top. */
    private fun maybeLoadPrevPage() {
        if (isClassicMode()) return // classic mode navigates via the bottom bar, not infinite scroll
        if (isLoadingPrevPage || isLoadingNextPage || !pagination.hasPrevPage()) return
        if (recyclerView.computeVerticalScrollOffset() <= prefetchThresholdPx()) loadPrevPage()
    }

    /**
     * When the topic opens on its LAST page and the few posts there don't fill the screen, pull previous
     * pages in and anchor the newest posts to the bottom. Without this: the empty area below the last post
     * (issue «пустой блок в конце темы»), AND scrolling back is impossible — a short page produces no scroll
     * events, so the upward infinite scroll (maybeLoadPrevPage) would never fire and previous pages stay
     * unreachable. Chained in [continueFillingLastPage] until the viewport is full or page 1 is reached.
     */
    private fun maybeFillLastPage() {
        if (isClassicMode() || fillingLastPage || isLoadingPrevPage || isLoadingNextPage) return
        if (pagination.loadedPage < pagination.totalPages) return // only at the very end of the topic
        if (!pagination.hasPrevPage()) return
        if (recyclerView.computeVerticalScrollRange() > recyclerView.height) return // already fills the screen
        fillingLastPage = true
        // Hide the list while filling so the user never sees the transient «few posts at the top + empty
        // block below → jump to the bottom» — only the final, filled state (posts at the bottom) is revealed.
        recyclerView.alpha = 0f
        loadPrevPage()
    }

    /** After a fill-prepend: pull another previous page if still short, else reveal anchored at the bottom. */
    private fun continueFillingLastPage() {
        recyclerView.post {
            if (view == null) { finishLastPageFill(scrollToBottom = false); return@post }
            if (pagination.hasPrevPage() && recyclerView.computeVerticalScrollRange() <= recyclerView.height) {
                loadPrevPage() // still under-filled → pull one more previous page
            } else {
                finishLastPageFill(scrollToBottom = true)
            }
        }
    }

    /** End a last-page fill: optionally anchor the newest post to the bottom, then reveal the list. */
    private fun finishLastPageFill(scrollToBottom: Boolean) {
        fillingLastPage = false
        if (scrollToBottom) {
            val last = (recyclerView.adapter?.itemCount ?: 0) - 1
            if (last >= 0) (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(last)
            anchoredBottomPostId = loadedItems.lastOrNull()?.postId // survive enrichment growth (see reanchorBottomAfterGrowth)
        }
        recyclerView.alpha = 1f
    }

    /**
     * The TOP swipe-down pull-to-refresh belongs to CLASSIC mode only. In HYBRID (infinite scroll) the top
     * pull must feed upward pagination, never reload — a swipe-refresh there yanks the reader to a different
     * page (the «прыжок» on scroll-up). HYBRID refreshes exclusively via the bottom-up gesture
     * (installBottomRefreshDetector); manual reload stays on the toolbar refresh button in both modes.
     */
    private fun updateRefreshGesture() {
        refreshLayout.isEnabled = isClassicMode()
    }

    private fun loadPrevPage() {
        val url = pagination.prevPageUrl() ?: return
        isLoadingPrevPage = true
        showHybridLoading(atTop = true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { themeApi.getTheme(url, hatOpen = false, pollOpen = false) }
            }
            if (view == null) return@launch
            var reArm = true
            result.onSuccess { page ->
                // Prepending page 1 brings the real hat into view — keep it and light the toolbar ⓘ; a
                // deeper page's repeated hat is stripped instead.
                val hatId = processHatForPage(page)
                if (hatId != null) {
                    topicHatPostId = hatId
                    postsAdapter.setTopicHat(hatId, hatCollapsed)
                }
                // Prepending page 1 also brings the poll into view — cache it so the toolbar «Опрос» button
                // lights up (and the inline poll header appears). The hat button follows toolbarHatItem.
                if (page.pagination.current <= 1 && page.poll != null && currentPoll == null) {
                    currentPoll = page.poll
                    cachedPollTopicId = page.id
                    pollHeaderAdapter.setPoll(page.poll)
                }
                if (hatId != null || toolbarHatItem != null) pageHasHat = true
                if (currentPoll != null) pageHasPoll = true
                if (pageHasHat || pageHasPoll) refreshToolbarState()
                val newItems = pagination.registerAndFilterNew(
                        filterBlacklisted(tagPage(mapper.map(page.posts), page.pagination.current)))
                pagination.onPagePrepended(page.pagination.current)
                updateRefreshGesture() // reaching page 1 re-enables pull-to-refresh
                if (newItems.isNotEmpty()) {
                    prependPreservingPosition(newItems)
                    reArm = false // re-armed inside the submitList callback after the scroll is restored
                    // Enrich the prepended page too (ratings/💬 counts), same as the initial + next-page
                    // paths — otherwise scrolling UP into earlier pages would show them without ratings.
                    enrichLoadedPage(page)
                }
            }
            if (reArm) {
                isLoadingPrevPage = false
                // A prev page that yielded no NEW posts (all duplicates) never calls the prepend callback,
                // so end the fill here (and reveal the list) or fillingLastPage would stay stuck true.
                if (fillingLastPage) finishLastPageFill(scrollToBottom = true)
            }
            hideHybridLoading()
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
        submitPosts {
            if (view != null) {
                val newIndex = anchorPostId
                        ?.let { id -> loadedItems.indexOfFirst { it.postId == id } }
                        ?.takeIf { it >= 0 }
                        ?: newItems.size
                (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(newIndex + header, anchorOffset)
            }
            isLoadingPrevPage = false
            hideHybridLoading()
            if (fillingLastPage) continueFillingLastPage()
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
            // Resolve the open target with the SAME policy the WebView presenter uses (getnewpost for an
            // unread open, findpost for an explicit post, page 1 otherwise) — the navigator hands us the
            // bare topic url, so loading it raw always landed on page 1 first. Deduped against the load
            // onViewCreated already issued: the navigator echoes the initial open right after onViewCreated,
            // and loading page 1 there is exactly what caused the visible «page 1 → jump to unread» flash.
            val resolved = resolveNavigatorOpenUrl(url, sourceScreen, openIntent, listHints)
            if (resolved != lastRequestedUrl) {
                // Remember where we were so «Назад» (HISTORY) can return to it — this navigator call is a
                // link tap that replaces the current post/topic in this tab.
                captureThemeBackEntry()?.let { themeBackStack.addLast(it) }
                loadTopic(resolved)
            }
        }
        // If the view is not created yet, onViewCreated will load from the (already updated) args.
    }

    /** Snapshot the current url + first-visible scroll anchor for the in-tab Back history. */
    private fun captureThemeBackEntry(): ThemeBackEntry? {
        val url = loadedUrl ?: return null
        if (view == null) return ThemeBackEntry(url, 0, 0)
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return ThemeBackEntry(url, 0, 0)
        val firstPos = lm.findFirstVisibleItemPosition()
        if (firstPos == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return ThemeBackEntry(url, 0, 0)
        val item = loadedItems.getOrNull(firstPos - headerOffset())
        val top = lm.findViewByPosition(firstPos)?.top ?: 0
        return ThemeBackEntry(url, item?.postId ?: 0, top)
    }

    override fun onTabStackBecameCurrent() {
        // Load lazily if this tab became visible before a load was ever REQUESTED (e.g. created hidden).
        // Guard on lastRequestedUrl, not loadedUrl: the navigator makes the tab current right after
        // onViewCreated issues its resolved (getnewpost) load, while loadedUrl is still null — checking
        // loadedUrl here fired a redundant bare page-1 load in parallel (the «page 1 → jump» flash). Also
        // resolve the open target so this safety-net path never lands on page 1 either.
        if (lastRequestedUrl == null && view != null) {
            loadTopic(resolveInitialOpenUrl())
        }
        // The user may have changed font/avatar prefs while away — re-apply on return.
        if (view != null) {
            applyDisplaySettings()
            setupFab() // the «Умная кнопка темы» pref may have been toggled while away
            applyToolbarAutoHide() // the «Поведение тулбара» pref may have been toggled while away
            updateRefreshGesture() // the «Режим чтения тем» pref may have been toggled while away
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
        smartNavMenu?.dispose()
        smartNavMenu = null
        fabHideHandler.removeCallbacks(fabHideRunnable)
        gestureOverlay = null
        gestureOverlayGlyph = null
        gestureOverlayLabel = null
        gestureOverlayProgress = null
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
                .showWithStyledButtons()
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
        submitPosts()
    }

    override fun onReply(item: NativePostItem) {
        insertIntoEditor("[snapback]${item.postId}[/snapback] [b]${item.nick},[/b] \n")
    }

    override fun onQuote(item: NativePostItem) {
        val date = item.date?.takeIf { it.isNotBlank() }?.let { " date=\"$it\"" } ?: ""
        // Full-post quote must carry the post's TEXT, not just the name/date header (mirrors the WebView
        // openFullQuote): take the raw body HTML, drop nested quote blocks, normalize DOM→editor BBCode.
        val body = item.rawBodyHtml?.let { raw ->
            val withoutQuotes = forpdateam.ru.forpda.common.stripHtmlQuoteBlocks(raw)
            val normalized = forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml(withoutQuotes)
                    .ifEmpty { forpdateam.ru.forpda.common.normalizeEditPostBodyFromDomHtml(raw) }
            forpdateam.ru.forpda.common.stripBbcodeQuotes(normalized).ifEmpty { normalized }
        }.orEmpty()
        insertIntoEditor("[quote name=\"${item.nick}\"$date post=${item.postId}]$body[/quote]${'\n'}")
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
                .showWithStyledButtons()
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
            if (item.canQuote) add("Цитировать из буфера" to { quoteFromBuffer(item) })
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
            // «Профиль автора» intentionally omitted — that action is reached by tapping the avatar
            // (onAvatarClick), so it would be a redundant row here.
            if (item.canReport) add("Пожаловаться" to {
                linkHandler.handle("https://4pda.to/forum/index.php?act=report&p=${item.postId}", null)
            })
            add("Создать заметку" to { createNoteForPost(item) })
            if (item.canEdit) add("Изменить" to { onEdit(item) })
            if (item.canDelete) add("Удалить" to { onDelete(item) })
        }
        // No title header — the user asked for action rows only (the nick added visual noise).
        showM3Menu(title = null, actions = actions)
    }

    /** «Цитировать из буфера»: wrap the current clipboard text in a quote from [item] (parity with the
     *  WebView quoteFromBuffer). */
    private fun quoteFromBuffer(item: NativePostItem) {
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(requireContext())
                ?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
            return
        }
        onQuoteSelection(item, text)
    }

    /** «Создать заметку»: open the note-create dialog pre-filled with this post's title/link (parity with
     *  the WebView createNote). */
    private fun createNoteForPost(item: NativePostItem) {
        val themeTitle = arguments?.getString(TabFragment.ARG_TITLE).orEmpty()
        val title = "пост $themeTitle ${item.nick.orEmpty()} ${item.postId}"
        val url = "https://4pda.to/forum/index.php?s=&showtopic=${item.topicId}&view=findpost&p=${item.postId}"
        forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
                .showAddNoteDialog(requireContext(), title, url, notesRepository)
    }

    /** Avatar tap → user menu (parity with the WebView showUserMenu), rendered as a clean M3 popup. */
    override fun onAvatarClick(item: NativePostItem) {
        if (item.userId <= 0) return
        val nick = item.nick.orEmpty()
        val actions = buildList<Pair<String, () -> Unit>> {
            add("Профиль" to { navigationUseCase.openProfile(item.userId) })
            add("Репутация" to { navigationUseCase.openReputationHistory(item.userId) })
            add("Личные сообщения QMS" to { navigationUseCase.openQms(item.userId) })
            add("Темы пользователя" to { navigationUseCase.openSearchUserTopics(nick, item.userId) })
            add("Сообщения в этой теме" to {
                navigationUseCase.openSearchInTopic(pageForumId, pageTopicId, nick, item.userId)
            })
            add("Сообщения пользователя" to { navigationUseCase.openSearchUserMessages(nick, item.userId) })
            // Own posts can't be blacklisted (parity with the WebView guard).
            if (item.userId != authHolder.get().userId) {
                val blacklisted = themeUseCase.isForumBlacklisted(item.userId, item.nick)
                val label = if (blacklisted) "Убрать из чёрного списка форума" else "Добавить в чёрный список форума"
                add(label to { toggleForumBlacklist(item, blacklisted) })
            }
        }
        showM3Menu(title = null, actions = actions)
    }

    /**
     * Toggle a user in the forum blacklist (parity with the WebView toggleForumBlacklist). Adding hides
     * their posts immediately from the loaded list; removing needs a reload to bring them back (they were
     * filtered out on load), so we refresh the topic.
     */
    private fun toggleForumBlacklist(item: NativePostItem, wasBlacklisted: Boolean) {
        val user = forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser(item.userId, item.nick.orEmpty())
        viewLifecycleOwner.lifecycleScope.launch {
            if (wasBlacklisted) themeUseCase.removeForumBlacklistedUser(user)
            else themeUseCase.addForumBlacklistedUser(user)
            if (view == null) return@launch
            if (wasBlacklisted) {
                Toast.makeText(requireContext(), "Убран из чёрного списка форума", Toast.LENGTH_SHORT).show()
                loadTopic(loadedUrl ?: topicUrl) // bring the un-blacklisted user's posts back
            } else {
                Toast.makeText(requireContext(), "Добавлен в чёрный список форума", Toast.LENGTH_SHORT).show()
                val removed = loadedItems.removeAll { it.userId == item.userId }
                if (removed) submitPosts()
            }
        }
    }

    /**
     * Clean Material-3 popup menu — reuses the same [DynamicDialogMenu] the WebView dialogs use, so the
     * post «⋮» menu and the avatar user menu look identical and polished (rounded surface, ripple rows,
     * M3 typography). [title] shows a TitleLarge header when non-null.
     */
    private fun showM3Menu(title: String?, actions: List<Pair<String, () -> Unit>>) {
        if (actions.isEmpty()) return
        val menu = forpdateam.ru.forpda.ui.views.DynamicDialogMenu<Unit, Unit>()
        actions.forEach { (label, action) -> menu.addItem(label) { _, _ -> action() } }
        menu.allowAll()
        val style = forpdateam.ru.forpda.ui.views.DynamicDialogMenu.Style(
                titleTextSizeSp = 18f,
                itemTextSizeSp = 16f,
                itemMinHeightDp = 52,
                contentVerticalPaddingDp = 8,
                itemVerticalPaddingDp = 12,
                titleBottomPaddingDp = 4,
        )
        menu.show(requireContext(), Unit, Unit, title, style)
    }

    override fun onReputation(item: NativePostItem) {
        if (item.userId <= 0) return
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (item.canPlusRep) options.add("Увеличить" to { showReputationChangeDialog(item, increase = true) })
        options.add("Посмотреть" to {
            linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.userId}&tab=reputation", null)
        })
        if (item.canMinusRep) options.add("Уменьшить" to { showReputationChangeDialog(item, increase = false) })
        showM3Menu("Репутация ${item.nick.orEmpty()}", options)
    }

    private fun showReputationChangeDialog(item: NativePostItem, increase: Boolean) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply { hint = "Комментарий (необязательно)" }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("${if (increase) "Увеличить" else "Уменьшить"} репутацию ${item.nick.orEmpty()}")
                .setView(input)
                .setPositiveButton("OK") { _, _ -> performReputationChange(item, increase, input.text?.toString().orEmpty()) }
                .setNegativeButton("Отмена", null)
                .showWithStyledButtons()
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

    /** Lazily builds the find-on-page bar: [query] «k/N» ↑ ↓ ✕, pinned just below the toolbar (top). */
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
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
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
        // Add the bar to the app bar, right below the toolbar, pinned (no scroll flags) — parity with the
        // WebView find-on-page bar, which sits at the top. Hidden (GONE) it takes no space.
        appBarLayout.addView(bar, com.google.android.material.appbar.AppBarLayout.LayoutParams(
                com.google.android.material.appbar.AppBarLayout.LayoutParams.MATCH_PARENT,
                com.google.android.material.appbar.AppBarLayout.LayoutParams.WRAP_CONTENT,
        ).apply { scrollFlags = 0 })
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
        is BodyBlock.EditNote -> "" // system meta line — not searchable content
    }

    /**
     * Apply the topic title to the toolbar from the freshly loaded [page] (parity with the WebView, which
     * resolves the title from [ThemePage.title] on every load). Without this the title comes ONLY from
     * ARG_TITLE, so a topic opened without a title argument (deep link / mention / history / a deep page)
     * shows an empty title over the «N / M» counter. [ThemeToolbarTitlePolicy.resolveForToolbar] never
     * clears an already-visible label when deep-pagination HTML omits the title.
     */
    private fun applyToolbarTitleFromPage(page: ThemePage) {
        val current = getTitle().takeIf { it.isNotBlank() }
        val resolved = ThemeToolbarTitlePolicy.resolveForToolbar(
                page = page,
                sessionTitle = current,
                argTitle = arguments?.getString(TabFragment.ARG_TITLE),
                currentTitle = current,
        )
        if (resolved.isNotEmpty() && resolved != getTitle()) {
            setTitle(resolved)
            setTabTitle(String.format(getString(forpdateam.ru.forpda.R.string.fragment_tab_title_theme), resolved))
        }
    }

    /** Refresh the bar's «N / M» text and hide it entirely for single-page topics. */
    private fun updatePaginationBar() {
        if (!pagination.isInitialised) return
        ensurePaginationBar()
        val total = pagination.totalPages
        paginationLabel?.text = "$barCurrentPage / $total"
        // Top-toolbar subtitle mirrors the page position — digits only, no «Страница … из …» text
        // (parity with the WebView toolbar: «1348 / 1349»).
        setSubtitle(if (total > 1) "$barCurrentPage / $total" else null)
        // The bottom pagination bar belongs to CLASSIC reading mode only; HYBRID (default) uses
        // continuous infinite scroll with no bar. Also hidden while the reply editor is open.
        paginationBar?.let { bar ->
            // Honour «Панель страниц темы» (getTopicPaginationPanelEnabled) — user can hide the bar
            // to rely on page swipes/FAB instead (parity with the WebView pagination panel toggle).
            val show = isClassicMode() && total > 1 && messagePanel?.visibility != View.VISIBLE &&
                    mainPreferencesHolder.getTopicPaginationPanelEnabled()
            bar.visibility = if (show) View.VISIBLE else View.GONE
            // Reserve bottom room: always the bottom-nav chrome (so the last post clears the tab bar), plus
            // the pagination bar's own height when it is shown (CLASSIC). clipToPadding=false keeps the
            // scroll edge-to-edge; this only affects the resting/bottom-aligned position.
            val barPad = if (show) (52 * resources.displayMetrics.density).toInt() else 0
            recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop,
                    recyclerView.paddingRight, bottomNavChromePad + barPad)
        }
    }

    /**
     * MainActivity reports the bottom-nav chrome height (tab bar + system nav) here. Reserve it as the
     * list's bottom padding so the last post's action buttons never hide behind the tab bar (see
     * [bottomNavChromePad]); re-pin if we were parked on the last post when the reservation lands.
     */
    override fun onBottomChromePaddingChanged(padding: Int) {
        if (bottomNavChromePad == padding) return
        bottomNavChromePad = padding
        if (view == null) return
        if (pagination.isInitialised) updatePaginationBar() else {
            recyclerView.setPadding(recyclerView.paddingLeft, recyclerView.paddingTop,
                    recyclerView.paddingRight, bottomNavChromePad)
        }
        if (anchoredBottomPostId != null) recyclerView.post { reanchorBottomAfterGrowth() }
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

    /**
     * FAB long-press → the exact WebView smart-navigation popup ([SmartNavigationMenu]) anchored to the
     * FAB: a page wheel («Текущая» highlighted) plus «В начало темы» / «К непрочитанному» / «В конец темы»
     * / «Ввести номер». On a single-page topic it's meaningless, so nothing shows.
     */
    private fun showSmartNavMenu() {
        if (!pagination.isInitialised || pagination.totalPages <= 1) return
        val menu = smartNavMenu ?: forpdateam.ru.forpda.ui.views.SmartNavigationMenu(
                requireContext(), fab, coordinatorLayout).also {
            it.setListener(object : forpdateam.ru.forpda.ui.views.SmartNavigationMenu.Listener {
                override fun onGoToPage(page: Int) = jumpToPage(page)
                override fun onGoToStart() = jumpToPage(1)
                override fun onGoToEnd() = jumpToLastPage()
                override fun onGoToUnread() {
                    pendingJumpToTop = false
                    loadTopic(unreadUrl())
                }
                override fun onDismiss() {}
            })
            smartNavMenu = it
        }
        menu.show(barCurrentPage, pagination.totalPages, hasUnread = topicHasUnread)
    }

    /** «К непрочитанному»: 4pda's `view=getnewpost` redirect lands on the first unread post. */
    private fun unreadUrl(): String =
            if (pageTopicId > 0) "https://4pda.to/forum/index.php?showtopic=$pageTopicId&view=getnewpost"
            else (loadedUrl ?: topicUrl)

    /** «В конец темы»: load the last page (or just scroll down if already there) and land on the last post. */
    private fun jumpToLastPage() {
        if (!pagination.isInitialised) return
        val last = pagination.totalPages
        if (last == barCurrentPage && loadedItems.isNotEmpty()) {
            val lastPos = (loadedItems.size - 1 + headerOffset()).coerceAtLeast(0)
            recyclerView.post {
                (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(lastPos, 0)
            }
            return
        }
        pendingJumpToBottom = true
        barCurrentPage = last
        loadTopic(pagination.pageUrl(last))
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
                .showWithStyledButtons()
    }

    /** Resolve the field text, falling back to the mirror if the CodeEditor lost it to view churn. */
    private fun resolveMessagePanelDraft(): String {
        val field = messagePanel?.message.orEmpty()
        return if (field.isNotEmpty()) field else messagePanelDraftMirror
    }

    /**
     * The editor «крестик» (clear-all) wipes the whole draft — an easy accidental tap. Confirm first so a
     * misfire doesn't lose a long message. Nothing to confirm on an empty field → just clear silently.
     */
    private fun confirmClearMessage() {
        val panel = messagePanel ?: return
        if (resolveMessagePanelDraft().isBlank() && panel.attachments.isEmpty()) {
            panel.clearMessage(); messagePanelDraftMirror = ""
            return
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setMessage("Очистить весь набранный текст?")
                .setPositiveButton("Очистить") { _, _ ->
                    messagePanel?.clearMessage()
                    messagePanelDraftMirror = ""
                }
                .setNegativeButton("Отмена", null)
                .showWithStyledButtons()
    }

    private fun sendMessage() {
        val panel = messagePanel ?: return
        val message = resolveMessagePanelDraft().trim()
        val attachments = panel.attachments.toMutableList()
        if ((message.isBlank() && attachments.isEmpty()) || isSending || pageTopicId <= 0) return
        isSending = true
        // A brand-new reply lands at the END of the topic; an edit stays on the current page.
        val isNewReply = editingForm == null
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
                // Новый ответ уходит в конец темы — грузим последнюю страницу и садимся на свежий пост
                // (getlastpost → pendingJumpToBottom → скролл на последний пост + подсветка). Правка —
                // просто перезагружаем текущую страницу.
                if (isNewReply && pageTopicId > 0) {
                    loadTopic("https://4pda.to/forum/index.php?showtopic=$pageTopicId&view=getlastpost")
                } else {
                    loadedUrl?.let { loadTopic(it) }
                }
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
        // Remember the requested target so the navigator's redundant echo of the initial open (see
        // loadThemeUrlFromNavigator) doesn't fire a second, page-1 load in parallel.
        lastRequestedUrl = url
        // «view=getlastpost» = «открыть в конец темы» (read topic, «Первое непрочитанное» setting). Its
        // server redirect anchors on the last-READ post, which on an already-read topic with newer posts
        // is a MIDDLE post — felt like landing on a random post. Force the landing to the true bottom of
        // the loaded last page instead (same as «В конец темы»).
        if (url.contains("getlastpost", ignoreCase = true)) pendingJumpToBottom = true
        refreshLayout.isRefreshing = true
        isLoadingNextPage = false
        isLoadingPrevPage = false
        // Defensive: never leave the list hidden if a previous last-page fill was interrupted mid-flight.
        fillingLastPage = false
        recyclerView.alpha = 1f
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
                // Клиентская граница прочитанного: на ПЕРВОМ открытии, если серверный якорь сел бы НИЖЕ
                // самого дальнего реально-виденного поста, перезагрузиться findpost'ом на границу (иначе
                // проскочим непрочитанное — walk-down 4PDA). Фаер один раз за открытие; findpost-резюм не
                // рендерим здесь — return, дальше отработает вложенная загрузка.
                if (maybeResumeToReadBoundary(url, page)) return@onSuccess
                // Fresh (re)load of the topic — forget the previous session's topic-level hat/poll state.
                knownHatPostId = null
                toolbarHatItem = null
                currentPoll = null
                cachedPollTopicId = null
                // The poll's HTML lives on page 1 only. Show it inline only there, but CACHE it at topic
                // level (currentPoll) so the «Опрос» toolbar button persists across pages once page 1 has
                // been seen — matching the WebView, where the poll button is a topic-level toggle.
                val inlinePoll = if (page.pagination.current <= 1) page.poll else null
                pollHeaderAdapter.setPoll(inlinePoll)
                if (page.poll != null) {
                    currentPoll = page.poll
                    cachedPollTopicId = page.id
                }
                pageHasPoll = currentPoll != null
                // Topic hat (SAME policy as the WebView): 4pda echoes the topic's first post as a «шапка»
                // at the top of EVERY page. Keep it only on the real first page (as a collapsible block);
                // on deep pages strip the repeated copy so it doesn't show again. processHatForPage returns
                // the hat id only for page 1 (inline), but also captures [toolbarHatItem] on ANY page so the
                // ⓘ toolbar button (and its popup) work even when the topic opens directly on a deep page.
                topicHatPostId = processHatForPage(page)
                pageHasHat = toolbarHatItem != null
                refreshToolbarState()
                applyToolbarTitleFromPage(page) // fill the title from the loaded page (deep-link/deep-page opens)
                val items = filterBlacklisted(tagPage(mapper.map(page.posts), page.pagination.current))
                val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: page.id
                pagination.reset(topicId, page.pagination, items)
                updateRefreshGesture() // top pull feeds prev-page loading, not refresh, when pages are above
                barCurrentPage = pagination.loadedPage
                loadedItems.clear()
                loadedItems.addAll(items)
                mentionScannedPostIds.clear()
                markedTopicReadAtEnd = false
                topicHasUnread = page.hasUnreadTarget // drives «К непрочитанному» in the smart-nav menu
                closeSearch() // matches from a previous page are stale after a reload
                updatePaginationBar()
                postsAdapter.setTopicHat(topicHatPostId, hatCollapsed)
                submitPosts {
                    if (view != null) {
                        applyInitialAnchor(page.anchorPostId, page.hasUnreadTarget, items)
                        // Fill an under-filled last page from previous pages (no empty area + scroll-back works).
                        recyclerView.post { maybeFillLastPage() }
                    }
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
                val existing = loadedItems[i]
                val enriched = enrichedById[existing.postId] ?: continue
                // Freshly-mapped items carry pageNumber=0 (the mapper has no page context) — preserve the
                // existing page tag so the «Страница N» dividers survive the deferred metadata merge.
                val updated = enriched.copy(pageNumber = existing.pageNumber)
                if (updated != existing) {
                    loadedItems[i] = updated
                    changed = true
                }
            }
            if (changed) submitPosts { reanchorBottomAfterGrowth() }
        }
    }

    /**
     * After the metadata enrichment re-lays out the list (taller posts), keep the already-read topic's last
     * post pinned to the BOTTOM with its action buttons visible — but only while the user is still sitting on
     * it (hasn't scrolled up). Otherwise it would yank them back down.
     */
    private fun reanchorBottomAfterGrowth() {
        if (view == null) return
        val anchorId = anchoredBottomPostId ?: return
        if (anchorId != loadedItems.lastOrNull()?.postId) { anchoredBottomPostId = null; return }
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val itemCount = recyclerView.adapter?.itemCount ?: return
        // Still parked at the end (last item at least partially visible) → re-pin it to the bottom.
        if (lm.findLastVisibleItemPosition() >= itemCount - 1) {
            lm.scrollToPosition(itemCount - 1)
            bottomAlignPost(itemCount - 1)
        } else {
            anchoredBottomPostId = null // user scrolled away — stop re-pinning
        }
    }

    /**
     * Клиентская граница прочитанного (модель Discourse) — общая с WebView-движком. На ПЕРВОМ открытии
     * темы сверяем, куда сел бы сервер, с самым дальним реально-виденным постом ([TopicReadBoundaryStore]).
     * Если сервер увёл бы СТРОГО НИЖЕ (новее) границы — между границей и серверным таргетом есть
     * непрочитанное, которое иначе проскочим (4PDA/IPB метит страницу прочитанной по факту загрузки, из-за
     * чего getnewpost/getlastpost уезжают вниз). Тогда перезагружаемся findpost'ом на границу.
     *
     * Фаер строго один раз за открытие ([boundaryResumeArmed] гасится сразу), явные findpost-дип-линки и
     * переходы по страницам не переопределяем. При отсутствии границы (cold-miss) — фолбэк на текущее
     * серверное поведение (безопасно).
     *
     * @return true, если запущен findpost-резюм (эту загрузку рендерить не нужно — return у вызывающего).
     */
    private fun maybeResumeToReadBoundary(
            url: String,
            page: forpdateam.ru.forpda.entity.remote.theme.ThemePage,
    ): Boolean {
        if (!boundaryResumeArmed) return false
        boundaryResumeArmed = false // один раз за открытие, что бы дальше ни решили
        if (page.id <= 0) return false
        // Явный findpost-дип-линк (упоминание/закладка на конкретный пост) или наш собственный резюм —
        // не переопределяем; переход по страницам тоже.
        if (url.contains("view=findpost", ignoreCase = true) ||
                url.contains("act=findpost", ignoreCase = true)) return false
        if (pendingJumpToTop) return false
        val boundaryId = readBoundaryStore.lastSeenPostId(page.id)
        if (boundaryId <= 0) return false
        val serverAnchorId = page.anchorPostId?.removePrefix("entry")?.trim()?.toIntOrNull()
                ?: page.anchor?.removePrefix("entry")?.trim()?.toIntOrNull()
        val lastLoadedId = page.posts.lastOrNull { it.id > 0 }?.id
        val resumeId = forpdateam.ru.forpda.presentation.theme.TopicReadBoundaryPolicy.resumeAnchorPostId(
                boundaryPostId = boundaryId,
                serverAnchorPostId = serverAnchorId,
                lastLoadedPostId = lastLoadedId,
        ) ?: return false
        // Резюм — findpost на границу. Гасим «в конец/на верх», чтобы вложенная загрузка села на границу.
        pendingJumpToBottom = false
        pendingJumpToTop = false
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_READ_BOUNDARY",
                    "native resume_findpost topic=${page.id} boundary=$resumeId serverAnchor=$serverAnchorId lastLoaded=$lastLoadedId")
        }
        loadTopic(forpdateam.ru.forpda.presentation.theme.TopicUnreadFindPostReloadPolicy
                .buildFindPostUrl(page.id, resumeId.toString()))
        return true
    }

    /**
     * Top-align [concatPos] (first-unread / findpost / read-boundary target) so the user starts reading
     * from it. When there is enough content below, its top sits at the list's top edge. When the anchor is
     * near the END (little/no content below), the layout manager clamps at the natural bottom and the post
     * ends up fully visible without any empty region. We do NOT add transient bottom padding to force it to
     * the very top — that left an ugly empty block below the anchor (user report «пустая область внизу»).
     * A short last page is handled separately by [maybeFillLastPage], which pulls previous pages in.
     */
    /**
     * Pull the post at [concatPos] fully into view at the BOTTOM: after a bottom-anchor [scrollToPosition]
     * the layout manager can still leave a post TALLER than the remaining space cut off (its action buttons
     * below the fold — user report «последнее сообщение обрезается по низу, не видно кнопок»). Measure the
     * laid-out item and, if it overshoots the bottom edge, [scrollBy] exactly that much so its bottom (with
     * the like/quote/reputation row) sits at the viewport bottom. No-op when the post already fits.
     */
    private fun bottomAlignPost(concatPos: Int) {
        recyclerView.post {
            if (view == null) return@post
            val itemView = recyclerView.findViewHolderForAdapterPosition(concatPos)?.itemView ?: return@post
            val bottomLimit = recyclerView.height - recyclerView.paddingBottom
            val overshoot = itemView.bottom - bottomLimit
            if (overshoot > 0) recyclerView.scrollBy(0, overshoot)
        }
    }

    private fun settleAnchorAtTop(concatPos: Int) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(concatPos, 0)
        recyclerView.post {
            if (view == null) return@post
            val itemView = recyclerView.findViewHolderForLayoutPosition(concatPos)?.itemView ?: return@post
            // When the anchor is near the END of the loaded window, top-aligning clamps at the natural
            // bottom and can leave the anchor CUT OFF below the viewport (user report «последний пост
            // срезается по низу»). Bottom-align it instead so it is FULLY visible — no transient padding,
            // so no empty block either. (A normal mid-list anchor has room above/below and never trips this.)
            if (itemView.bottom > recyclerView.height) {
                lm.scrollToPosition(concatPos)
            }
        }
    }

    private fun applyInitialAnchor(
            anchorPostId: String?,
            hasUnreadTarget: Boolean,
            items: List<NativePostItem>,
    ) {
        val ids = items.map { it.postId }
        val targetId = anchorPostId?.toIntOrNull()
        // Restore-scroll «где остановился»: вернуться на сохранённый пост и его точный offset (после
        // пересоздания фрагмента). Приоритетнее серверного якоря и границы прочитанного — это ровно то
        // место, где стоял пользователь.
        if (pendingRestorePostId > 0) {
            val restoreId = pendingRestorePostId
            val restoreOffset = pendingRestoreOffset
            pendingRestorePostId = 0
            pendingRestoreOffset = 0
            pendingJumpToBottom = false
            pendingJumpToTop = false
            val idx = ids.indexOf(restoreId)
            if (idx >= 0) {
                (recyclerView.layoutManager as? LinearLayoutManager)
                        ?.scrollToPositionWithOffset(idx + headerOffset(), restoreOffset)
                recyclerView.post { markVisiblePostsRead(); maybeMarkTopicReadAtEnd() }
                return
            }
            // Пост не в загруженном окне (findpost вернул другую страницу) — падаем в обычный якорь ниже.
        }
        // «В конец темы»: land on the very last post of the (last) page — fully visible at the BOTTOM,
        // with the previous posts filling the space above. NOT top-aligned: top-aligning the last item
        // needs a big transient bottom pad that leaves an ugly empty area below it (user report). A plain
        // scrollToPosition shows the whole last post without any empty region or cut-off.
        if (pendingJumpToBottom) {
            pendingJumpToBottom = false
            val lastPos = (ids.size - 1 + headerOffset()).coerceAtLeast(0)
            anchoredBottomPostId = ids.lastOrNull()
            (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(lastPos)
            bottomAlignPost(lastPos)
            // Highlight the last post (2s border) — parity with the first-unread open; the read-topic open
            // lands on the last post, so flash it too.
            ids.lastOrNull()?.let { postsAdapter.requestHighlight(it) }
            recyclerView.post { markVisiblePostsRead(); maybeMarkTopicReadAtEnd() }
            return
        }
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
                // The anchor is the very LAST loaded post (already-read topic opening on its final post):
                // top-aligning clamps it CUT OFF at the bottom (action buttons off-screen). Bottom-align it
                // so the whole post + its buttons are visible, and remember it so the async enrichment
                // (which grows posts) re-anchors instead of pushing the buttons back below the fold.
                val isLastPost = request !is AnchorRequest.Top && resolution.index == ids.size - 1
                when {
                    request is AnchorRequest.Top ->
                        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(target, 0)
                    isLastPost -> {
                        anchoredBottomPostId = ids.last()
                        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPosition(target)
                        bottomAlignPost(target)
                    }
                    else -> settleAnchorAtTop(target)
                }
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
        // Клиентская граница прочитанного: двигаем её на самый дальний реально-виденный сейчас пост
        // (recordSeen монотонно игнорит откат). Это тот же фид, что WebView-движок делает в
        // updatePageHistoryHtml/RefreshScrollSnapshot — при переоткрытии не сядем ниже непрочитанных.
        visibleIds.maxOrNull()?.let { deepestSeen ->
            readBoundaryStore.recordSeen(pageTopicId, deepestSeen, barCurrentPage)
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
        // Тема реально дочитана до конца — клиентская граница больше не нужна: следующее открытие пусть
        // идёт по серверу (getnewpost → первый непрочитанный, если появятся новые посты). Иначе стухшая
        // граница удерживала бы якорь на старом посте.
        readBoundaryStore.clear(pageTopicId)
    }

    private companion object {
        /** Arm hybrid page prefetch when scrolled within this fraction of a viewport from an edge
         *  (WebView parity: an ~800px pixel threshold rather than an item count). */
        const val HYBRID_PREFETCH_VIEWPORT_FRACTION = 0.75f

        /** A deep page's leading post is the prepended topic hat when its id is at least this much OLDER
         *  (smaller) than the page's next post — the hat is the topic's ancient first post, the page's own
         *  posts are recent and clustered. Large enough to never trip on normal consecutive posts. */
        const val HAT_LEADING_ID_GAP = 1_000_000L

        /** …and the leading gap must also be at least this many times the page's typical intra-post gap,
         *  so a merely-slow topic with large but uniform gaps isn't mistaken for a prepended hat. */
        const val HAT_LEADING_GAP_RATIO = 20L

        /** Font-size pref value that maps to textScale 1.0 (matches the WebView default defaultFontSize). */
        const val REFERENCE_FONT_SIZE = 16f

        /** Minimum horizontal travel (dp) for a page-swipe drag to register on release. */
        const val SWIPE_MIN_DISTANCE_DP = 80f

        /** Per-frame scroll delta (px) beyond which the FAB direction arrow flips. */
        const val SCROLL_HIDE_THRESHOLD = 8

        /** Idle delay after which the smart button auto-hides (appears again on the next scroll). */
        const val FAB_AUTO_HIDE_MS = 2500L

        /** onSaveInstanceState keys for restore-scroll «где остановился» / устойчивость состояния. */
        private const val STATE_RESTORE_POST_ID = "native_topic_restore_post_id"
        private const val STATE_RESTORE_OFFSET = "native_topic_restore_offset"
        private const val STATE_RESTORE_BAR_PAGE = "native_topic_restore_bar_page"

        private const val MENU_SEARCH = 0x4E01
        private const val MENU_REFRESH = 0x4E03
        private const val MENU_CREATE = 0x4E06
        private const val MENU_POLL = 0x4E07
        private const val MENU_HAT = 0x4E08
        private const val MENU_OVERFLOW = 0x4E0C
    }
}
