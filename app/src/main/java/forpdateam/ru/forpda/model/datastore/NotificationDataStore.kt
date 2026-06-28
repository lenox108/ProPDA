package forpdateam.ru.forpda.model.datastore

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Использует SharedPreferences для полной совместимости с UI настроек (preferences_notifications.xml).
 * UI и код приложения теперь используют одно и то же хранилище.
 */
class NotificationDataStore(private val context: Context) {

    companion object {
        // PreferenceFragmentCompat uses default name based on package name: {packageName}_preferences
        // Must be dynamic — flavors have different applicationIds.
        private fun prefsName(context: Context): String = "${context.packageName}_preferences"
        
        // Ключи должны совпадать с ключами в preferences_notifications.xml
        private const val KEY_MAIN_ENABLED = "notifications.main.enabled"
        private const val KEY_MAIN_SOUND_ENABLED = "notifications.main.sound_enabled"
        private const val KEY_MAIN_VIBRATION_ENABLED = "notifications.main.vibration_enabled"
        private const val KEY_MAIN_INDICATOR_ENABLED = "notifications.main.indicator_enabled"
        private const val KEY_MAIN_AVATARS_ENABLED = "notifications.main.avatars_enabled"
        private const val KEY_FAV_ENABLED = "notifications.fav.enabled"
        private const val KEY_FAV_ONLY_IMPORTANT = "notifications.fav.only_important"
        private const val KEY_FAV_LIVE_TAB = "notifications.fav.live_tab"
        private const val KEY_QMS_ENABLED = "notifications.qms.enabled"
        private const val KEY_MENTIONS_ENABLED = "notifications.mentions.enabled"
        private const val KEY_DOWNLOADS_ENABLED = "notifications.downloads.enabled"
        private const val KEY_DATA_QMS_EVENTS = "data_qms_events"
        private const val KEY_DATA_FAVORITES_EVENTS = "data_favorites_events"
        // Фоновая проверка уведомлений (WorkManager polling, когда сервис убит).
        private const val KEY_BG_CHECK_ENABLED = "notifications.bg.enabled"
        private const val KEY_BG_CHECK_INTERVAL_MIN = "notifications.bg.interval_min"
        // Список тем, для которых пользователь отключил уведомления локально.
        private const val KEY_MUTED_TOPIC_IDS = "notifications.muted_topic_ids"
    }

    // StateFlows для реактивных обновлений (instance-level для поддержки multiple contexts)
    private val mainEnabledFlow = MutableStateFlow(true)
    private val favEnabledFlow = MutableStateFlow(true)
    private val qmsEnabledFlow = MutableStateFlow(true)
    private val mentionsEnabledFlow = MutableStateFlow(true)
    private val bgCheckEnabledFlow = MutableStateFlow(true)
    private val bgCheckIntervalMinFlow = MutableStateFlow(15L) // 15 минут по умолчанию (минимум AOSP)
    private val mutedTopicsFlow = MutableStateFlow<Set<Int>>(emptySet())

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(prefsName(context), Context.MODE_PRIVATE).also {
            // Инициализируем StateFlows текущими значениями
            mainEnabledFlow.value = it.getBoolean(KEY_MAIN_ENABLED, true)
            favEnabledFlow.value = it.getBoolean(KEY_FAV_ENABLED, true)
            qmsEnabledFlow.value = it.getBoolean(KEY_QMS_ENABLED, true)
            mentionsEnabledFlow.value = it.getBoolean(KEY_MENTIONS_ENABLED, true)
            bgCheckEnabledFlow.value = it.getBoolean(KEY_BG_CHECK_ENABLED, true)
            bgCheckIntervalMinFlow.value = (it.getString(KEY_BG_CHECK_INTERVAL_MIN, "15")?.toLongOrNull() ?: 15L)
            mutedTopicsFlow.value = readMutedTopicsFromPrefs(it)

            // Слушатель изменений для обновления Flow
            it.registerOnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    KEY_MAIN_ENABLED -> mainEnabledFlow.value = it.getBoolean(KEY_MAIN_ENABLED, true)
                    KEY_FAV_ENABLED -> favEnabledFlow.value = it.getBoolean(KEY_FAV_ENABLED, true)
                    KEY_QMS_ENABLED -> qmsEnabledFlow.value = it.getBoolean(KEY_QMS_ENABLED, true)
                    KEY_MENTIONS_ENABLED -> mentionsEnabledFlow.value = it.getBoolean(KEY_MENTIONS_ENABLED, true)
                    KEY_BG_CHECK_ENABLED -> bgCheckEnabledFlow.value = it.getBoolean(KEY_BG_CHECK_ENABLED, true)
                    KEY_BG_CHECK_INTERVAL_MIN -> bgCheckIntervalMinFlow.value = (it.getString(KEY_BG_CHECK_INTERVAL_MIN, "15")?.toLongOrNull() ?: 15L)
                    KEY_MUTED_TOPIC_IDS -> mutedTopicsFlow.value = readMutedTopicsFromPrefs(it)
                }
            }
        }
    }

    private fun readMutedTopicsFromPrefs(p: SharedPreferences): Set<Int> {
        val raw = p.getString(KEY_MUTED_TOPIC_IDS, "").orEmpty()
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    // --- Flow-наблюдения (реактивное обновление) ---
    fun favEnabledFlow(): Flow<Boolean> = favEnabledFlow.asStateFlow()

    fun qmsEnabledFlow(): Flow<Boolean> = qmsEnabledFlow.asStateFlow()

    fun mainEnabledFlow(): Flow<Boolean> = mainEnabledFlow.asStateFlow()

    /**
     * Реактивный аналог [wantsPushNotificationsSync]: эмитит false, как только выключен
     * общий тумблер ИЛИ все семейства push-уведомлений (темы/QMS/упоминания). Нужен, чтобы
     * foreground-сервис мог сам заглушиться и снять «служебное» уведомление из шторки.
     * Обращение к [prefs] инициализирует StateFlow'ы актуальными значениями из хранилища.
     */
    fun wantsPushNotificationsFlow(): Flow<Boolean> {
        prefs // прогреваем lazy-инициализацию StateFlow'ов перед combine
        return combine(
                mainEnabledFlow,
                favEnabledFlow,
                qmsEnabledFlow,
                mentionsEnabledFlow
        ) { main, fav, qms, mentions ->
            main && (fav || qms || mentions)
        }.distinctUntilChanged()
    }

    fun bgCheckEnabledFlow(): Flow<Boolean> = bgCheckEnabledFlow.asStateFlow()

    fun bgCheckIntervalMinFlow(): Flow<Long> = bgCheckIntervalMinFlow.asStateFlow()

    fun mutedTopicsFlow(): Flow<Set<Int>> = mutedTopicsFlow.asStateFlow()

    // --- Bg-check / muted topics синхронные геттеры/сеттеры ---
    fun getBgCheckEnabledSync(): Boolean = prefs.getBoolean(KEY_BG_CHECK_ENABLED, true)

    fun getBgCheckIntervalMinSync(): Long =
            (prefs.getString(KEY_BG_CHECK_INTERVAL_MIN, "15")?.toLongOrNull() ?: 15L).coerceAtLeast(15L)

    fun getMutedTopicsSync(): Set<Int> = readMutedTopicsFromPrefs(prefs)

    fun setMutedTopicsSync(value: Set<Int>) {
        prefs.edit().putString(KEY_MUTED_TOPIC_IDS, value.joinToString(",")).apply()
    }

    fun isTopicMutedSync(topicId: Int): Boolean = topicId > 0 && readMutedTopicsFromPrefs(prefs).contains(topicId)

    fun muteTopicSync(topicId: Int) {
        if (topicId <= 0) return
        val current = readMutedTopicsFromPrefs(prefs).toMutableSet()
        if (current.add(topicId)) setMutedTopicsSync(current)
    }

    fun unmuteTopicSync(topicId: Int) {
        if (topicId <= 0) return
        val current = readMutedTopicsFromPrefs(prefs).toMutableSet()
        if (current.remove(topicId)) setMutedTopicsSync(current)
    }

    fun toggleTopicMuteSync(topicId: Int): Boolean {
        if (topicId <= 0) return false
        val current = readMutedTopicsFromPrefs(prefs).toMutableSet()
        val nowMuted = if (current.contains(topicId)) {
            current.remove(topicId); false
        } else {
            current.add(topicId); true
        }
        setMutedTopicsSync(current)
        return nowMuted
    }

    // --- Синхронные геттеры/сеттеры (SharedPreferences) ---
    fun setDataQmsEventsSync(value: Set<String>) {
        prefs.edit().putString(KEY_DATA_QMS_EVENTS, value.joinToString(",")).apply()
    }

    fun setDataFavoritesEventsSync(value: Set<String>) {
        prefs.edit().putString(KEY_DATA_FAVORITES_EVENTS, value.joinToString(",")).apply()
    }

    fun getMainEnabledSync(): Boolean = prefs.getBoolean(KEY_MAIN_ENABLED, true)

    fun getMainSoundEnabledSync(): Boolean = prefs.getBoolean(KEY_MAIN_SOUND_ENABLED, true)

    fun getMainVibrationEnabledSync(): Boolean = prefs.getBoolean(KEY_MAIN_VIBRATION_ENABLED, true)

    fun getMainIndicatorEnabledSync(): Boolean = prefs.getBoolean(KEY_MAIN_INDICATOR_ENABLED, true)

    fun getMainAvatarsEnabledSync(): Boolean = prefs.getBoolean(KEY_MAIN_AVATARS_ENABLED, true)

    fun getFavEnabledSync(): Boolean = prefs.getBoolean(KEY_FAV_ENABLED, true)

    fun getFavOnlyImportantSync(): Boolean = prefs.getBoolean(KEY_FAV_ONLY_IMPORTANT, false)

    fun getFavLiveTabSync(): Boolean = prefs.getBoolean(KEY_FAV_LIVE_TAB, true)

    fun getQmsEnabledSync(): Boolean = prefs.getBoolean(KEY_QMS_ENABLED, true)

    fun getMentionsEnabledSync(): Boolean = prefs.getBoolean(KEY_MENTIONS_ENABLED, true)

    fun getDownloadsEnabledSync(): Boolean = prefs.getBoolean(KEY_DOWNLOADS_ENABLED, true)

    fun getDataQmsEventsSync(): Set<String> {
        val value = prefs.getString(KEY_DATA_QMS_EVENTS, "") ?: ""
        return if (value.isEmpty()) emptySet() else value.split(",").toSet()
    }

    fun getDataFavoritesEventsSync(): Set<String> {
        val value = prefs.getString(KEY_DATA_FAVORITES_EVENTS, "") ?: ""
        return if (value.isEmpty()) emptySet() else value.split(",").toSet()
    }

    fun wantsPushNotificationsSync(): Boolean {
        val main = getMainEnabledSync()
        val fav = getFavEnabledSync()
        val qms = getQmsEnabledSync()
        val mentions = getMentionsEnabledSync()
        return main && (fav || qms || mentions)
    }
}
