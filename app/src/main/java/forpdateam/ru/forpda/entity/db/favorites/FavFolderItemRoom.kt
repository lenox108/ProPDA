package forpdateam.ru.forpda.entity.db.favorites

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Привязка темы/форума избранного к папке.
 *
 * Ключ — [targetKey] (`t:<topicId>` / `f:<forumId>`), а НЕ favId: favId выдаётся сервером
 * записи избранного и меняется при удалении/повторном добавлении темы, после чего папка
 * потерялась бы. Таблица отдельная от `favorites` намеренно: та вычищается целиком на
 * каждом обновлении списка (replaceFavorites), колонку внутри неё стирало бы.
 */
@Entity(tableName = "fav_folder_items", indices = [Index("folderId")])
data class FavFolderItemRoom(
    @PrimaryKey
    val targetKey: String,
    val folderId: Long,
    val updatedAt: Long
) {
    constructor() : this("", 0, 0)
}
