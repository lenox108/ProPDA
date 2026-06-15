package forpdateam.ru.forpda.model.data.offline

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Phase 5 of the offline-reading feature (§5.1 of REFACTOR_PLAN.md).
 *
 * Walks the rendered HTML of a saved item, collects every
 * `<img src="…">` reference, downloads each unique URL into
 * the item's `images/` directory and rewrites the HTML so the
 * `src` attribute points at the locally cached file.
 *
 * Behaviour:
 *  - The HTML is re-rendered with Jsoup, image URLs are
 *    absolute, and the local relative path is written back
 *    into `src` so a [androidx.webkit.WebViewAssetLoader] can
 *    serve the file via `https://offline.local/images/...`
 *    from Phase 4.
 *  - URLs that have already been resolved to relative paths
 *    (start with `images/`) are skipped, so the function is
 *    idempotent and safe to re-run.
 *  - All network/IO failures are logged and skipped; the
 *    caller receives the count of successful downloads.
 *
 * This is a pure data-layer component: no WorkManager, no
 * notifications. Phases 2 and 5 schedule it from the
 * "Save for offline" action.
 */
class OfflineImageDownloader(
        private val httpClient: OkHttpClient,
        private val storage: OfflineStorage,
) {

    data class Result(
            val imagesDownloaded: Int,
            val imagesFailed: Int,
            val rewrittenHtml: String,
    )

    /**
     * Downloads all images referenced from [html] into the item's
     * `images/` directory and rewrites the HTML in-place.
     *
     * @param itemId the saved item id (e.g. "article:12345")
     * @param html the rendered HTML to scan
     * @return counts of successful/failed downloads plus the
     * rewritten HTML
     */
    fun downloadAndRewrite(itemId: String, html: String): Result {
        val document = Jsoup.parse(html)
        val imagesDir = storage.imagesDir(itemId)
        val rewritten = HashSet<String>() // already-rewritten <img>s
        var downloaded = 0
        var failed = 0
        for (img in document.select("img[src]")) {
            val src = img.attr("src")
            if (src.isBlank()) continue
            // Skip already-rewritten images (idempotent re-runs).
            if (src.startsWith(LOCAL_IMAGES_PREFIX)) {
                rewritten.add(src)
                continue
            }
            val absoluteUrl = absolutize(src) ?: continue
            val fileName = fileNameFor(absoluteUrl) ?: continue
            val target = File(imagesDir, fileName)
            try {
                downloadTo(absoluteUrl, target)
                img.attr("src", "$LOCAL_IMAGES_PREFIX$fileName")
                rewritten.add("$LOCAL_IMAGES_PREFIX$fileName")
                downloaded++
            } catch (io: IOException) {
                Timber.w(io, "OfflineImageDownloader: failed to fetch %s", absoluteUrl)
                failed++
            }
        }
        Timber.d(
                "OfflineImageDownloader: id=%s downloaded=%d failed=%d unique=%d",
                itemId,
                downloaded,
                failed,
                rewritten.size
        )
        return Result(
                imagesDownloaded = downloaded,
                imagesFailed = failed,
                rewrittenHtml = rewriteDocumentOutput(document, html)
        )
    }

    private fun rewriteDocumentOutput(document: Document, fallback: String): String {
        // Jsoup's Document.toString() is well-defined for the
        // re-serialised tree, but if the parser ate the original
        // HTML structure (rare edge case with very malformed
        // articles), we keep the original.
        return runCatching { document.outerHtml() }.getOrDefault(fallback)
    }

    private fun downloadTo(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IOException("empty body for $url")
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    private fun absolutize(src: String): String? {
        val trimmed = src.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "${SITE_BASE_HTTPS}$trimmed"
            trimmed.startsWith("data:") -> null // inline data: URI, nothing to download
            else -> "${SITE_BASE_HTTPS}/$trimmed"
        }
    }

    private fun fileNameFor(url: String): String? {
        // Strip the query string and fragment, then take the last
        // path segment. The result keeps the original extension so
        // the WebView MIME inference keeps working.
        val noQuery = url.substringBefore('?').substringBefore('#')
        val last = noQuery.substringAfterLast('/')
        if (last.isBlank()) return null
        val safe = last.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (safe.isBlank()) return null
        return safe.take(120)
    }

    companion object {
        const val LOCAL_IMAGES_PREFIX: String = "images/"
        const val SITE_BASE_HTTPS: String = "https://4pda.to"
    }
}
