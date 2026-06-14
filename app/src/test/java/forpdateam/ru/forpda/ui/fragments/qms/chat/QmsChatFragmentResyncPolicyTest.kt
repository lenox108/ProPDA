package forpdateam.ru.forpda.ui.fragments.qms.chat

import forpdateam.ru.forpda.presentation.qms.chat.QmsWebRenderProbe
import forpdateam.ru.forpda.presentation.qms.chat.QmsWebRenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents first-open blank chat recovery: alone-tab re-entry called [QmsChatFragment.applyChatScreenFromNavigator]
 * resync, but first open had no equivalent until [QmsChatFragment.ensureQmsMessagesVisible].
 */
class QmsChatFragmentResyncPolicyTest {

    @Test
    fun firstOpenBlankState_matchesForceResyncPredicate() {
        assertTrue(
                QmsWebRenderPolicy.shouldForceMessageResync(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                )
        )
    }

    @Test
    fun domReadyFlush_doesNotDropPendingShowBatch_whenOnlyShowMessagesQueued() {
        val scripts = listOf(
                """if(typeof resetQmsMessageList==='function'){resetQmsMessageList();}showNewMess("html",true);"""
        )
        assertEquals(scripts, QmsWebRenderProbe.collapsePendingFullResetShowNewMessScripts(scripts))
    }

    @Test
    fun collapsePending_keepsLastFullReset_only() {
        val first = """resetQmsMessageList();showNewMess("a",true);"""
        val second = """resetQmsMessageList();showNewMess("b",true);"""
        val incremental = """showNewMess("c",false);"""
        val collapsed = QmsWebRenderProbe.collapsePendingFullResetShowNewMessScripts(
                listOf(first, incremental, second)
        )
        assertEquals(listOf(incremental, second), collapsed)
    }

    @Test
    fun zeroLayoutDefersInjectUntilSized() {
        assertTrue(QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(0, 1080))
        assertTrue(QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(1080, 0))
    }

    @Test
    fun contentWatchdog_keepsRetryingUntilApplied() {
        assertTrue(
                QmsWebRenderPolicy.shouldScheduleContentWatchdog(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        attempt = 2,
                )
        )
    }

    @Test
    fun injectWeakCount_triggersResendPolicy() {
        assertTrue(
                QmsWebRenderProbe.shouldResendOnZeroInjectCount(
                        injectCount = 0,
                        expectedContainers = 3,
                        hasLoadedMessages = true,
                )
        )
    }
}
