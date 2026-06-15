package forpdateam.ru.forpda.model.data.offline

import forpdateam.ru.forpda.entity.db.offline.OfflineItemStatus
import forpdateam.ru.forpda.entity.db.offline.OfflineItemType
import timber.log.Timber

/**
 * Phase 4 of the offline-reading feature (§5.1 of REFACTOR_PLAN.md).
 *
 * Companion to [OfflineRepository] that decides whether a
 * given article/theme request can be served entirely from the
 * offline cache, and exposes the saved HTML for the rendering
 * pipeline.
 *
 * The rendering pipeline (WebView in `ArticleContentFragment`
 * and `ThemeFragmentWeb`) is expected to be re-pointed at a
 * `WebViewAssetLoader` whose baseURL matches the values in
 * [OFFLINE_BASE_URL] / [OFFLINE_IMAGES_PREFIX]. That re-pointing
 * is the actual Phase 4 work; this class is the data-layer
 * half that the re-pointing consults to decide whether to
 * short-circuit the network.
 */
class OfflineArticleSource(
        private val repository: OfflineRepository,
) {

    /**
     * Probe result. `null` means the article is not saved
     * offline, so the caller should fall through to the
     * network path.
     */
    sealed class Probe {
        /** No offline copy; the caller must hit the network. */
        object NotSaved : Probe()

        /**
         * Offline copy exists but the image download is still
         * in flight; the caller may render the partial HTML
         * (images will be broken until the worker finishes)
         * or fall back to network.
         */
        data class Partial(val html: String) : Probe()

        /** Offline copy is fully cached. Render the local HTML. */
        data class Ready(val html: String, val modelJson: String) : Probe()
    }

    fun articleId(newsId: Long): String = "article:$newsId"

    fun themeId(themeId: Long, page: Int = 0): String =
            if (page <= 0) "theme:$themeId" else "theme:$themeId:$page"

    /**
     * Look up an offline article by [newsId]. Returns the
     * cached HTML payload (and the saved modelJson when the
     * entry is fully cached), or `null` when the article is
     * not in the offline cache.
     */
    suspend fun lookupArticle(newsId: Long): Probe {
        if (newsId <= 0L) return Probe.NotSaved
        val row = repository.getById(articleId(newsId)) ?: return Probe.NotSaved
        return probeFor(row, repository)
    }

    /**
     * Look up an offline theme page by [themeId] and optional
     * [page] (used for paginated themes).
     */
    suspend fun lookupTheme(themeId: Long, page: Int = 0): Probe {
        if (themeId <= 0L) return Probe.NotSaved
        val row = repository.getById(themeId(themeId, page)) ?: return Probe.NotSaved
        return probeFor(row, repository)
    }

    private suspend fun probeFor(
            row: forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom,
            repo: OfflineRepository,
    ): Probe {
        val html = repo.readHtml(row.id) ?: run {
            Timber.w("OfflineArticleSource: row %s missing HTML on disk", row.id)
            return Probe.NotSaved
        }
        return when (row.status) {
            OfflineItemStatus.COMPLETE -> Probe.Ready(html, row.modelJson)
            OfflineItemStatus.PARTIAL -> Probe.Partial(html)
            else -> Probe.NotSaved
        }
    }

    companion object {
        /**
         * Base URL the [androidx.webkit.WebViewAssetLoader] is
         * expected to serve from when rendering offline articles
         * and themes. Path is hard-coded to keep the contract
         * between the asset loader and this class aligned.
         */
        const val OFFLINE_BASE_URL: String = "https://offline.local/"

        /**
         * Path prefix under [OFFLINE_BASE_URL] that resolves to
         * the item's `images/` directory. Must match
         * [OfflineImageDownloader.LOCAL_IMAGES_PREFIX].
         */
        const val OFFLINE_IMAGES_PREFIX: String = "images/"

        const val OFFLINE_HTML_FILENAME: String = "index.html"
    }
}
