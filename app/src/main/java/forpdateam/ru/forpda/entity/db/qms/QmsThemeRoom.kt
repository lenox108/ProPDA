package forpdateam.ru.forpda.entity.db.qms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qms_themes")
data class QmsThemeRoom(
    @PrimaryKey
    val id: Int = 0,
    val userId: Int = 0,
    val countMessages: Int = 0,
    val countNew: Int = 0,
    val name: String? = null,
    val date: String? = null
) {
    constructor() : this(0, 0, 0, 0, null, null)
}
