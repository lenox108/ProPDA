package forpdateam.ru.forpda.entity.app.favorites

/**
 * Локальная папка избранного. На 4pda папок нет — это чисто клиентская группировка,
 * которая живёт в БД и переживает полную перезапись серверного списка
 * (см. [forpdateam.ru.forpda.entity.db.favorites.FavItemDao.replaceFavorites]).
 */
data class FavFolder(
    val id: Long = 0,
    val name: String = "",
    val sortOrder: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
