package forpdateam.ru.forpda.model.repository.history

import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
import forpdateam.ru.forpda.model.repository.theme.TopicForumStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only определение «в теме появились новые ответы» для НЕ-избранных тем Истории.
 *
 * Механика: заход в тему помечает её прочитанной на сервере, поэтому персональный флаг «+» (новое)
 * в списке раздела [showforum=N] у такой темы гаснет и загорается снова ТОЛЬКО при новых постах после
 * визита — то есть «+» = «новое с момента, когда ты её читал». Загрузка списка раздела тему НЕ «читает»
 * (в отличие от открытия самой темы), значит сигнал безопасен.
 *
 * Синглтон-источник истины: результат живёт в [unread] (StateFlow), чтобы точку можно было погасить
 * ЖИВЬЁМ в момент, когда тема открывается ([markOpened]) — иначе harvest как «снимок» не узнавал бы о
 * прочтении не-избранной темы (у избранных точка гаснет через кэш Избранного, а тут своего сигнала нет).
 *
 * Стоимость ограничиваем: группируем темы по разделам (запрос на РАЗДЕЛ, не на тему), кэшируем разбор
 * раздела с коротким TTL и режем число реально дёргаемых разделов за проход. Темы с новыми постами
 * всплывают в топ списка раздела, поэтому 1-й страницы обычно достаточно; тема, ушедшая ниже в очень
 * активном разделе, точку не получит (осознанная слепая зона).
 */
@Singleton
class HistoryUnreadHarvester @Inject constructor(
        private val topicsApi: TopicsApi,
        private val topicForumStore: TopicForumStore,
) {

    private data class ForumSnapshot(val unreadTopicIds: Set<Int>, val atMs: Long)

    /** forumId → последний разбор его списка. */
    private val cache = ConcurrentHashMap<Int, ForumSnapshot>()

    /**
     * topicId → момент [markOpened]. Подавляет повторное зажигание только что открытой темы: [refresh]
     * делает ПОЛНЫЙ перезатир [_unread] ПОСЛЕ async-сети, и параллельный (или чуть запоздавший) проход
     * иначе воскресил бы точку, которую [markOpened] уже погасил (гонка refresh↔markOpened). Плюс сам
     * список раздела ([showforum]) отдаёт `isNew=true` ещё какое-то время после того, как GET темы пометил
     * её read на сервере (лаг). Окно [OPENED_SUPPRESS_MS] закрывает оба случая; по истечении, если реально
     * появятся НОВЫЕ посты, тема честно загорится снова. Ключи чистятся лениво в [isRecentlyOpened].
     */
    private val recentlyOpened = ConcurrentHashMap<Int, Long>()

    private val _unread = MutableStateFlow<Set<Int>>(emptySet())
    /** Множество topicId тем истории, у которых сейчас есть новые ответы (по harvest'у). */
    val unread: StateFlow<Set<Int>> = _unread.asStateFlow()

    /**
     * Пересчитать harvest по текущему списку тем истории и опубликовать в [unread]. Никогда не бросает:
     * сбой запроса раздела просто оставляет его темы без точки. Тяжёлую работу держит на IO.
     */
    suspend fun refresh(topicIds: List<Int>) {
        _unread.value = computeUnread(topicIds)
    }

    /**
     * Тема открыта ⇒ прочитана на сервере (4PDA метит read на GET), значит «+» в её разделе теперь
     * снят. Гасим её точку немедленно и сбрасываем кэш её раздела, чтобы следующий [refresh] перечитал
     * раздел свежим (если появятся НОВЫЕ посты позже — «+» вернётся, и точка честно загорится снова).
     */
    fun markOpened(topicId: Int) {
        if (topicId <= 0) return
        recentlyOpened[topicId] = System.currentTimeMillis() // подавить воскрешение параллельным refresh
        val forumId = topicForumStore.get(topicId)
        if (forumId > 0) cache.remove(forumId)
        _unread.update { current -> if (topicId in current) current - topicId else current }
    }

    /** Тема открыта в пределах [OPENED_SUPPRESS_MS] назад ⇒ её stale-«новое» из списка раздела игнорируем. */
    private fun isRecentlyOpened(topicId: Int): Boolean {
        val at = recentlyOpened[topicId] ?: return false
        if (System.currentTimeMillis() - at > OPENED_SUPPRESS_MS) {
            recentlyOpened.remove(topicId)
            return false
        }
        return true
    }

    private suspend fun computeUnread(topicIds: List<Int>): Set<Int> = withContext(Dispatchers.IO) {
        if (topicIds.isEmpty()) return@withContext emptySet()

        // Группируем по разделу; темы без известного forumId пропускаем (их подсветит только Избранное).
        val topicsByForum = HashMap<Int, MutableList<Int>>()
        for (topicId in topicIds) {
            val forumId = topicForumStore.get(topicId)
            if (forumId > 0) topicsByForum.getOrPut(forumId) { mutableListOf() }.add(topicId)
        }
        if (topicsByForum.isEmpty()) return@withContext emptySet()

        val now = System.currentTimeMillis()
        val result = HashSet<Int>()
        var fetchedForums = 0
        var skippedByCap = 0

        for ((forumId, wantedTopicIds) in topicsByForum) {
            val fresh = cache[forumId]?.takeIf { now - it.atMs < TTL_MS }
            val snapshot = when {
                fresh != null -> fresh
                fetchedForums >= MAX_FORUMS_PER_HARVEST -> { skippedByCap++; null }
                else -> {
                    fetchedForums++
                    fetchForumUnread(forumId)?.also { cache[forumId] = it }
                }
            }
            if (snapshot != null) {
                for (topicId in wantedTopicIds) {
                    // Гонка refresh↔markOpened + лаг серверного списка: только что открытую тему не зажигаем.
                    if (topicId in snapshot.unreadTopicIds && !isRecentlyOpened(topicId)) result.add(topicId)
                }
            }
        }

        if (skippedByCap > 0) {
            Timber.d("HistoryUnreadHarvester: capped, %d forum(s) not polled this pass", skippedByCap)
        }
        result
    }

    private fun fetchForumUnread(forumId: Int): ForumSnapshot? = runCatching {
        val data = topicsApi.getTopics(forumId, 0)
        val unread = HashSet<Int>()
        // Учитываем обычные и закреплённые темы (визитная тема могла быть запинена); анонсы/подфорумы
        // read-state не несут.
        for (t in data.topicItems) if (t.isNew && t.id > 0) unread.add(t.id)
        for (t in data.pinnedItems) if (t.isNew && t.id > 0) unread.add(t.id)
        ForumSnapshot(unread, System.currentTimeMillis())
    }.getOrNull()

    private companion object {
        const val TTL_MS = 3 * 60 * 1000L        // 3 минуты: одно открытие Истории не дёргает раздел повторно
        const val MAX_FORUMS_PER_HARVEST = 15    // потолок реальных запросов за проход
        const val OPENED_SUPPRESS_MS = 3 * 60 * 1000L // окно, где открытая тема не зажигается повторно (race+lag)
    }
}
