package forpdateam.ru.forpda.entity.db.notes

import androidx.room.Database
import androidx.room.RoomDatabase
import forpdateam.ru.forpda.entity.db.ForumUserDao
import forpdateam.ru.forpda.entity.db.ForumUserRoom
import forpdateam.ru.forpda.entity.db.favorites.FavItemDao
import forpdateam.ru.forpda.entity.db.favorites.FavItemRoom
import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatDao
import forpdateam.ru.forpda.entity.db.forum.ForumItemFlatRoom
import forpdateam.ru.forpda.entity.db.history.HistoryItemDao
import forpdateam.ru.forpda.entity.db.history.HistoryItemRoom
import forpdateam.ru.forpda.entity.db.offline.OfflineItemDao
import forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
import forpdateam.ru.forpda.entity.db.qms.QmsContactDao
import forpdateam.ru.forpda.entity.db.qms.QmsContactRoom
import forpdateam.ru.forpda.entity.db.qms.QmsThemeDao
import forpdateam.ru.forpda.entity.db.qms.QmsThemeRoom
import forpdateam.ru.forpda.entity.db.qms.QmsThemesDao
import forpdateam.ru.forpda.entity.db.qms.QmsThemesRoom

@Database(
    entities = [
        NoteItemRoom::class,
        NoteFolderRoom::class,
        HistoryItemRoom::class,
        QmsContactRoom::class,
        QmsThemeRoom::class,
        QmsThemesRoom::class,
        FavItemRoom::class,
        ForumItemFlatRoom::class,
        ForumUserRoom::class,
        OfflineItemRoom::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteItemDao(): NoteItemDao
    abstract fun noteFolderDao(): NoteFolderDao
    abstract fun historyItemDao(): HistoryItemDao
    abstract fun qmsContactDao(): QmsContactDao
    abstract fun qmsThemeDao(): QmsThemeDao
    abstract fun qmsThemesDao(): QmsThemesDao
    abstract fun favItemDao(): FavItemDao
    abstract fun forumItemFlatDao(): ForumItemFlatDao
    abstract fun forumUserDao(): ForumUserDao
    abstract fun offlineItemDao(): OfflineItemDao
}
