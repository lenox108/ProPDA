package forpdateam.ru.forpda.entity.db.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_folders")
data class NoteFolderRoom(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long
) {
    constructor() : this(0, "", 0, 0, 0)
}
