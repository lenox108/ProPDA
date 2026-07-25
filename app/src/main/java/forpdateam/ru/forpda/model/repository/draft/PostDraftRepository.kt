package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.db.draft.PostDraftDao
import forpdateam.ru.forpda.entity.db.draft.PostDraftRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Персистентные черновики редактора для темы, полноэкранной формы и QMS.
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
    private val operationMutex = Mutex()
    private val operationClock = AtomicLong()
    private val latestOperation = ConcurrentHashMap<String, Long>()

    suspend fun load(key: String): String? = loadDraft(key)?.message

    suspend fun loadDraft(key: String): PostDraft? = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val now = System.currentTimeMillis()
            dao.deleteOlderThan(now - DRAFT_TTL_MS)
            val room = dao.get(key) ?: return@withLock null
            if (room.message.isBlank() && room.attachmentsJson == "[]" && !room.attachmentsChanged) {
                return@withLock null
            }
            PostDraft(
                message = room.message,
                selectionStart = room.selectionStart,
                selectionEnd = room.selectionEnd,
                attachments = decodeAttachments(room.attachmentsJson),
                editorMode = room.editorMode,
                attachmentsChanged = room.attachmentsChanged,
            )
        }
    }

    suspend fun save(key: String, message: String, updatedAt: Long) =
        save(key, PostDraft(message = message), updatedAt)

    suspend fun save(key: String, draft: PostDraft, updatedAt: Long) {
        val operation = registerOperation(key)
        withContext(Dispatchers.IO) {
            saveRegistered(key, draft, updatedAt, operation)
        }
    }

    /** App-lifetime write used by lifecycle flush; it survives destruction of the screen scope. */
    fun saveFireAndForget(key: String, draft: PostDraft, updatedAt: Long) {
        val operation = registerOperation(key)
        ioScope.launch {
            runCatching { saveRegistered(key, draft, updatedAt, operation) }
        }
    }

    private suspend fun saveRegistered(
        key: String,
        draft: PostDraft,
        updatedAt: Long,
        operation: Long,
    ) {
        operationMutex.withLock {
            // Latest invocation wins. In particular, a delayed save can never resurrect a draft
            // after clearFireAndForget registered a newer operation for the same key.
            if (latestOperation[key] != operation) return
            dao.deleteOlderThan(updatedAt - DRAFT_TTL_MS)
            if (draft.isEmpty) {
                dao.delete(key)
            } else {
                dao.upsert(
                    PostDraftRoom(
                        key = key,
                        message = draft.message,
                        updatedAt = updatedAt,
                        selectionStart = draft.selectionStart,
                        selectionEnd = draft.selectionEnd,
                        attachmentsJson = encodeAttachments(draft.attachments),
                        editorMode = draft.editorMode,
                        attachmentsChanged = draft.attachmentsChanged,
                    )
                )
            }
        }
    }

    /** Удаление, переживающее уничтожение ViewModel (выход/отправка). */
    fun clearFireAndForget(key: String) {
        val operation = registerOperation(key)
        ioScope.launch {
            runCatching {
                operationMutex.withLock {
                    if (latestOperation[key] == operation) {
                        dao.delete(key)
                    }
                }
            }
        }
    }

    private fun registerOperation(key: String): Long =
        operationClock.incrementAndGet().also { latestOperation[key] = it }

    companion object {
        private const val DRAFT_TTL_MS = 30L * 24L * 60L * 60L * 1000L

        fun topicKey(topicId: Int): String = "topic:$topicId"
        fun postKey(postId: Int): String = "post:$postId"

        private fun encodeAttachments(items: List<DraftAttachment>): String =
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("id", item.id)
                            put("name", item.name)
                            put("extension", item.extension)
                            put("weight", item.weight)
                            put("typeFile", item.typeFile)
                            put("loadState", item.loadState)
                            put("status", item.status)
                            put("imageUrl", item.imageUrl)
                            put("url", item.url)
                            put("width", item.width)
                            put("height", item.height)
                            put("md5", item.md5)
                            put("isError", item.isError)
                            put("errorText", item.errorText)
                            put("sourceUri", item.sourceUri)
                            put("sourceMimeType", item.sourceMimeType)
                            put("sourceFileSize", item.sourceFileSize)
                        }
                    )
                }
            }.toString()

        private fun decodeAttachments(value: String): List<DraftAttachment> = runCatching {
            val array = JSONArray(value)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        DraftAttachment(
                            id = item.optInt("id", -1),
                            name = item.stringOrNull("name"),
                            extension = item.stringOrNull("extension"),
                            weight = item.stringOrNull("weight"),
                            typeFile = item.optInt("typeFile"),
                            loadState = item.optInt("loadState"),
                            status = item.optInt("status"),
                            imageUrl = item.stringOrNull("imageUrl"),
                            url = item.stringOrNull("url"),
                            width = item.optInt("width"),
                            height = item.optInt("height"),
                            md5 = item.stringOrNull("md5"),
                            isError = item.optBoolean("isError"),
                            errorText = item.stringOrNull("errorText"),
                            sourceUri = item.stringOrNull("sourceUri"),
                            sourceMimeType = item.stringOrNull("sourceMimeType"),
                            sourceFileSize = item.longOrNull("sourceFileSize"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf(String::isNotEmpty)

        private fun JSONObject.longOrNull(key: String): Long? =
            if (isNull(key) || !has(key)) null else optLong(key).takeIf { it >= 0L }
    }
}
