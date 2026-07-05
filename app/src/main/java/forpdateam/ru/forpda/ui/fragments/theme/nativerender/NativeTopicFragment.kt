package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import android.os.Bundle
import android.view.View
import android.widget.Toast
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
        // Editor integration is the next step; the reply BBCode is put on the clipboard for now.
        insertIntoEditor("[snapback]${item.postId}[/snapback] [b]${item.nick},[/b] \n")
    }

    override fun onQuote(item: NativePostItem) {
        // Full-post quote (selection→quote comes via the text ActionMode).
        insertIntoEditor("[quote name=\"${item.nick}\" date=\"${item.date.orEmpty()}\" post=${item.postId}]\n")
    }

    /** Placeholder until the inline editor lands: copy the reply/quote form to the clipboard. */
    private fun insertIntoEditor(text: String) {
        val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("quote", text))
        Toast.makeText(requireContext(), "Форма скопирована в буфер", Toast.LENGTH_SHORT).show()
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
