package forpdateam.ru.forpda.model.repository.faviorites

import forpdateam.ru.forpda.entity.app.favorites.FavFolder
import forpdateam.ru.forpda.entity.db.favorites.FavFolderDao
import forpdateam.ru.forpda.entity.db.favorites.FavFolderItemRoom
import forpdateam.ru.forpda.entity.db.favorites.FavFolderRoom
import forpdateam.ru.forpda.entity.remote.favorites.IFavItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Локальные папки избранного: список папок и привязка «тема/форум → папка».
 *
 * Хранилище чисто клиентское (у 4pda папок нет) и намеренно не завязано на favId —
 * см. [forpdateam.ru.forpda.entity.db.favorites.FavFolderItemRoom].
 */
class FavoritesFoldersRepository(
        private val dao: FavFolderDao,
        /** Параметром — чтобы тесты гоняли БД на тест-планировщике, а не на реальном IO. */
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _folders = MutableStateFlow<List<FavFolder>>(emptyList())
    val folders: StateFlow<List<FavFolder>> = _folders.asStateFlow()

    /** targetKey → folderId. Темы без папки в карте отсутствуют. */
    private val _assignments = MutableStateFlow<Map<String, Long>>(emptyMap())
    val assignments: StateFlow<Map<String, Long>> = _assignments.asStateFlow()

    /**
     * Прочитано ли хранилище хоть раз. До первого чтения пустой список папок означает
     * «ещё не знаем», а не «папок нет» — иначе подписчик примет холодный старт за удаление
     * папки и сбросит выбранный фильтр (см. FavoritesViewModel.start).
     */
    @Volatile
    var isLoaded: Boolean = false
        private set

    suspend fun load() = withContext(ioDispatcher) {
        reloadFolders()
        reloadAssignments()
        isLoaded = true
    }

    suspend fun createFolder(name: String): FavFolder = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val id = dao.insertFolder(FavFolderRoom(name = name, sortOrder = now, createdAt = now, updatedAt = now))
        reloadFolders()
        FavFolder(id = id, name = name, sortOrder = now, createdAt = now, updatedAt = now)
    }

    suspend fun renameFolder(id: Long, name: String) = withContext(ioDispatcher) {
        dao.renameFolder(id, name, System.currentTimeMillis())
        reloadFolders()
    }

    /** Удаляет папку; темы из неё не пропадают, а возвращаются в «Без папки». */
    suspend fun deleteFolder(id: Long) = withContext(ioDispatcher) {
        dao.deleteFolderWithAssignments(id)
        reloadFolders()
        reloadAssignments()
    }

    suspend fun moveToFolder(targetKeys: List<String>, folderId: Long?) = withContext(ioDispatcher) {
        if (targetKeys.isEmpty()) return@withContext
        dao.moveToFolder(targetKeys, folderId, System.currentTimeMillis())
        reloadAssignments()
    }

    private suspend fun reloadFolders() {
        _folders.value = dao.getFolders().map { it.toAppItem() }
    }

    private suspend fun reloadAssignments() {
        _assignments.value = dao.getAssignments().associate { it.targetKey to it.folderId }
    }

    private fun FavFolderRoom.toAppItem(): FavFolder = FavFolder(
            id = id,
            name = name,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
    )

    companion object {
        /**
         * Ключ привязки. Тема и форум живут в одном пространстве ключей, но с разными
         * префиксами: topicId и forumId нумеруются независимо и могут совпасть.
         */
        fun targetKey(item: IFavItem): String =
                if (item.isForum) "f:${item.forumId}" else "t:${item.topicId}"

        /** Есть ли у элемента вообще идентификатор, по которому его можно положить в папку. */
        fun isAssignable(item: IFavItem): Boolean =
                if (item.isForum) item.forumId > 0 else item.topicId > 0
    }
}
