package forpdateam.ru.forpda.entity.db.forum

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "forum_items_flat")
data class ForumItemFlatRoom(
    @PrimaryKey
    val id: Int = -1,
    val parentId: Int = -1,
    val level: Int = -1,
    val title: String? = null,
    val position: Int = 0
) {
    constructor() : this(-1, -1, -1, null, 0)
}
