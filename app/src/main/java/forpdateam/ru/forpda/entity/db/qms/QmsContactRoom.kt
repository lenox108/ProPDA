package forpdateam.ru.forpda.entity.db.qms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qms_contacts")
data class QmsContactRoom(
    @PrimaryKey
    val nick: String = "",
    val id: Int = 0,
    val count: Int = 0,
    val avatar: String? = null
) {
    constructor() : this("", 0, 0, null)
}
