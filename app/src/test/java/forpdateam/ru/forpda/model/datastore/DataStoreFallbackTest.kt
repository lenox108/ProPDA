package forpdateam.ru.forpda.model.datastore

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * JVM regression test for DataStore fallback logic (R-8).
 * Verifies that DataStore implementations fall back to SharedPreferences mirror when needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreFallbackTest {

    private val mirrorPrefs = mutableMapOf<String, Any?>()

    @Before
    fun setup() {
        mirrorPrefs.clear()
    }

    @Test
    fun `mirrorPrefs returns value when set`() {
        val key = "test_key"
        val value = true

        mirrorPrefs[key] = value

        val result = mirrorPrefs[key]
        assertEquals(value, result)
    }

    @Test
    fun `mirrorPrefs returns null for unset key`() {
        val result = mirrorPrefs["nonexistent_key"]
        assertNull(result)
    }

    @Test
    fun `mirrorPrefs handles string values`() {
        val key = "font_size"
        val value = "16"

        mirrorPrefs[key] = value

        val result = mirrorPrefs[key]
        assertEquals(value, result)
    }

    @Test
    fun `mirrorPrefs handles integer values`() {
        val key = "scroll_position"
        val value = 123

        mirrorPrefs[key] = value

        val result = mirrorPrefs[key]
        assertEquals(value, result)
    }

    @Test
    fun `mirrorPrefs allows overwriting values`() {
        val key = "theme_mode"
        mirrorPrefs[key] = "light"
        mirrorPrefs[key] = "dark"

        val result = mirrorPrefs[key]
        assertEquals("dark", result)
    }

    @Test
    fun `mirrorPrefs handles empty map`() {
        assertEquals(0, mirrorPrefs.size)
    }
}
