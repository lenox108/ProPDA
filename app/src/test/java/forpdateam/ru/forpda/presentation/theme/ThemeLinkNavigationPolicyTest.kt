package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ThemeLinkNavigationPolicyTest {

    @Test
    fun `resolve navigates to forum href when resolved is image preview`() {
        val preview = "https://4pda.to/s/Zy0hJxxcT0JoRTz1nS4BM7mUldoNq5UXYYVk.png"
        val forumHref = "https://4pda.to/forum/index.php?showtopic=239158&view=findpost&p=120759588"

        val decision = ThemeLinkNavigationPolicy.resolve(preview, forumHref)

        assertEquals(ThemeLinkNavigationAction.NAVIGATE_TO_URL, decision.action)
        assertEquals(forumHref, decision.url)
    }

    @Test
    fun `resolve downloads apk when href is file and resolved is gif preview`() {
        val preview = "https://4pda.to/s/Zy0hRMDmDtR8z09yu8z22rW0oH7HPbiK40M5cDUEPz0TXXYoMr9sh3X7z1njalEH.gif"
        val apkHref = "https://4pda.to/forum/dl/post/35656696/ProPDA-2.9.4fixCrash.apk"

        val decision = ThemeLinkNavigationPolicy.resolve(preview, apkHref)

        assertEquals(ThemeLinkNavigationAction.DOWNLOAD_URL, decision.action)
        assertEquals(apkHref, decision.url)
    }

    @Test
    fun `resolve downloads when href equals resolved image url`() {
        val image = "https://4pda.to/forum/dl/post/1/photo.jpg"

        val decision = ThemeLinkNavigationPolicy.resolve(image, image)

        assertEquals(ThemeLinkNavigationAction.DOWNLOAD_URL, decision.action)
        assertEquals(image, decision.url)
    }

    @Test
    fun `resolve opens viewer for plain image tap without source href`() {
        val preview = "https://4pda.to/s/Zy0hJdJhXo4sHCLpS6kl8e2vWUPl8z08mpImL7l5uxWk.jpg"

        val decision = ThemeLinkNavigationPolicy.resolve(preview)

        assertEquals(ThemeLinkNavigationAction.OPEN_IMAGE_VIEWER, decision.action)
        assertEquals(preview, decision.url)
    }

    @Test
    fun `resolve navigates cross topic link wrapped in preview image`() {
        val preview = "https://4pda.to/s/thumb.png"
        val topicHref = "https://4pda.to/forum/index.php?showtopic=239158"

        val decision = ThemeLinkNavigationPolicy.resolve(preview, topicHref)

        assertEquals(ThemeLinkNavigationAction.NAVIGATE_TO_URL, decision.action)
        assertEquals(topicHref, decision.url)
    }
}
