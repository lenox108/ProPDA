package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.common.MessageCounters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterGrowthDetectorTest {

    @Test
    fun hasGrowth_detectsAnyIncrease() {
        val before = MessageCounters().apply { qms = 1; favorites = 2; mentions = 0 }
        val after = MessageCounters().apply { qms = 1; favorites = 2; mentions = 1 }
        assertTrue(CounterGrowthDetector.hasGrowth(before, after))
    }

    @Test
    fun hasGrowth_falseWhenUnchanged() {
        val before = MessageCounters().apply { qms = 3; favorites = 1; mentions = 0 }
        val after = MessageCounters().apply { qms = 3; favorites = 1; mentions = 0 }
        assertFalse(CounterGrowthDetector.hasGrowth(before, after))
    }

    @Test
    fun hasSharpGrowth_singleChannelPlusTwo() {
        val before = MessageCounters().apply { qms = 1 }
        val after = MessageCounters().apply { qms = 3 }
        assertTrue(CounterGrowthDetector.hasSharpGrowth(before, after))
    }

    @Test
    fun hasSharpGrowth_totalPlusThreeAcrossChannels() {
        val before = MessageCounters().apply { qms = 1; favorites = 1; mentions = 1 }
        val after = MessageCounters().apply { qms = 2; favorites = 2; mentions = 2 }
        assertTrue(CounterGrowthDetector.hasSharpGrowth(before, after))
    }

    @Test
    fun hasSharpGrowth_falseForSingleIncrement() {
        val before = MessageCounters().apply { mentions = 4 }
        val after = MessageCounters().apply { mentions = 5 }
        assertFalse(CounterGrowthDetector.hasSharpGrowth(before, after))
    }

    @Test
    fun headerSharpGrowth_requiresInitializedBefore() {
        assertFalse(CounterGrowthDetector.hasSharpGrowth(HeaderCounters.UNSET, HeaderCounters(1, 0, 0)))
        assertTrue(
                CounterGrowthDetector.hasSharpGrowth(
                        HeaderCounters(0, 0, 0),
                        HeaderCounters(0, 0, 2)
                )
        )
    }
}
