package forpdateam.ru.forpda.entity.db.qms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qms_themes_list")
data class QmsThemesRoom(
    @PrimaryKey
    val userId: Int = 0,
    val nick: String? = null
) {
    constructor() : this(0, null)
}
