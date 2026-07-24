package forpdateam.ru.forpda.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ExternalBrowserLauncherTest {

    @Test
    fun `selected browser is launched through system default resolution`() {
        val context = RecordingContext(rejectDefault = false)

        val launched = ExternalBrowserLauncher.launchSelectedBrowserIfSet(context, browserIntent())

        assertTrue(launched)
        val intent = context.startedIntents.single()
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0)
        assertNull(intent.component)
        assertNull(intent.`package`)
    }

    @Test
    fun `missing default browser falls through to browser chooser`() {
        val context = RecordingContext(rejectDefault = true)

        val launched = ExternalBrowserLauncher.launchSelectedBrowserIfSet(context, browserIntent())

        assertFalse(launched)
        assertEquals(1, context.startedIntents.size)
        assertTrue(
                context.startedIntents.single().flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0
        )
    }

    @Test
    fun `internal site URL is never launched implicitly back into this app`() {
        val context = RecordingContext(rejectDefault = false)
        val siteIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://4pda.to/forum/index.php?showtopic=123"),
        ).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val launched = ExternalBrowserLauncher.launchSelectedBrowserIfSet(context, siteIntent)

        assertFalse(launched)
        assertTrue(context.startedIntents.isEmpty())
    }

    @Test
    fun `internal site URL opens as real URL in explicitly resolved browser`() {
        val context = RecordingContext(rejectDefault = false)
        val browser = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "org.example.browser"
                name = "org.example.browser.MainActivity"
                applicationInfo = ApplicationInfo().apply {
                    packageName = "org.example.browser"
                    uid = context.applicationInfo.uid + 1
                }
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(browserIntent(), browser)
        val url = "https://4pda.to/forum/index.php?showtopic=123"

        val launched = ExternalBrowserLauncher.open(context, url)

        assertTrue(launched)
        val intent = context.startedIntents.single()
        assertEquals("org.example.browser", intent.component?.packageName)
        assertEquals(url, intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT == 0)
    }

    private fun browserIntent(): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }

    private class RecordingContext(
            private val rejectDefault: Boolean
    ) : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val startedIntents = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            startedIntents += Intent(intent)
            if (rejectDefault && intent.flags and Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT != 0) {
                throw ActivityNotFoundException("No browser selected")
            }
        }
    }
}
