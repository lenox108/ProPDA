package forpdateam.ru.forpda.model

import android.content.SharedPreferences
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.common.MessageCounters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class CountersHolder(
        private val preferences: SharedPreferences
) {
    private val _counters = MutableStateFlow(loadFromPreferences())
    @Volatile
    private var ignoreFavoritesInflateUntilMs: Long = 0L
    @Volatile
    private var ignoreMentionsInflateUntilMs: Long = 0L

    private fun loadFromPreferences(): MessageCounters = MessageCounters().apply {
        qms = preferences.getInt("counter_qms", 0)
        favorites = preferences.getInt("counter_favorites", 0)
        mentions = preferences.getInt("counter_mentions", 0)
    }

    init {
        persist(_counters.value)
    }

    private fun persist(c: MessageCounters) {
        preferences
                .edit()
                .putInt("counter_qms", c.qms)
                .putInt("counter_favorites", c.favorites)
                .putInt("counter_mentions", c.mentions)
                .apply()
    }

    private fun copyOf(c: MessageCounters): MessageCounters = MessageCounters().apply {
        qms = c.qms
        favorites = c.favorites
        mentions = c.mentions
    }

    fun observe(): Flow<MessageCounters> = _counters.asStateFlow()

    /** Снимок счётчиков; изменения не затрагивают внутреннее состояние до вызова [set]. */
    fun get(): MessageCounters = copyOf(_counters.value)

    fun set(value: MessageCounters, source: String? = null) {
        val prev = _counters.value
        val snapshot = copyOf(value).apply {
            if (source == "index_header") {
                val now = System.currentTimeMillis()
                if (now < ignoreFavoritesInflateUntilMs && favorites > prev.favorites) {
                    favorites = prev.favorites
                }
                if (now < ignoreMentionsInflateUntilMs && mentions > prev.mentions) {
                    mentions = prev.mentions
                }
            }
        }
        if (BuildConfig.DEBUG) {
            Timber.d(
                "CountersHolder.set(source=%s) mentions:%d→%d fav:%d→%d qms:%d→%d",
                source,
                prev.mentions, snapshot.mentions,
                prev.favorites, snapshot.favorites,
                prev.qms, snapshot.qms
            )
        }
        persist(snapshot)
        _counters.value = snapshot
    }

    fun update(source: String? = null, transform: (MessageCounters) -> Unit) {
        val snapshot = copyOf(_counters.value)
        transform(snapshot)
        if (BuildConfig.DEBUG) {
            val prev = _counters.value
            Timber.d(
                "CountersHolder.update(source=%s) mentions:%d→%d fav:%d→%d qms:%d→%d",
                source,
                prev.mentions, snapshot.mentions,
                prev.favorites, snapshot.favorites,
                prev.qms, snapshot.qms
            )
        }
        persist(snapshot)
        _counters.value = snapshot
    }

    fun decrementMentions(by: Int = 1, source: String? = null) {
        if (by <= 0) return
        protectMentionsFromHeaderInflation()
        update(source) { it.mentions = (it.mentions - by).coerceAtLeast(0) }
    }

    fun setMentions(count: Int, source: String? = null) {
        protectMentionsFromHeaderInflation()
        update(source) { it.mentions = count.coerceAtLeast(0) }
    }

    fun decrementQms(by: Int = 1, source: String? = null) {
        if (by <= 0) return
        update(source) { it.qms = (it.qms - by).coerceAtLeast(0) }
    }

    fun decrementFavorites(by: Int = 1, source: String? = null) {
        if (by <= 0) return
        update(source) { it.favorites = (it.favorites - by).coerceAtLeast(0) }
    }

    /**
     * После локальной синхронизации избранного (открыли тему / обновили список) серверная шапка может
     * кратковременно отдавать устаревший (больший) счётчик. На этот небольшой интервал игнорируем
     * "inflate" обновления favorites из source=index_header.
     */
    fun protectFavoritesFromHeaderInflation(windowMs: Long = 8_000L) {
        ignoreFavoritesInflateUntilMs = System.currentTimeMillis() + windowMs
    }

    /**
     * Локально прочитанные ответы/упоминания могут ещё несколько секунд приходить в шапке форума
     * старым счётчиком. Не даём такому ответу вернуть только что снятый бейдж.
     */
    fun protectMentionsFromHeaderInflation(windowMs: Long = 8_000L) {
        ignoreMentionsInflateUntilMs = System.currentTimeMillis() + windowMs
    }
}
