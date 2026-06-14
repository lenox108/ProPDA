package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: stale favorites/topics unread hints must not affect in-tab navigation. */
class TopicOpenListHintsLifecycleTest {

    @Test
    fun firstOpen_keepsHintsUntilPageLoaded() {
        assertFalse(ThemeViewModel.shouldClearListOpenHints(hasLoadedPage = false))
    }

    @Test
    fun afterPageLoaded_implicitLoadUrl_clearsHints() {
        assertTrue(ThemeViewModel.shouldClearListOpenHints(hasLoadedPage = true))
    }
}
