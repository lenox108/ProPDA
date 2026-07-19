package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.db.draft.PostDraftDao
import forpdateam.ru.forpda.entity.db.draft.PostDraftRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Персистентные черновики ответа в теме (полноэкранный редактор, TYPE_NEW_POST).
 * Ключ строится из topicId; см. [topicKey].
 */
class PostDraftRepository(
    private val dao: PostDraftDao,
) {

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

    suspend fun clear(key: String) = withContext(Dispatchers.IO) {
        dao.delete(key)
    }

    companion object {
        fun topicKey(topicId: Int): String = "topic:$topicId"
    }
}
