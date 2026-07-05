package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import forpdateam.ru.forpda.common.getColorFromAttr
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.theme.ThemeTabHost
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

    // topicPreferencesHolder is provided by the TabFragment supertype.

    private val mapper = NativePostMapper()
    private val anchorResolver = NativeAnchorResolver()
    private val pagination = TopicPaginationController()
    private val postsAdapter by lazy { TopicPostsAdapter(linkHandler, this) }
    private val pollHeaderAdapter = PollHeaderAdapter()

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
    /** Post id of the topic hat on the loaded page (the «Инфо» toolbar button scrolls to it). */
    private var topicHatPostId: Int? = null

    private var editorBar: android.widget.LinearLayout? = null
    private var editorInput: android.widget.EditText? = null
    /** Non-null when the editor is editing an existing post (else composing a new reply). */
    private var editingForm: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm? = null

    private var paginationBar: android.widget.LinearLayout? = null
    private var paginationLabel: TextView? = null
    /** True while the pagination bar is slid off-screen by a downward scroll (hide-on-scroll). */
    private var barHiddenByScroll: Boolean = false

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

    override fun hasBackHandling(): Boolean =
            editorBar?.visibility == View.VISIBLE || searchBar?.visibility == View.VISIBLE

    override fun onBackPressed(): Boolean {
        // Back closes the find-on-page bar / reply editor first, before leaving the topic.
        if (searchBar?.visibility == View.VISIBLE) {
            closeSearch()
            return true
        }
        if (editorBar?.visibility == View.VISIBLE) {
            editorBar?.visibility = View.GONE
            editingForm = null
            hideKeyboard()
            updatePaginationBar() // restore the bar the editor was covering
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
                applyBarHideOnScroll(dy)
            }
        })
        installPageSwipeDetector()
        refreshLayout.setOnRefreshListener { loadTopic(loadedUrl ?: topicUrl) }
        setupToolbarMenu()
        loadTopic(topicUrl)
    }

    /**
     * Native top-toolbar (parity with the WebView theme toolbar): dedicated icon BUTTONS shown
     * always — «Написать» (pencil, opens the editor), «Опрос» (visible only with a poll), search,
     * «Обновить» and «Инфо» (topic hat, visible only when a hat exists) — plus an overflow with
     * page-jump / copy-link / open-forum. Icons match the WebView (ic_toolbar_create / ic_poll_box /
     * ic_toolbar_search / ic_toolbar_refresh / ic_info). The tab's [toolbar] comes from [TabFragment].
     */
    private fun setupToolbarMenu() {
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
        menu.add(0, MENU_GOTO_PAGE, 5, "Перейти на страницу")
        menu.add(0, MENU_COPY_LINK, 6, "Копировать ссылку на тему")
        menu.add(0, MENU_OPEN_FORUM, 7, "Открыть раздел форума")
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_CREATE -> { toggleComposeEditor(); true }
                MENU_POLL -> { onPollToolbarClick(); true }
                MENU_SEARCH -> { toggleSearchBar(); true }
                MENU_REFRESH -> { loadTopic(loadedUrl ?: topicUrl); true }
                MENU_HAT -> { onHatToolbarClick(); true }
                MENU_GOTO_PAGE -> { showPagePicker(); true }
                MENU_COPY_LINK -> {
                    val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                    val url = "https://4pda.to/forum/index.php?showtopic=$pageTopicId"
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("topic", url))
                    Toast.makeText(requireContext(), "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    true
                }
                MENU_OPEN_FORUM -> {
                    if (pageForumId > 0) linkHandler.handle("https://4pda.to/forum/index.php?showforum=$pageForumId", null)
                    true
                }
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
        toolbar.menu.findItem(MENU_POLL)?.isVisible = pageHasPoll
        toolbar.menu.findItem(MENU_HAT)?.isVisible = pageHasHat
    }

    /** Toolbar «Написать»: opens the empty compose editor (or closes it if already open). */
    private fun toggleComposeEditor() {
        if (editorBar?.visibility == View.VISIBLE) {
            editorBar?.visibility = View.GONE
            editingForm = null
            hideKeyboard()
            updatePaginationBar()
            return
        }
        val input = ensureEditorBar()
        editingForm = null
        editorBar?.visibility = View.VISIBLE
        paginationBar?.visibility = View.GONE // editor and bar share the bottom
        input.requestFocus()
        showKeyboard(input)
    }

    /** Toolbar «Опрос»: bring the poll (page-1 header) into view. Voting itself lives in the header. */
    private fun onPollToolbarClick() {
        if (!pageHasPoll) return
        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
    }

    /** Toolbar «Инфо»: scroll to the topic hat post (its collapsible rendering is handled separately). */
    private fun onHatToolbarClick() {
        val hatId = topicHatPostId ?: return
        val index = loadedItems.indexOfFirst { it.postId == hatId }
        if (index < 0) return
        (recyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(index + headerOffset(), 0)
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
        if (view != null) applyDisplaySettings()
    }

    /**
     * Read the user's font-size / avatar prefs and push them into the adapter (parity with the
     * WebView path, which sets `defaultFontSize` + avatar CSS at load). Font size is an absolute
     * base (default 16); [PostDisplaySettings.textScale] is relative to that reference so the
     * default look is unchanged.
     */
    private fun applyDisplaySettings() {
        val fontSize = mainPreferencesHolder.getWebViewFontSize()
        postsAdapter.setDisplaySettings(
                TopicPostsAdapter.PostDisplaySettings(
                        textScale = fontSize / REFERENCE_FONT_SIZE,
                        showAvatars = topicPreferencesHolder.getShowAvatars(),
                        circleAvatars = topicPreferencesHolder.getCircleAvatars(),
                        density = mainPreferencesHolder.getTopicPostDensity(),
                )
        )
    }

    override fun onRestoredAfterChildFragmentRemoved() {
        // Native list keeps its state/scroll across a covering child fragment — nothing to restore.
    }

    // endregion

    // region PostActionListener — write actions (authorised by the user)

    override fun onVote(item: NativePostItem, up: Boolean) {
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
            val input = ensureEditorBar()
            input.setText(form.message)
            editorBar?.visibility = View.VISIBLE
            input.requestFocus()
            input.setSelection(input.text?.length ?: 0)
            showKeyboard(input)
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

    private fun insertIntoEditor(text: String) {
        val input = ensureEditorBar()
        input.text?.insert(input.selectionStart.coerceAtLeast(0), text)
        editorBar?.visibility = View.VISIBLE
        paginationBar?.visibility = View.GONE // editor and bar share the bottom
        input.requestFocus()
        showKeyboard(input)
    }

    /** Lazily builds the inline reply bar (EditText + «Отправить») pinned to the bottom. */
    private fun ensureEditorBar(): android.widget.EditText {
        editorInput?.let { return it }
        val ctx = requireContext()
        val dm = ctx.resources.displayMetrics
        val bar = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            val p = (8 * dm.density).toInt()
            setPadding(p, p, p, p)
            visibility = View.GONE
        }
        val input = android.widget.EditText(ctx).apply {
            hint = "Ответ…"
            textSize = 15f
            maxLines = 5
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val send = TextView(ctx).apply {
            text = "Отправить"
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            val ph = (12 * dm.density).toInt()
            setPadding(ph, ph, ph, ph)
            setOnClickListener { sendReply() }
        }
        bar.addView(input)
        bar.addView(send)
        coordinatorLayout.addView(bar, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
        ).apply { gravity = android.view.Gravity.BOTTOM })
        editorBar = bar
        editorInput = input
        return input
    }

    /** Lazily builds the bottom pagination bar (⏮ ◀ «N / M» ▶ ⏭); tapping the label opens a page picker. */
    private fun ensurePaginationBar() {
        if (paginationBar != null) return
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
        fun navButton(label: String, onClick: () -> Unit) = TextView(ctx).apply {
            text = label
            textSize = 18f
            gravity = android.view.Gravity.CENTER
            setTextColor(ctx.getColorFromAttr(androidx.appcompat.R.attr.colorPrimary))
            val ph = (12 * dm.density).toInt()
            val pv = (6 * dm.density).toInt()
            setPadding(ph, pv, ph, pv)
            setOnClickListener { onClick() }
        }
        val label = TextView(ctx).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface))
            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showPagePicker() }
        }
        bar.addView(navButton("🔍") { toggleSearchBar() })
        bar.addView(navButton("⏮") { jumpToPage(1) })
        bar.addView(navButton("◀") { jumpToPage(barCurrentPage - 1) })
        bar.addView(label)
        bar.addView(navButton("▶") { jumpToPage(barCurrentPage + 1) })
        bar.addView(navButton("⏭") { jumpToPage(pagination.totalPages) })
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
            val show = isClassicMode() && total > 1 && editorBar?.visibility != View.VISIBLE
            bar.visibility = if (show) View.VISIBLE else View.GONE
            // A (re)shown bar must sit at rest, undoing any hide-on-scroll offset.
            bar.translationY = 0f
            barHiddenByScroll = false
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

    /** Slide the pagination bar off on downward scroll and back on upward scroll (more reading room). */
    private fun applyBarHideOnScroll(dy: Int) {
        val bar = paginationBar ?: return
        if (bar.visibility != View.VISIBLE) return
        if (dy > SCROLL_HIDE_THRESHOLD && !barHiddenByScroll) {
            barHiddenByScroll = true
            bar.animate().translationY(bar.height.toFloat()).setDuration(150).start()
        } else if (dy < -SCROLL_HIDE_THRESHOLD && barHiddenByScroll) {
            barHiddenByScroll = false
            bar.animate().translationY(0f).setDuration(150).start()
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

    private fun sendReply() {
        val input = editorInput ?: return
        val message = input.text?.toString()?.trim().orEmpty()
        if (message.isBlank() || isSending || pageTopicId <= 0) return
        isSending = true
        hideKeyboard()
        Toast.makeText(requireContext(), "Отправка…", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            // Editing an existing post reuses its loaded form (type=EDIT, postId set); a new reply
            // builds a fresh NEW_POST form from the current topic context.
            val form = editingForm?.apply { this.message = message }
                    ?: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm().apply {
                        type = forpdateam.ru.forpda.entity.remote.editpost.EditPostForm.TYPE_NEW_POST
                        forumId = pageForumId
                        topicId = pageTopicId
                        st = pageSt
                        this.message = message
                    }
            val result = withContext(Dispatchers.IO) { runCatching { editPostApi.sendPost(form) } }
            isSending = false
            if (view == null) return@launch
            result.onSuccess {
                input.setText("")
                editorBar?.visibility = View.GONE
                editingForm = null
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
                pageHasHat = page.topicHatPost != null
                topicHatPostId = page.topicHatPost?.id
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
                postsAdapter.submitList(loadedItems.toList()) {
                    if (view != null) applyInitialAnchor(page.anchorPostId, page.hasUnreadTarget, items)
                }
            }.onFailure { error ->
                Toast.makeText(requireContext(), "Ошибка загрузки темы: ${error.message}", Toast.LENGTH_LONG).show()
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

        /** Per-frame scroll delta (px) beyond which the pagination bar hides/shows on scroll. */
        const val SCROLL_HIDE_THRESHOLD = 8

        private const val MENU_SEARCH = 0x4E01
        private const val MENU_GOTO_PAGE = 0x4E02
        private const val MENU_REFRESH = 0x4E03
        private const val MENU_COPY_LINK = 0x4E04
        private const val MENU_OPEN_FORUM = 0x4E05
        private const val MENU_CREATE = 0x4E06
        private const val MENU_POLL = 0x4E07
        private const val MENU_HAT = 0x4E08
    }
}
