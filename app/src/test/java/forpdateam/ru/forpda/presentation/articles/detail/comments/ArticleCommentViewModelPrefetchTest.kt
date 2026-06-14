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
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleCommentViewModelPrefetchTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(interactor: ArticleInteractor, commentsFlow: MutableSharedFlow<Comment>? = null): ArticleCommentViewModel {
        val flow = commentsFlow ?: MutableSharedFlow(replay = 1, extraBufferCapacity = 1)
        every { interactor.observeComments() } returns flow
        every { interactor.initData } returns ArticleInteractor.InitData(newsId = 1)
        every { interactor.takePendingScrollCommentId() } returns 0
        val router = mockk<TabRouter>(relaxed = true)
        val linkHandler = mockk<ILinkHandler>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true) {
            every { observe() } returns kotlinx.coroutines.flow.flowOf(mockk(relaxed = true))
        }
        val errorHandler = mockk<IErrorHandler>(relaxed = true)
        return ArticleCommentViewModel(interactor, router, linkHandler, authHolder, errorHandler).also {
            it.start()
        }
    }

    @Test
    fun `prefetch is no-op and does not call interactor`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val vm = vm(interactor)

        vm.prefetchCommentsIfNeeded("article_ready")
        advanceUntilIdle()

        verify(exactly = 0) { interactor.prefetchCommentsIfNeeded(any()) }
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)
    }

    @Test
    fun `background comment flow does not flip UI to loaded before expand`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val commentsFlow = MutableSharedFlow<Comment>(replay = 1, extraBufferCapacity = 1)
        val vm = vm(interactor, commentsFlow)
        val tree = Comment().apply { children.add(Comment().apply { id = 5 }) }

        commentsFlow.emit(tree)
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)
        coVerify(exactly = 0) { interactor.loadComments(any()) }
    }

    @Test
    fun `tap loads comments after silent tree cache`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val commentsFlow = MutableSharedFlow<Comment>(replay = 1, extraBufferCapacity = 1)
        val tree = Comment().apply { children.add(Comment().apply { id = 2 }) }
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(tree, fromCache = true)
        val vm = vm(interactor, commentsFlow)

        commentsFlow.emit(tree)
        advanceUntilIdle()
        assertTrue(vm.commentsState.value is ArticleCommentsState.NotLoaded)

        vm.loadCommentsIfNeeded()
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        coVerify(exactly = 1) { interactor.loadComments(false) }
    }

    @Test
    fun `loading transitions to loaded when tree arrives during active load`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        val commentsFlow = MutableSharedFlow<Comment>(replay = 0, extraBufferCapacity = 1)
        coEvery { interactor.loadComments(any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        val vm = vm(interactor, commentsFlow)

        vm.loadCommentsIfNeeded(forceReload = true)
        runCurrent()
        assertTrue(vm.commentsState.value is ArticleCommentsState.Loading)

        val tree = Comment().apply {
            repeat(InlineCommentsBatchConfig.BATCH_SIZE) { index ->
                children.add(Comment().apply { id = index + 1; content = "c$index" })
            }
        }
        commentsFlow.emit(tree)
        advanceUntilIdle()

        assertTrue(vm.commentsState.value is ArticleCommentsState.Loaded)
        val loaded = vm.commentsState.value as ArticleCommentsState.Loaded
        assertEquals(InlineCommentsBatchConfig.BATCH_SIZE, loaded.comments.size)
    }
}
