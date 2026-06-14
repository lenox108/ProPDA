package forpdateam.ru.forpda.presentation.articles.detail.comments

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.entity.remote.news.CommentKarmaVoteResult
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleCommentViewModelLikeTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleLikeComment applies vote without reloading comments`() = runTest(dispatcher) {
        val interactor = mockk<ArticleInteractor>(relaxed = true)
        every { interactor.observeComments() } returns emptyFlow()
        every { interactor.initData } returns ArticleInteractor.InitData(newsId = 457355)
        every { interactor.expectedCommentsCount() } returns 3
        val router = mockk<TabRouter>(relaxed = true)
        val linkHandler = mockk<ILinkHandler>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true) {
            every { observe() } returns kotlinx.coroutines.flow.flowOf(mockk(relaxed = true))
            every { get() } returns mockk(relaxed = true)
        }
        val errorHandler = mockk<IErrorHandler>(relaxed = true)
        val comment = Comment().apply {
            id = 10614341
            likeAction = Comment.Action(
                    url = "https://4pda.to/pages/karma?p=457355&c=10614341&v=1",
                    type = Comment.Action.Type.COMMENT_LIKE,
            )
        }
        coEvery { interactor.voteComment(any()) } returns CommentKarmaVoteResult(
                commentId = 10614341,
                karma = Comment.Karma().apply {
                    status = Comment.Karma.LIKED
                    count = 1
                },
        )
        coEvery { interactor.loadComments(forceReload = false) } returns ArticleInteractor.CommentLoadResult.Loaded(
                tree = Comment().apply { children.add(comment) },
                fromCache = false,
        )
        val vm = ArticleCommentViewModel(interactor, router, linkHandler, authHolder, errorHandler).also {
            it.start()
            it.onArticleChanged(457355)
        }
        vm.loadCommentsIfNeeded()
        advanceUntilIdle()
        val events = mutableListOf<ArticleCommentUiEvent>()
        val job = launch { vm.uiEvents.collect { events.add(it) } }

        vm.toggleLikeComment(comment)
        advanceUntilIdle()

        val loaded = vm.commentsState.value as ArticleCommentsState.Loaded
        assertTrue(loaded.comments.single().likedByMe)
        assertEquals(1, loaded.comments.single().likeCount)
        coVerify(exactly = 1) { interactor.voteComment(any()) }
        coVerify(exactly = 0) { interactor.loadComments(forceReload = true) }
        assertTrue(events.filterIsInstance<ArticleCommentUiEvent.UpdateCommentLike>().any { it.likedByMe })
        job.cancel()
    }
}
