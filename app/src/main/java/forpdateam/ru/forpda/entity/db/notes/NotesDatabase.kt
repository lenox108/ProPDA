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
        ForumUserRoom::class
    ],
    // Версию НЕ поднимаем: любой подъём ломает откат на сборку без новой фичи («A migration
    // from N to 6 was required but not found»). Новые таблицы заводим отдельными БД —
    // см. FavoritesFoldersDatabase / PostDraftDatabase / TopicReadBoundaryDatabase.
    version = 6,
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
}
