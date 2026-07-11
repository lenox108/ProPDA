package forpdateam.ru.forpda.model.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.model.preferences.ForumBlacklistSerializer
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

private val Context.topicDataStore: DataStore<Preferences> by preferencesDataStore(name = "topic")

class TopicDataStore(private val context: Context) {
    private val mirrorPrefs = context.getSharedPreferences("topic_mirror", Context.MODE_PRIVATE)

    private inline fun <T> safeDataStoreFlow(
            flow: Flow<T>,
            default: T
    ): Flow<T> = flow.catch { e ->
        Timber.e(e, "DataStore read error, returning default")
        emit(default)
    }

    private suspend inline fun safeEdit(crossinline block: suspend (MutablePreferences) -> Unit) {
        try {
            context.topicDataStore.edit { preferences ->
                block(preferences)
            }
        } catch (e: Exception) {
            Timber.e(e, "DataStore edit error")
        }
    }

    private object PreferencesKeys {
        val SHOW_AVATARS = booleanPreferencesKey("show_avatars")
        val CIRCLE_AVATARS = booleanPreferencesKey("circle_avatars")
        val ANIMATED_SMILES = booleanPreferencesKey("animated_smiles")
        val ANCHOR_HISTORY = booleanPreferencesKey("anchor_history")
        val HAT_OPENED = booleanPreferencesKey("hat_opened")
        val FORUM_BLACKLIST = stringPreferencesKey("forum_blacklist")

        fun inlineHatOpened(topicId: Int) = booleanPreferencesKey("inline_hat_opened_$topicId")
    }

    fun observeShowAvatarsFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.topicDataStore.data.map { preferences ->
                preferences[PreferencesKeys.SHOW_AVATARS]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Theme.SHOW_AVATARS, true)
            }, true)

    fun observeCircleAvatarsFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.topicDataStore.data.map { preferences ->
                preferences[PreferencesKeys.CIRCLE_AVATARS]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Theme.CIRCLE_AVATARS, true)
            }, true)

    fun observeAnimatedSmilesFlow(): Flow<Boolean> =
            safeDataStoreFlow(context.topicDataStore.data.map { preferences ->
                preferences[PreferencesKeys.ANIMATED_SMILES]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Theme.ANIMATED_SMILES, true)
            }, true)

    suspend fun getShowAvatars(): Boolean =
            observeShowAvatarsFlow().map { it }.first()

    suspend fun getCircleAvatars(): Boolean =
            observeCircleAvatarsFlow().map { it }.first()

    suspend fun setShowAvatars(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.SHOW_AVATARS] = value
        }
        mirrorPrefs.edit().putBoolean("show_avatars", value).apply()
    }

    fun getShowAvatarsImmediate(): Boolean = mirrorPrefs.getBoolean("show_avatars", true)

    suspend fun setCircleAvatars(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.CIRCLE_AVATARS] = value
        }
        mirrorPrefs.edit().putBoolean("circle_avatars", value).apply()
    }

    fun getCircleAvatarsImmediate(): Boolean = mirrorPrefs.getBoolean("circle_avatars", true)

    suspend fun setAnimatedSmiles(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.ANIMATED_SMILES] = value
        }
        mirrorPrefs.edit().putBoolean("animated_smiles", value).apply()
    }

    fun getAnimatedSmilesImmediate(): Boolean = mirrorPrefs.getBoolean("animated_smiles", true)

    suspend fun setAnchorHistory(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.ANCHOR_HISTORY] = value
        }
        mirrorPrefs.edit().putBoolean("anchor_history", value).apply()
    }

    fun getAnchorHistoryImmediate(): Boolean = mirrorPrefs.getBoolean("anchor_history", true)

    suspend fun setHatOpened(value: Boolean) {
        safeEdit { preferences ->
            preferences[PreferencesKeys.HAT_OPENED] = value
        }
        mirrorPrefs.edit().putBoolean("hat_opened", value).apply()
    }

    fun getHatOpenedImmediate(): Boolean = mirrorPrefs.getBoolean("hat_opened", false)

    suspend fun setInlineHatOpened(topicId: Int, value: Boolean) {
        if (topicId <= 0) return
        safeEdit { preferences ->
            preferences[PreferencesKeys.inlineHatOpened(topicId)] = value
        }
        mirrorPrefs.edit().putBoolean("inline_hat_opened_$topicId", value).apply()
    }

    fun getInlineHatOpenedImmediate(topicId: Int): Boolean =
        if (topicId <= 0) false else mirrorPrefs.getBoolean("inline_hat_opened_$topicId", false)

    fun hasInlineHatPreferenceImmediate(topicId: Int): Boolean =
        topicId > 0 && mirrorPrefs.contains("inline_hat_opened_$topicId")

    fun observeForumBlacklistFlow(): Flow<List<ForumBlacklistedUser>> =
            safeDataStoreFlow(context.topicDataStore.data.map { preferences ->
                ForumBlacklistSerializer.deserialize(
                        preferences[PreferencesKeys.FORUM_BLACKLIST]
                                ?: mirrorPrefs.getString(AppPreferences.Theme.FORUM_BLACKLIST, null)
                )
            }, emptyList())

    fun getForumBlacklistImmediate(): List<ForumBlacklistedUser> =
            ForumBlacklistSerializer.deserialize(mirrorPrefs.getString(AppPreferences.Theme.FORUM_BLACKLIST, null))

    fun isForumBlacklistedImmediate(userId: Int, nick: String?): Boolean =
            getForumBlacklistImmediate().any { it.matches(userId, nick) }

    suspend fun addForumBlacklistedUser(user: ForumBlacklistedUser) {
        val raw = ForumBlacklistSerializer.add(
                mirrorPrefs.getString(AppPreferences.Theme.FORUM_BLACKLIST, null),
                user
        )
        safeEdit { preferences ->
            preferences[PreferencesKeys.FORUM_BLACKLIST] = raw
        }
        mirrorPrefs.edit().putString(AppPreferences.Theme.FORUM_BLACKLIST, raw).apply()
    }

    suspend fun removeForumBlacklistedUser(user: ForumBlacklistedUser) {
        val raw = ForumBlacklistSerializer.remove(
                mirrorPrefs.getString(AppPreferences.Theme.FORUM_BLACKLIST, null),
                user
        )
        safeEdit { preferences ->
            preferences[PreferencesKeys.FORUM_BLACKLIST] = raw
        }
        mirrorPrefs.edit().putString(AppPreferences.Theme.FORUM_BLACKLIST, raw).apply()
    }

    suspend fun getAnchorHistory(): Boolean =
            context.topicDataStore.data.map { preferences ->
                preferences[PreferencesKeys.ANCHOR_HISTORY]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Theme.ANCHOR_HISTORY, true)
            }.first()

    suspend fun getHatOpened(): Boolean =
            context.topicDataStore.data.map { preferences ->
                preferences[PreferencesKeys.HAT_OPENED]
                    ?: context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
                        .getBoolean(AppPreferences.Theme.HAT_OPENED, false)
            }.first()

    suspend fun getInlineHatOpened(topicId: Int): Boolean =
            if (topicId <= 0) {
                false
            } else {
                context.topicDataStore.data.map { preferences ->
                    preferences[PreferencesKeys.inlineHatOpened(topicId)] ?: false
                }.first()
            }

}
