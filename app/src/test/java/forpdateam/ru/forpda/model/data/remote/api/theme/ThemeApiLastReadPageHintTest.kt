package forpdateam.ru.forpda.model.data.remote.api.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeApiLastReadPageHintTest {

    @Test
    fun topHint_matchesFirstEntryOnly() {
        val entries = listOf(100, 101, 102)
        assertTrue(ThemeApi.isLikelyLastReadPageTopHint(100, entries))
        assertFalse(ThemeApi.isLikelyLastReadPageTopHint(101, entries))
    }

    @Test
    fun bottomHint_matchesLastEntryOnly() {
        val entries = listOf(100, 101, 102)
        assertTrue(ThemeApi.isLikelyLastReadPageBottomHint(102, entries))
        assertFalse(ThemeApi.isLikelyLastReadPageBottomHint(101, entries))
        assertFalse(ThemeApi.isLikelyLastReadPageBottomHint(102, listOf(102)))
    }

    @Test
    fun allReadBottomRedirect_detectsLastEntryWithoutUnreadMarkers() {
        val entries = listOf(143179849, 143784670, 143784679)
        assertTrue(ThemeApi.isLikelyAllReadGetNewPostBottomRedirect(143784679, entries))
        assertFalse(ThemeApi.isLikelyAllReadGetNewPostBottomRedirect(143179849, entries))
        assertFalse(ThemeApi.isLikelyAllReadGetNewPostBottomRedirect(null, entries))
    }

    @Test
    fun allReadBottomRedirect_requiresMultipleEntriesOnPage() {
        assertFalse(ThemeApi.isLikelyAllReadGetNewPostBottomRedirect(302, listOf(302)))
    }
}
