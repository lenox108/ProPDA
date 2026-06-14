package forpdateam.ru.forpda.presentation.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeJsInterfaceTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var callbacks: RecordingThemeCallbacks
    private lateinit var guard: ThemeRenderGuard
    private lateinit var jsInterface: ThemeJsInterface

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        callbacks = RecordingThemeCallbacks()
        guard = ThemeRenderGuard()
        jsInterface = ThemeJsInterface(callbacks, guard)
    }

    @After
    fun tearDown() {
        jsInterface.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun destructiveAction_withValidToken_callsCallback() = runTest(dispatcher) {
        val token = guard.newToken()

        jsInterface.deletePost("42", token)
        advanceUntilIdle()

        assertEquals(listOf("delete:42"), callbacks.actions)
    }

    @Test
    fun destructiveAction_withMissingOrWrongToken_isBlocked() = runTest(dispatcher) {
        guard.newToken()

        jsInterface.deletePost("42", null)
        jsInterface.votePost("42", true, "")
        jsInterface.submitPoll("/forum", "post", "a=b", "wrong")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), callbacks.actions)
    }

    @Test
    fun destructiveAction_withStaleToken_isBlocked() = runTest(dispatcher) {
        val stale = guard.newToken()
        val current = guard.newToken()

        jsInterface.reply("42", stale)
        jsInterface.reply("43", current)
        advanceUntilIdle()

        assertEquals(listOf("reply:43"), callbacks.actions)
    }

    private class RecordingThemeCallbacks : ThemeWebCallbacks {
        val actions = mutableListOf<String>()

        override fun onFirstPageClick() = Unit
        override fun onPrevPageClick() = Unit
        override fun onNextPageClick() = Unit
        override fun onLastPageClick() = Unit
        override fun onSelectPageClick() = Unit
        override fun onUserMenuClick(postId: Int) = Unit
        override fun onReputationMenuClick(postId: Int) = Unit
        override fun onPostMenuClick(postId: Int) = Unit
        override fun onReportPostClick(postId: Int) {
            actions += "report:$postId"
        }
        override fun onReplyPostClick(postId: Int) {
            actions += "reply:$postId"
        }
        override fun onQuotePostClick(postId: Int, text: String, displayedDate: String?) {
            actions += "quote:$postId"
        }
        override fun onQuoteFullPostClick(postId: Int, displayedDate: String?) {
            actions += "quoteFull:$postId"
        }
        override fun onDeletePostClick(postId: Int) {
            actions += "delete:$postId"
        }
        override fun onEditPostClick(postId: Int) {
            actions += "edit:$postId"
        }
        override fun onVotePostClick(postId: Int, type: Boolean) {
            actions += "vote:$postId:$type"
        }
        override fun setHistoryBody(index: Int, body: String) = Unit
        override fun copyText(text: String) = Unit
        override fun shareText(text: String) = Unit
        override fun toast(text: String) = Unit
        override fun log(text: String) = Unit
        override fun onPollResultsClick(url: String?) = Unit
        override fun onPollClick() = Unit
        override fun onPollSubmit(action: String, method: String, encodedForm: String) {
            actions += "poll:$method:$encodedForm"
        }
        override fun onSpoilerCopyLinkClick(postId: Int, spoilNumber: String) = Unit
        override fun onAnchorClick(postId: Int, name: String) = Unit
        override fun onPollHeaderClick(bValue: Boolean) = Unit
        override fun onHatHeaderClick(bValue: Boolean) = Unit
        override fun onOpenLink(url: String) = Unit
        override fun openProfile(postId: Int) = Unit
        override fun openQms(postId: Int) = Unit
        override fun openSearchUserTopic(postId: Int) = Unit
        override fun openSearchInTopic(postId: Int) = Unit
        override fun openSearchUserMessages(postId: Int) = Unit
        override fun toggleForumBlacklist(postId: Int) = Unit
        override fun onChangeReputationClick(postId: Int, type: Boolean) {
            actions += "reputation:$postId:$type"
        }
        override fun changeReputation(postId: Int, type: Boolean, message: String) = Unit
        override fun votePost(postId: Int, type: Boolean) = Unit
        override fun openReputationHistory(postId: Int) = Unit
        override fun quoteFromBuffer(postId: Int) = Unit
        override fun reportPost(postId: Int, message: String) = Unit
        override fun deletePost(postId: Int) = Unit
        override fun createNote(postId: Int) = Unit
        override fun copyPostLink(postId: Int) = Unit
        override fun sharePostLink(postId: Int) = Unit
        override fun copyAnchorLink(postId: Int, name: String) = Unit
        override fun copySpoilerLink(postId: Int, spoilNumber: String) = Unit
    }
}
