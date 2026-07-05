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
    lateinit var linkHandler: ILinkHandler

    private val mapper = NativePostMapper()
    private val anchorResolver = NativeAnchorResolver()
    private val pagination = TopicPaginationController()
    private val postsAdapter by lazy { TopicPostsAdapter(linkHandler, this) }
    private val pollHeaderAdapter = PollHeaderAdapter()

    /** Adapter positions are shifted by the poll header (0 or 1) — offset scroll targets by this. */
    private fun headerOffset(): Int = pollHeaderAdapter.itemCount

    /** The accumulated posts across all loaded pages (source of truth for the adapter). */
    private val loadedItems = ArrayList<NativePostItem>()

    /** The URL actually loaded into the list (may differ from ARG_TAB after a navigator switch). */
    private var loadedUrl: String? = null
    private var isLoadingNextPage = false
    private var isLoadingPrevPage = false

    /** Topic context for posting a reply (from the loaded page). */
    private var pageForumId = 0
    private var pageTopicId = 0
    private var pageSt = 0
    private var isSending = false

    private var editorBar: android.widget.LinearLayout? = null
    private var editorInput: android.widget.EditText? = null
    /** Non-null when the editor is editing an existing post (else composing a new reply). */
    private var editingForm: forpdateam.ru.forpda.entity.remote.editpost.EditPostForm? = null

    override fun hasBackHandling(): Boolean = editorBar?.visibility == View.VISIBLE

    override fun onBackPressed(): Boolean {
        // Back closes the reply editor first, before leaving the topic.
        if (editorBar?.visibility == View.VISIBLE) {
            editorBar?.visibility = View.GONE
            editingForm = null
            hideKeyboard()
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
        recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) maybeLoadNextPage()
                if (dy < 0) maybeLoadPrevPage()
            }
        })
        refreshLayout.setOnRefreshListener { loadTopic(topicUrl) }
        loadTopic(topicUrl)
    }

    /** Downward infinite scroll: when within [NEXT_PAGE_PREFETCH_DISTANCE] items of the end. */
    private fun maybeLoadNextPage() {
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
            }
            // On failure: silently stop; the user can pull-to-refresh. isLoadingNextPage resets so a
            // later scroll retries.
            isLoadingNextPage = false
        }
    }

    /** Upward infinite scroll: when the top of the list nears item 0 and a page above exists. */
    private fun maybeLoadPrevPage() {
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
                pollHeaderAdapter.setPoll(page.poll)
                val items = mapper.map(page.posts)
                val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: page.id
                pagination.reset(topicId, page.pagination, items)
                loadedItems.clear()
                loadedItems.addAll(items)
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
        val request = when {
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
            }
            // PostNotLoaded / Empty: nothing to do — downward pagination will bring later pages in.
            else -> Unit
        }
    }

    private companion object {
        /** Start fetching the next page this many items before the current list end. */
        const val NEXT_PAGE_PREFETCH_DISTANCE = 3

        /** Start fetching the previous page when the first-visible item is within this of the top. */
        const val PREV_PAGE_PREFETCH_DISTANCE = 1
    }
}
