package forpdateam.ru.forpda.model.preferences

import android.content.Context
import androidx.collection.ArraySet
import forpdateam.ru.forpda.model.datastore.NotificationDataStore
import kotlinx.coroutines.flow.Flow

class NotificationPreferencesHolder(
        private val context: Context
) {
    private val dataStore = NotificationDataStore(context)

    // --- Flow-наблюдения (реактивное обновление без RxJava) ---
    fun favEnabledFlow(): Flow<Boolean> = dataStore.favEnabledFlow()

    fun qmsEnabledFlow(): Flow<Boolean> = dataStore.qmsEnabledFlow()

    fun mainEnabledFlow(): Flow<Boolean> = dataStore.mainEnabledFlow()

    fun wantsPushNotificationsFlow(): Flow<Boolean> = dataStore.wantsPushNotificationsFlow()

    fun bgCheckEnabledFlow(): Flow<Boolean> = dataStore.bgCheckEnabledFlow()

    fun bgCheckIntervalMinFlow(): Flow<Long> = dataStore.bgCheckIntervalMinFlow()

    fun mutedTopicsFlow(): Flow<Set<Int>> = dataStore.mutedTopicsFlow()

    // --- Bg-check / muted topics ---
    fun getBgCheckEnabled(): Boolean = dataStore.getBgCheckEnabledSync()

    fun getBgCheckIntervalMin(): Long = dataStore.getBgCheckIntervalMinSync()

    fun isTopicMuted(topicId: Int): Boolean = dataStore.isTopicMutedSync(topicId)

    fun getMutedTopics(): Set<Int> = dataStore.getMutedTopicsSync()

    fun muteTopic(topicId: Int) = dataStore.muteTopicSync(topicId)

    fun unmuteTopic(topicId: Int) = dataStore.unmuteTopicSync(topicId)

    fun toggleTopicMute(topicId: Int): Boolean = dataStore.toggleTopicMuteSync(topicId)

    // --- Фоновые упоминания ---
    fun getNotifiedMentionKeys(): Set<String> = dataStore.getNotifiedMentionKeysSync()

    fun setNotifiedMentionKeys(value: Set<String>) = dataStore.setNotifiedMentionKeysSync(value)

    fun getMentionKeysSeeded(): Boolean = dataStore.getMentionKeysSeededSync()

    fun setMentionKeysSeeded(value: Boolean) = dataStore.setMentionKeysSeededSync(value)

    // --- Синхронные геттеры/сеттеры ---
    fun setDataQmsEvents(value: Set<String>) = dataStore.setDataQmsEventsSync(value)

    fun setDataFavoritesEvents(value: Set<String>) = dataStore.setDataFavoritesEventsSync(value)

    fun getMainEnabled(): Boolean = dataStore.getMainEnabledSync()

    fun getMainAvatarsEnabled(): Boolean = dataStore.getMainAvatarsEnabledSync()

    fun getFavEnabled(): Boolean = dataStore.getFavEnabledSync()

    fun getFavOnlyImportant(): Boolean = dataStore.getFavOnlyImportantSync()

    fun getFavLiveTab(): Boolean = dataStore.getFavLiveTabSync()

    fun getQmsEnabled(): Boolean = dataStore.getQmsEnabledSync()

    fun getMentionsEnabled(): Boolean = dataStore.getMentionsEnabledSync()

    fun getDownloadsEnabled(): Boolean = dataStore.getDownloadsEnabledSync()

    fun getDataQmsEvents(): Set<String> = dataStore.getDataQmsEventsSync()

    fun getDataFavoritesEvents(): Set<String> = dataStore.getDataFavoritesEventsSync()

    /** Нужны push-каналы (темы / QMS / упоминания) при включённых уведомлениях. */
    fun wantsPushNotifications(): Boolean = dataStore.wantsPushNotificationsSync()
}
