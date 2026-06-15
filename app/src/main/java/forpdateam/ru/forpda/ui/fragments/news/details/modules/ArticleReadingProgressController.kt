package forpdateam.ru.forpda.ui.fragments.news.details.modules

import android.os.SystemClock
import forpdateam.ru.forpda.model.interactors.news.ArticleReadingProgressStore
import forpdateam.ru.forpda.ui.views.ExtendedWebView

/**
 * Owns the "remember article scroll percent" responsibility for the
 * article WebView fragment. The fragment only forwards scroll updates
 * and asks us to restore progress after a successful render; all
 * state (last-saved percent, throttle timestamps, restore-pending
 * article id) lives here.
 *
 * Extracted from `ArticleContentFragment` (god-class §1.1) — see
 * `REFACTOR_PLAN.md` §1.1. Behaviour is unchanged.
 */
class ArticleReadingProgressController(
        private val store: ArticleReadingProgressStore,
        private val webViewProvider: () -> ExtendedWebView?,
        private val currentArticleIdProvider: () -> Int,
        private val isWebViewReadyProvider: () -> Boolean,
        private val postDelayed: (Runnable, Long) -> Boolean,
) {

    private var lastSavedScrollPercent: Int = -1
    private var lastScrollSaveAtMs: Long = 0L

    /**
     * Called from the WebView scroll listener. Computes a percent and
     * persists it, throttled by [READING_PROGRESS_SAVE_INTERVAL_MS].
     */
    fun onScrollChanged(scrollY: Int) {
        val articleId = currentArticleIdProvider().takeIf { it > 0 } ?: return
        val webView = webViewProvider() ?: return
        val contentHeightPx = (webView.contentHeight * webView.scale).toInt()
        val viewportHeightPx = webView.height
        if (contentHeightPx <= 0 || viewportHeightPx <= 0) return
        val maxScroll = (contentHeightPx - viewportHeightPx).coerceAtLeast(1)
        val percent = ArticleReadingProgressStore.scrollPercentFrom(scrollY, maxScroll)
        val now = SystemClock.elapsedRealtime()
        if (percent == lastSavedScrollPercent && now - lastScrollSaveAtMs < READING_PROGRESS_SAVE_INTERVAL_MS) {
            return
        }
        lastSavedScrollPercent = percent
        lastScrollSaveAtMs = now
        store.saveScrollPercent(articleId, percent)
    }

    /**
     * Schedules a restore to the persisted percent, on the WebView's
     * message queue. Cancels itself if the article id changes between
     * scheduling and execution.
     */
    fun restoreFor(articleId: Int) {
        val percent = store.readScrollPercent(articleId)
        if (percent <= 0) return
        val webView = webViewProvider() ?: return
        if (!isWebViewReadyProvider()) return
        postDelayed(Runnable {
            val currentWebView = webViewProvider() ?: return@Runnable
            if (!isWebViewReadyProvider()) return@Runnable
            if (currentArticleIdProvider() != articleId) return@Runnable
            currentWebView.evaluateJavascript(
                    """(function(){
                      var doc=document.documentElement||{};
                      var body=document.body||{};
                      var scrollHeight=Math.max(doc.scrollHeight||0,body.scrollHeight||0);
                      var clientHeight=window.innerHeight||doc.clientHeight||0;
                      var maxScroll=Math.max(0,scrollHeight-clientHeight);
                      if(maxScroll<=0) return;
                      window.scrollTo(0, Math.round(maxScroll * ($percent / 100)));
                    })();""",
                    null
            )
        }, READING_PROGRESS_RESTORE_DELAY_MS)
    }

    private companion object {
        const val READING_PROGRESS_SAVE_INTERVAL_MS = 1_500L
        const val READING_PROGRESS_RESTORE_DELAY_MS = 120L
    }
}
