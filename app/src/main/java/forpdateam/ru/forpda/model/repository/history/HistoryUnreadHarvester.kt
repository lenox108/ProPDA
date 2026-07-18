package forpdateam.ru.forpda.model.repository.history

import forpdateam.ru.forpda.model.data.remote.api.topcis.TopicsApi
import forpdateam.ru.forpda.model.repository.theme.TopicForumStore
import kotlinx.coroutines.Dispatchers
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
     * Возвращает подмножество [topicIds], у которых сейчас есть новые ответы (по флагу «+» в их разделе).
     * Никогда не бросает: сбой запроса раздела просто оставляет его темы без точки. Тяжёлую работу
     * держит на IO.
     */
    suspend fun harvest(topicIds: List<Int>): Set<Int> = withContext(Dispatchers.IO) {
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
                    if (topicId in snapshot.unreadTopicIds) result.add(topicId)
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
    }
}
