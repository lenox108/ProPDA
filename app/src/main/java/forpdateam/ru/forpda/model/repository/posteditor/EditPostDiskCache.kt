package forpdateam.ru.forpda.model.repository.posteditor

import android.content.Context
import android.os.Parcel
import android.util.Base64
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent disk cache для форм редактирования постов. Сохраняет warm cache на диск,
 * чтобы при перезапуске приложения или возврате к теме через долгое время
 * форма открывалась мгновенно без сетевого запроса.
 *
 * Сериализация: EditPostForm → простые поля в JSON; AttachmentItem → Parcel marshall + Base64.
 * Poll не сохраняется (редкий случай).
 *
 * Invalidation: [remove] при отправке сообщения / вручную.
 * TTL на диске: [DISK_TTL_MS] (после этого запись игнорируется при загрузке).
 */
internal class EditPostDiskCache(
        private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var saveJob: Job? = null
    /** Запись в буфер — защищает от частых IO при множественных putWarmEntry. */
    private val pending = ConcurrentHashMap<Int, EditPostWarmEntry>()
    /** Известные id удалённых записей для следующей записи в файл. */
    private val pendingRemovals = ConcurrentHashMap.newKeySet<Int>()

    private val file: File? by lazy {
        runCatching {
            File(context.filesDir, FILE_NAME)
        }.getOrNull()
    }

    /**
     * Загрузить все валидные записи с диска в память. Вызывается один раз при старте репозитория.
     * Невалидные/просроченные/поврежденные записи молча пропускаются.
     */
    suspend fun loadAll(): Map<Int, EditPostWarmEntry> = mutex.withLock {
        val f = file ?: return@withLock emptyMap()
        if (!f.exists() || f.length() == 0L) return@withLock emptyMap()
        val result = HashMap<Int, EditPostWarmEntry>()
        try {
            val json = JSONObject(f.readText(Charsets.UTF_8))
            if (json.optInt("v", 0) != VERSION) return@withLock emptyMap()
            val items = json.optJSONArray("items") ?: return@withLock emptyMap()
            val now = System.currentTimeMillis()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val entry = deserializeEntry(item) ?: continue
                if (now - entry.storedAtMillis > DISK_TTL_MS) continue
                val postId = item.optInt("postId", 0)
                if (postId <= 0) continue
                result[postId] = entry
            }
            Timber.d("edit-post disk cache loaded: ${result.size} entries")
        } catch (e: Throwable) {
            Timber.w(e, "disk cache load failed, ignoring")
            runCatching { f.delete() }
        }
        return@withLock result
    }

    /** Поместить/обновить запись. Запись на диск выполняется с дебаунсом 500 мс. */
    fun put(postId: Int, entry: EditPostWarmEntry) {
        if (postId <= 0) return
        pending[postId] = entry
        pendingRemovals.remove(postId)
        scheduleFlush()
    }

    /** Удалить запись. Фактическое удаление на диске — с тем же дебаунсом. */
    fun remove(postId: Int) {
        if (postId <= 0) return
        pending.remove(postId)
        pendingRemovals.add(postId)
        scheduleFlush()
    }

    private fun scheduleFlush() {
        saveJob?.cancel()
        saveJob = scope.launch {
            kotlinx.coroutines.delay(FLUSH_DEBOUNCE_MS)
            flush()
        }
    }

    private suspend fun flush() = mutex.withLock {
        val f = file ?: return@withLock
        try {
            // Читаем текущий файл (если есть), применяем изменения, пишем обратно.
            val current = HashMap<Int, JSONObject>()
            if (f.exists() && f.length() > 0) {
                runCatching {
                    val json = JSONObject(f.readText(Charsets.UTF_8))
                    if (json.optInt("v", 0) == VERSION) {
                        val items = json.optJSONArray("items")
                        if (items != null) {
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val pid = item.optInt("postId", 0)
                                if (pid > 0) current[pid] = item
                            }
                        }
                    }
                }
            }
            // Apply pending changes.
            val toWrite = HashMap(pending)
            for ((pid, entry) in toWrite) {
                serializeEntry(pid, entry)?.let { current[pid] = it }
            }
            for (pid in pendingRemovals) {
                current.remove(pid)
            }
            pending.clear()
            pendingRemovals.clear()

            // Ограничение размера: если больше MAX_ENTRIES — оставляем самые свежие.
            val trimmed = if (current.size > MAX_ENTRIES) {
                current.entries
                        .sortedByDescending { it.value.optLong("storedAt", 0L) }
                        .take(MAX_ENTRIES)
                        .associate { it.toPair() }
            } else {
                current
            }

            val out = JSONObject().apply {
                put("v", VERSION)
                put("items", JSONArray().apply {
                    trimmed.values.forEach { put(it) }
                })
            }
            f.writeText(out.toString(), Charsets.UTF_8)
        } catch (e: Throwable) {
            Timber.w(e, "disk cache flush failed")
        }
    }

    private fun serializeEntry(postId: Int, entry: EditPostWarmEntry): JSONObject? {
        val form = entry.form ?: return null
        return try {
            JSONObject().apply {
                put("postId", postId)
                put("storedAt", entry.storedAtMillis)
                put("form", JSONObject().apply {
                    put("type", form.type)
                    put("errorCode", form.errorCode)
                    put("editReason", form.editReason)
                    put("message", form.message)
                    put("forumId", form.forumId)
                    put("topicId", form.topicId)
                    put("postId", form.postId)
                    put("st", form.st)
                })
                val allAttachments = form.attachments.toMutableList().apply {
                    entry.attachments?.let { addAll(it) }
                }
                put("attachments", JSONArray().apply {
                    allAttachments.forEach { att ->
                        serializeAttachment(att)?.let { put(it) }
                    }
                })
                put("hasSeparateAttachments", entry.attachments != null)
            }
        } catch (e: Throwable) {
            Timber.w(e, "serializeEntry failed")
            null
        }
    }

    private fun deserializeEntry(item: JSONObject): EditPostWarmEntry? {
        return try {
            val formObj = item.optJSONObject("form") ?: return null
            val form = EditPostForm().apply {
                type = formObj.optInt("type", 0)
                errorCode = formObj.optInt("errorCode", EditPostForm.ERROR_NONE)
                editReason = formObj.optString("editReason", "")
                message = formObj.optString("message", "")
                forumId = formObj.optInt("forumId", 0)
                topicId = formObj.optInt("topicId", 0)
                postId = formObj.optInt("postId", 0)
                st = formObj.optInt("st", 0)
            }
            val attArray = item.optJSONArray("attachments")
            val attachments = mutableListOf<AttachmentItem>()
            if (attArray != null) {
                for (j in 0 until attArray.length()) {
                    val s = attArray.optString(j, "")
                    if (s.isEmpty()) continue
                    deserializeAttachment(s)?.let { attachments.add(it) }
                }
            }
            form.attachments.clear()
            form.attachments.addAll(attachments)
            val storedAt = item.optLong("storedAt", System.currentTimeMillis())
            EditPostWarmEntry(
                    form = form,
                    // Записи в form.attachments уже содержат всё; раздельный список отмечаем как «загружен»
                    // если при сохранении он был явно установлен.
                    attachments = if (item.optBoolean("hasSeparateAttachments", false)) emptyList() else null,
                    storedAtMillis = storedAt,
            )
        } catch (e: Throwable) {
            Timber.w(e, "deserializeEntry failed")
            null
        }
    }

    private fun serializeAttachment(att: AttachmentItem): String? {
        val p = Parcel.obtain()
        return try {
            att.writeToParcel(p, 0)
            Base64.encodeToString(p.marshall(), Base64.NO_WRAP)
        } catch (e: Throwable) {
            Timber.w(e, "serializeAttachment failed")
            null
        } finally {
            p.recycle()
        }
    }

    private fun deserializeAttachment(b64: String): AttachmentItem? {
        val p = Parcel.obtain()
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            p.unmarshall(bytes, 0, bytes.size)
            p.setDataPosition(0)
            AttachmentItem.CREATOR.createFromParcel(p)
        } catch (e: Throwable) {
            Timber.w(e, "deserializeAttachment failed")
            null
        } finally {
            p.recycle()
        }
    }

    companion object {
        private const val FILE_NAME = "edit_post_cache.json"
        private const val VERSION = 1
        private const val FLUSH_DEBOUNCE_MS = 500L
        private const val DISK_TTL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val MAX_ENTRIES = 64
    }
}
