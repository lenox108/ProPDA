package forpdateam.ru.forpda.model.data.cache.mentions

import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.db.mentions.MentionArchiveDao
import forpdateam.ru.forpda.entity.db.mentions.MentionArchiveRoom
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.repository.mentions.mentionIdentity

class MentionsArchiveStore(
        private val dao: MentionArchiveDao,
        private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun archive(accountId: Int, items: Collection<MentionItem>) {
        if (accountId <= 0) return
        val seenAt = now()
        for (item in items) {
            val identity = item.mentionIdentity() ?: continue
            val existing = dao.get(accountId, identity.key)
            val parsedDate = Utils.parseForumDateTime(item.date)?.time
            dao.insert(
                    MentionArchiveRoom(
                            accountId = accountId,
                            mentionKey = identity.key,
                            title = item.title.orEmpty(),
                            link = item.link.orEmpty().replace("&amp;", "&"),
                            date = item.date.orEmpty(),
                            dateMillis = parsedDate ?: existing?.dateMillis ?: seenAt,
                            nick = item.nick.orEmpty(),
                            state = item.state,
                            type = item.type,
                            topicId = identity.topicId,
                            postId = identity.postId,
                            firstSeenAt = existing?.firstSeenAt ?: seenAt,
                            lastSeenAt = seenAt,
                    )
            )
        }
    }

    suspend fun getPage(accountId: Int, offset: Int, pageSize: Int = DEFAULT_PAGE_SIZE): MentionsData {
        if (accountId <= 0) return MentionsData()
        val total = dao.count(accountId)
        val totalPages = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
        val safeOffset = offset.coerceAtLeast(0)
        return MentionsData().apply {
            dao.getPage(accountId, safeOffset, pageSize).mapTo(items) { row ->
                MentionItem().apply {
                    title = row.title
                    link = row.link
                    date = row.date
                    nick = row.nick
                    state = row.state
                    type = row.type
                }
            }
            pagination = Pagination().apply {
                perPage = pageSize
                all = totalPages
                current = (safeOffset / pageSize + 1).coerceAtMost(totalPages)
                st = safeOffset
                isForum = true
            }
        }
    }

    suspend fun markRead(accountId: Int, item: MentionItem) {
        val identity = item.mentionIdentity() ?: return
        dao.markRead(accountId, identity.key, MentionItem.STATE_READ)
    }

    suspend fun markTopicPostsRead(accountId: Int, topicId: Int, postIds: Collection<Int>) {
        if (accountId <= 0 || topicId <= 0 || postIds.isEmpty()) return
        dao.markTopicPostsRead(accountId, topicId, postIds.toList(), MentionItem.STATE_READ)
    }

    suspend fun markTopicReadUpTo(accountId: Int, topicId: Int, upToPostId: Int) {
        if (accountId <= 0 || topicId <= 0) return
        dao.markTopicReadUpTo(accountId, topicId, upToPostId, MentionItem.STATE_READ)
    }

    suspend fun markAllRead(accountId: Int) {
        if (accountId <= 0) return
        dao.markAllRead(accountId, MentionItem.STATE_READ)
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
