package forpdateam.ru.forpda.presentation.qms.chat

import forpdateam.ru.forpda.common.webview.WebViewLoadDispatchPolicy

/**
 * Pure retry / defer rules for QMS WebView render verification (unit-testable).
 */
object QmsWebRenderPolicy {

    const val MAX_BLANK_RENDER_RETRIES = 10
    const val MAX_DOM_READY_ATTEMPTS = 28
    const val MAX_LAYOUT_WAIT_ATTEMPTS = 20
    const val DOM_READY_RETRY_MS = 100L
    const val DOM_READY_WATCHDOG_MS = 1_500L
    private val DOM_READY_BACKOFF_MS = longArrayOf(
            60L,
            100L,
            160L,
            220L,
            320L,
            450L,
            600L,
            800L,
            1100L,
            1500L,
    )

    /** Exponential-ish backoff for DOM-ready probes — first attempts stay tight, tail widens. */
    fun domReadyDelayMs(attempt: Int): Long {
        val index = attempt.coerceIn(0, DOM_READY_BACKOFF_MS.lastIndex)
        return DOM_READY_BACKOFF_MS[index]
    }
    const val WEB_RENDER_TIMEOUT_MS = 20_000L
    const val PAGE_FINISHED_DOM_VERIFY_DELAY_MS = 32L
    /** First post-[Content] watchdog delay; further attempts use [contentWatchdogDelayMs]. */
    const val CONTENT_VISIBLE_WATCHDOG_MS = 400L
    const val CONTENT_VISIBLE_WATCHDOG_MAX_ATTEMPTS = 10
    private val CONTENT_VISIBLE_WATCHDOG_DELAYS_MS = longArrayOf(
            400L,
            600L,
            900L,
            1200L,
            1600L,
            2200L,
            3000L,
            4000L,
            5500L,
            7000L,
    )
    const val NETWORK_IDLE_RESYNC_DELAY_MS = 100L

    val RENDER_VERIFY_DELAYS_MS = longArrayOf(
            80L,
            160L,
            280L,
            450L,
            700L,
            1000L,
            1400L,
            2000L,
    )

    enum class VerifyDeferReason {
        NETWORK_REFRESHING,
        WEBVIEW_NOT_LAYED_OUT,
        DOM_NOT_READY,
        JS_BRIDGE_NOT_READY
    }

    fun verifyDelayMs(blankRetryCount: Int): Long {
        val index = blankRetryCount.coerceAtMost(RENDER_VERIFY_DELAYS_MS.lastIndex)
        return RENDER_VERIFY_DELAYS_MS[index]
    }

    /** [QmsChatFragment.execQmsJsAfterLayout] must not inject while WebView is still 0×0 (audit ID 7). */
    fun shouldDeferJsInjectUntilLayout(webViewWidth: Int, webViewHeight: Int): Boolean =
            WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(webViewWidth, webViewHeight)

    fun shouldDeferVerify(
            networkRefreshing: Boolean,
            webViewWidth: Int,
            webViewHeight: Int,
            qmsDomReady: Boolean,
            jsBridgeReady: Boolean,
            layoutWaitAttempts: Int
    ): VerifyDeferReason? {
        if (networkRefreshing) return VerifyDeferReason.NETWORK_REFRESHING
        if (webViewWidth <= 0 || webViewHeight <= 0) {
            if (layoutWaitAttempts < MAX_LAYOUT_WAIT_ATTEMPTS) {
                return VerifyDeferReason.WEBVIEW_NOT_LAYED_OUT
            }
        }
        if (!qmsDomReady) return VerifyDeferReason.DOM_NOT_READY
        if (!jsBridgeReady) return VerifyDeferReason.JS_BRIDGE_NOT_READY
        return null
    }

    fun shouldGiveUpBlankRender(blankRetryCount: Int): Boolean =
            blankRetryCount >= MAX_BLANK_RENDER_RETRIES

    fun shouldGiveUpDomReady(attempt: Int): Boolean =
            attempt >= MAX_DOM_READY_ATTEMPTS

    fun shouldGiveUpLayoutWait(layoutWaitAttempts: Int): Boolean =
            layoutWaitAttempts >= MAX_LAYOUT_WAIT_ATTEMPTS

    /** Native [qmsDomReady] must track JS bridge; stale true after WebView reload/reattach. */
    fun reconcileDomReadyFlag(qmsDomReady: Boolean, jsBridgeReady: Boolean): Boolean =
            qmsDomReady && jsBridgeReady

    /** Resume repair must re-run DOM verify instead of bailing while the shell is still loading. */
    fun shouldKickDomVerifyOnRepair(qmsPendingWebRender: Boolean, qmsDomReady: Boolean): Boolean =
            qmsPendingWebRender && !qmsDomReady

    /**
     * DOM readiness is a single WebView-shell property. While one probe loop is active for the
     * current generation, additional UI triggers should not start parallel retry chains.
     */
    fun shouldStartDomReadyProbe(
            activeGeneration: Int?,
            requestedGeneration: Int,
            domReady: Boolean
    ): Boolean =
            !domReady && activeGeneration != requestedGeneration

    fun isCurrentDomReadyProbe(
            activeGeneration: Int?,
            activeToken: Int,
            requestedGeneration: Int,
            requestedToken: Int
    ): Boolean =
            activeGeneration == requestedGeneration && activeToken == requestedToken

    /**
     * Base HTML shell never reached [WebViewClient.onPageFinished] — queued JS cannot run.
     * Reload the container instead of probing [isQmsMessageListReady] in a vacuum.
     */
    fun shouldReloadBaseContainerOnRecovery(basePageFinished: Boolean, domReady: Boolean): Boolean =
            !domReady && !basePageFinished

    /**
     * [onPageFinished] fired but [IBase.domContentLoaded] / JS bridge never became ready —
     * another ~2.8s DOM probe budget will not help; reload the shell.
     */
    fun shouldReloadFinishedShellOnRecovery(
            basePageFinished: Boolean,
            domReady: Boolean,
            jsBridgeReady: Boolean
    ): Boolean = basePageFinished && !domReady && !jsBridgeReady

    /** Unified auto-recovery reload rule (shell missing or bridge dead). */
    fun shouldReloadWebShellOnRecovery(
            basePageFinished: Boolean,
            domReady: Boolean,
            jsBridgeReady: Boolean
    ): Boolean =
            shouldReloadBaseContainerOnRecovery(basePageFinished, domReady) ||
                    shouldReloadFinishedShellOnRecovery(basePageFinished, domReady, jsBridgeReady)

    /**
     * [loadDataWithBaseURL] can lag [onPageFinished] on some devices; probing
     * [isQmsMessageListReady] before the shell exists wastes ~3s of dom_not_ready retries.
     */
    fun shouldFastReloadOnDomWatchdog(basePageFinished: Boolean, domReady: Boolean): Boolean =
            !domReady && !basePageFinished

    /**
     * Post-load watchdog: if native DOM is still not ready and the JS bridge never reported in,
     * reload instead of spending the full DOM-ready probe budget on an empty/stale document.
     *
     * When the HTML shell was never dispatched, reload is premature — [dispatchQmsShellLoad] must run first.
     */
    fun shouldReloadOnDomWatchdog(
            domReady: Boolean,
            jsBridgeReady: Boolean,
            shellLoadDispatched: Boolean = true
    ): Boolean = shellLoadDispatched && !domReady && !jsBridgeReady

    /** Watchdog should load the shell, not reload/recover, while [loadDataWithBaseURL] was deferred. */
    fun shouldDispatchShellOnDomWatchdog(shellLoadDispatched: Boolean, domReady: Boolean): Boolean =
            !shellLoadDispatched && !domReady

    /**
     * Auto-scroll-to-bottom must run only for genuinely forced scrolls (initial open,
     * dialog switch, user-sent / explicit new messages) and never while the user is
     * dragging or has taken control of the viewport. Incidental re-renders (resume,
     * refresh re-verify, blank-retry recovery) must not yank the user back to the bottom,
     * which is the QMS "scroll freezes / jitters / won't scroll" symptom.
     */
    fun shouldAutoScrollToBottom(forceScrollRequested: Boolean, userScrollSuppressed: Boolean): Boolean =
            forceScrollRequested && !userScrollSuppressed

    /** Data is loaded but WebView never confirmed render (blank shell, no spinner). */
    fun shouldForceMessageResync(hasLoadedMessages: Boolean, messagesApplied: Boolean): Boolean =
            hasLoadedMessages && !messagesApplied

    /**
     * Inject / DOM-verify must not run while the alone-tab chat is hidden or not resumed —
     * [ExtendedWebView] is paused and [TabFragment.onPauseOrHide] cleared the JS queue.
     */
    fun shouldDeferWebPipelineUntilTabShown(
            isAdded: Boolean,
            isHidden: Boolean,
            isResumed: Boolean
    ): Boolean = !isAdded || isHidden || !isResumed

    /** [loadDataWithBaseURL] while paused / 0×0 never reaches [onPageFinished] on some OEM WebViews. */
    fun shouldDeferShellLoadUntilTabShown(
            isAdded: Boolean,
            isHidden: Boolean,
            isResumed: Boolean
    ): Boolean = shouldDeferWebPipelineUntilTabShown(isAdded, isHidden, isResumed)

    /**
     * [WebView.stopLoading] during shell reload can fire [onPageFinished] before
     * [loadDataWithBaseURL] — ignore that stale callback.
     */
    fun shouldAcceptShellPageFinished(shellLoadDispatched: Boolean): Boolean = shellLoadDispatched

    /**
     * First-open watchdog: HTML shell may be blocked (paused tab, 0×0 layout) before network
     * returns messages — still reload / dispatch the shell.
     */
    fun shouldRecoverShellWithoutMessages(
            shellLoadDispatched: Boolean,
            basePageFinished: Boolean,
            domReady: Boolean,
            jsBridgeReady: Boolean
    ): Boolean =
            !domReady &&
                    (!shellLoadDispatched ||
                            shouldReloadWebShellOnRecovery(basePageFinished, domReady, jsBridgeReady))

    /**
     * [markQmsDomReadyAndFlush] already schedules a show batch via [pendingQmsShowMessages] or
     * queued [showNewMess] scripts — skip a second [ensureQmsMessagesVisible] resend (log: double
     * dom_inject_success on cache_instant + switch_fast).
     */
    fun shouldDomReadyResyncAfterFlush(
            hasLoadedMessages: Boolean,
            messagesApplied: Boolean,
            pendingShowBatch: Boolean,
            pendingHasShowNewMess: Boolean
    ): Boolean =
            shouldForceMessageResync(hasLoadedMessages, messagesApplied) &&
                    !pendingShowBatch &&
                    !pendingHasShowNewMess

    fun contentWatchdogDelayMs(attempt: Int): Long {
        val index = attempt.coerceIn(0, CONTENT_VISIBLE_WATCHDOG_DELAYS_MS.lastIndex)
        return CONTENT_VISIBLE_WATCHDOG_DELAYS_MS[index]
    }

    fun shouldScheduleContentWatchdog(
            hasLoadedMessages: Boolean,
            messagesApplied: Boolean,
            attempt: Int
    ): Boolean =
            shouldForceMessageResync(hasLoadedMessages, messagesApplied) &&
                    attempt < CONTENT_VISIBLE_WATCHDOG_MAX_ATTEMPTS

    /**
     * Loaded messages may still render via content watchdog / shell dispatch while DOM probes
     * time out — avoid «Обновить» until soft recovery budgets are exhausted.
     */
    fun shouldShowWebRenderError(
            hasLoadedMessages: Boolean,
            messagesApplied: Boolean,
            contentWatchdogAttempt: Int,
            recoveryAttemptCount: Int,
            maxRecoveryPerGeneration: Int,
            errorAlreadyShownForGeneration: Boolean,
    ): Boolean {
        if (messagesApplied) return false
        if (errorAlreadyShownForGeneration) return false
        if (!hasLoadedMessages) return true
        val recoveryExhausted = recoveryAttemptCount >= maxRecoveryPerGeneration
        val watchdogExhausted = contentWatchdogAttempt >= CONTENT_VISIBLE_WATCHDOG_MAX_ATTEMPTS
        return recoveryExhausted && watchdogExhausted
    }
}
