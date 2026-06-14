package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression: topic 123 hybrid-visible page 10 must not affect topic 456 open or «В конец темы».
 */
class ThemeTopicSwitchPolicyTest {

    private val paginationController = ThemePaginationController()

    @Test
    fun needsTopicSwitchReset_detectsCrossTopicNavigation() {
        assertTrue(ThemeViewModel.needsTopicSwitchReset(incomingTopicId = 456, previouslyLoadedTopicId = 123))
        assertFalse(ThemeViewModel.needsTopicSwitchReset(incomingTopicId = 123, previouslyLoadedTopicId = 123))
        assertFalse(ThemeViewModel.needsTopicSwitchReset(incomingTopicId = 456, previouslyLoadedTopicId = null))
        assertFalse(ThemeViewModel.needsTopicSwitchReset(incomingTopicId = null, previouslyLoadedTopicId = 123))
    }

    @Test
    fun freshSameTopicOpen_requiresPreResolveReset() {
        assertTrue(
                ThemeViewModel.isFreshSameTopicOpen(
                        incomingTopicId = 123,
                        previouslyLoadedTopicId = 123,
                        hasLoadedPage = true,
                        isFreshOpen = true
                )
        )
        assertFalse(
                ThemeViewModel.isFreshSameTopicOpen(
                        incomingTopicId = 123,
                        previouslyLoadedTopicId = 123,
                        hasLoadedPage = true,
                        isFreshOpen = false
                )
        )
        assertFalse(
                ThemeViewModel.isFreshSameTopicOpen(
                        incomingTopicId = 123,
                        previouslyLoadedTopicId = 456,
                        hasLoadedPage = true,
                        isFreshOpen = true
                )
        )
    }

    @Test
    fun shouldClearToolbarOnTopicSwitch_matchesCrossTopicNavigation() {
        assertTrue(ThemeViewModel.shouldClearToolbarOnTopicSwitch(456, 123))
        assertFalse(ThemeViewModel.shouldClearToolbarOnTopicSwitch(123, 123))
        assertFalse(ThemeViewModel.shouldClearToolbarOnTopicSwitch(456, null))
    }

    @Test
    fun shouldApplyToolbarPage_rejectsStaleTopicWhileLoadingAnother() {
        assertFalse(
                ThemeViewModel.shouldApplyToolbarPage(
                        pageTopicId = 123,
                        targetTopicId = 456,
                        loadedTopicId = null
                )
        )
        assertTrue(
                ThemeViewModel.shouldApplyToolbarPage(
                        pageTopicId = 456,
                        targetTopicId = 456,
                        loadedTopicId = null
                )
        )
        assertTrue(
                ThemeViewModel.shouldApplyToolbarPage(
                        pageTopicId = 123,
                        targetTopicId = 456,
                        loadedTopicId = 123
                )
        )
    }

    @Test
    fun crossTopicLoad_detectsDifferentTopicIds() {
        assertTrue(ThemeViewModel.isCrossTopicLoad(requestedTopicId = 456, loadedTopicId = 123))
        assertFalse(ThemeViewModel.isCrossTopicLoad(requestedTopicId = 123, loadedTopicId = 123))
        assertFalse(ThemeViewModel.isCrossTopicLoad(requestedTopicId = 456, loadedTopicId = null))
    }

    /**
     * [ThemeViewModel.loadUrl] clears [ThemeViewModel.currentPage] before [ThemeViewModel.loadData],
     * so [ThemeViewModel.isCrossTopicLoad] is often false during loadData even on cross-topic navigation.
     * Hat overlay reset must therefore live in [ThemeViewModel.resetTransientStateForNewTopic], not only
     * in the loadData crossTopicLoad branch.
     */
    @Test
    fun loadDataCrossTopicLoad_missesAfterLoadUrlClearsCurrentPage() {
        assertTrue(ThemeViewModel.needsTopicSwitchReset(incomingTopicId = 456, previouslyLoadedTopicId = 123))
        assertFalse(ThemeViewModel.isCrossTopicLoad(requestedTopicId = 456, loadedTopicId = null))
    }

    @Test
    fun inTopicLinkCrossTopic_preservesHistoryStack() {
        assertTrue(ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen("in_topic_link"))
        assertFalse(ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen("favorites"))
        assertFalse(ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen("topics"))
        assertFalse(ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen("unknown"))
    }

    @Test
    fun inTopicLinkSameTopicFreshOpen_preservesHistoryOnReset() {
        assertTrue(
                ThemeViewModel.isFreshSameTopicOpen(
                        incomingTopicId = 601691,
                        previouslyLoadedTopicId = 601691,
                        hasLoadedPage = true,
                        isFreshOpen = true
                )
        )
        assertTrue(ThemeViewModel.shouldPreserveHistoryOnCrossTopicOpen("in_topic_link"))
    }

    @Test
    fun hatOpenForLoad_usesOverrideOnlyWhileSessionRetainsIt() {
        assertFalse(ThemeViewModel.hatOpenForLoad(userHatOpenOverride = null))
        assertTrue(ThemeViewModel.hatOpenForLoad(userHatOpenOverride = true))
        assertFalse(ThemeViewModel.hatOpenForLoad(userHatOpenOverride = false))
    }

    @Test
    fun visiblePageUpdate_rejectsStaleHybridPageWhileNewTopicLoaded() {
        assertFalse(
                ThemeViewModel.shouldAcceptVisiblePageUpdate(
                        pageNumber = 10,
                        loadedPages = setOf(2),
                        loadInFlight = false
                )
        )
        assertTrue(
                ThemeViewModel.shouldAcceptVisiblePageUpdate(
                        pageNumber = 2,
                        loadedPages = setOf(2),
                        loadInFlight = false
                )
        )
    }

    @Test
    fun visiblePageUpdate_ignoredWhileLoadInFlight() {
        assertFalse(
                ThemeViewModel.shouldAcceptVisiblePageUpdate(
                        pageNumber = 1,
                        loadedPages = emptySet(),
                        loadInFlight = true
                )
        )
    }

    @Test
    fun renderedReadCommit_explicitPostVisibleOnRenderedPage() {
        assertTrue(
                ThemeViewModel.shouldCommitRenderedRead(
                        pendingTopicId = 123,
                        pendingPostId = 456,
                        pendingPage = 3,
                        topicId = 123,
                        postId = 456,
                        page = 3
                )
        )
        assertFalse(
                ThemeViewModel.shouldCommitRenderedRead(
                        pendingTopicId = 123,
                        pendingPostId = 456,
                        pendingPage = 3,
                        topicId = 123,
                        postId = null,
                        page = 3
                )
        )
    }

    @Test
    fun renderedReadCommit_requiresCurrentTargetAck() {
        assertTrue(
                ThemeViewModel.shouldCommitRenderedRead(
                        pendingTopicId = 123,
                        pendingPostId = 456,
                        pendingPage = 3,
                        topicId = 123,
                        postId = 456,
                        page = 3
                )
        )
        assertFalse(
                ThemeViewModel.shouldCommitRenderedRead(
                        pendingTopicId = 123,
                        pendingPostId = 456,
                        pendingPage = 3,
                        topicId = 123,
                        postId = 999,
                        page = 3
                )
        )
        assertFalse(
                ThemeViewModel.shouldCommitRenderedRead(
                        pendingTopicId = 123,
                        pendingPostId = null,
                        pendingPage = 3,
                        topicId = 123,
                        postId = null,
                        page = 2
                )
        )
    }

    @Test
    fun secondTopicOpen_resolvesGetNewPostAndEmptyVisiblePagination() {
        val resolution = TopicOpenTargetResolver.resolve(
                TopicOpenContext(
                        rawUrl = "https://4pda.to/forum/index.php?showtopic=456&st=200",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "topics",
                        unreadUrlFromList = "https://4pda.to/forum/index.php?showtopic=123&view=getnewpost"
                )
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=456&view=getnewpost",
                resolution.url
        )
        assertTrue(resolution.suppressScrollRestore)

        val staleVisible = 10
        val loadedPages = setOf(2)
        val state = ThemePaginationState(
                currentPage = 2,
                allPages = 8,
                perPage = 20,
                isForum = true,
                visiblePage = staleVisible,
                loadedPages = loadedPages
        )
        assertEquals(2, state.activePage)
        val endTransition = paginationController.lastPage(state)
        assertTrue(endTransition is ThemePageTransition.LoadSt)
    }
}
