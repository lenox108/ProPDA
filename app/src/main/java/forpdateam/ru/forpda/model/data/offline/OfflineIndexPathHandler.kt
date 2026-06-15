package forpdateam.ru.forpda.model.data.offline

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * [WebViewAssetLoader.PathHandler] implementation that serves the
 * offline-saved item's HTML and image directory under the
 * [OfflineArticleSource.OFFLINE_BASE_URL].
 *
 *   `<OFFLINE_BASE_URL>`         → `<storage>/<itemId>/index.html`
 *   `<OFFLINE_BASE_URL>images/x` → `<storage>/<itemId>/images/x`
 *
 * Other paths return `null` so the loader's default behavior takes over
 * (it falls back to the network); the offline WebView still uses the
 * network for cross-origin assets (e.g. CDN images) but the article's
 * own HTML and its downloaded images are served from disk.
 */
internal class OfflineIndexPathHandler(
        private val context: Context,
        private val storage: OfflineStorage,
        private val itemId: String,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        val safePath = path.trimStart('/')
        return try {
            when {
                safePath.isEmpty() || safePath == OfflineArticleSource.OFFLINE_HTML_FILENAME ->
                        respondHtml("$itemId/${OfflineArticleSource.OFFLINE_HTML_FILENAME}")
                safePath.startsWith(OfflineArticleSource.OFFLINE_IMAGES_PREFIX) -> {
                    val imageName = safePath.removePrefix(OfflineArticleSource.OFFLINE_IMAGES_PREFIX)
                    if (imageName.contains("..") || imageName.contains('/')) {
                        Timber.w("OfflineIndexPathHandler: rejecting path traversal in %s", safePath)
                        null
                    } else {
                        respondImage("$itemId/images/$imageName")
                    }
                }
                else -> null
            }
        } catch (ex: Exception) {
            Timber.w(ex, "OfflineIndexPathHandler: failed to serve %s", path)
            null
        }
    }

    private fun respondHtml(relPath: String): WebResourceResponse? {
        val file = File(storage.rootDirectory(), relPath)
        if (!file.exists()) return null
        val stream: InputStream = FileInputStream(file)
        return WebResourceResponse("text/html", "utf-8", stream)
    }

    private fun respondImage(relPath: String): WebResourceResponse? {
        val file = File(storage.rootDirectory(), relPath)
        if (!file.exists()) return null
        val mime = guessImageMime(file.name)
        return WebResourceResponse(mime, null, FileInputStream(file))
    }

    private fun guessImageMime(name: String): String = when {
        name.endsWith(".png", ignoreCase = true) -> "image/png"
        name.endsWith(".webp", ignoreCase = true) -> "image/webp"
        name.endsWith(".gif", ignoreCase = true) -> "image/gif"
        name.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        else -> "image/jpeg"
    }
}

