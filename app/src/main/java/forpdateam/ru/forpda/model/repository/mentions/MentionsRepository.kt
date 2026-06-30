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
        mentionsApi.getMentions(page).also { data ->
            val syncStartedAt = SystemClock.uptimeMillis()
            synchronized(stateLock) {
                restoreLocalUnreadStateLocked(data)
                cachedPages[page] = data.copyMentionsData()
            }
            logPerf("read-state sync", syncStartedAt, "page=$page items=${data.items.size}")
            logPerf("network refresh", startedAt, "page=$page items=${data.items.size}")
        }
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

    suspend fun getUnreadSnapshot(): UnreadMentionsSnapshot = withContext(Dispatchers.IO) {
        getUnreadSnapshotInternalLocked()
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
            }
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
