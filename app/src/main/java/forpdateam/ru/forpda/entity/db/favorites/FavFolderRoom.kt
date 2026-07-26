package forpdateam.ru.forpda.entity.db.favorites

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fav_folders")
data class FavFolderRoom(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    constructor() : this(0, "", 0, 0, 0)
}
