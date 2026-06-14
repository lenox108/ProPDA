package forpdateam.ru.forpda.model.datastore

import android.content.Context
import android.content.SharedPreferences
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ListsDataStoreTest {

    private val context: Context = mockk()
    private val mirrorPrefs: SharedPreferences = mockk()
    private val legacyPrefs: SharedPreferences = mockk()

    @Test
    fun `sorting immediate getters fall back to legacy favorites preferences`() {
        every { context.packageName } returns PACKAGE_NAME
        every { context.getSharedPreferences("lists_mirror", Context.MODE_PRIVATE) } returns mirrorPrefs
        every { context.getSharedPreferences("${PACKAGE_NAME}_preferences", Context.MODE_PRIVATE) } returns legacyPrefs
        every { mirrorPrefs.getString(Preferences.Lists.Favorites.SORTING_KEY, null) } returns null
        every { mirrorPrefs.getString(Preferences.Lists.Favorites.SORTING_ORDER, null) } returns null
        every { mirrorPrefs.getString("sorting_key", null) } returns null
        every { mirrorPrefs.getString("sorting_order", null) } returns null
        every {
            legacyPrefs.getString(Preferences.Lists.Favorites.SORTING_KEY, null)
        } returns Sorting.Companion.Key.TITLE
        every {
            legacyPrefs.getString(Preferences.Lists.Favorites.SORTING_ORDER, null)
        } returns Sorting.Companion.Order.ASC

        val dataStore = ListsDataStore(context)

        assertEquals(Sorting.Companion.Key.TITLE, dataStore.getSortingKeyImmediate())
        assertEquals(Sorting.Companion.Order.ASC, dataStore.getSortingOrderImmediate())
    }

    @Test
    fun `unread top immediate getter uses xml preference key before legacy mirror key`() {
        every { context.packageName } returns PACKAGE_NAME
        every { context.getSharedPreferences("lists_mirror", Context.MODE_PRIVATE) } returns mirrorPrefs
        every { context.getSharedPreferences("${PACKAGE_NAME}_preferences", Context.MODE_PRIVATE) } returns legacyPrefs
        every { mirrorPrefs.contains(Preferences.Lists.Topic.UNREAD_TOP) } returns true
        every { mirrorPrefs.getBoolean(Preferences.Lists.Topic.UNREAD_TOP, false) } returns true
        every { mirrorPrefs.contains("unread_top") } returns true
        every { mirrorPrefs.getBoolean("unread_top", false) } returns false

        val dataStore = ListsDataStore(context)

        assertEquals(true, dataStore.getUnreadTopImmediate())
    }

    @Test
    fun `unread top immediate getter falls back to settings shared preferences`() {
        every { context.packageName } returns PACKAGE_NAME
        every { context.getSharedPreferences("lists_mirror", Context.MODE_PRIVATE) } returns mirrorPrefs
        every { context.getSharedPreferences("${PACKAGE_NAME}_preferences", Context.MODE_PRIVATE) } returns legacyPrefs
        every { mirrorPrefs.contains(Preferences.Lists.Topic.UNREAD_TOP) } returns false
        every { mirrorPrefs.contains("unread_top") } returns false
        every { legacyPrefs.contains(Preferences.Lists.Topic.UNREAD_TOP) } returns true
        every { legacyPrefs.getBoolean(Preferences.Lists.Topic.UNREAD_TOP, false) } returns true

        val dataStore = ListsDataStore(context)

        assertEquals(true, dataStore.getUnreadTopImmediate())
    }

    companion object {
        private const val PACKAGE_NAME = "forpdateam.ru.forpda.test"
    }
}
