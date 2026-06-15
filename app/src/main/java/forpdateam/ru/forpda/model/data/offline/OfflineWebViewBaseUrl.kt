package forpdateam.ru.forpda.model.data.offline

import android.content.Context
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import timber.log.Timber

/**
 * Phase 4 (UI half) of the offline-reading feature (§5.1 of
 * REFACTOR_PLAN.md). Wires the [WebViewAssetLoader] that the offline
 * rendering path uses as the base URL, and exposes helpers to load a
 * saved item's HTML into a [WebView].
 *
 * The asset loader is intentionally simple: it serves the cached
 * `index.html` at the base URL, and the cached `images/` directory
 * under the [OFFLINE_IMAGES_PREFIX]. It does NOT intercept network
 * requests — the [WebView] is expected to be loaded via
 * [loadOfflineHtml] which sets the base URL via
 * `loadDataWithBaseURL` so relative image references resolve through
 * the asset loader.
 */
object OfflineWebViewBaseUrl {

    /**
     * Build a [WebViewAssetLoader] that serves the offline cache.
     * Path mappings:
     *   `<OFFLINE_BASE_URL>`          → `index.html` of the saved item
     *   `<OFFLINE_BASE_URL>images/`   → `images/` directory of the saved item
     *
     * The loader is stateless — it requires a fresh instance per saved
     * item, which is fine because the asset loader is created lazily
     * when an item is opened.
     */
    fun newAssetLoader(
            context: Context,
            storage: OfflineStorage,
            itemId: String,
    ): WebViewAssetLoader {
        val domain = OfflineArticleSource.OFFLINE_BASE_URL
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
        return WebViewAssetLoader.Builder()
                .setDomain(domain)
                .addPathHandler(
                        "/",
                        OfflineIndexPathHandler(context, storage, itemId)
                )
                .build()
    }

    /**
     * Load the saved HTML for [itemId] into [webView] with [OFFLINE_BASE_URL]
     * as the base URL so that relative image references and CSS paths
     * resolve through the asset loader's [OFFLINE_IMAGES_PREFIX] handler.
     *
     * Returns `true` if the HTML was loaded, `false` if the item is not
     * in the offline cache.
     */
    suspend fun loadOfflineHtml(
            webView: WebView,
            repository: OfflineRepository,
            itemId: String,
    ): Boolean {
        val html = repository.readHtml(itemId) ?: return false
        val mime = "text/html"
        val encoding = "utf-8"
        runCatching {
            webView.loadDataWithBaseURL(OfflineArticleSource.OFFLINE_BASE_URL, html, mime, encoding, null)
        }.onFailure { Timber.w(it, "OfflineWebViewBaseUrl: loadDataWithBaseURL failed for %s", itemId) }
        return true
    }
}
