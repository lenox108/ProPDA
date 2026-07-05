package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination

/**
 * Pure downward-pagination state for the native topic renderer (roadmap
 * `native-topic-renderer.md`, Фаза 1 "бесконечная подгрузка вниз"). Tracks which page is loaded
 * and dedups posts across page boundaries, and builds the next page URL. No Android deps → JVM
 * unit-testable.
 *
 * 4pda page URLs are `showtopic=<id>&st=<st>` where `st` is a 0-based post offset: page N (1-based)
 * has `st = (N-1) * perPage`. So the NEXT page after [loadedPage] is at `st = loadedPage * perPage`.
 */
class TopicPaginationController {

    var topicId: Int = 0
        private set
    var perPage: Int = DEFAULT_PER_PAGE
        private set
    var totalPages: Int = 1
        private set

    /** Highest 1-based page number appended so far (downward edge). */
    var loadedPage: Int = 1
        private set

    /** Lowest 1-based page number loaded so far (upward edge). */
    var firstLoadedPage: Int = 1
        private set

    private val seenPostIds = HashSet<Int>()

    /** True once a topic has been [reset] into this controller. */
    var isInitialised: Boolean = false
        private set

    /**
     * (Re)start pagination for a freshly (re)loaded first slice. Registers the initial items so
     * they are never appended again, and reads page geometry from [pagination].
     */
    fun reset(topicId: Int, pagination: Pagination, initialItems: List<NativePostItem>) {
        this.topicId = topicId
        this.perPage = pagination.perPage.coerceAtLeast(1)
        this.totalPages = pagination.all.coerceAtLeast(1)
        this.loadedPage = pagination.current.coerceAtLeast(1)
        this.firstLoadedPage = this.loadedPage
        seenPostIds.clear()
        initialItems.forEach { seenPostIds.add(it.postId) }
        isInitialised = true
    }

    /** URL of an arbitrary 1-based [pageNumber] (clamped to 1..totalPages) for a page-jump. */
    fun pageUrl(pageNumber: Int): String {
        val n = pageNumber.coerceIn(1, totalPages.coerceAtLeast(1))
        val st = (n - 1) * perPage
        return "https://4pda.to/forum/index.php?showtopic=$topicId&st=$st"
    }

    fun hasNextPage(): Boolean = isInitialised && topicId > 0 && loadedPage < totalPages

    /** URL of the next page down, or null if there is none. */
    fun nextPageUrl(): String? {
        if (!hasNextPage()) return null
        val st = loadedPage * perPage
        return "https://4pda.to/forum/index.php?showtopic=$topicId&st=$st"
    }

    /**
     * Record that the page numbered [pageNumber] (1-based) has been appended, advancing
     * [loadedPage]; also refresh [totalPages] from the freshly parsed [pagination] (it can grow
     * while the user reads).
     */
    fun onPageAppended(pageNumber: Int, pagination: Pagination) {
        loadedPage = maxOf(loadedPage, pageNumber.coerceAtLeast(1))
        totalPages = maxOf(totalPages, pagination.all.coerceAtLeast(1))
    }

    fun hasPrevPage(): Boolean = isInitialised && topicId > 0 && firstLoadedPage > 1

    /** URL of the page above the currently-loaded top, or null if we are already at page 1. */
    fun prevPageUrl(): String? {
        if (!hasPrevPage()) return null
        // Page (firstLoadedPage - 1) → st = ((firstLoadedPage - 1) - 1) * perPage.
        val st = (firstLoadedPage - 2).coerceAtLeast(0) * perPage
        return "https://4pda.to/forum/index.php?showtopic=$topicId&st=$st"
    }

    /** Record that the page numbered [pageNumber] (1-based) has been PREPENDED, lowering the top edge. */
    fun onPagePrepended(pageNumber: Int) {
        firstLoadedPage = minOf(firstLoadedPage, pageNumber.coerceAtLeast(1))
    }

    /**
     * Filter [items] down to posts not seen on already-loaded pages (server pages can overlap by a
     * post), registering the survivors. Returns the new-only list to append to the adapter.
     */
    fun registerAndFilterNew(items: List<NativePostItem>): List<NativePostItem> =
        items.filter { seenPostIds.add(it.postId) }

    private companion object {
        const val DEFAULT_PER_PAGE = 20
    }
}
