package forpdateam.ru.forpda.common.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewLoadDispatchPolicyTest {

    private fun snapshot(
            pendingTargetId: Int = 42,
            pendingContentHash: Int = 100,
            loadDispatched: Boolean = false,
            requestGeneration: Int = 1,
            domConfirmedGeneration: Int = 0,
            lastDomConfirmedTargetId: Int = -1,
            lastRequestedTargetId: Int = 42,
    ) = WebViewLoadDispatchPolicy.Snapshot(
            pendingTargetId = pendingTargetId,
            pendingContentHash = pendingContentHash,
            loadDispatched = loadDispatched,
            requestGeneration = requestGeneration,
            domConfirmedGeneration = domConfirmedGeneration,
            lastDomConfirmedTargetId = lastDomConfirmedTargetId,
            lastRequestedTargetId = lastRequestedTargetId,
    )

    @Test
    fun `skip inflight duplicate only after load was dispatched`() {
        assertFalse(
                WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
                        force = false,
                        targetId = 42,
                        contentHash = 100,
                        snapshot = snapshot(loadDispatched = false)
                )
        )
        assertTrue(
                WebViewLoadDispatchPolicy.shouldSkipInflightDuplicate(
                        force = false,
                        targetId = 42,
                        contentHash = 100,
                        snapshot = snapshot(loadDispatched = true, requestGeneration = 3)
                )
        )
    }

    @Test
    fun `force ensure render when load never dispatched`() {
        assertTrue(
                WebViewLoadDispatchPolicy.shouldForceEnsureRender(
                        targetId = 42,
                        snapshot = snapshot(loadDispatched = false)
                )
        )
    }

    @Test
    fun `force ensure render after timeout cleared pending but lastRequested remains`() {
        assertTrue(
                WebViewLoadDispatchPolicy.shouldForceEnsureRender(
                        targetId = 42,
                        snapshot = snapshot(
                                pendingTargetId = -1,
                                loadDispatched = false,
                                lastRequestedTargetId = 42,
                        )
                )
        )
    }

    @Test
    fun `force ensure render when dispatched but not confirmed`() {
        assertTrue(
                WebViewLoadDispatchPolicy.shouldForceEnsureRender(
                        targetId = 42,
                        snapshot = snapshot(
                                loadDispatched = true,
                                requestGeneration = 5,
                                domConfirmedGeneration = 0,
                        )
                )
        )
    }

    @Test
    fun `no force after dom confirmation`() {
        assertFalse(
                WebViewLoadDispatchPolicy.shouldForceEnsureRender(
                        targetId = 42,
                        snapshot = snapshot(
                                loadDispatched = true,
                                requestGeneration = 5,
                                domConfirmedGeneration = 5,
                                lastDomConfirmedTargetId = 42,
                        )
                )
        )
    }

    @Test
    fun `defer load until webview has non zero layout`() {
        assertTrue(WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(0, 1080))
        assertTrue(WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(1080, 0))
        assertFalse(WebViewLoadDispatchPolicy.shouldDeferLoadUntilLayout(1080, 1920))
    }
}
