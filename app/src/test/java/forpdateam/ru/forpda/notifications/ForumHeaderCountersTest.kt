package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumHeaderCountersTest {

    @Test
    fun parse_legacyCombinedPattern() {
        val html = """
            <a href="https://4pda.to/forum/index.php?act=mentions" data-count="2"></a>
            <a href="act=fav&amp;code=no" data-count="5"></a>
            <span id="events-count" data-count="3"></span>
        """.trimIndent()
        val counters = ForumHeaderCounters.parse(html)
        assertEquals(2, counters.mentions)
        assertEquals(5, counters.favorites)
        assertEquals(3, counters.qms)
    }

    @Test
    fun parse_fallbackPatterns() {
        val html = """
            <a href="/forum/index.php?act=mentions" data-count="1">m</a>
            <a href="/forum/index.php?act=fav&amp;code=no" data-count="4">f</a>
            <span id="events-count" data-count="7"></span>
        """.trimIndent()
        val counters = ForumHeaderCounters.parse(html)
        assertEquals(1, counters.mentions)
        assertEquals(4, counters.favorites)
        assertEquals(7, counters.qms)
    }

    @Test
    fun headerCounters_unsetNotInitialized() {
        assertTrue(!HeaderCounters.UNSET.isInitialized())
    }
}
