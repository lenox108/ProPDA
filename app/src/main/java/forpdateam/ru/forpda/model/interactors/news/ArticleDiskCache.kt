package forpdateam.ru.forpda.model.interactors.news

import android.content.Context
import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.news.ARTICLE_PARSER_VERSION
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleHtmlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk L2 cache for mapped articles. Uses the same [ArticleHtmlValidator] keys as memory cache.
 *
 * Storage model (PERF-003): a single JSON index file (`article_disk_cache.json`) holds all entries.
 * An in-memory [diskEntriesIndex] mirrors the parsed file between flushes; [pending] queues writes
 * and [pendingRemovals] tombstones ids until the debounced flush merges into the index atomically.
 * Per-article files were not adopted to keep invalidation and trim logic in one place.
 */
class ArticleDiskCache(
        context: Context,
        private val maxEntries: Int = 24,
        private val maxAgeMs: Long = 24 * 60 * 60 * 1000L
) {

    data class Entry(
            val page: DetailsPage,
            val parserVersion: Int,
            val storedAtMs: Long,
            val deferredExtrasPending: Boolean = false
    )

    data class Lookup(
            val entry: Entry?,
            val hit: Boolean,
            val valid: Boolean,
            val reason: String?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = ConcurrentHashMap<Int, Entry>()
    private val pendingRemovals = ConcurrentHashMap.newKeySet<Int>()
    @Volatile
    private var diskEntriesIndex: Map<Int, Entry>? = null
    private var saveJob: Job? = null

    private val file: File? = runCatching {
        File(context.filesDir, FILE_NAME)
    }.getOrNull()

    internal val cacheFileForTest: File?
        get() = file

    internal suspend fun flushForTest() {
        saveJob?.cancel()
        flushLocked()
    }

    suspend fun get(articleId: Int, nowMs: Long = System.currentTimeMillis()): Lookup = mutex.withLock {
        if (articleId <= 0) {
            return Lookup(null, hit = false, valid = false, reason = "missing_id")
        }
        pending[articleId]?.let { pendingEntry ->
            return validateEntry(pendingEntry, articleId, nowMs, fromPending = true)
        }
        val diskEntry = readEntryFromDisk(articleId) ?: run {
            ArticleCacheTrace.log(
                    event = "miss",
                    articleId = articleId,
                    cacheLayer = "disk",
                    hit = false,
                    valid = false,
                    reason = "not_found"
            )
            return Lookup(null, hit = false, valid = false, reason = "not_found")
        }
        validateEntry(diskEntry, articleId, nowMs, fromPending = false)
    }

    fun put(page: DetailsPage, nowMs: Long = System.currentTimeMillis()) {
        val id = page.id
        if (id <= 0 || !ArticleHtmlValidator.hasNonEmptyParsedBody(page) || page.title.isNullOrBlank()) {
            return
        }
        pending[id] = Entry(
                page = page,
                parserVersion = ARTICLE_PARSER_VERSION,
                storedAtMs = nowMs,
                deferredExtrasPending = ArticleDeferredExtrasMerger.needsDeferredExtras(page)
        )
        pendingRemovals.remove(id)
        scheduleFlush()
        ArticleCacheTrace.log(
                event = "write_ok",
                articleId = id,
                cacheLayer = "disk",
                hit = false,
                valid = true,
                mappedHtmlLen = page.html?.length,
                reason = "queued"
        )
    }

    fun invalidate(articleId: Int = -1) {
        if (articleId > 0) {
            pending.remove(articleId)
            pendingRemovals.add(articleId)
            diskEntriesIndex = diskEntriesIndex?.minus(articleId)
        } else {
            pending.clear()
            pendingRemovals.clear()
            diskEntriesIndex = null
            runCatching { file?.delete() }
        }
        scheduleFlush()
    }

    private fun validateEntry(
            entry: Entry,
            articleId: Int,
            nowMs: Long,
            fromPending: Boolean
    ): Lookup {
        val verdict = ArticleHtmlValidator.validateCached(
                page = entry.page,
                parserVersion = entry.parserVersion,
                storedAtMs = entry.storedAtMs,
                maxAgeMs = maxAgeMs,
                nowMs = nowMs
        )
        if (!verdict.valid) {
            if (!fromPending) {
                pendingRemovals.add(articleId)
                scheduleFlush()
            }
            ArticleCacheTrace.log(
                    event = "rejected_invalid",
                    articleId = articleId,
                    cacheLayer = "disk",
                    hit = true,
                    valid = false,
                    mappedHtmlLen = entry.page.html?.length,
                    reason = verdict.reason
            )
            return Lookup(null, hit = true, valid = false, reason = verdict.reason)
        }
        ArticleCacheTrace.log(
                event = "hit",
                articleId = articleId,
                cacheLayer = "disk",
                hit = true,
                valid = true,
                mappedHtmlLen = entry.page.html?.length,
                reason = if (fromPending) "pending" else "ok",
                extra = mapOf("ageMs" to (nowMs - entry.storedAtMs))
        )
        return Lookup(
                entry = entry,
                hit = true,
                valid = true,
                reason = if (entry.deferredExtrasPending) "deferred_extras_pending" else null
        )
    }

    private fun scheduleFlush() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(FLUSH_DEBOUNCE_MS)
            flushLocked()
        }
    }

    private suspend fun flushLocked() = mutex.withLock {
        val target = file ?: return@withLock
        val merged = readAllFromDisk().toMutableMap()
        pending.forEach { (id, entry) -> merged[id] = entry }
        pendingRemovals.forEach { merged.remove(it) }
        pending.clear()
        pendingRemovals.clear()
        diskEntriesIndex = merged.toMap()
        trim(merged)
        if (merged.isEmpty()) {
            runCatching { target.delete() }
            return@withLock
        }
        runCatching {
            val items = org.json.JSONArray()
            merged.values.sortedByDescending { it.storedAtMs }.forEach { entry ->
                serializeEntry(entry)?.let { items.put(it) }
            }
            val root = JSONObject()
                    .put("v", VERSION)
                    .put("items", items)
            writeAtomically(target, root.toString())
        }.onFailure { error ->
            Timber.w(error, "Article disk cache flush failed")
        }
    }

    private fun trim(entries: MutableMap<Int, Entry>) {
        while (entries.size > maxEntries) {
            val oldest = entries.values.minByOrNull { it.storedAtMs } ?: break
            entries.remove(oldest.page.id)
        }
    }

    private fun readAllFromDisk(): Map<Int, Entry> {
        diskEntriesIndex?.let { return it }
        val target = file ?: return emptyMap()
        if (!target.exists() || target.length() == 0L) return emptyMap()
        return runCatching {
            val json = JSONObject(target.readText(Charsets.UTF_8))
            if (json.optInt("v", 0) != VERSION) return emptyMap()
            val items = json.optJSONArray("items") ?: return emptyMap()
            val result = HashMap<Int, Entry>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                deserializeEntry(item)?.let { entry ->
                    result[entry.page.id] = entry
                }
            }
            diskEntriesIndex = result
            result
        }.getOrElse { error ->
            Timber.w(error, "Article disk cache read failed")
            quarantineCorruptFile(target, error)
            emptyMap()
        }
    }

    private fun readEntryFromDisk(articleId: Int): Entry? = readAllFromDisk()[articleId]

    private fun serializeEntry(entry: Entry): JSONObject? {
        val page = entry.page
        return runCatching {
            JSONObject()
                    .put("articleId", page.id)
                    .put("parserVersion", entry.parserVersion)
                    .put("storedAtMs", entry.storedAtMs)
                    .put("title", page.title.orEmpty())
                    .put("html", page.html.orEmpty())
                    .put("url", page.url.orEmpty())
                    .put("commentsCount", page.commentsCount)
                    .put("commentsSource", page.commentsSource.orEmpty())
                    .put("desktopCommentsSource", page.desktopCommentsSource.orEmpty())
                    .put("imgUrl", page.imgUrl.orEmpty())
                    .put("author", page.author.orEmpty())
                    .put("date", page.date.orEmpty())
                    .put("deferredExtrasPending", entry.deferredExtrasPending)
        }.getOrNull()
    }

    private fun deserializeEntry(item: JSONObject): Entry? {
        val id = item.optInt("articleId", 0)
        if (id <= 0) return null
        val storedAtMs = item.optLong("storedAtMs", 0L)
        if (storedAtMs <= 0L) return null
        val page = DetailsPage().apply {
            this.id = id
            title = item.optString("title").takeIf { it.isNotBlank() }
            html = item.optString("html").takeIf { it.isNotBlank() }
            url = item.optString("url").takeIf { it.isNotBlank() }
            commentsCount = item.optInt("commentsCount", 0)
            commentsSource = item.optString("commentsSource").takeIf { it.isNotBlank() }
            desktopCommentsSource = item.optString("desktopCommentsSource").takeIf { it.isNotBlank() }
            imgUrl = item.optString("imgUrl").takeIf { it.isNotBlank() }
            author = item.optString("author").takeIf { it.isNotBlank() }
            date = item.optString("date").takeIf { it.isNotBlank() }
        }
        return Entry(
                page = page,
                parserVersion = item.optInt("parserVersion", ARTICLE_PARSER_VERSION),
                storedAtMs = storedAtMs,
                deferredExtrasPending = item.optBoolean("deferredExtrasPending", false)
        )
    }

    private fun writeAtomically(target: File, body: String) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(body, Charsets.UTF_8)
        if (temp.renameTo(target)) {
            return
        }
        if (target.exists() && target.delete() && temp.renameTo(target)) {
            return
        }
        runCatching { temp.delete() }
        throw IOException("Unable to rename temp cache file")
    }

    private fun quarantineCorruptFile(target: File, error: Throwable) {
        val corruptLen = target.length().takeIf { it >= 0L }
        val quarantine = File(target.parentFile, "${target.name}.corrupt")
        val quarantined = runCatching {
            if (quarantine.exists()) quarantine.delete()
            target.renameTo(quarantine) || target.delete()
        }.getOrDefault(false)
        ArticleCacheTrace.log(
                event = "read_corrupt",
                cacheLayer = "disk",
                hit = false,
                valid = false,
                reason = "parse_failed",
                extra = mapOf(
                        "fileLen" to corruptLen,
                        "quarantined" to quarantined,
                        "errorClass" to error::class.java.simpleName
                )
        )
    }

    private companion object {
        private const val FILE_NAME = "article_disk_cache.json"
        private const val VERSION = 1
        private const val FLUSH_DEBOUNCE_MS = 500L
    }
}
