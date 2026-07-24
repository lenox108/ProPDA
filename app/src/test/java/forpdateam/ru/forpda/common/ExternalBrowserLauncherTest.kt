package forpdateam.ru.forpda.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
