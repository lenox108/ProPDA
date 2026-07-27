package forpdateam.ru.forpda.model.data.cache.news

import android.content.Context
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.entity.remote.news.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * L2-кэш ЛЕНТЫ новостей (первая страница каждой категории) на диске.
 *
 * Зачем: в памяти лента жила 60 секунд, поэтому холодный старт и возврат во вкладку всегда упирались
 * в сеть — пустой экран на всё время запроса. Диск позволяет показать прошлую ленту мгновенно и
 * обновить её в фоне (stale-while-revalidate), а сохранённые валидаторы (`ETag`/`Last-Modified`)
 * дают шанс получить дешёвый `304 Not Modified` вместо полной HTML-страницы.
 *
 * Хранилище — один JSON-файл: записей мало (по одной на открытую категорию) и они компактные
 * (~20 заголовков), поэтому дробить на файлы, как для статей, смысла нет.
 */
class NewsListDiskCache(
        context: Context,
        private val maxEntries: Int = 8,
        private val maxAgeMs: Long = 24 * 60 * 60 * 1000L
) {

    data class Entry(
            val items: List<NewsItem>,
            val storedAtMs: Long,
            val etag: String?,
            val lastModified: String?
    ) {
        fun isFresh(nowMs: Long, maxAgeMs: Long): Boolean = nowMs - storedAtMs <= maxAgeMs
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<String, Entry>()
    @Volatile
    private var index: MutableMap<String, Entry>? = null
    private var saveJob: Job? = null

    private val file: File? = runCatching { File(context.filesDir, FILE_NAME) }.getOrNull()

    internal val cacheFileForTest: File?
        get() = file

    internal suspend fun flushForTest() {
        saveJob?.cancel()
        flushLocked()
    }

    suspend fun get(key: String, nowMs: Long = System.currentTimeMillis()): Entry? = mutex.withLock {
        pending[key]?.let { return if (it.isFresh(nowMs, maxAgeMs)) it else null }
        val entry = readAll()[key] ?: return null
        if (!entry.isFresh(nowMs, maxAgeMs)) return null
        entry
    }

    fun put(
            key: String,
            items: List<NewsItem>,
            etag: String? = null,
            lastModified: String? = null,
            nowMs: Long = System.currentTimeMillis()
    ) {
        if (items.isEmpty()) return
        pending[key] = Entry(items.map { it.copyItem() }, nowMs, etag, lastModified)
        scheduleFlush()
    }

    /** Ответ `304`: тело прежнее, обновляем только отметку свежести (и валидаторы, если пришли). */
    fun refreshValidity(
            key: String,
            entry: Entry,
            etag: String? = entry.etag,
            lastModified: String? = entry.lastModified,
            nowMs: Long = System.currentTimeMillis()
    ) {
        pending[key] = entry.copy(storedAtMs = nowMs, etag = etag, lastModified = lastModified)
        scheduleFlush()
    }

    fun invalidateAll() {
        pending.clear()
        scope.launch {
            mutex.withLock {
                index = HashMap()
                runCatching { file?.takeIf { it.exists() }?.delete() }
            }
        }
    }

    private fun scheduleFlush() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            mutex.withLock { flushLocked() }
        }
    }

    private suspend fun flushLocked() {
        val target = file ?: return
        if (pending.isEmpty()) return
        runCatching {
            val merged = HashMap(readAll())
            val drained = HashMap(pending)
            pending.keys.removeAll(drained.keys)
            merged.putAll(drained)
            trim(merged)
            index = merged
            val items = JSONArray()
            merged.entries
                    .sortedByDescending { it.value.storedAtMs }
                    .forEach { (key, entry) -> serializeEntry(key, entry)?.let(items::put) }
            writeAtomically(target, JSONObject().put("v", VERSION).put("items", items).toString())
        }.onFailure { error ->
            Timber.w(error, "News list disk cache flush failed")
        }
    }

    private fun trim(entries: MutableMap<String, Entry>) {
        while (entries.size > maxEntries) {
            val oldest = entries.entries.minByOrNull { it.value.storedAtMs } ?: break
            entries.remove(oldest.key)
        }
    }

    private fun readAll(): Map<String, Entry> {
        index?.let { return it }
        val target = file ?: return emptyMap()
        if (!target.exists() || target.length() == 0L) return emptyMap()
        return runCatching {
            val json = JSONObject(target.readText(Charsets.UTF_8))
            if (json.optInt("v", 0) != VERSION) return emptyMap()
            val items = json.optJSONArray("items") ?: return emptyMap()
            val result = HashMap<String, Entry>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val key = item.optString("key").takeIf { it.isNotBlank() } ?: continue
                deserializeEntry(item)?.let { result[key] = it }
            }
            index = result
            result
        }.getOrElse { error ->
            Timber.w(error, "News list disk cache read failed")
            runCatching { target.delete() }
            emptyMap()
        }
    }

    private fun serializeEntry(key: String, entry: Entry): JSONObject? = runCatching {
        val items = JSONArray()
        entry.items.forEach { item ->
            items.put(JSONObject()
                    .put("id", item.id)
                    .put("authorId", item.authorId)
                    .put("url", item.url.orEmpty())
                    .put("title", item.title.orEmpty())
                    .put("description", item.description.orEmpty())
                    .put("author", item.author.orEmpty())
                    .put("date", item.date.orEmpty())
                    .put("imgUrl", item.imgUrl.orEmpty())
                    .put("commentsCount", item.commentsCount)
                    .put("avatar", item.avatar.orEmpty())
                    .put("tags", JSONArray().also { array ->
                        item.tags.forEach { tag ->
                            array.put(JSONObject()
                                    .put("tag", tag.tag.orEmpty())
                                    .put("title", tag.title.orEmpty())
                                    .put("url", tag.url.orEmpty()))
                        }
                    }))
        }
        JSONObject()
                .put("key", key)
                .put("storedAtMs", entry.storedAtMs)
                .put("etag", entry.etag.orEmpty())
                .put("lastModified", entry.lastModified.orEmpty())
                .put("items", items)
    }.getOrNull()

    private fun deserializeEntry(json: JSONObject): Entry? {
        val storedAtMs = json.optLong("storedAtMs", 0L)
        if (storedAtMs <= 0L) return null
        val itemsJson = json.optJSONArray("items") ?: return null
        val items = ArrayList<NewsItem>(itemsJson.length())
        for (i in 0 until itemsJson.length()) {
            val itemJson = itemsJson.optJSONObject(i) ?: continue
            val id = itemJson.optInt("id", 0)
            if (id <= 0) continue
            items.add(NewsItem().apply {
                this.id = id
                authorId = itemJson.optInt("authorId", 0)
                url = itemJson.optString("url").takeIf { it.isNotBlank() }
                title = itemJson.optString("title").takeIf { it.isNotBlank() }
                description = itemJson.optString("description").takeIf { it.isNotBlank() }
                author = itemJson.optString("author").takeIf { it.isNotBlank() }
                date = itemJson.optString("date").takeIf { it.isNotBlank() }
                imgUrl = itemJson.optString("imgUrl").takeIf { it.isNotBlank() }
                commentsCount = itemJson.optInt("commentsCount", 0)
                avatar = itemJson.optString("avatar").takeIf { it.isNotBlank() }
                itemJson.optJSONArray("tags")?.let { tagsJson ->
                    for (t in 0 until tagsJson.length()) {
                        val tagJson = tagsJson.optJSONObject(t) ?: continue
                        tags.add(Tag(
                                tagJson.optString("tag").takeIf { it.isNotBlank() },
                                tagJson.optString("title").takeIf { it.isNotBlank() },
                                tagJson.optString("url").takeIf { it.isNotBlank() }
                        ))
                    }
                }
            })
        }
        if (items.isEmpty()) return null
        return Entry(
                items = items,
                storedAtMs = storedAtMs,
                etag = json.optString("etag").takeIf { it.isNotBlank() },
                lastModified = json.optString("lastModified").takeIf { it.isNotBlank() }
        )
    }

    private fun writeAtomically(target: File, body: String) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(body, Charsets.UTF_8)
        if (temp.renameTo(target)) return
        if (target.exists() && target.delete() && temp.renameTo(target)) return
        runCatching { temp.delete() }
        throw IOException("Unable to rename temp news list cache file")
    }

    private companion object {
        const val FILE_NAME = "news_list_cache.json"
        const val VERSION = 1
        const val FLUSH_DEBOUNCE_MS = 500L

        fun NewsItem.copyItem(): NewsItem = NewsItem().also { copy ->
            copy.id = id
            copy.authorId = authorId
            copy.url = url
            copy.title = title
            copy.description = description
            copy.author = author
            copy.date = date
            copy.imgUrl = imgUrl
            copy.commentsCount = commentsCount
            copy.avatar = avatar
            copy.tags.addAll(tags.map { Tag(it.tag, it.title, it.url) })
        }
    }
}
