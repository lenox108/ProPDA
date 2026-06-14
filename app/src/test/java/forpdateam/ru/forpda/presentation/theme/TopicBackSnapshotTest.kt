package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicBackSnapshotTest {

    @Test
    fun key_isStablePerTopicAndSt() {
        assertEquals("42:80", TopicBackSnapshot.key(42, 80))
    }

    @Test
    fun capturedAndPending_areUsable() {
        val captured = TopicBackSnapshot.fromPage(1, 0, "10", 100, 0.5, false, TopicBackSnapshotStatus.CAPTURED)
        val pending = captured.copy(status = TopicBackSnapshotStatus.PENDING)
        assertTrue(captured.isUsable())
        assertTrue(pending.isUsable())
    }

    @Test
    fun stale_isNotUsable() {
        val stale = TopicBackSnapshot.fromPage(1, 0, "10", 100, 0.5, false, TopicBackSnapshotStatus.STALE)
        assertFalse(stale.isUsable())
    }
}
