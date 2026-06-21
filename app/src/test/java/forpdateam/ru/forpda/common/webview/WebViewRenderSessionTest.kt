package forpdateam.ru.forpda.common.webview

import forpdateam.ru.forpda.common.webview.WebViewRenderSession.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewRenderSessionTest {

    private fun session(
            owner: Owner = Owner.THEME,
            targetId: Int = 1,
            contentHash: Int = 100,
            renderGeneration: Int = 1,
            bridgeToken: String? = "tok",
            createdAt: Long = 0L,
    ) = WebViewRenderSession.create(
            owner = owner,
            targetId = targetId,
            contentHash = contentHash,
            renderGeneration = renderGeneration,
            bridgeToken = bridgeToken,
            createdAt = createdAt,
    )

    @Test
    fun `equality ignores nothing - same fields are equal`() {
        assertEquals(session(), session())
    }

    @Test
    fun `equality differs when generation differs`() {
        assertNotEquals(session(renderGeneration = 1), session(renderGeneration = 2))
    }

    @Test
    fun `isSameTarget matches on owner and targetId only`() {
        assertTrue(
                session(targetId = 7, contentHash = 1, renderGeneration = 1)
                        .isSameTarget(session(targetId = 7, contentHash = 999, renderGeneration = 9))
        )
    }

    @Test
    fun `isSameTarget false for different owner`() {
        assertFalse(
                session(owner = Owner.THEME, targetId = 7)
                        .isSameTarget(session(owner = Owner.NEWS, targetId = 7))
        )
    }

    @Test
    fun `isSameTarget false for different targetId`() {
        assertFalse(session(targetId = 1).isSameTarget(session(targetId = 2)))
    }

    @Test
    fun `isCurrent true when no active session`() {
        assertTrue(session().isCurrent(null))
    }

    @Test
    fun `isCurrent true for same target with equal or higher generation`() {
        val active = session(targetId = 5, renderGeneration = 3)
        assertTrue(session(targetId = 5, renderGeneration = 3).isCurrent(active))
        assertTrue(session(targetId = 5, renderGeneration = 4).isCurrent(active))
    }

    @Test
    fun `isCurrent false for same target with older generation`() {
        val active = session(targetId = 5, renderGeneration = 3)
        assertFalse(session(targetId = 5, renderGeneration = 2).isCurrent(active))
    }

    @Test
    fun `isCurrent false for different target`() {
        val active = session(targetId = 5, renderGeneration = 1)
        assertFalse(session(targetId = 6, renderGeneration = 9).isCurrent(active))
    }

    @Test
    fun `isStaleComparedTo false when no active session`() {
        assertFalse(session().isStaleComparedTo(null))
    }

    @Test
    fun `isStaleComparedTo true for older generation on same target`() {
        val active = session(targetId = 5, renderGeneration = 5)
        assertTrue(session(targetId = 5, renderGeneration = 4).isStaleComparedTo(active))
    }

    @Test
    fun `isStaleComparedTo false for equal or newer generation on same target`() {
        val active = session(targetId = 5, renderGeneration = 5)
        assertFalse(session(targetId = 5, renderGeneration = 5).isStaleComparedTo(active))
        assertFalse(session(targetId = 5, renderGeneration = 6).isStaleComparedTo(active))
    }

    @Test
    fun `isStaleComparedTo true for different target`() {
        val active = session(owner = Owner.SEARCH, targetId = 5, renderGeneration = 1)
        assertTrue(session(owner = Owner.QMS, targetId = 5, renderGeneration = 99).isStaleComparedTo(active))
    }

    @Test
    fun `current and stale are mutually consistent for same target`() {
        val active = session(targetId = 5, renderGeneration = 5)
        val older = session(targetId = 5, renderGeneration = 4)
        val newer = session(targetId = 5, renderGeneration = 6)
        assertTrue(older.isStaleComparedTo(active))
        assertFalse(older.isCurrent(active))
        assertFalse(newer.isStaleComparedTo(active))
        assertTrue(newer.isCurrent(active))
    }
}
