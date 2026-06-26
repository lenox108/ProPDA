package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.common.MessageCounters

object CounterGrowthDetector {

    /** Любой рост счётчика (QMS / избранное / ответы). */
    fun hasGrowth(before: MessageCounters, after: MessageCounters): Boolean {
        return after.qms > before.qms ||
                after.favorites > before.favorites ||
                after.mentions > before.mentions
    }

    /**
     * «Резкий» рост: суммарный прирост ≥ [SHARP_TOTAL_DELTA] или любой канал +≥ [SHARP_SINGLE_DELTA].
     */
    fun hasSharpGrowth(before: MessageCounters, after: MessageCounters): Boolean {
        val dqms = (after.qms - before.qms).coerceAtLeast(0)
        val dfav = (after.favorites - before.favorites).coerceAtLeast(0)
        val dmen = (after.mentions - before.mentions).coerceAtLeast(0)
        return dqms >= SHARP_SINGLE_DELTA ||
                dfav >= SHARP_SINGLE_DELTA ||
                dmen >= SHARP_SINGLE_DELTA ||
                dqms + dfav + dmen >= SHARP_TOTAL_DELTA
    }

    fun hasSharpGrowth(before: HeaderCounters, after: HeaderCounters): Boolean {
        if (!before.isInitialized()) return false
        val dqms = (after.qms - before.qms).coerceAtLeast(0)
        val dfav = (after.favorites - before.favorites).coerceAtLeast(0)
        val dmen = (after.mentions - before.mentions).coerceAtLeast(0)
        return dqms >= SHARP_SINGLE_DELTA ||
                dfav >= SHARP_SINGLE_DELTA ||
                dmen >= SHARP_SINGLE_DELTA ||
                dqms + dfav + dmen >= SHARP_TOTAL_DELTA
    }

    private const val SHARP_SINGLE_DELTA = 2
    private const val SHARP_TOTAL_DELTA = 3
}
