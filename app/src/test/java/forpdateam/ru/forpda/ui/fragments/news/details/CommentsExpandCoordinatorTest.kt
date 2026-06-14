package forpdateam.ru.forpda.ui.fragments.news.details

import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommentsExpandCoordinatorTest {

    private var clock = 0L
    private val coordinator = CommentsExpandCoordinator(nowMs = { clock })

    @Before
    fun setUp() {
        clock = 0L
        coordinator.resetForArticle(0, 0)
    }

    private fun readyEnv(
            articleId: Int = 457203,
            hint: Int = 10,
            footerInDom: Boolean = true,
            generation: Int = 7,
    ) = CommentsExpandCoordinator.Environment(
            webViewReady = true,
            bridgeReady = true,
            domReady = true,
            footerInDom = footerInDom,
            commentsJsReady = true,
            webViewGeneration = generation,
            articleId = articleId,
            commentsCountHint = hint,
    )

    private fun withReadyEnv(
            block: () -> List<CommentsExpandCoordinator.Action>,
            footerInDom: Boolean = true,
            articleId: Int = 457203,
            hint: Int = 10,
            generation: Int = 7,
    ): List<CommentsExpandCoordinator.Action> {
        clock += 1_000L
        coordinator.updateEnvironment(readyEnv(articleId = articleId, hint = hint, footerInDom = footerInDom, generation = generation))
        return block()
    }

    @Test
    fun `tap while collapsed queues until footer bound`() {
        coordinator.resetForArticle(457203, 7)
        val queued = withReadyEnv({ coordinator.userTapExpand("tap") }, footerInDom = false)

        assertTrue(queued.any { it is CommentsExpandCoordinator.Action.QueueExpand })
        assertTrue(queued.any { it is CommentsExpandCoordinator.Action.MountFooter })
        assertFalse(coordinator.current().collapsed)

        val flushed = coordinator.onFooterBound()
        assertTrue(flushed.any { it is CommentsExpandCoordinator.Action.StartLoad })
        assertEquals(null, coordinator.current().pendingExpandSource)
    }

    @Test
    fun `debounced duplicate tap is ignored`() {
        coordinator.resetForArticle(1, 1)
        coordinator.syncVmState(ArticleCommentsState.NotLoaded)
        coordinator.updateEnvironment(readyEnv(articleId = 1, hint = 0))
        clock = 1_000L
        coordinator.userTapExpand("tap")
        clock = 1_100L
        val second = coordinator.userTapExpand("tap")

        assertTrue(second.any { it is CommentsExpandCoordinator.Action.Ignore })
    }

    @Test
    fun `loaded vm reinjects without reload`() {
        coordinator.resetForArticle(457203, 9)
        val loaded = ArticleCommentsState.Loaded(
                comments = listOf(Comment().apply { id = 1 }),
                canLoadMore = false,
                totalCount = 1,
        )
        coordinator.syncVmState(loaded)

        val actions = withReadyEnv({ coordinator.userTapExpand("tap") }, hint = 1, generation = 9)

        assertFalse(actions.any { it is CommentsExpandCoordinator.Action.StartLoad })
        assertTrue(
                actions.any {
                    it is CommentsExpandCoordinator.Action.InjectLoadedComments ||
                            it is CommentsExpandCoordinator.Action.AppendLoadedComments
                }
        )
        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.VerifyDom })
    }

    @Test
    fun `empty vm with hint forces reload on expand`() {
        coordinator.resetForArticle(457203, 3)
        coordinator.syncVmState(ArticleCommentsState.Empty)

        val actions = withReadyEnv({ coordinator.userTapExpand("tap") }, hint = 10, generation = 3)

        val start = actions.filterIsInstance<CommentsExpandCoordinator.Action.StartLoad>().single()
        assertTrue(start.forceReload)
    }

    @Test
    fun `empty vm without hint forces reload on expand`() {
        coordinator.resetForArticle(457203, 3)
        coordinator.syncVmState(ArticleCommentsState.Empty)

        val actions = withReadyEnv({ coordinator.userTapExpand("tap") }, hint = 0, generation = 3)

        val start = actions.filterIsInstance<CommentsExpandCoordinator.Action.StartLoad>().single()
        assertTrue(start.forceReload)
    }

    @Test
    fun `deferred not loaded sync while expanded starts load`() {
        coordinator.resetForArticle(11, 4)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 11, hint = 0, generation = 4)
        coordinator.syncVmState(ArticleCommentsState.Loading(1))
        coordinator.syncVmState(ArticleCommentsState.Empty)

        val actions = coordinator.syncVmState(ArticleCommentsState.NotLoaded)

        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.StartLoad })
    }

    @Test
    fun `prefetch vm sync does not expand collapsed section`() {
        coordinator.resetForArticle(11, 2)
        withReadyEnv({ emptyList() }, articleId = 11, hint = 11, generation = 2)

        val actions = coordinator.syncVmState(
                ArticleCommentsState.Loaded(
                        comments = List(11) { Comment().apply { id = it + 1 } },
                        canLoadMore = false,
                        totalCount = 11,
                )
        )

        assertTrue(actions.isEmpty())
        assertTrue(coordinator.current().collapsed)
        assertTrue(coordinator.shouldAllowPrefetch())
    }

    @Test
    fun `vm sync while expanded injects loaded comments`() {
        coordinator.resetForArticle(11, 4)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 11, hint = 11, generation = 4)
        coordinator.syncVmState(ArticleCommentsState.Loading(1))

        val actions = coordinator.syncVmState(
                ArticleCommentsState.Loaded(
                        comments = List(11) { Comment().apply { id = it + 1 } },
                        canLoadMore = false,
                        totalCount = 11,
                )
        )

        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
        assertEquals(5, coordinator.current().injectGeneration)
    }

    @Test
    fun `inject generation is monotonic per inject`() {
        coordinator.resetForArticle(11, 4)
        assertEquals(5, coordinator.nextInjectGeneration())
        assertEquals(6, coordinator.nextInjectGeneration())
        assertTrue(coordinator.isInjectGenerationCurrent(6))
        assertFalse(coordinator.isInjectGenerationCurrent(5))
    }

    @Test
    fun `error state reloads only on explicit expand source`() {
        coordinator.resetForArticle(1, 1)
        coordinator.syncVmState(ArticleCommentsState.Error(IllegalStateException("x")))

        val silent = withReadyEnv({ coordinator.syncVmState(ArticleCommentsState.Error(IllegalStateException("x"))) }, articleId = 1, hint = 0)
        assertTrue(silent.none { it is CommentsExpandCoordinator.Action.StartLoad })

        val retry = withReadyEnv({ coordinator.userTapExpand("toggle_expand") }, articleId = 1, hint = 0)
        assertTrue(retry.any { it is CommentsExpandCoordinator.Action.StartLoad })
    }

    @Test
    fun `bind section uses coordinator collapsed flag`() {
        coordinator.resetForArticle(1, 1)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 1, hint = 0)

        val bind = coordinator.bindSectionAction()
        assertFalse(bind.collapsed)
    }

    @Test
    fun `webview reload while expanded queues reinject after dom ready`() {
        coordinator.resetForArticle(11, 3)
        val loaded = ArticleCommentsState.Loaded(
                comments = listOf(Comment().apply { id = 1 }),
                canLoadMore = false,
                totalCount = 1,
        )
        coordinator.syncVmState(loaded)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 11, hint = 1, generation = 3)
        coordinator.syncVmState(loaded)

        coordinator.onWebViewLoadStarted(4)
        assertEquals("webview_reload", coordinator.current().pendingExpandSource)
        assertFalse(coordinator.current().env.footerInDom)

        coordinator.updateEnvironment(readyEnv(articleId = 11, hint = 1, footerInDom = false, generation = 4))
        val domReady = coordinator.onArticleDomReady(4)
        assertTrue(domReady.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
        assertEquals(null, coordinator.current().pendingExpandSource)

        val afterFooter = coordinator.onFooterBound()
        assertTrue(afterFooter.isEmpty() || afterFooter.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
    }

    @Test
    fun `dom expand sync loads when coordinator still collapsed`() {
        coordinator.resetForArticle(5, 2)
        coordinator.syncVmState(ArticleCommentsState.NotLoaded)
        coordinator.updateEnvironment(readyEnv(articleId = 5, hint = 3, generation = 2))

        val actions = coordinator.onNativeCollapsedSync(collapsed = false)

        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.StartLoad })
        assertFalse(coordinator.current().collapsed)
    }

    @Test
    fun `vm sync while expanded updates empty after loading`() {
        coordinator.resetForArticle(11, 4)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 11, hint = 0, generation = 4)
        coordinator.syncVmState(ArticleCommentsState.Loading(1))

        val actions = coordinator.syncVmState(ArticleCommentsState.Empty)

        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.RenderVmState })
        assertTrue(
                (actions.filterIsInstance<CommentsExpandCoordinator.Action.RenderVmState>().single().vmState
                        is ArticleCommentsState.Empty)
        )
    }

    @Test
    fun `vm sync with metadata only does not reinject`() {
        coordinator.resetForArticle(12, 5)
        val loaded = ArticleCommentsState.Loaded(
                comments = List(20) { Comment().apply { id = it + 1 } },
                canLoadMore = true,
                totalCount = 60,
        )
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 12, hint = 60, generation = 5)
        coordinator.syncVmState(loaded)

        val metadataOnly = loaded.copy(totalCount = 60, canLoadMore = true)
        val actions = coordinator.syncVmState(metadataOnly)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `vm sync appendFromIndex reset without comment change does not reinject`() {
        coordinator.resetForArticle(12, 5)
        val firstPage = ArticleCommentsState.Loaded(
                comments = List(40) { Comment().apply { id = it + 1 } },
                canLoadMore = true,
                totalCount = 414,
                appendFromIndex = 20,
        )
        coordinator.syncVmState(firstPage)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 12, hint = 414, generation = 5)

        val metadataOnly = firstPage.copy(totalCount = 414, canLoadMore = true, appendFromIndex = 0)
        val actions = coordinator.syncVmState(metadataOnly)

        assertTrue(actions.isEmpty())
    }

    @Test
    fun `vm sync with appendFromIndex emits append action`() {
        coordinator.resetForArticle(12, 5)
        val firstPage = ArticleCommentsState.Loaded(
                comments = List(20) { Comment().apply { id = it + 1 } },
                canLoadMore = true,
                totalCount = 60,
        )
        coordinator.syncVmState(firstPage)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 12, hint = 60, generation = 5)

        val nextPage = firstPage.copy(
                comments = List(40) { Comment().apply { id = it + 1 } },
                appendFromIndex = 20,
        )
        val actions = coordinator.syncVmState(nextPage)

        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.AppendLoadedComments })
        assertFalse(actions.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
    }

    @Test
    fun `inject retry while expanded reinjects loaded comments`() {
        coordinator.resetForArticle(9, 6)
        val loaded = ArticleCommentsState.Loaded(
                comments = List(3) { Comment().apply { id = it + 1 } },
                canLoadMore = false,
                totalCount = 3,
        )
        coordinator.syncVmState(loaded)
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 9, hint = 3, generation = 6)

        val retry = coordinator.injectRetryIfExpanded()
        assertTrue(retry.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
    }

    @Test
    fun `expand starts load before comments js is ready`() {
        coordinator.resetForArticle(457203, 7)
        coordinator.syncVmState(ArticleCommentsState.NotLoaded)
        clock += 1_000L
        coordinator.updateEnvironment(
                CommentsExpandCoordinator.Environment(
                        webViewReady = true,
                        bridgeReady = true,
                        domReady = true,
                        footerInDom = true,
                        commentsJsReady = false,
                        webViewGeneration = 7,
                        articleId = 457203,
                        commentsCountHint = 10,
                )
        )

        val actions = coordinator.userTapExpand("tap")

        assertTrue(actions.any { it is CommentsExpandCoordinator.Action.StartLoad })
        assertFalse(actions.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
    }

    @Test
    fun `load requested while expanded bypasses expand debounce`() {
        coordinator.resetForArticle(1, 1)
        coordinator.syncVmState(ArticleCommentsState.Error(IllegalStateException("x")))
        withReadyEnv({ coordinator.userTapExpand("tap") }, articleId = 1, hint = 0)
        clock = 1_100L
        val debounced = coordinator.userTapExpand("load_requested")
        assertTrue(debounced.any { it is CommentsExpandCoordinator.Action.Ignore })

        val load = coordinator.userRequestLoad("load_requested")
        assertTrue(load.any { it is CommentsExpandCoordinator.Action.StartLoad })
    }

    @Test
    fun `loaded inject waits until comments js is ready`() {
        coordinator.resetForArticle(457203, 7)
        val loaded = ArticleCommentsState.Loaded(
                comments = listOf(Comment().apply { id = 1 }),
                canLoadMore = false,
                totalCount = 1,
        )
        coordinator.syncVmState(loaded)
        clock += 1_000L
        coordinator.updateEnvironment(
                CommentsExpandCoordinator.Environment(
                        webViewReady = true,
                        bridgeReady = true,
                        domReady = true,
                        footerInDom = true,
                        commentsJsReady = false,
                        webViewGeneration = 7,
                        articleId = 457203,
                        commentsCountHint = 1,
                )
        )

        val expand = coordinator.userTapExpand("tap")
        assertFalse(expand.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })

        val afterJsReady = coordinator.updateEnvironment(
                CommentsExpandCoordinator.Environment(
                        webViewReady = true,
                        bridgeReady = true,
                        domReady = true,
                        footerInDom = true,
                        commentsJsReady = true,
                        webViewGeneration = 7,
                        articleId = 457203,
                        commentsCountHint = 1,
                )
        )

        assertTrue(afterJsReady.any { it is CommentsExpandCoordinator.Action.InjectLoadedComments })
    }

    @Test
    fun `expand reloads when loaded state underfetches comment count hint without canLoadMore`() {
        coordinator.resetForArticle(457253, 7)
        val underfetched = ArticleCommentsState.Loaded(
                comments = List(10) { Comment().apply { id = it + 1 } },
                canLoadMore = false,
                totalCount = 10,
        )
        coordinator.syncVmState(underfetched)
        val actions = withReadyEnv(
                { coordinator.userTapExpand("tap") },
                articleId = 457253,
                hint = 353,
        )

        val startLoad = actions.filterIsInstance<CommentsExpandCoordinator.Action.StartLoad>().single()
        assertTrue(startLoad.forceReload)
    }

    @Test
    fun `expand does not reload when paginated canLoadMore is true`() {
        coordinator.resetForArticle(457253, 7)
        val firstBatch = ArticleCommentsState.Loaded(
                comments = List(10) { Comment().apply { id = it + 1 } },
                canLoadMore = true,
                totalCount = 353,
        )
        coordinator.syncVmState(firstBatch)
        val actions = withReadyEnv(
                { coordinator.userTapExpand("tap") },
                articleId = 457253,
                hint = 353,
        )
        assertFalse(actions.any { it is CommentsExpandCoordinator.Action.StartLoad })
    }
}
