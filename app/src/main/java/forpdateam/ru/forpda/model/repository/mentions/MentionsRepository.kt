package forpdateam.ru.forpda.model.repository.mentions

import android.content.SharedPreferences
import android.os.SystemClock
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Created by radiationx on 01.01.18.
 */

class MentionsRepository(
        private val mentionsApi: MentionsApi,
        preferences: SharedPreferences? = null
) {

    private val stateLock = Any()
    private val readStateStore = MentionsReadStateStore(preferences)
    private val unreadEventsStore = MentionsUnreadEventsStore(preferences)
    private val cachedPages = linkedMapOf<Int, MentionsData>()
    private val locallyReadKeys = linkedSetOf<String>().apply {
        addAll(readStateStore.getReadKeys())
    }
    // Упоминания, про которые realtime-уведомление сообщило «непрочитано», но страница
    // act=mentions отдаёт их уже прочитанными (сервер гасит «жирность» в списке «Ответы»
    // по факту просмотра, тогда как бейдж в шапке держится до реального захода в тему).
    // Держим этот набор авторитетным источником «жирной» строки, пока пост не откроют.
    private val unreadFromEventsKeys = linkedSetOf<String>().apply {
        addAll(unreadEventsStore.getKeys())
        removeAll(locallyReadKeys)
    }
    private val locallyUnreadKeys = linkedSetOf<String>().apply {
        addAll(unreadFromEventsKeys)
    }
    private var hasLoadedMentions = false

    data class UnreadMentionsSnapshot(
            val unreadCount: Int,
            val topicPostIds: List<Int>
    )

    suspend fun getCachedMentions(page: Int): MentionsData? = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.uptimeMillis()
        synchronized(stateLock) {
            cachedPages[page]?.copyMentionsData()
        }.also {
            logPerf("local cache load", startedAt, "page=$page hit=${it != null} items=${it?.items?.size ?: 0}")
        }
    }

    suspend fun getMentions(page: Int): MentionsData = refreshMentions(page)

    suspend fun refreshMentions(page: Int): MentionsData = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.uptimeMillis()
        val data = try {
            mentionsApi.getMentions(page)
        } catch (e: Exception) {
            // Транзиентный сбой запроса (напр. Cloudflare-404 на act=mentions). Держим последний
            // хороший список, если он есть; ошибку пробрасываем только когда показывать нечего.
            val cached = synchronized(stateLock) { cachedPages[page] }
            if (cached != null && cached.items.isNotEmpty()) {
                logPerf("refresh failed (kept cached)", startedAt, "page=$page cached=${cached.items.size} err=${e.message}")
                return@withContext cached.copyMentionsData()
            }
            throw e
        }
        val syncStartedAt = SystemClock.uptimeMillis()
        val result = synchronized(stateLock) {
            val previous = cachedPages[page]
            if (data.items.isEmpty() && previous != null && previous.items.isNotEmpty()) {
                // A non-empty list turning empty on refresh is virtually always a transient fetch or
                // parse glitch (flaky network, a rate-limit/redirect page parsed as 0 rows) — 4pda
                // keeps read mentions listed, they don't disappear. Keep the previous list instead of
                // flashing "Нет упоминаний" and clobbering the cache. A genuinely emptied list still
                // clears on the next non-empty/real load; the badge is computed separately.
                previous.copyMentionsData()
            } else {
                restoreLocalUnreadStateLocked(data)
                cachedPages[page] = data.copyMentionsData()
                data
            }
        }
        logPerf("read-state sync", syncStartedAt, "page=$page items=${result.items.size}")
        logPerf("network refresh", startedAt, "page=$page netItems=${data.items.size} shown=${result.items.size}")
        result
    }

    suspend fun markPostsRead(topicId: Int, postIds: Collection<Int>): Boolean = withContext(Dispatchers.IO) {
        markAnswersReadInternal(topicId, postIds).changed
    }

    suspend fun markAnswerRead(topicId: Int, postId: Int): Pair<Boolean, UnreadMentionsSnapshot> = withContext(Dispatchers.IO) {
        markAnswersReadInternal(topicId, listOf(postId)).let { it.changed to it.snapshot }
    }

    suspend fun markMentionItemRead(item: MentionItem): Pair<Boolean, UnreadMentionsSnapshot> = withContext(Dispatchers.IO) {
        val normalizedLink = item.link
                ?.replace("&amp;", "&")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@withContext false to getUnreadSnapshotInternalLocked()
        MentionReadKey.fromLink(normalizedLink)?.let { key ->
            return@withContext markAnswersReadInternal(key.topicId, listOf(key.postId)).let { it.changed to it.snapshot }
        }
        markMentionKeyRead("${item.type}:$normalizedLink")
    }

    suspend fun markPostsReadAndRecomputeUnreadSnapshot(
            topicId: Int,
            postIds: Collection<Int>
    ): Pair<Boolean, UnreadMentionsSnapshot> = withContext(Dispatchers.IO) {
        markAnswersReadInternal(topicId, postIds).let { it.changed to it.snapshot }
    }

    /**
     * Помечаем упоминание непрочитанным по realtime-уведомлению (topic+post). Это держит строку в
     * списке «Ответы» жирной, даже если act=mentions отдаёт её прочитанной, — список совпадает с
     * бейджем. Набор переживает рестарт процесса (prefs) и снимается только при реальном прочтении
     * поста ([markAnswersReadInternal] / [removeUnreadFromEvent]).
     */
    suspend fun markMentionUnreadFromNotification(topicId: Int, postId: Int): UnreadMentionsSnapshot = withContext(Dispatchers.IO) {
        if (topicId <= 0 || postId <= 0) return@withContext getUnreadSnapshotInternalLocked()
        val key = MentionReadKey(topicId, postId).value
        synchronized(stateLock) {
            // Уже прочитано локально (открывали пост) — не воскрешаем.
            if (key !in locallyReadKeys && unreadFromEventsKeys.add(key)) {
                locallyUnreadKeys.add(key)
                unreadEventsStore.saveKeys(unreadFromEventsKeys)
                markCachedItemUnreadLocked(key)
            }
        }
        getUnreadSnapshotInternalLocked()
    }

    /**
     * Снимаем override непрочитанного упоминания (например, пришло READ-событие по WebSocket —
     * пост прочитан на другом устройстве), не помечая его при этом локально прочитанным навсегда.
     */
    suspend fun removeUnreadFromEvent(topicId: Int, postId: Int): UnreadMentionsSnapshot = withContext(Dispatchers.IO) {
        if (topicId <= 0 || postId <= 0) return@withContext getUnreadSnapshotInternalLocked()
        val key = MentionReadKey(topicId, postId).value
        synchronized(stateLock) {
            if (unreadFromEventsKeys.remove(key)) {
                locallyUnreadKeys.remove(key)
                unreadEventsStore.saveKeys(unreadFromEventsKeys)
                markCachedItemReadLocked(key)
            }
        }
        getUnreadSnapshotInternalLocked()
    }

    suspend fun recomputeUnreadSnapshot(): UnreadMentionsSnapshot = withContext(Dispatchers.IO) {
        getUnreadSnapshotInternalLocked()
    }

    /**
     * READ-событие по теме (прочитано на другом устройстве/сайте): снимаем непрочитанность всех
     * упоминаний темы [topicId] с postId <= [upToPostId] (семантика READ — «прочитано до этого
     * поста включительно»; при upToPostId <= 0 граница неизвестна — чистим всю тему). Ключи НЕ
     * помечаются прочитанными навсегда: если сервер вновь отдаст строку непрочитанной или придёт
     * новое MENTION-событие, она снова станет жирной. Возвращает (changed, snapshot).
     */
    suspend fun clearTopicUnreadUpTo(topicId: Int, upToPostId: Int): Pair<Boolean, UnreadMentionsSnapshot> = withContext(Dispatchers.IO) {
        if (topicId <= 0) return@withContext false to getUnreadSnapshotInternalLocked()
        val result = synchronized(stateLock) {
            val matches: (String) -> Boolean = { key ->
                MentionReadKey.fromValue(key)?.let {
                    it.topicId == topicId && (upToPostId <= 0 || it.postId <= upToPostId)
                } ?: false
            }
            var changed = false
            for (key in locallyUnreadKeys.filter(matches)) {
                locallyUnreadKeys.remove(key)
                markCachedItemReadLocked(key)
                changed = true
            }
            val eventKeys = unreadFromEventsKeys.filter(matches)
            if (eventKeys.isNotEmpty()) {
                unreadFromEventsKeys.removeAll(eventKeys.toSet())
                unreadEventsStore.saveKeys(unreadFromEventsKeys)
                for (key in eventKeys) {
                    markCachedItemReadLocked(key)
                }
                changed = true
            }
            MarkReadResult(changed, UnreadMentionsSnapshot(
                    locallyUnreadKeys.size,
                    locallyUnreadKeys.mapNotNull { MentionReadKey.fromValue(it)?.postId }
            ))
        }
        logPerf("read-event clear", SystemClock.uptimeMillis(), "topic=$topicId upTo=$upToPostId changed=${result.changed} unread=${result.snapshot.unreadCount}")
        result.changed to result.snapshot
    }

    /**
     * Сверяем локальное состояние непрочитанных упоминаний с АВТОРИТЕТНЫМ серверным счётчиком из шапки
     * форума. Если сервер говорит, что упоминаний меньше, чем держим локально, значит часть наших
     * override'ов устарела («осиротела»): пользователь прочитал упоминание на другом устройстве/сайте
     * или пост совпал не по тому id, а READ-событие по WebSocket не пришло. Такие ключи вечно висели в
     * `unreadFromEventsKeys`/`locallyUnreadKeys` (prefs переживают рестарт) и держали бейдж «Ответы»
     * зажжённым, хотя всё прочитано — локальный пересчёт `setMentions(getUnreadSnapshot())` перебивал
     * корректный серверный 0 обратно на 1.
     *
     * Гасим ТОЛЬКО безошибочный случай — сервер авторитетно говорит «упоминаний 0», а локально мы
     * ещё держим непрочитанные. Тогда все локальные override'ы устарели, снимаем их полностью. При
     * `0 < serverMentions < size` неизвестно, какой именно override устарел, поэтому НЕ трогаем (лучше
     * показать лишний бейдж, чем спрятать реальное упоминание). Свежее упоминание сервер считает
     * (serverMentions>=1 вместе с приходом override из realtime-события) — под срез не попадает.
     * Возвращаем true, если что-то реально сняли (вызвавшему стоит перетянуть бейдж к снапшоту).
     */
    suspend fun reconcileWithServerMentionCount(serverMentions: Int): Boolean = withContext(Dispatchers.IO) {
        if (serverMentions != 0) return@withContext false
        synchronized(stateLock) {
            if (locallyUnreadKeys.isEmpty() && unreadFromEventsKeys.isEmpty()) return@synchronized false
            // Помечаем ключи локально-прочитанными навсегда (как при реальном открытии поста): сервер
            // авторитетно подтвердил, что непрочитанных нет, поэтому ни повторный refresh act=mentions,
            // ни запоздавшее realtime-событие по этому же посту не должны воскресить override.
            val keysToRead = (locallyUnreadKeys + unreadFromEventsKeys)
            for (key in keysToRead) {
                locallyReadKeys.add(key)
                markCachedItemReadLocked(key)
            }
            locallyUnreadKeys.clear()
            unreadFromEventsKeys.clear()
            readStateStore.saveReadKeys(locallyReadKeys)
            unreadEventsStore.saveKeys(unreadFromEventsKeys)
            logPerf("server reconcile", SystemClock.uptimeMillis(), "server=0 cleared=${keysToRead.size}")
            true
        }
    }

    suspend fun getUnreadSnapshot(): UnreadMentionsSnapshot = withContext(Dispatchers.IO) {
        getUnreadSnapshotInternalLocked()
    }

    /**
     * Полный сброс локального состояния упоминаний (выход из аккаунта): прочитанные ключи,
     * unread-override'ы и кэш страниц принадлежат конкретному пользователю — у другого аккаунта
     * те же topic:post-ключи означают ЕГО упоминания, и чужой стейт давал бы ложную жирность/
     * прочитанность и залипший бейдж.
     */
    suspend fun clearAllLocalState() = withContext(Dispatchers.IO) {
        synchronized(stateLock) {
            cachedPages.clear()
            locallyReadKeys.clear()
            locallyUnreadKeys.clear()
            unreadFromEventsKeys.clear()
            readStateStore.saveReadKeys(locallyReadKeys)
            unreadEventsStore.saveKeys(unreadFromEventsKeys)
            hasLoadedMentions = false
        }
    }

    private fun getUnreadSnapshotInternalLocked(): UnreadMentionsSnapshot {
        val startedAt = SystemClock.uptimeMillis()
        return synchronized(stateLock) {
            val topicPostIds = locallyUnreadKeys.mapNotNull { MentionReadKey.fromValue(it)?.postId }
            UnreadMentionsSnapshot(locallyUnreadKeys.size, topicPostIds)
        }.also {
            logPerf("badge recompute", startedAt, "unread=${it.unreadCount}")
        }
    }

    private fun markMentionKeyRead(key: String): Pair<Boolean, UnreadMentionsSnapshot> {
        if (key.isBlank()) return false to getUnreadSnapshotInternalLocked()
        val result = synchronized(stateLock) {
            var changed = false
            val wasUnread = locallyUnreadKeys.remove(key)
            if (unreadFromEventsKeys.remove(key)) {
                unreadEventsStore.saveKeys(unreadFromEventsKeys)
            }
            if (locallyReadKeys.add(key)) {
                val markedUnread = markCachedItemReadLocked(key)
                readStateStore.saveReadKeys(locallyReadKeys)
                changed = wasUnread || markedUnread
            } else if (wasUnread) {
                markCachedItemReadLocked(key)
                changed = true
            }
            val topicPostIds = locallyUnreadKeys.mapNotNull { MentionReadKey.fromValue(it)?.postId }
            MarkReadResult(changed, UnreadMentionsSnapshot(locallyUnreadKeys.size, topicPostIds))
        }
        return result.changed to result.snapshot
    }

    private fun markAnswersReadInternal(topicId: Int, postIds: Collection<Int>): MarkReadResult {
        val startedAt = SystemClock.uptimeMillis()
        if (topicId <= 0 || postIds.isEmpty()) return MarkReadResult(false, getUnreadSnapshotInternalLocked())
        val visiblePostIds = postIds.asSequence().filter { it > 0 }.toSet()
        if (visiblePostIds.isEmpty()) return MarkReadResult(false, getUnreadSnapshotInternalLocked())

        val result = synchronized(stateLock) {
            var changed = false
            val keysToRead = visiblePostIds.map { MentionReadKey(topicId, it).value }.toSet()
            for (key in keysToRead) {
                val wasUnread = locallyUnreadKeys.remove(key)
                if (unreadFromEventsKeys.remove(key)) {
                    unreadEventsStore.saveKeys(unreadFromEventsKeys)
                }
                if (locallyReadKeys.add(key)) {
                    val markedUnread = markCachedItemReadLocked(key)
                    readStateStore.saveReadKeys(locallyReadKeys)
                    changed = wasUnread || markedUnread || changed
                } else if (wasUnread) {
                    markCachedItemReadLocked(key)
                    changed = true
                }
            }
            val topicPostIds = locallyUnreadKeys.mapNotNull { MentionReadKey.fromValue(it)?.postId }
            MarkReadResult(changed, UnreadMentionsSnapshot(locallyUnreadKeys.size, topicPostIds))
        }
        logPerf("read-state sync", startedAt, "topic=$topicId posts=${visiblePostIds.size} changed=${result.changed} unread=${result.snapshot.unreadCount}")
        return result
    }

    private fun markCachedItemReadLocked(key: String): Boolean {
        var markedUnread = false
        for (page in cachedPages.values) {
            page.items
                    .asSequence()
                    .filter { it.localReadStateKey() == key }
                    .forEach {
                        if (it.state == MentionItem.STATE_UNREAD) {
                            markedUnread = true
                        }
                        it.state = MentionItem.STATE_READ
                    }
        }
        return markedUnread
    }

    private fun markCachedItemUnreadLocked(key: String) {
        for (page in cachedPages.values) {
            page.items
                    .asSequence()
                    .filter { it.localReadStateKey() == key }
                    .forEach { it.state = MentionItem.STATE_UNREAD }
        }
    }

    private fun extractPostId(link: String): Int? = extractMentionPostId(link)

    private fun restoreLocalUnreadStateLocked(data: MentionsData) {
        var eventsStoreDirty = false
        for (item in data.items) {
            val key = item.localReadStateKey() ?: continue
            if (key in locallyReadKeys) {
                locallyUnreadKeys.remove(key)
                item.state = MentionItem.STATE_READ
            } else if (key in unreadFromEventsKeys) {
                // Сервер на act=mentions отдаёт это упоминание прочитанным, но из realtime-уведомления
                // мы знаем, что пост ещё не открывали. Держим строку жирной, чтобы список «Ответы»
                // совпадал с бейджем; снимется при реальном прочтении поста.
                locallyUnreadKeys.add(key)
                item.state = MentionItem.STATE_UNREAD
            } else if (item.isRead && key in locallyUnreadKeys) {
                if (hasLoadedMentions) {
                    item.state = MentionItem.STATE_UNREAD
                } else {
                    locallyUnreadKeys.remove(key)
                }
            } else if (!item.isRead) {
                locallyUnreadKeys.add(key)
                // Персистим непрочитанность: сам GET act=mentions гасит unread-класс на сервере
                // (следующий запрос вернёт строку прочитанной), а `locallyUnreadKeys` живёт только
                // в памяти. Без записи в prefs фоновая проба воркера или рестарт процесса теряли
                // «жирность» строки при всё ещё горящем бейдже из шапки. Снимается теми же путями,
                // что и event-override: открытие поста / READ-событие / server-reconcile(0).
                if (unreadFromEventsKeys.add(key)) {
                    eventsStoreDirty = true
                }
            }
        }
        if (eventsStoreDirty) {
            unreadEventsStore.saveKeys(unreadFromEventsKeys)
        }
        hasLoadedMentions = true
    }

    private fun MentionItem.localReadStateKey(): String? {
        val normalizedLink = link
                ?.replace("&amp;", "&")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        return MentionReadKey.fromLink(normalizedLink)?.value ?: "${type}:${normalizedLink}"
    }

    companion object {
        private fun extractMentionPostId(link: String): Int? {
            return Regex("""(?i)(?:[?&](?:p|pid)=|[/#]entry)(\d+)""")
                    .find(link.replace("&amp;", "&"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
        }

        private fun logPerf(label: String, startedAt: Long, extra: String = "") {
            if (BuildConfig.DEBUG) {
                Timber.d("MentionsPerf %s took %dms %s", label, SystemClock.uptimeMillis() - startedAt, extra)
            }
        }
    }

    private data class MarkReadResult(
            val changed: Boolean,
            val snapshot: UnreadMentionsSnapshot
    )

    private data class MentionReadKey(
            val topicId: Int,
            val postId: Int
    ) {
        val value: String = "topic:$topicId:post:$postId"

        companion object {
            fun fromLink(link: String): MentionReadKey? {
                val normalized = link.replace("&amp;", "&")
                val topicId = Regex("""(?i)[?&]showtopic=(\d+)""")
                        .find(normalized)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?: return null
                val postId = extractMentionPostId(normalized) ?: return null
                return MentionReadKey(topicId, postId)
            }

            fun fromValue(value: String): MentionReadKey? {
                val match = Regex("""^topic:(\d+):post:(\d+)$""").find(value) ?: return null
                return MentionReadKey(
                        match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null,
                        match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
                )
            }
        }
    }

}

private fun MentionsData.copyMentionsData(): MentionsData {
    val source = this
    return MentionsData().also { copy ->
        copy.pagination = source.pagination
        source.items.mapTo(copy.items) { it.copyMentionItem() }
    }
}

private fun MentionItem.copyMentionItem(): MentionItem {
    val source = this
    return MentionItem().also { copy ->
        copy.title = source.title
        copy.desc = source.desc
        copy.link = source.link
        copy.date = source.date
        copy.nick = source.nick
        copy.state = source.state
        copy.type = source.type
    }
}

private class MentionsReadStateStore(
        private val preferences: SharedPreferences?
) {
    fun getReadKeys(): Set<String> {
        return preferences
                ?.getStringSet(KEY_READ_MENTION_KEYS, emptySet())
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
    }

    fun saveReadKeys(keys: Set<String>) {
        preferences
                ?.edit()
                ?.putStringSet(KEY_READ_MENTION_KEYS, keys.toSet())
                ?.apply()
    }

    private companion object {
        const val KEY_READ_MENTION_KEYS = "mentions_read_state_keys"
    }
}

private class MentionsUnreadEventsStore(
        private val preferences: SharedPreferences?
) {
    fun getKeys(): Set<String> {
        return preferences
                ?.getStringSet(KEY_UNREAD_EVENT_KEYS, emptySet())
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
    }

    fun saveKeys(keys: Set<String>) {
        preferences
                ?.edit()
                ?.putStringSet(KEY_UNREAD_EVENT_KEYS, keys.toSet())
                ?.apply()
    }

    private companion object {
        const val KEY_UNREAD_EVENT_KEYS = "mentions_unread_event_keys"
    }
}
