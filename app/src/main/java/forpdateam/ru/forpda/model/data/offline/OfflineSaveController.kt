package forpdateam.ru.forpda.model.data.offline

import forpdateam.ru.forpda.entity.db.offline.OfflineItemStatus
import forpdateam.ru.forpda.entity.db.offline.OfflineItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Thin façade that ties [OfflineRepository] + [OfflineImageDownloader] to a coroutine scope so
 * the UI layer can fire-and-forget the "save for offline" action without dealing with
 * dispatcher plumbing.
 *
 * The data layer ([OfflineRepository.saveWithImages]) is already async — the controller just
 * launches it on [Dispatchers.IO] and invokes [onSuccess] / [onError] on the main thread.
 */
class OfflineSaveController(
        private val repository: OfflineRepository,
        private val imageDownloader: OfflineImageDownloader,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun save(
            id: String,
            type: String,
            sourceUrl: String,
            title: String,
            html: String,
            modelJson: String,
            onSuccess: ((OfflineRepository.SaveResult) -> Unit)? = null,
            onError: ((Throwable) -> Unit)? = null,
    ) {
        scope.launch {
            try {
                val result = repository.saveWithImages(
                        id = id,
                        type = type,
                        sourceUrl = sourceUrl,
                        title = title,
                        html = html,
                        modelJson = modelJson,
                        imageDownloader = imageDownloader
                )
                repository.markStatus(id, OfflineItemStatus.COMPLETE, sizeBytes = null)
                onSuccess?.invoke(result)
            } catch (t: Throwable) {
                Timber.w(t, "OfflineSaveController.save failed for %s", id)
                runCatching { repository.markStatus(id, OfflineItemStatus.FAILED, sizeBytes = null) }
                onError?.invoke(t)
            }
        }
    }

    companion object {
        /** Helper to build the stable id for an article or theme. */
        fun articleId(id: Int): String = "${OfflineItemType.ARTICLE.lowercase()}:$id"

        fun themeId(topicId: Int, page: Int): String =
                "${OfflineItemType.THEME.lowercase()}:$topicId:$page"
    }
}
