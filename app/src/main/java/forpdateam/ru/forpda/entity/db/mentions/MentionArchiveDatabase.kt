package forpdateam.ru.forpda.entity.db.mentions

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
        entities = [MentionArchiveRoom::class],
        version = 1,
        exportSchema = true,
)
abstract class MentionArchiveDatabase : RoomDatabase() {
    abstract fun mentionArchiveDao(): MentionArchiveDao
}
