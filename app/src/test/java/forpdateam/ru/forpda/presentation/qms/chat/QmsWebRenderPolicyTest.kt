package forpdateam.ru.forpda.presentation.qms.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QmsWebRenderPolicyTest {

    @Test
    fun `shouldDeferJsInjectUntilLayout when webview is zero sized`() {
        assertTrue(QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(0, 1920))
        assertTrue(QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(1080, 0))
        assertFalse(QmsWebRenderPolicy.shouldDeferJsInjectUntilLayout(1080, 1920))
    }

    @Test
    fun `shouldDeferVerify waits for layout before dom`() {
        val reason = QmsWebRenderPolicy.shouldDeferVerify(
                networkRefreshing = false,
                webViewWidth = 0,
                webViewHeight = 1080,
                qmsDomReady = true,
                jsBridgeReady = true,
                layoutWaitAttempts = 0
        )
        assertEquals(QmsWebRenderPolicy.VerifyDeferReason.WEBVIEW_NOT_LAYED_OUT, reason)
    }

    @Test
    fun `shouldDeferVerify defers during network refresh`() {
        val reason = QmsWebRenderPolicy.shouldDeferVerify(
                networkRefreshing = true,
                webViewWidth = 1080,
                webViewHeight = 1920,
                qmsDomReady = true,
                jsBridgeReady = true,
                layoutWaitAttempts = 0
        )
        assertEquals(QmsWebRenderPolicy.VerifyDeferReason.NETWORK_REFRESHING, reason)
    }

    @Test
    fun `shouldDeferVerify proceeds when layout bridge and dom ready`() {
        val reason = QmsWebRenderPolicy.shouldDeferVerify(
                networkRefreshing = false,
                webViewWidth = 1080,
                webViewHeight = 1920,
                qmsDomReady = true,
                jsBridgeReady = true,
                layoutWaitAttempts = 0
        )
        assertNull(reason)
    }

    @Test
    fun `shouldDeferVerify keeps waiting for bridge after layout timeout budget`() {
        val reason = QmsWebRenderPolicy.shouldDeferVerify(
                networkRefreshing = false,
                webViewWidth = 0,
                webViewHeight = 0,
                qmsDomReady = false,
                jsBridgeReady = false,
                layoutWaitAttempts = QmsWebRenderPolicy.MAX_LAYOUT_WAIT_ATTEMPTS
        )
        assertNotNull(reason)
        assertEquals(QmsWebRenderPolicy.VerifyDeferReason.DOM_NOT_READY, reason)
    }

    @Test
    fun `verifyDelayMs grows with blank retry count`() {
        assertTrue(QmsWebRenderPolicy.verifyDelayMs(0) < QmsWebRenderPolicy.verifyDelayMs(5))
        assertEquals(
                QmsWebRenderPolicy.RENDER_VERIFY_DELAYS_MS.last(),
                QmsWebRenderPolicy.verifyDelayMs(999)
        )
    }

    @Test
    fun `shouldKickDomVerifyOnRepair when pending shell without dom ready`() {
        assertTrue(QmsWebRenderPolicy.shouldKickDomVerifyOnRepair(qmsPendingWebRender = true, qmsDomReady = false))
        assertFalse(QmsWebRenderPolicy.shouldKickDomVerifyOnRepair(qmsPendingWebRender = false, qmsDomReady = false))
        assertFalse(QmsWebRenderPolicy.shouldKickDomVerifyOnRepair(qmsPendingWebRender = true, qmsDomReady = true))
    }

    @Test
    fun `shouldStartDomReadyProbe coalesces active generation`() {
        assertTrue(
                QmsWebRenderPolicy.shouldStartDomReadyProbe(
                        activeGeneration = null,
                        requestedGeneration = 1,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldStartDomReadyProbe(
                        activeGeneration = 1,
                        requestedGeneration = 1,
                        domReady = false
                )
        )
        assertTrue(
                QmsWebRenderPolicy.shouldStartDomReadyProbe(
                        activeGeneration = 1,
                        requestedGeneration = 2,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldStartDomReadyProbe(
                        activeGeneration = null,
                        requestedGeneration = 1,
                        domReady = true
                )
        )
    }

    @Test
    fun `isCurrentDomReadyProbe rejects stale token callbacks`() {
        assertTrue(
                QmsWebRenderPolicy.isCurrentDomReadyProbe(
                        activeGeneration = 3,
                        activeToken = 7,
                        requestedGeneration = 3,
                        requestedToken = 7
                )
        )
        assertFalse(
                QmsWebRenderPolicy.isCurrentDomReadyProbe(
                        activeGeneration = 3,
                        activeToken = 8,
                        requestedGeneration = 3,
                        requestedToken = 7
                )
        )
        assertFalse(
                QmsWebRenderPolicy.isCurrentDomReadyProbe(
                        activeGeneration = 4,
                        activeToken = 7,
                        requestedGeneration = 3,
                        requestedToken = 7
                )
        )
    }

    @Test
    fun `reconcileDomReadyFlag clears stale dom ready without bridge`() {
        assertFalse(QmsWebRenderPolicy.reconcileDomReadyFlag(qmsDomReady = true, jsBridgeReady = false))
        assertTrue(QmsWebRenderPolicy.reconcileDomReadyFlag(qmsDomReady = true, jsBridgeReady = true))
    }

    @Test
    fun `shouldAutoScrollToBottom only on forced scroll while user idle`() {
        assertTrue(
                QmsWebRenderPolicy.shouldAutoScrollToBottom(
                        forceScrollRequested = true,
                        userScrollSuppressed = false
                )
        )
    }

    @Test
    fun `shouldAutoScrollToBottom suppressed while user controls scroll`() {
        assertFalse(
                QmsWebRenderPolicy.shouldAutoScrollToBottom(
                        forceScrollRequested = true,
                        userScrollSuppressed = true
                )
        )
    }

    @Test
    fun `shouldAutoScrollToBottom skips incidental re-renders`() {
        assertFalse(
                QmsWebRenderPolicy.shouldAutoScrollToBottom(
                        forceScrollRequested = false,
                        userScrollSuppressed = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldAutoScrollToBottom(
                        forceScrollRequested = false,
                        userScrollSuppressed = true
                )
        )
    }

    @Test
    fun `shouldGiveUpBlankRender respects max retries`() {
        assertFalse(QmsWebRenderPolicy.shouldGiveUpBlankRender(0))
        assertFalse(QmsWebRenderPolicy.shouldGiveUpBlankRender(QmsWebRenderPolicy.MAX_BLANK_RENDER_RETRIES - 1))
        assertTrue(QmsWebRenderPolicy.shouldGiveUpBlankRender(QmsWebRenderPolicy.MAX_BLANK_RENDER_RETRIES))
    }

    @Test
    fun `shouldForceMessageResync when data loaded but not applied`() {
        assertTrue(QmsWebRenderPolicy.shouldForceMessageResync(hasLoadedMessages = true, messagesApplied = false))
        assertFalse(QmsWebRenderPolicy.shouldForceMessageResync(hasLoadedMessages = false, messagesApplied = false))
        assertFalse(QmsWebRenderPolicy.shouldForceMessageResync(hasLoadedMessages = true, messagesApplied = true))
    }

    @Test
    fun `shouldDeferWebPipelineUntilTabShown when hidden or not resumed`() {
        assertTrue(
                QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = true,
                        isHidden = true,
                        isResumed = true
                )
        )
        assertTrue(
                QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = true,
                        isHidden = false,
                        isResumed = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldDeferWebPipelineUntilTabShown(
                        isAdded = true,
                        isHidden = false,
                        isResumed = true
                )
        )
    }

    @Test
    fun `shouldDomReadyResyncAfterFlush skips when show batch already queued`() {
        assertFalse(
                QmsWebRenderPolicy.shouldDomReadyResyncAfterFlush(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        pendingShowBatch = true,
                        pendingHasShowNewMess = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldDomReadyResyncAfterFlush(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        pendingShowBatch = false,
                        pendingHasShowNewMess = true
                )
        )
        assertTrue(
                QmsWebRenderPolicy.shouldDomReadyResyncAfterFlush(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        pendingShowBatch = false,
                        pendingHasShowNewMess = false
                )
        )
    }

    @Test
    fun `shouldReloadBaseContainerOnRecovery when shell never finished loading`() {
        assertTrue(
                QmsWebRenderPolicy.shouldReloadBaseContainerOnRecovery(
                        basePageFinished = false,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldReloadBaseContainerOnRecovery(
                        basePageFinished = true,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldReloadBaseContainerOnRecovery(
                        basePageFinished = false,
                        domReady = true
                )
        )
    }

    @Test
    fun `shouldFastReloadOnDomWatchdog when shell never finished`() {
        assertTrue(
                QmsWebRenderPolicy.shouldFastReloadOnDomWatchdog(
                        basePageFinished = false,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldFastReloadOnDomWatchdog(
                        basePageFinished = true,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldFastReloadOnDomWatchdog(
                        basePageFinished = false,
                        domReady = true
                )
        )
    }

    @Test
    fun `shouldReloadOnDomWatchdog skips when shell never dispatched`() {
        assertFalse(
                QmsWebRenderPolicy.shouldReloadOnDomWatchdog(
                        domReady = false,
                        jsBridgeReady = false,
                        shellLoadDispatched = false
                )
        )
        assertTrue(
                QmsWebRenderPolicy.shouldReloadOnDomWatchdog(
                        domReady = false,
                        jsBridgeReady = false,
                        shellLoadDispatched = true
                )
        )
    }

    @Test
    fun `shouldDispatchShellOnDomWatchdog when shell pending`() {
        assertTrue(
                QmsWebRenderPolicy.shouldDispatchShellOnDomWatchdog(
                        shellLoadDispatched = false,
                        domReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldDispatchShellOnDomWatchdog(
                        shellLoadDispatched = true,
                        domReady = false
                )
        )
    }

    @Test
    fun `shouldReloadOnDomWatchdog when bridge never reported`() {
        assertTrue(QmsWebRenderPolicy.shouldReloadOnDomWatchdog(domReady = false, jsBridgeReady = false))
        assertFalse(QmsWebRenderPolicy.shouldReloadOnDomWatchdog(domReady = true, jsBridgeReady = false))
        assertFalse(QmsWebRenderPolicy.shouldReloadOnDomWatchdog(domReady = false, jsBridgeReady = true))
    }

    @Test
    fun `contentWatchdogDelayMs escalates with attempt`() {
        assertTrue(
                QmsWebRenderPolicy.contentWatchdogDelayMs(0) <
                        QmsWebRenderPolicy.contentWatchdogDelayMs(3)
        )
        assertEquals(
                QmsWebRenderPolicy.contentWatchdogDelayMs(999),
                QmsWebRenderPolicy.contentWatchdogDelayMs(
                        QmsWebRenderPolicy.CONTENT_VISIBLE_WATCHDOG_MAX_ATTEMPTS
                )
        )
    }

    @Test
    fun `shouldScheduleContentWatchdog while messages not applied`() {
        assertTrue(
                QmsWebRenderPolicy.shouldScheduleContentWatchdog(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        attempt = 0
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldScheduleContentWatchdog(
                        hasLoadedMessages = true,
                        messagesApplied = true,
                        attempt = 0
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldScheduleContentWatchdog(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        attempt = QmsWebRenderPolicy.CONTENT_VISIBLE_WATCHDOG_MAX_ATTEMPTS
                )
        )
    }

    @Test
    fun `shouldReloadWebShellOnRecovery when shell finished but bridge dead`() {
        assertTrue(
                QmsWebRenderPolicy.shouldReloadWebShellOnRecovery(
                        basePageFinished = true,
                        domReady = false,
                        jsBridgeReady = false
                )
        )
        assertTrue(
                QmsWebRenderPolicy.shouldReloadWebShellOnRecovery(
                        basePageFinished = false,
                        domReady = false,
                        jsBridgeReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldReloadWebShellOnRecovery(
                        basePageFinished = true,
                        domReady = false,
                        jsBridgeReady = true
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldReloadWebShellOnRecovery(
                        basePageFinished = true,
                        domReady = true,
                        jsBridgeReady = true
                )
        )
    }

    @Test
    fun `shouldDeferShellLoadUntilTabShown mirrors web pipeline defer`() {
        assertTrue(
                QmsWebRenderPolicy.shouldDeferShellLoadUntilTabShown(
                        isAdded = true,
                        isHidden = false,
                        isResumed = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldDeferShellLoadUntilTabShown(
                        isAdded = true,
                        isHidden = false,
                        isResumed = true
                )
        )
    }

    @Test
    fun `shouldAcceptShellPageFinished only after load dispatched`() {
        assertFalse(QmsWebRenderPolicy.shouldAcceptShellPageFinished(shellLoadDispatched = false))
        assertTrue(QmsWebRenderPolicy.shouldAcceptShellPageFinished(shellLoadDispatched = true))
    }

    @Test
    fun `shouldRecoverShellWithoutMessages when shell never dispatched`() {
        assertTrue(
                QmsWebRenderPolicy.shouldRecoverShellWithoutMessages(
                        shellLoadDispatched = false,
                        basePageFinished = false,
                        domReady = false,
                        jsBridgeReady = false
                )
        )
        assertFalse(
                QmsWebRenderPolicy.shouldRecoverShellWithoutMessages(
                        shellLoadDispatched = true,
                        basePageFinished = true,
                        domReady = true,
                        jsBridgeReady = true
                )
        )
    }

    @Test
    fun `shouldShowWebRenderError defers while recovery still active`() {
        assertFalse(
                QmsWebRenderPolicy.shouldShowWebRenderError(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        contentWatchdogAttempt = 3,
                        recoveryAttemptCount = 1,
                        maxRecoveryPerGeneration = 2,
                        errorAlreadyShownForGeneration = false,
                )
        )
    }

    @Test
    fun `shouldShowWebRenderError shows after watchdog and recovery exhausted`() {
        assertTrue(
                QmsWebRenderPolicy.shouldShowWebRenderError(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        contentWatchdogAttempt = QmsWebRenderPolicy.CONTENT_VISIBLE_WATCHDOG_MAX_ATTEMPTS,
                        recoveryAttemptCount = 2,
                        maxRecoveryPerGeneration = 2,
                        errorAlreadyShownForGeneration = false,
                )
        )
    }

    @Test
    fun `shouldShowWebRenderError suppresses duplicate for same generation`() {
        assertFalse(
                QmsWebRenderPolicy.shouldShowWebRenderError(
                        hasLoadedMessages = true,
                        messagesApplied = false,
                        contentWatchdogAttempt = 99,
                        recoveryAttemptCount = 99,
                        maxRecoveryPerGeneration = 2,
                        errorAlreadyShownForGeneration = true,
                )
        )
    }

    @Test
    fun `shouldShowWebRenderError skips when messages already applied`() {
        assertFalse(
                QmsWebRenderPolicy.shouldShowWebRenderError(
                        hasLoadedMessages = true,
                        messagesApplied = true,
                        contentWatchdogAttempt = 99,
                        recoveryAttemptCount = 99,
                        maxRecoveryPerGeneration = 2,
                        errorAlreadyShownForGeneration = false,
                )
        )
    }
}
