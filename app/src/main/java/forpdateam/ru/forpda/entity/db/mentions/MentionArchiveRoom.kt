package forpdateam.ru.forpda.entity.db.mentions

import androidx.room.Entity
import androidx.room.Index

@Entity(
        tableName = "mention_archive",
        primaryKeys = ["accountId", "mentionKey"],
        indices = [
            Index(value = ["accountId", "dateMillis"]),
            Index(value = ["accountId", "topicId", "postId"]),
        ],
)
data class MentionArchiveRoom(
        val accountId: Int,
        val mentionKey: String,
        val title: String,
        val link: String,
        val date: String,
        val dateMillis: Long,
        val nick: String,
        val state: Int,
        val type: Int,
        val topicId: Int,
        val postId: Int,
        val firstSeenAt: Long,
        val lastSeenAt: Long,
)
