package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeHistoryControllerSnapshotTest {

    @Test
    fun consumeBackSnapshot_removesAfterRead() {
        val controller = ThemeHistoryController()
        val snapshot = TopicBackSnapshot.fromPage(10, 20, "5", 300, 0.25, false)
        controller.captureBackSnapshot(snapshot)
        assertEquals(snapshot, controller.peekBackSnapshot(10, 20))
        assertEquals(snapshot, controller.consumeBackSnapshot(10, 20))
        assertNull(controller.peekBackSnapshot(10, 20))
    }

    @Test
    fun markBackSnapshotStale_keepsEntryButNotUsable() {
        val controller = ThemeHistoryController()
        val snapshot = TopicBackSnapshot.fromPage(1, 0, "2", 50, null, false)
        controller.captureBackSnapshot(snapshot)
        controller.markBackSnapshotStale(1, 0)
        val stale = controller.peekBackSnapshot(1, 0)
        assertEquals(TopicBackSnapshotStatus.STALE, stale?.status)
        assertEquals(false, stale?.isUsable())
    }
}
