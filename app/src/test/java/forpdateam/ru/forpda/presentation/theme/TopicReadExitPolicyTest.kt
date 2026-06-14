package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicReadExitPolicyTest {

    @Test
    fun marksReadAtLegacyBottomThreshold() {
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = 0.995))
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = true, scrollRatio = null))
    }

    @Test
    fun marksReadNearBottom_log752_1122662() {
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = 0.979))
        assertTrue(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = 0.9759))
    }

    @Test
    fun skipsMarkReadWhenUserDidNotReachLastPageContent() {
        assertFalse(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = 0.92))
        assertFalse(TopicReadExitPolicy.shouldMarkReadOnLastPageExit(wasNearBottom = false, scrollRatio = null))
    }
}
