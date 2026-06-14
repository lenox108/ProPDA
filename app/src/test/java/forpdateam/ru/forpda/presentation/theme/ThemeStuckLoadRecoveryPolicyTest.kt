package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStuckLoadRecoveryPolicyTest {

    @Test
    fun reEmitsWhenPageHtmlPresent() {
        assertTrue(ThemeStuckLoadRecoveryPolicy.shouldReEmitLoadedPage("<html></html>"))
        assertFalse(ThemeStuckLoadRecoveryPolicy.shouldReEmitLoadedPage(null))
        assertFalse(ThemeStuckLoadRecoveryPolicy.shouldReEmitLoadedPage(""))
    }

    @Test
    fun clearsOrphanedRefreshingWithoutPageOrJob() {
        assertTrue(
                ThemeStuckLoadRecoveryPolicy.shouldClearOrphanedRefreshing(
                        isRefreshing = true,
                        loadJobActive = false,
                        pageHtml = null,
                )
        )
        assertFalse(
                ThemeStuckLoadRecoveryPolicy.shouldClearOrphanedRefreshing(
                        isRefreshing = true,
                        loadJobActive = true,
                        pageHtml = null,
                )
        )
        assertFalse(
                ThemeStuckLoadRecoveryPolicy.shouldClearOrphanedRefreshing(
                        isRefreshing = true,
                        loadJobActive = false,
                        pageHtml = "<html></html>",
                )
        )
    }
}
