package forpdateam.ru.forpda.entity.db.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState

@Entity(tableName = "favorites")
data class FavItemRoom(
    @PrimaryKey
    val favId: Int = 0,
    val topicId: Int = 0,
    val forumId: Int = 0,
    val authorId: Int = 0,
    val lastUserId: Int = 0,
    val stParam: Int = 0,
    val pages: Int = 0,
    val curatorId: Int = 0,
    val trackType: String? = null,
    val infoColor: String? = null,
    val topicTitle: String? = null,
    val forumTitle: String? = null,
    val authorUserNick: String? = null,
    val lastUserNick: String? = null,
    val date: String? = null,
    val desc: String? = null,
    val curatorNick: String? = null,
    val subType: String? = null,
    val isPin: Boolean = false,
    val isForum: Boolean = false,
    val isNew: Boolean = false,
    val readState: Int = FavoriteReadState.STORAGE_UNKNOWN,
    val isPoll: Boolean = false,
    val isClosed: Boolean = false,
    val unreadPostCount: Int = 0,
    val localReadPostId: Int = 0,
    val localReadPostDateMillis: Long = 0L
) {
    constructor() : this(
            0, 0, 0, 0, 0, 0, 0, 0,
            null, null, null, null, null, null, null, null, null, null,
            false, false, false,
            FavoriteReadState.STORAGE_UNKNOWN,
            false, false,
            0, 0, 0L
    )
}
