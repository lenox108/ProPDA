package forpdateam.ru.forpda.entity.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forum_users")
data class ForumUserRoom(
    @PrimaryKey
    val id: Int = 0,
    val nick: String? = null,
    val avatar: String? = null
) {
    constructor() : this(0, null, null)
}
