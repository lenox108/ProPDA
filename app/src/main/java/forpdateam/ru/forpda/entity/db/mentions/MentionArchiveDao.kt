package forpdateam.ru.forpda.entity.db.mentions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MentionArchiveDao {
    @Query(
            """
            SELECT * FROM mention_archive
            WHERE accountId = :accountId
            ORDER BY dateMillis DESC, firstSeenAt DESC
            LIMIT :limit OFFSET :offset
            """
    )
    suspend fun getPage(accountId: Int, offset: Int, limit: Int): List<MentionArchiveRoom>

    @Query("SELECT COUNT(*) FROM mention_archive WHERE accountId = :accountId")
    suspend fun count(accountId: Int): Int

    @Query(
            """
            SELECT * FROM mention_archive
            WHERE accountId = :accountId AND mentionKey = :mentionKey
            LIMIT 1
            """
    )
    suspend fun get(accountId: Int, mentionKey: String): MentionArchiveRoom?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MentionArchiveRoom)

    @Query(
            """
            UPDATE mention_archive
            SET state = :readState
            WHERE accountId = :accountId AND mentionKey = :mentionKey
            """
    )
    suspend fun markRead(accountId: Int, mentionKey: String, readState: Int)

    @Query(
            """
            UPDATE mention_archive
            SET state = :readState
            WHERE accountId = :accountId
              AND topicId = :topicId
              AND postId IN (:postIds)
            """
    )
    suspend fun markTopicPostsRead(accountId: Int, topicId: Int, postIds: List<Int>, readState: Int)

    @Query(
            """
            UPDATE mention_archive
            SET state = :readState
            WHERE accountId = :accountId
              AND topicId = :topicId
              AND (:upToPostId <= 0 OR postId <= :upToPostId)
            """
    )
    suspend fun markTopicReadUpTo(accountId: Int, topicId: Int, upToPostId: Int, readState: Int)

    @Query("UPDATE mention_archive SET state = :readState WHERE accountId = :accountId")
    suspend fun markAllRead(accountId: Int, readState: Int)
}
