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

    fun hatEnabledFlow(): Flow<Boolean> = dataStore.hatEnabledFlow()

    // --- Слежение за новыми версиями (apk в шапке) ---
    fun getHatEnabled(): Boolean = dataStore.getHatEnabledSync()

    fun getHatWatchTopics(): Set<Int> = dataStore.getHatWatchTopicsSync()

    fun isHatWatched(topicId: Int): Boolean = dataStore.isHatWatchedSync(topicId)

    fun toggleHatWatch(topicId: Int): Boolean = dataStore.toggleHatWatchSync(topicId)

    fun getHatApkSnapshot(topicId: Int): Set<String> = dataStore.getHatApkSnapshotSync(topicId)

    fun hasHatApkSnapshot(topicId: Int): Boolean = dataStore.hasHatApkSnapshotSync(topicId)

    fun setHatApkSnapshot(topicId: Int, value: Set<String>) = dataStore.setHatApkSnapshotSync(topicId, value)

    // --- Bg-check / muted topics ---
    fun getBgCheckEnabled(): Boolean = dataStore.getBgCheckEnabledSync()

    fun getBgCheckIntervalMin(): Long = dataStore.getBgCheckIntervalMinSync()

    fun bgPersistentWsFlow(): Flow<Boolean> = dataStore.bgPersistentWsFlow()

    fun getBgPersistentWs(): Boolean = dataStore.getBgPersistentWsSync()

    fun getBgNightSlowdown(): Boolean = dataStore.getBgNightSlowdownSync()

    /**
     * Интервал с учётом «Реже проверять ночью»: с 00:00 до 07:00 — не чаще раза в час.
     * Планировщик будильника и дедуп воркера обязаны сходиться в этот метод.
     */
    fun getEffectiveBgIntervalMin(hourOfDay: Int): Long {
        val base = getBgCheckIntervalMin()
        if (!getBgNightSlowdown()) return base
        return if (hourOfDay in 0..6) maxOf(base, 60L) else base
    }

    // --- Самодиагностика фоновых проверок ---
    fun getLastWorkerRunAt(): Long = dataStore.getLastWorkerRunAtSync()

    fun setLastWorkerRunAt(value: Long) = dataStore.setLastWorkerRunAtSync(value)

    fun getBgScheduledAt(): Long = dataStore.getBgScheduledAtSync()

    fun setBgScheduledAt(value: Long) = dataStore.setBgScheduledAtSync(value)

    fun getLastCheckAt(): Long = dataStore.getLastCheckAtSync()

    fun setLastCheckAt(value: Long) = dataStore.setLastCheckAtSync(value)

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

    fun getFavEventsSeeded(): Boolean = dataStore.getFavEventsSeededSync()

    fun setFavEventsSeeded(value: Boolean) = dataStore.setFavEventsSeededSync(value)

    fun getQmsEventsSeeded(): Boolean = dataStore.getQmsEventsSeededSync()

    fun setQmsEventsSeeded(value: Boolean) = dataStore.setQmsEventsSeededSync(value)

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
