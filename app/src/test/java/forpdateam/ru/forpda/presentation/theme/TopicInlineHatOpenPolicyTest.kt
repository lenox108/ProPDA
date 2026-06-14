package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicInlineHatOpenPolicyTest {

    private val page1Url = "https://4pda.to/forum/index.php?showtopic=1103268"
    private val deepUrl = "https://4pda.to/forum/index.php?showtopic=1103268&st=24240"
    private val topicId = 1103268

    @Test
    fun `FIRST_PAGE fresh open page 1 EXPANDED expands inline hat`() {
        assertTrue(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "favorites",
                        currentPage = null,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `FIRST_PAGE fresh open page 1 COLLAPSED keeps inline hat closed`() {
        assertFalse(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.COLLAPSED,
                        sourceScreen = "favorites",
                        currentPage = null,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `LAST_UNREAD fresh open page 1 EXPANDED expands inline hat`() {
        assertTrue(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "favorites",
                        currentPage = null,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `LAST_UNREAD pagination to page 1 EXPANDED keeps inline hat collapsed`() {
        val current = ThemePage().apply {
            id = topicId
            isInlineHatOpen = false
        }
        assertFalse(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "pagination",
                        currentPage = current,
                        preserveInSession = true,
                )
        )
    }

    @Test
    fun `LAST_UNREAD pagination to page 1 COLLAPSED keeps inline hat collapsed`() {
        val current = ThemePage().apply {
            id = topicId
            isInlineHatOpen = false
        }
        assertFalse(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.COLLAPSED,
                        sourceScreen = "pagination",
                        currentPage = current,
                        preserveInSession = true,
                )
        )
    }

    @Test
    fun `FIRST_PAGE pagination to page 1 EXPANDED still expands inline hat`() {
        val current = ThemePage().apply {
            id = topicId
            isInlineHatOpen = false
        }
        assertTrue(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "pagination",
                        currentPage = current,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `does not open inline hat for deep page loads`() {
        assertFalse(
                shouldOpen(
                        url = deepUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "pagination",
                        currentPage = null,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `does not open inline hat for unread navigation urls`() {
        assertFalse(
                shouldOpen(
                        url = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "favorites",
                        currentPage = null,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `fresh open uses expanded setting even when current page has collapsed inline hat`() {
        val current = ThemePage().apply {
            id = topicId
            isInlineHatOpen = false
        }
        assertTrue(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "favorites",
                        currentPage = current,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `fresh open uses collapsed setting even when current page has expanded inline hat`() {
        val current = ThemePage().apply {
            id = topicId
            isInlineHatOpen = true
        }
        assertFalse(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.COLLAPSED,
                        sourceScreen = "favorites",
                        currentPage = current,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `fresh open from another topic uses global setting not stale current page`() {
        val otherTopic = ThemePage().apply {
            id = 999
            isInlineHatOpen = false
        }
        assertTrue(
                shouldOpen(
                        url = page1Url,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.EXPANDED,
                        sourceScreen = "favorites",
                        currentPage = otherTopic,
                        preserveInSession = false,
                )
        )
    }

    @Test
    fun `does not render inline hat block on deep page`() {
        val page = ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=80"
            pagination.current = 5
            topicHatPost = ThemePost().apply { id = 100; number = 1 }
            isInlineHatOpen = true
        }
        assertFalse(TopicInlineHatOpenPolicy.shouldRenderInlineBlock(page))
    }

    @Test
    fun `renders inline hat block when collapsed on page 1`() {
        val page = ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10"
            pagination.current = 1
            topicHatPost = ThemePost().apply { id = 100; number = 1 }
            isInlineHatOpen = false
        }
        assertTrue(TopicInlineHatOpenPolicy.shouldRenderInlineBlock(page))
    }

    @Test
    fun `does not render inline hat when floating overlay is open`() {
        val page = ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10"
            pagination.current = 1
            topicHatPost = ThemePost().apply { id = 100; number = 1 }
            isInlineHatOpen = true
            isHatOpen = true
        }
        assertFalse(TopicInlineHatOpenPolicy.shouldRenderInlineBlock(page))
    }

    @Test
    fun `preserves inline hat state when reloading same topic in session`() {
        val current = ThemePage().apply {
            id = topicId
            isInlineHatOpen = true
        }
        assertTrue(
                shouldOpen(
                        url = deepUrl,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        headerState = AppPreferences.Main.TopicHeaderInitialState.COLLAPSED,
                        sourceScreen = "pagination",
                        currentPage = current,
                        preserveInSession = true,
                )
        )
    }

    @Test
    fun `pagination page 1 url is treated as first page`() {
        assertTrue(TopicInlineHatOpenPolicy.isFirstPageTopicUrl(page1Url))
    }

    @Test
    fun `resolveOpenStateForLoad uses expanded setting when no stored preference`() {
        assertTrue(
                TopicInlineHatOpenPolicy.resolveOpenStateForLoad(
                        storedOpen = false,
                        hasStoredPreference = false,
                        initialFromSetting = true,
                )
        )
    }

    @Test
    fun `resolveOpenStateForLoad uses collapsed setting when no stored preference`() {
        assertFalse(
                TopicInlineHatOpenPolicy.resolveOpenStateForLoad(
                        storedOpen = false,
                        hasStoredPreference = false,
                        initialFromSetting = false,
                )
        )
    }

    @Test
    fun `resolveOpenStateForLoad respects stored preference over expanded setting`() {
        assertFalse(
                TopicInlineHatOpenPolicy.resolveOpenStateForLoad(
                        storedOpen = false,
                        hasStoredPreference = true,
                        initialFromSetting = true,
                )
        )
        assertTrue(
                TopicInlineHatOpenPolicy.resolveOpenStateForLoad(
                        storedOpen = true,
                        hasStoredPreference = true,
                        initialFromSetting = false,
                )
        )
    }

    @Test
    fun `LAST_UNREAD pagination to page 1 suppresses stored hat preference`() {
        val current = ThemePage().apply { id = topicId }
        assertTrue(
                TopicInlineHatOpenPolicy.shouldSuppressStoredHatPreferenceForLoad(
                        url = page1Url,
                        requestedTopicId = topicId,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "pagination",
                        currentPage = current,
                )
        )
    }

    @Test
    fun `LAST_UNREAD fresh open page 1 does not suppress stored hat preference`() {
        assertFalse(
                TopicInlineHatOpenPolicy.shouldSuppressStoredHatPreferenceForLoad(
                        url = page1Url,
                        requestedTopicId = topicId,
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "favorites",
                        currentPage = null,
                )
        )
    }

    @Test
    fun `LAST_UNREAD pagination treats navigation as in session`() {
        val current = ThemePage().apply { id = topicId }
        assertTrue(
                TopicInlineHatOpenPolicy.shouldTreatAsInSessionForInlineHat(
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        sourceScreen = "pagination",
                        requestedTopicId = topicId,
                        currentPage = current,
                )
        )
    }

    @Test
    fun `FIRST_PAGE pagination does not treat navigation as in session while fresh open`() {
        val current = ThemePage().apply { id = topicId }
        assertFalse(
                TopicInlineHatOpenPolicy.shouldTreatAsInSessionForInlineHat(
                        topicOpenTarget = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        sourceScreen = "pagination",
                        requestedTopicId = topicId,
                        currentPage = current,
                )
        )
    }

    private fun shouldOpen(
            url: String,
            topicOpenTarget: AppPreferences.Main.TopicOpenTarget,
            headerState: AppPreferences.Main.TopicHeaderInitialState,
            sourceScreen: String,
            currentPage: ThemePage?,
            preserveInSession: Boolean,
    ): Boolean =
            TopicInlineHatOpenPolicy.shouldOpenForLoad(
                    url = url,
                    requestedTopicId = topicId,
                    currentPage = currentPage,
                    topicHeaderInitialState = headerState,
                    topicOpenTarget = topicOpenTarget,
                    sourceScreen = sourceScreen,
                    preserveInSessionInlineHatState = preserveInSession,
            )
}
