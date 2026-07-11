package forpdateam.ru.forpda.model.preferences

import android.content.Context
import forpdateam.ru.forpda.model.datastore.TopicDataStore
import kotlinx.coroutines.flow.Flow

class TopicPreferencesHolder(
        private val context: Context
) {
    private val dataStore = TopicDataStore(context)

    fun observeShowAvatarsFlow(): Flow<Boolean> = dataStore.observeShowAvatarsFlow()

    fun observeCircleAvatarsFlow(): Flow<Boolean> = dataStore.observeCircleAvatarsFlow()

    fun observeAnimatedSmilesFlow(): Flow<Boolean> = dataStore.observeAnimatedSmilesFlow()

    fun observeForumBlacklistFlow(): Flow<List<ForumBlacklistedUser>> = dataStore.observeForumBlacklistFlow()

    fun getShowAvatars(): Boolean = dataStore.getShowAvatarsImmediate()

    fun getCircleAvatars(): Boolean = dataStore.getCircleAvatarsImmediate()

    fun getAnimatedSmiles(): Boolean = dataStore.getAnimatedSmilesImmediate()

    fun getForumBlacklist(): List<ForumBlacklistedUser> = dataStore.getForumBlacklistImmediate()

    fun isForumBlacklisted(userId: Int, nick: String?): Boolean = dataStore.isForumBlacklistedImmediate(userId, nick)

    fun getAnchorHistory(): Boolean = dataStore.getAnchorHistoryImmediate()

    fun getHatOpened(): Boolean = dataStore.getHatOpenedImmediate()

    fun getInlineHatOpened(topicId: Int): Boolean = dataStore.getInlineHatOpenedImmediate(topicId)

    fun hasInlineHatPreference(topicId: Int): Boolean = dataStore.hasInlineHatPreferenceImmediate(topicId)

    suspend fun setShowAvatars(value: Boolean) = dataStore.setShowAvatars(value)

    suspend fun setCircleAvatars(value: Boolean) = dataStore.setCircleAvatars(value)

    suspend fun setAnimatedSmiles(value: Boolean) = dataStore.setAnimatedSmiles(value)

    suspend fun setAnchorHistory(value: Boolean) = dataStore.setAnchorHistory(value)

    suspend fun setHatOpened(value: Boolean) = dataStore.setHatOpened(value)

    suspend fun setInlineHatOpened(topicId: Int, value: Boolean) = dataStore.setInlineHatOpened(topicId, value)

    suspend fun addForumBlacklistedUser(user: ForumBlacklistedUser) = dataStore.addForumBlacklistedUser(user)

    suspend fun removeForumBlacklistedUser(user: ForumBlacklistedUser) = dataStore.removeForumBlacklistedUser(user)
}
