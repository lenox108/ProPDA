package forpdateam.ru.forpda.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.listsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lists")

class ListsDataStore(private val context: Context) {
    private val mirrorPrefs = context.getSharedPreferences("lists_mirror", Context.MODE_PRIVATE)

    private inline fun <T> safeDataStoreFlow(
            flow: Flow<T>,
            default: T
    ): Flow<T> = flow.catch { e ->
        Timber.e(e, "DataStore read error, returning default")
        emit(default)
    }

    private suspend inline fun safeEdit(crossinline block: suspend (MutablePreferences) -> Unit) {
        try {
            context.listsDataStore.edit { preferences ->
                block(preferences)
            }
        } catch (e: Exception) {
            Timber.e(e, "DataStore edit error")
        }
    }

    private object PreferencesKeys {
        val UNREAD_TOP = booleanPreferencesKey(AppPreferences.Lists.Topic.UNREAD_TOP)
        val LEGACY_UNREAD_TOP = booleanPreferencesKey("unread_top")
        val SHOW_DOT = booleanPreferencesKey("show_dot")
        val FAV_LOAD_ALL = booleanPreferencesKey("lists.favorites.load_all")
        val FAV_SHOW_UNREAD_BADGE = booleanPreferencesKey("lists.favorites.show_unread_badge")
        val SORTING_KEY = stringPreferencesKey(AppPreferences.Lists.Favorites.SORTING_KEY)
        val SORTING_ORDER = stringPreferencesKey(AppPreferences.Lists.Favorites.SORTING_ORDER)
        val LEGACY_SORTING_KEY = stringPreferencesKey("sorting_key")
        val LEGACY_SORTING_ORDER = stringPreferencesKey("sorting_order")
        // Локально скрытые из списка избранного темы/форумы (не удаляются на сервере).
        val FAV_HIDDEN_TOPIC_IDS = stringSetPreferencesKey("lists.favorites.hidden_topic_ids")
        val FAV_HIDDEN_FORUM_IDS = stringSetPreferencesKey("lists.favorites.hidden_forum_ids")
    }

    private val mirrorKeyHiddenTopicIds = "lists.favorites.hidden_topic_ids"
    private val mirrorKeyHiddenForumIds = "lists.favorites.hidden_forum_ids"

    private val legacyPrefs = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)

    fun observeUnreadTopFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.listsDataStore.data.map { preferences ->
                preferences[PreferencesKeys.UNREAD_TOP]
                        ?: preferences[PreferencesKeys.LEGACY_UNREAD_TOP]
                        ?: legacyPrefs.booleanOrNull(AppPreferences.Lists.Topic.UNREAD_TOP)
                        ?: false
            }, false)

    fun observeShowDotFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.listsDataStore.data.map { preferences ->
                preferences[PreferencesKeys.SHOW_DOT]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Lists.Topic.SHOW_DOT, false)
            }, false)

    fun observeFavLoadAllFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.listsDataStore.data.map { preferences ->
                preferences[PreferencesKeys.FAV_LOAD_ALL]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean("lists.favorites.load_all", false)
            }, false)

    fun observeFavShowUnreadBadgeFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.listsDataStore.data.map { preferences ->
                preferences[PreferencesKeys.FAV_SHOW_UNREAD_BADGE]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Lists.Favorites.SHOW_UNREAD_BADGE, true)
            }, true)

    suspend fun setSortingKey(key: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SORTING_KEY] = key
        }
        mirrorPrefs.edit().putString(AppPreferences.Lists.Favorites.SORTING_KEY, key).apply()
    }

    fun getSortingKeyImmediate(): String =
        mirrorPrefs.getString(AppPreferences.Lists.Favorites.SORTING_KEY, null)
                ?: mirrorPrefs.getString("sorting_key", null)
                ?: legacyPrefs.getString(AppPreferences.Lists.Favorites.SORTING_KEY, null)
                ?: Sorting.Companion.Key.LAST_POST

    suspend fun setSortingOrder(order: String) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SORTING_ORDER] = order
        }
        mirrorPrefs.edit().putString(AppPreferences.Lists.Favorites.SORTING_ORDER, order).apply()
    }

    fun getSortingOrderImmediate(): String =
        mirrorPrefs.getString(AppPreferences.Lists.Favorites.SORTING_ORDER, null)
                ?: mirrorPrefs.getString("sorting_order", null)
                ?: legacyPrefs.getString(AppPreferences.Lists.Favorites.SORTING_ORDER, null)
                ?: Sorting.Companion.Order.DESC

    suspend fun setUnreadTop(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.UNREAD_TOP] = value
        }
        mirrorPrefs.edit()
                .putBoolean(AppPreferences.Lists.Topic.UNREAD_TOP, value)
                .putBoolean("unread_top", value)
                .apply()
    }

    fun getUnreadTopImmediate(): Boolean =
            mirrorPrefs.booleanOrNull(AppPreferences.Lists.Topic.UNREAD_TOP)
                    ?: mirrorPrefs.booleanOrNull("unread_top")
                    ?: legacyPrefs.booleanOrNull(AppPreferences.Lists.Topic.UNREAD_TOP)
                    ?: false

    suspend fun setShowDot(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SHOW_DOT] = value
        }
        mirrorPrefs.edit().putBoolean("show_dot", value).apply()
    }

    fun getShowDotImmediate(): Boolean = mirrorPrefs.getBoolean("show_dot", false)

    suspend fun getUnreadTop(): Boolean =
            observeUnreadTopFlow().map { it }.first()

    suspend fun getShowDot(): Boolean =
            observeShowDotFlow().map { it }.first()

    suspend fun setFavLoadAll(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.FAV_LOAD_ALL] = value
        }
        mirrorPrefs.edit().putBoolean("lists.favorites.load_all", value).apply()
    }

    fun getFavLoadAllImmediate(): Boolean = mirrorPrefs.getBoolean("lists.favorites.load_all", false)

    suspend fun getFavLoadAll(): Boolean =
            observeFavLoadAllFlow().map { it }.first()

    suspend fun setFavShowUnreadBadge(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.FAV_SHOW_UNREAD_BADGE] = value
        }
        mirrorPrefs.edit().putBoolean(AppPreferences.Lists.Favorites.SHOW_UNREAD_BADGE, value).apply()
    }

    fun getFavShowUnreadBadgeImmediate(): Boolean =
            mirrorPrefs.getBoolean(AppPreferences.Lists.Favorites.SHOW_UNREAD_BADGE, true)

    suspend fun getFavShowUnreadBadge(): Boolean =
            observeFavShowUnreadBadgeFlow().map { it }.first()

    // --- Скрытые из списка избранного (локально) ---
    fun observeHiddenTopicIdsFlow(): Flow<Set<Int>> =
            safeDataStoreFlow(context.listsDataStore.data.map { preferences ->
                preferences[PreferencesKeys.FAV_HIDDEN_TOPIC_IDS].toIntIdSet()
            }, emptySet())

    fun observeHiddenForumIdsFlow(): Flow<Set<Int>> =
            safeDataStoreFlow(context.listsDataStore.data.map { preferences ->
                preferences[PreferencesKeys.FAV_HIDDEN_FORUM_IDS].toIntIdSet()
            }, emptySet())

    fun getHiddenTopicIdsImmediate(): Set<Int> =
            mirrorPrefs.getStringSet(mirrorKeyHiddenTopicIds, emptySet()).toIntIdSet()

    fun getHiddenForumIdsImmediate(): Set<Int> =
            mirrorPrefs.getStringSet(mirrorKeyHiddenForumIds, emptySet()).toIntIdSet()

    suspend fun setHiddenTopicIds(value: Set<Int>) {
        val asStrings = value.map { it.toString() }.toSet()
        safeEdit { preferences -> preferences[PreferencesKeys.FAV_HIDDEN_TOPIC_IDS] = asStrings }
        mirrorPrefs.edit().putStringSet(mirrorKeyHiddenTopicIds, asStrings).apply()
    }

    suspend fun setHiddenForumIds(value: Set<Int>) {
        val asStrings = value.map { it.toString() }.toSet()
        safeEdit { preferences -> preferences[PreferencesKeys.FAV_HIDDEN_FORUM_IDS] = asStrings }
        mirrorPrefs.edit().putStringSet(mirrorKeyHiddenForumIds, asStrings).apply()
    }

    suspend fun getSortingKey(): String =
            context.listsDataStore.data.map { preferences ->
                val raw = preferences[PreferencesKeys.SORTING_KEY]
                        ?: preferences[PreferencesKeys.LEGACY_SORTING_KEY]
                        ?: legacyPrefs.getString(AppPreferences.Lists.Favorites.SORTING_KEY, null)
                if (raw.isNullOrBlank()) Sorting.Companion.Key.LAST_POST else raw
            }.first()

    suspend fun getSortingOrder(): String =
            context.listsDataStore.data.map { preferences ->
                val raw = preferences[PreferencesKeys.SORTING_ORDER]
                        ?: preferences[PreferencesKeys.LEGACY_SORTING_ORDER]
                        ?: legacyPrefs.getString(AppPreferences.Lists.Favorites.SORTING_ORDER, null)
                if (raw.isNullOrBlank()) Sorting.Companion.Order.DESC else raw
            }.first()

}

private fun android.content.SharedPreferences.booleanOrNull(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

private fun Set<String>?.toIntIdSet(): Set<Int> =
        this?.mapNotNull { it.trim().toIntOrNull() }?.filter { it > 0 }?.toSet() ?: emptySet()
