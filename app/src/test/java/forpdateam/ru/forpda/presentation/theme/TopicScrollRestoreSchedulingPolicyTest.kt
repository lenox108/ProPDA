package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicScrollRestoreSchedulingPolicyTest {

    @Test
    fun pageComplete_skipsWhenDomLifecycleClaimed() {
        assertFalse(
                TopicScrollRestoreSchedulingPolicy.shouldScheduleKotlinRestoreOnPageComplete(
                        domLifecycleClaimed = true,
                )
        )
    }

    @Test
    fun pageComplete_schedulesWhenDomLifecycleMissed() {
        assertTrue(
                TopicScrollRestoreSchedulingPolicy.shouldScheduleKotlinRestoreOnPageComplete(
                        domLifecycleClaimed = false,
                )
        )
    }

    @Test
    fun restorePathLabel_includesKindWhenPresent() {
        assertEquals(
                "dom_complete:REFRESH_RESTORE",
                TopicScrollRestoreSchedulingPolicy.restorePathLabel(
                        surface = "dom_complete",
                        kind = "REFRESH_RESTORE",
                )
        )
    }
}
