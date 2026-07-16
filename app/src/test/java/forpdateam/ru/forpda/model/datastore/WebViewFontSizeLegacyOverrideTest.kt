package forpdateam.ru.forpda.model.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for «Размер шрифта в темах не меняется / остаётся на 13».
 *
 * The topic renderer reads the font size through [MainDataStore.getWebViewFontSizeImmediate],
 * which consults the ORIGINAL-ForPDA legacy `<pkg>_preferences` key FIRST. Users who upgraded
 * from the stock app carry that key (commonly stuck at 13). The new size dialog wrote only the
 * DataStore + `main_mirror`, so the stale legacy value kept winning and every change/reset was
 * ignored — but only on those devices, which is why it «works for me» on a clean install.
 *
 * The fix makes [MainDataStore.setWebViewFontSize] authoritative across all three stores. This
 * test pins that: with a stale legacy value present, a write must win.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebViewFontSizeLegacyOverrideTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun legacyPrefs() =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    @Test
    fun `stale legacy font size no longer overrides a fresh write`() = runBlocking {
        // Simulate an upgraded-from-stock user: legacy key stuck at 13.
        legacyPrefs().edit().putInt(AppPreferences.Main.WEBVIEW_FONT_SIZE, 13).commit()

        val store = MainDataStore(context)

        // Sanity: before any write the renderer honours the migrated legacy seed.
        assertEquals(13, store.getWebViewFontSizeImmediate())

        // User changes the size in the dialog → the renderer path must reflect it (was the bug).
        store.setWebViewFontSize(30)
        assertEquals(30, store.getWebViewFontSizeImmediate())

        // «Сброс» → 16 must also stick.
        store.setWebViewFontSize(16)
        assertEquals(16, store.getWebViewFontSizeImmediate())
    }
}
