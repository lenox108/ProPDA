package forpdateam.ru.forpda.ui.fragments.theme.modules

import forpdateam.ru.forpda.ui.views.ExtendedWebView
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeJsApiTest {

    private val jsApi = ThemeJsApi(mockk<ExtendedWebView>(relaxed = true))

    @Test
    fun nativeScrollToAnchorPost_doesNotScheduleJsAnchorRetries() {
        val script = jsApi.nativeScrollToAnchorPost("entry143825965")

        assertFalse(script.contains("scrollToElementWithRetries"))
        assertFalse(script.contains("scrollToElement("))
        assertTrue(script.contains("window.scrollTo(0,y)"))
    }
}
