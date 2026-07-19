package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.db.draft.PostDraftDao
import forpdateam.ru.forpda.entity.db.draft.PostDraftRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Персистентные черновики ответа в теме (полноэкранный редактор, TYPE_NEW_POST).
 * Ключ строится из topicId; см. [topicKey].
 */
class PostDraftRepository(
    private val dao: PostDraftDao,
) {

    /**
     * Собственный app-lifetime scope (репозиторий @Singleton) для fire-and-forget удаления.
     * Очистка при выходе не должна зависеть от scope ViewModel: тот отменяется в onCleared сразу
     * за router.exit(), и удаление, запущенное на нём, не успевало выполниться — черновик воскресал.
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun load(key: String): String? = withContext(Dispatchers.IO) {
        dao.get(key)?.message?.takeIf { it.isNotEmpty() }
    }

    suspend fun save(key: String, message: String, updatedAt: Long) = withContext(Dispatchers.IO) {
        if (message.isEmpty()) {
            dao.delete(key)
        } else {
            dao.upsert(PostDraftRoom(key = key, message = message, updatedAt = updatedAt))
        }
    }

    /** Удаление, переживающее уничтожение ViewModel (выход/отправка). */
    fun clearFireAndForget(key: String) {
        ioScope.launch { runCatching { dao.delete(key) } }
    }

    companion object {
        fun topicKey(topicId: Int): String = "topic:$topicId"
    }
}
