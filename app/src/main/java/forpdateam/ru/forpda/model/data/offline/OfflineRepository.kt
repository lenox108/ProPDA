package forpdateam.ru.forpda.model.data.offline

import forpdateam.ru.forpda.entity.db.offline.OfflineItemDao
import forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
import forpdateam.ru.forpda.entity.db.offline.OfflineItemStatus
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Phase 1 facade for the offline-reading feature (§5.1 of
 * REFACTOR_PLAN.md). Combines the [OfflineItemDao] (metadata)
 * with the [OfflineStorage] (HTML and image files on disk).
 *
 * The actual "Save" action and image downloading are scheduled
 * for Phases 2/5; this repository only knows how to insert and
 * update rows and the corresponding HTML payload.
 */
class OfflineRepository(
        private val dao: OfflineItemDao,
        private val storage: OfflineStorage,
) {

    fun observeAll(): Flow<List<OfflineItemRoom>> = dao.observeAll()

    suspend fun getAll(): List<OfflineItemRoom> = dao.getAll()

    suspend fun getById(id: String): OfflineItemRoom? = dao.getById(id)

    /**
     * Persists a new saved item. Caller is responsible for
     * filling in [OfflineItemRoom.modelJson] and the rendered
     * HTML; image downloads happen later (Phase 5).
     */
    suspend fun save(
            id: String,
            type: String,
            sourceUrl: String,
            title: String,
            html: String,
            modelJson: String,
    ): OfflineItemRoom {
        storage.writeHtml(id, html)
        val size = storage.sizeOf(id)
        val item = OfflineItemRoom(
                id = id,
                type = type,
                sourceUrl = sourceUrl,
                title = title,
                savedAtMs = System.currentTimeMillis(),
                sizeBytes = size,
                status = OfflineItemStatus.PARTIAL,
                htmlPath = "offline/${id}/index.html",
                modelJson = modelJson,
        )
        dao.insert(item)
        return item
    }

    /** Persists the rendered HTML for an existing record. */
    suspend fun writeHtml(id: String, html: String): Long {
        val bytes = storage.writeHtml(id, html)
        dao.updateStatus(id, statusOf(id) ?: OfflineItemStatus.PARTIAL, bytes)
        return bytes
    }

    suspend fun readHtml(id: String): String? = storage.readHtml(id)

    suspend fun markStatus(id: String, status: String, sizeBytes: Long?) {
        val finalSize = sizeBytes ?: storage.sizeOf(id)
        dao.updateStatus(id, status, finalSize)
    }

    suspend fun delete(id: String) {
        storage.delete(id)
        dao.deleteById(id)
    }

    suspend fun deleteAll() {
        storage.deleteAll()
        dao.deleteAll()
    }

    suspend fun totalSizeBytes(): Long = dao.sumSize()

    /**
     * Phase 2 + Phase 5 combined save flow: persists the rendered
     * HTML, then runs the [imageDownloader] to fetch and rewrite
     * images, and finally updates the row to [OfflineItemStatus.COMPLETE].
     *
     * The caller (Phase 2 menu action / WorkManager worker) supplies
     * the [imageDownloader]; in production that is the Hilt-provided
     * singleton. Tests can inject a fake.
     */
    suspend fun saveWithImages(
            id: String,
            type: String,
            sourceUrl: String,
            title: String,
            html: String,
            modelJson: String,
            imageDownloader: OfflineImageDownloader,
    ): SaveResult {
        save(id, type, sourceUrl, title, html, modelJson)
        val download = imageDownloader.downloadAndRewrite(id, html)
        // Persist the rewritten HTML so subsequent reads see local
        // image references (avoids re-downloading on every load).
        storage.writeHtml(id, download.rewrittenHtml)
        markStatus(id, OfflineItemStatus.COMPLETE, sizeBytes = null)
        return SaveResult(
                downloadedImages = download.imagesDownloaded,
                failedImages = download.imagesFailed,
        )
    }

    /**
     * Backwards-compatible overload of [saveWithImages] that also
     * enforces the storage cap ([maxBytes]) after a successful
     * save. Phase 5+ callers (WorkManager worker, menu action)
     * can use this so the cache stays bounded without an extra
     * post-save invocation. A `maxBytes <= 0` value is a no-op
     * and falls back to the unlimited behaviour of the original
     * saveWithImages contract.
     */
    suspend fun saveWithImages(
            id: String,
            type: String,
            sourceUrl: String,
            title: String,
            html: String,
            modelJson: String,
            imageDownloader: OfflineImageDownloader,
            maxBytes: Long,
    ): SaveResult {
        val result = saveWithImages(id, type, sourceUrl, title, html, modelJson, imageDownloader)
        if (maxBytes > 0L) {
            runCatching { enforceStorageLimit(maxBytes) }
                    .onFailure { Timber.w(it, "OfflineRepository: enforceStorageLimit failed after save") }
        }
        return result
    }

    data class SaveResult(
            val downloadedImages: Int,
            val failedImages: Int,
    )

    /**
     * Phase 6 — LRU-by-savedAtMs eviction. Deletes the oldest
     * saved items until the total on-disk size is at or below
     * [maxBytes]. Returns the number of items removed.
     *
     * Items with [OfflineItemStatus.PARTIAL] are evicted first
     * (incomplete downloads clog the cache), then the oldest
     * complete items. The deletion is best-effort: failures
     * are logged and counted in the returned number.
     */
    suspend fun enforceStorageLimit(maxBytes: Long): Int {
        if (maxBytes <= 0L) return 0
        var current = dao.sumSize()
        if (current <= maxBytes) return 0
        val candidates = dao.getOldestFirst(limit = MAX_EVICTION_CANDIDATES)
        var removed = 0
        for (item in candidates) {
            if (current <= maxBytes) break
            if (item.status == OfflineItemStatus.PARTIAL) {
                runCatching { delete(item.id) }
                        .onSuccess {
                            current -= item.sizeBytes
                            removed++
                            Timber.d(
                                    "OfflineRepository: evicted PARTIAL item %s (freed=%d)",
                                    item.id,
                                    item.sizeBytes
                            )
                        }
                        .onFailure { Timber.w(it, "OfflineRepository: failed to evict %s", item.id) }
            }
        }
        for (item in candidates) {
            if (current <= maxBytes) break
            runCatching { delete(item.id) }
                    .onSuccess {
                        current -= item.sizeBytes
                        removed++
                        Timber.d(
                                "OfflineRepository: evicted LRU item %s (freed=%d)",
                                item.id,
                                item.sizeBytes
                        )
                    }
                    .onFailure { Timber.w(it, "OfflineRepository: failed to evict %s", item.id) }
        }
        return removed
    }

    private suspend fun statusOf(id: String): String? = runCatching { dao.getById(id)?.status }
            .onFailure { Timber.w(it, "OfflineRepository.statusOf failed for %s", id) }
            .getOrNull()

    companion object {
        /**
         * Hard cap on the number of eviction candidates we pull
         * per enforcement pass. The current on-device corpus
         * is small (a handful of articles), but the cap keeps
         * the query bounded if the table grows.
         */
        const val MAX_EVICTION_CANDIDATES: Int = 200
    }
}
