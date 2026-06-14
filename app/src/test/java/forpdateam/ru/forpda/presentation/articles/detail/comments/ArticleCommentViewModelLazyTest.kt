package forpdateam.ru.forpda.presentation.articles.detail.comments

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.suspendCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeoutException
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleCommentViewModelLazyTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(interactor: ArticleInteractor): ArticleCommentViewModel {
        every { interactor.observeComments() } returns emptyFlow()
        every { interactor.initData } returns ArticleInteractor.InitData(newsId = 1)
        every { interactor.takePendingScrollCommentId() } returns 0
        val router = mockk<TabRouter>(relaxed = true)
        val linkHandler = mockk<ILinkHandler>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true) {
            every { observe() } returns kotlinx.coroutines.flow.flowOf(mockk(relaxed = true))
            every { get() } returns mockk(relaxed = true)
        }
        val errorHandler = mockk<IErrorHandler>(relaxed = true)
        return ArticleCommentViewModel(interactor, router, linkHandler, authHolder, errorHandler).also {
            it.start()
        }
    }

    @Test
    fun `applyCommentLoadResult publishes first batch with hasMore`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        every { interactor.expectedCommentsCount() } returns 353
        val children = (1..20).map { id ->
            Comment().apply { this.id = id; userNick = "u$id" }
        }
        val tree = Comment().apply { this.children.addAll(children) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                tree = tree,
                fromCache = false,
                hasMore = true,
                page = 1,
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        val loaded = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(20, loaded.comments.size)
        assertTrue(loaded.canLoadMore)
        assertEquals(353, loaded.totalCount)
    }

    @Test
    fun `loadMoreComments fetches next server batch`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        every { interactor.expectedCommentsCount() } returns 55
        val firstBatch = (1..20).map { id -> Comment().apply { this.id = id; userNick = "u$id" } }
        val secondBatch = (21..40).map { id -> Comment().apply { this.id = id; userNick = "u$id" } }
        val firstTree = Comment().apply { children.addAll(firstBatch) }
        val mergedTree = Comment().apply { children.addAll(firstBatch + secondBatch) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                tree = firstTree,
                fromCache = false,
                hasMore = true,
                page = 1,
        )
        coEvery { interactor.loadCommentsNextPage() } returns ArticleInteractor.CommentLoadResult.Loaded(
                tree = mergedTree,
                fromCache = false,
                hasMore = true,
                append = true,
                page = 2,
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        val first = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(20, first.comments.size)
        assertTrue(first.canLoadMore)

        vm.loadMoreComments()
        advanceUntilIdle()

        val second = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(40, second.comments.size)
        assertEquals(20, second.appendFromIndex)
        assertTrue(second.canLoadMore)
        assertEquals(55, second.totalCount)
        coVerify(exactly = 1) { interactor.loadCommentsNextPage() }
    }

    @Test
    fun `opening article does not load comments`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val vm = vm(interactor)

        // No explicit request made.
        advanceUntilIdle()

        coVerify(exactly = 0) { interactor.loadComments(any()) }
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)
    }

    @Test
    fun `tap loads comments once and persists loaded state`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 1; userNick = "u" }) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 1) { interactor.loadComments(false) }

        // Collapse/expand should not trigger reload when already loaded.
        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        coVerify(exactly = 1) { interactor.loadComments(false) }
    }

    @Test
    fun `error then retry loads again`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        coEvery { interactor.loadComments(forceReload = false) } returnsMany listOf(
                ArticleInteractor.CommentLoadResult.Error(IllegalStateException("boom")),
                ArticleInteractor.CommentLoadResult.Empty("none")
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Error)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Empty)

        coVerify(exactly = 2) { interactor.loadComments(false) }
    }

    @Test
    fun `expand triggers load when not loaded`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 7; userNick = "u" }) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        val vm = vm(interactor)

        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)
        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 1) { interactor.loadComments(false) }
    }

    @Test
    fun `loaded state survives re-expand without network`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 3; userNick = "a" }) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)

        vm.loadCommentsIfNeeded()
        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 1) { interactor.loadComments(false) }
    }

    @Test
    fun `stale result retries once then allows reload`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 9 }) }
        coEvery { interactor.loadComments(forceReload = false) } returnsMany listOf(
                ArticleInteractor.CommentLoadResult.Stale,
                ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 2) { interactor.loadComments(false) }
    }

    @Test
    fun `article change resets loaded state`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 1 }) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        val vm = vm(interactor)
        vm.onArticleChanged(10)
        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        vm.onArticleChanged(20)
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)
    }

    @Test
    fun `loading times out after fifteen seconds`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true) {
            coEvery { loadComments(any()) } coAnswers {
                suspendCoroutine { }
            }
        }
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        runCurrent()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Loading)

        advanceTimeBy(15_001)
        runCurrent()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Error)
        val error = vm.commentsState.value as ArticleCommentsState.Error
        assertTrue(error.throwable is TimeoutException)
    }

    @Test
    fun `force reload retries after empty state`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 2; userNick = "u" }) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Empty("no_comments_source")
        coEvery { interactor.loadComments(forceReload = true) } returns ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Empty)

        vm.loadCommentsIfNeeded(forceReload = true)
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 1) { interactor.loadComments(false) }
        coVerify(exactly = 1) { interactor.loadComments(true) }
    }

    @Test
    fun `deferred comments source resets empty state for next expand`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val tree = Comment().apply { children.add(Comment().apply { id = 4; userNick = "u" }) }
        coEvery { interactor.loadComments(forceReload = false) } returnsMany listOf(
                ArticleInteractor.CommentLoadResult.Empty("no_comments_source"),
                ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = false)
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Empty)

        vm.onDeferredCommentsSourceAvailable(commentsCount = 1, hasCommentsSource = true)
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 2) { interactor.loadComments(false) }
    }

    @Test
    fun `empty result shows empty state`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Empty("no_comments_source")
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Empty)
    }

    @Test
    fun `loaded cache refresh keeps accumulated batch after load more`() = runTest(dispatcher) {
        val commentsFlow = MutableSharedFlow<Comment>(extraBufferCapacity = 4)
        val interactor = mockk<ArticleInteractor>(relaxed = true) {
            every { observeComments() } returns commentsFlow
            every { initData } returns ArticleInteractor.InitData(newsId = 1)
            every { takePendingScrollCommentId() } returns 0
            every { expectedCommentsCount() } returns 60
        }
        val router = mockk<TabRouter>(relaxed = true)
        val linkHandler = mockk<ILinkHandler>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true) {
            every { observe() } returns kotlinx.coroutines.flow.flowOf(mockk(relaxed = true))
        }
        val errorHandler = mockk<IErrorHandler>(relaxed = true)
        val vm = ArticleCommentViewModel(interactor, router, linkHandler, authHolder, errorHandler).also { it.start() }
        val firstTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = index + 1 }) }
        }
        val mergedTree = Comment().apply {
            repeat(40) { index -> children.add(Comment().apply { id = index + 1 }) }
        }
        coEvery { interactor.loadComments(forceReload = false) } coAnswers {
            commentsFlow.emit(firstTree)
            ArticleInteractor.CommentLoadResult.Loaded(firstTree, fromCache = false, hasMore = true, page = 1)
        }
        coEvery { interactor.loadCommentsNextPage() } coAnswers {
            commentsFlow.emit(mergedTree)
            ArticleInteractor.CommentLoadResult.Loaded(mergedTree, fromCache = false, hasMore = true, append = true, page = 2)
        }

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        vm.loadMoreComments()
        advanceUntilIdle()

        val beforeRefresh = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(40, beforeRefresh.comments.size)

        commentsFlow.emit(mergedTree)
        advanceUntilIdle()

        val afterRefresh = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(40, afterRefresh.comments.size)
        assertTrue(afterRefresh.canLoadMore)
        assertEquals(60, afterRefresh.totalCount)
    }

    @Test
    fun `loaded cache metadata refresh preserves appendFromIndex after load more`() = runTest(dispatcher) {
        val commentsFlow = MutableSharedFlow<Comment>(extraBufferCapacity = 4)
        val interactor = mockk<ArticleInteractor>(relaxed = true) {
            every { observeComments() } returns commentsFlow
            every { initData } returns ArticleInteractor.InitData(newsId = 1)
            every { takePendingScrollCommentId() } returns 0
            every { expectedCommentsCount() } returns 182
        }
        val vm = vm(interactor).also { it.start() }
        val firstTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = index + 1 }) }
        }
        val mergedTree = Comment().apply {
            repeat(40) { index -> children.add(Comment().apply { id = index + 1 }) }
        }
        coEvery { interactor.loadComments(forceReload = false) } coAnswers {
            commentsFlow.emit(firstTree)
            ArticleInteractor.CommentLoadResult.Loaded(firstTree, fromCache = false, hasMore = true, page = 1)
        }
        coEvery { interactor.loadCommentsNextPage() } coAnswers {
            commentsFlow.emit(mergedTree)
            ArticleInteractor.CommentLoadResult.Loaded(mergedTree, fromCache = false, hasMore = true, append = true, page = 2)
        }

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        vm.loadMoreComments()
        advanceUntilIdle()

        val before = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(40, before.comments.size)
        assertEquals(20, before.appendFromIndex)

        commentsFlow.emit(mergedTree)
        advanceUntilIdle()

        val after = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(40, after.comments.size)
        assertEquals(20, after.appendFromIndex)
        assertTrue(after.canLoadMore)
        assertEquals(182, after.totalCount)
    }

    @Test
    fun `publishVisibleComments keeps canLoadMore when server flag missing`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        every { interactor.expectedCommentsCount() } returns 21
        val children = (1..20).map { id ->
            Comment().apply { this.id = id; userNick = "u$id" }
        }
        val tree = Comment().apply { this.children.addAll(children) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                tree = tree,
                fromCache = false,
                hasMore = false,
                page = 1,
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        val loaded = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(20, loaded.comments.size)
        assertTrue(loaded.canLoadMore)
        assertEquals(21, loaded.totalCount)
    }

    @Test
    fun `loadMoreComments loads 20 then 20 then 15 for 55 total`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        every { interactor.expectedCommentsCount() } returns 55
        fun batch(startId: Int, count: Int) = (startId until startId + count).map { id ->
            Comment().apply { this.id = id; userNick = "u$id" }
        }
        val firstTree = Comment().apply { children.addAll(batch(1, 20)) }
        val secondTree = Comment().apply { children.addAll(batch(1, 40)) }
        val thirdTree = Comment().apply { children.addAll(batch(1, 55)) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                tree = firstTree,
                fromCache = false,
                hasMore = true,
                page = 1,
        )
        coEvery { interactor.loadCommentsNextPage() } returnsMany listOf(
                ArticleInteractor.CommentLoadResult.Loaded(
                        tree = secondTree,
                        fromCache = false,
                        hasMore = true,
                        append = true,
                        page = 2,
                ),
                ArticleInteractor.CommentLoadResult.Loaded(
                        tree = thirdTree,
                        fromCache = false,
                        hasMore = false,
                        append = true,
                        page = 3,
                ),
        )
        val vm = vm(interactor)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        val first = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(20, first.comments.size)
        assertTrue(first.canLoadMore)

        vm.loadMoreComments()
        advanceUntilIdle()
        val second = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(40, second.comments.size)
        assertTrue(second.canLoadMore)

        vm.loadMoreComments()
        advanceUntilIdle()
        val third = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(55, third.comments.size)
        assertFalse(third.canLoadMore)
        coVerify(exactly = 2) { interactor.loadCommentsNextPage() }
    }

    @Test
    fun `partial first batch keeps canLoadMore for paginated fetch`() = runTest(dispatcher) {
        val commentsFlow = MutableSharedFlow<Comment>(extraBufferCapacity = 4)
        val interactor = mockk<ArticleInteractor>(relaxed = true) {
            every { observeComments() } returns commentsFlow
            every { initData } returns ArticleInteractor.InitData(newsId = 1)
            every { takePendingScrollCommentId() } returns 0
            every { expectedCommentsCount() } returns 353
        }
        val router = mockk<TabRouter>(relaxed = true)
        val linkHandler = mockk<ILinkHandler>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true) {
            every { observe() } returns kotlinx.coroutines.flow.flowOf(mockk(relaxed = true))
        }
        val errorHandler = mockk<IErrorHandler>(relaxed = true)
        val vm = ArticleCommentViewModel(interactor, router, linkHandler, authHolder, errorHandler).also { it.start() }
        val partialTree = Comment().apply {
            repeat(10) { index -> children.add(Comment().apply { id = index + 1 }) }
        }

        commentsFlow.emit(partialTree)
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)

        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                partialTree,
                fromCache = false,
                hasMore = true,
                page = 1,
        )
        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        val loadedPartial = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(10, loadedPartial.comments.size)
        assertTrue(loadedPartial.canLoadMore)
        assertEquals(353, loadedPartial.totalCount)
    }

    @Test
    fun `stale full-tree replay does not expand visible batch`() = runTest(dispatcher) {
        val commentsFlow = MutableSharedFlow<Comment>(replay = 1, extraBufferCapacity = 1)
        val interactor = mockk<ArticleInteractor>(relaxed = true) {
            every { observeComments() } returns commentsFlow
            every { initData } returns ArticleInteractor.InitData(newsId = 1)
            every { takePendingScrollCommentId() } returns 0
            every { expectedCommentsCount() } returns 353
        }
        val vm = vm(interactor).also { it.start() }
        val firstTree = Comment().apply {
            repeat(20) { index -> children.add(Comment().apply { id = index + 1 }) }
        }
        val staleFullTree = Comment().apply {
            repeat(353) { index -> children.add(Comment().apply { id = index + 1 }) }
        }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                firstTree,
                fromCache = false,
                hasMore = true,
                page = 1,
        )

        commentsFlow.emit(staleFullTree)
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        val loaded = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(20, loaded.comments.size)
        assertTrue(loaded.canLoadMore)

        commentsFlow.emit(staleFullTree)
        advanceUntilIdle()

        val afterReplay = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(20, afterReplay.comments.size)
        assertTrue(afterReplay.canLoadMore)
    }

    @Test
    fun `deep link reveal only on first comments emission`() = runTest(dispatcher) {
        val commentsFlow = kotlinx.coroutines.flow.MutableSharedFlow<Comment>(extraBufferCapacity = 2)
        val interactor = mockk<ArticleInteractor>(relaxed = true) {
            every { observeComments() } returns commentsFlow
            every { initData } returns ArticleInteractor.InitData(newsId = 1, commentId = 42)
            every { takePendingScrollCommentId() } returns 0
        }
        val router = mockk<TabRouter>(relaxed = true)
        val linkHandler = mockk<ILinkHandler>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true) {
            every { observe() } returns kotlinx.coroutines.flow.flowOf(mockk(relaxed = true))
        }
        val errorHandler = mockk<IErrorHandler>(relaxed = true)
        val vm = ArticleCommentViewModel(interactor, router, linkHandler, authHolder, errorHandler).also { it.start() }
        val events = mutableListOf<ArticleCommentUiEvent>()
        val job = launch { vm.uiEvents.collect { events.add(it) } }
        advanceUntilIdle()
        val tree = Comment().apply { children.add(Comment().apply { id = 42 }) }

        commentsFlow.emit(tree)
        advanceUntilIdle()
        assertTrue(events.any { it is ArticleCommentUiEvent.ShowComments && it.revealSection })

        events.clear()
        val updatedTree = Comment().apply {
            children.add(Comment().apply { id = 42 })
            children.add(Comment().apply { id = 43 })
        }
        commentsFlow.emit(updatedTree)
        advanceUntilIdle()
        job.cancel()

        assertFalse(events.any { it is ArticleCommentUiEvent.ShowComments && it.revealSection })
    }
}

