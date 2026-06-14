package forpdateam.ru.forpda.entity.db.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteItemRoom(
    @PrimaryKey
    val id: Long,
    val title: String,
    val link: String,
    val content: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long
) {
    constructor() : this(0, "", "", "", null, 0, 0, 0)
}
