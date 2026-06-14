package forpdateam.ru.forpda.downloads

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DownloadStoreTest {

    @Test
    fun `existing records without completion timestamp read as incomplete`() {
        val id = UUID.randomUUID()
        val store = DownloadStore(testPrefs())

        store.put(id, "https://example.com/file.zip", "file.zip", "application/zip")

        assertNull(store.get(id)?.completedAt)
    }

    @Test
    fun `markCompleted stores completion timestamp without changing metadata`() {
        val id = UUID.randomUUID()
        val store = DownloadStore(testPrefs())

        store.put(id, "https://example.com/file.zip", "file.zip", "application/zip")
        store.markCompleted(id, 1_779_100_800_000L)

        val meta = store.get(id)
        assertEquals("https://example.com/file.zip", meta?.url)
        assertEquals("file.zip", meta?.fileName)
        assertEquals("application/zip", meta?.mime)
        assertEquals(1_779_100_800_000L, meta?.completedAt)
    }

    private fun testPrefs() =
        RuntimeEnvironment.getApplication().getSharedPreferences(
            "download-store-${UUID.randomUUID()}",
            Context.MODE_PRIVATE
        )
}
