package forpdateam.ru.forpda.common.webview

import forpdateam.ru.forpda.common.webview.WebViewRenderSession.Owner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebViewRenderControllerTest {

    private lateinit var controller: WebViewRenderController

    @Before
    fun setUp() {
        controller = WebViewRenderController()
    }

    private fun begin(targetId: Int = 10, contentHash: Int = 100) =
            controller.beginRender(Owner.THEME, targetId, contentHash)

    @Test
    fun `duplicate render is skipped only after real load dispatch`() {
        val session = begin(targetId = 10, contentHash = 100)
        // Before dispatch: a same-payload request must NOT be skipped.
        assertFalse(controller.shouldSkipDuplicate(targetId = 10, contentHash = 100, force = false))
        // After dispatch (and before DOM confirmation): same payload is an in-flight duplicate.
        controller.markLoadDispatched(session)
        assertTrue(controller.shouldSkipDuplicate(targetId = 10, contentHash = 100, force = false))
        // force always bypasses.
        assertFalse(controller.shouldSkipDuplicate(targetId = 10, contentHash = 100, force = true))
    }

    @Test
    fun `render is forced if DOM was never confirmed`() {
        val session = begin(targetId = 10)
        controller.markLoadDispatched(session)
        // Dispatched but not DOM-confirmed -> force ensure render.
        assertTrue(controller.shouldForceEnsureRender(10))
    }

    @Test
    fun `no force after DOM confirmation`() {
        val session = begin(targetId = 10)
        controller.markLoadDispatched(session)
        controller.markDomConfirmed(session)
        assertFalse(controller.shouldForceEnsureRender(10))
    }

    @Test
    fun `zero size WebView load is deferred`() {
        assertTrue(controller.shouldDeferUntilLayout(0, 1920))
        assertTrue(controller.shouldDeferUntilLayout(1080, 0))
        assertFalse(controller.shouldDeferUntilLayout(1080, 1920))
    }

    @Test
    fun `stale session callback is ignored`() {
        val first = begin(targetId = 10, contentHash = 100)
        val second = begin(targetId = 10, contentHash = 200)
        assertTrue(controller.isStaleCallback(first))
        assertFalse(controller.isStaleCallback(second))
        // Marking confirmations against a stale session is a no-op (does not become current).
        controller.markDomConfirmed(first)
        assertTrue(controller.shouldForceEnsureRender(10))
        assertFalse(controller.isCurrent(first))
        assertTrue(controller.isCurrent(second))
    }

    @Test
    fun `cleanup invalidates active session`() {
        val session = begin(targetId = 10)
        controller.markLoadDispatched(session)
        assertNotNull(controller.activeSession())
        controller.cleanup()
        assertNull(controller.activeSession())
        assertFalse(controller.isCurrent(session))
        // After cleanup there is nothing pending, so no duplicate is skipped.
        assertFalse(controller.shouldSkipDuplicate(targetId = 10, contentHash = 100, force = false))
    }

    @Test
    fun `each beginRender increments generation`() {
        val a = begin()
        val b = begin()
        assertTrue(b.renderGeneration > a.renderGeneration)
        assertTrue(controller.isCurrent(b))
        assertFalse(controller.isCurrent(a))
    }

    @Test
    fun `markLoadDispatched on stale session does not flip dispatch state`() {
        val first = begin(targetId = 10, contentHash = 100)
        begin(targetId = 10, contentHash = 200)
        controller.markLoadDispatched(first)
        // Old session's content should not be considered an in-flight duplicate.
        assertFalse(controller.shouldSkipDuplicate(targetId = 10, contentHash = 100, force = false))
    }
}
