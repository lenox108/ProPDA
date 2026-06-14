package forpdateam.ru.forpda.entity.db.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryItemRoom(
    @PrimaryKey
    val id: Int,
    val url: String,
    val date: String,
    val title: String,
    val unixTime: Long
) {
    constructor() : this(0, "", "", "", 0)
}
