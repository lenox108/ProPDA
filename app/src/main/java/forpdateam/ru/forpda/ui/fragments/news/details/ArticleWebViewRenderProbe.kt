package forpdateam.ru.forpda.ui.fragments.news.details

/**
 * Parses WebView visibility probes and builds the JS used to detect rendered article body.
 */
internal object ArticleWebViewRenderProbe {

    const val MIN_VISIBLE_TEXT_LENGTH = 24
    private val intFieldRegex = Regex(""""(\w+)"\s*:\s*(\d+)""")
    private val boolFieldRegex = Regex(""""(\w+)"\s*:\s*(true|false)""")

    fun visibilityProbeScript(): String =
            """(function(){
                var body=document.body;
                if(!body) return JSON.stringify({textLen:0,hasContent:false,hasHeader:false,contentLen:0});
                var text=(body.innerText||body.textContent||'').replace(/\s+/g,' ').trim();
                var content=document.querySelector('.content')
                    ||document.querySelector('#news .content')
                    ||document.querySelector('.news-body')
                    ||document.querySelector('#news .material_item');
                var contentText=content?((content.innerText||content.textContent||'').replace(/\s+/g,' ').trim()):'';
                if(contentText.length<${MIN_VISIBLE_TEXT_LENGTH} && text.length>=${MIN_VISIBLE_TEXT_LENGTH}){
                    var newsShell=document.querySelector('#news,.material_item,.news-detail-header');
                    if(newsShell){
                        var shellText=(newsShell.innerText||newsShell.textContent||'').replace(/\s+/g,' ').trim();
                        if(shellText.length>=${MIN_VISIBLE_TEXT_LENGTH}){
                            content=newsShell;
                            contentText=shellText;
                        }
                    }
                }
                var doc=document.documentElement||{};
                var scrollHeight=Math.max(
                    body.scrollHeight||0,
                    body.offsetHeight||0,
                    doc.scrollHeight||0,
                    doc.offsetHeight||0
                );
                var viewport=window.innerHeight||doc.clientHeight||0;
                return JSON.stringify({
                    textLen:text.length,
                    contentLen:contentText.length,
                    hasContent:!!content,
                    hasHeader:!!document.querySelector('.news-detail-header,.material_item,#news'),
                    scrollHeight:scrollHeight,
                    viewportHeight:viewport
                });
            })();"""

    fun parseVisibilityResult(raw: String?): ArticleVisibilityResult {
        if (raw.isNullOrBlank() || raw == "null") {
            return ArticleVisibilityResult()
        }
        val jsonString = decodeJsJsonPayload(raw) ?: return ArticleVisibilityResult()
        val ints = intFieldRegex.findAll(jsonString).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        val bools = boolFieldRegex.findAll(jsonString).associate { it.groupValues[1] to (it.groupValues[2] == "true") }
        return ArticleVisibilityResult(
                textLen = ints["textLen"] ?: 0,
                contentLen = ints["contentLen"] ?: 0,
                hasContent = bools["hasContent"] == true,
                hasHeader = bools["hasHeader"] == true,
                scrollHeight = ints["scrollHeight"] ?: 0,
                viewportHeight = ints["viewportHeight"] ?: 0
        )
    }

    fun decodeJsJsonPayload(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) return trimmed
        if (!trimmed.startsWith("\"")) return null
        return trimmed
                .removeSurrounding("\"")
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .takeIf { it.startsWith("{") }
    }

    /**
     * Article is visible only when the main [.content] block has readable text.
     * Header/meta alone must not hide loading or stop blank-body retries.
     */
    fun isArticleBodyVisible(result: ArticleVisibilityResult): Boolean =
            result.hasContent && result.contentLen >= MIN_VISIBLE_TEXT_LENGTH

    fun isArticleVisible(
            result: ArticleVisibilityResult,
            contentHeight: Int,
            blankContentHeightThreshold: Int
    ): Boolean = isArticleBodyVisible(result)

    /**
     * Post-confirm safety net for the first-open blank-render race.
     *
     * The JS text probe reads `.content` via `innerText||textContent`; `textContent` keeps returning
     * the article text for a DOM that was BUILT but never PAINTED (zero-size/late-layout WebView),
     * so a blank screen can falsely pass [isArticleBodyVisible]. The native rendered height does not
     * lie: a painted article reports hundreds+ of CSS px while an unpainted body stays at/near zero.
     *
     * A confirmed render must be treated as still-blank (and retried) when EITHER the text probe
     * fails OR the rendered height is at/below the blank threshold.
     */
    fun isConfirmedRenderBlank(
            bodyVisibleByText: Boolean,
            contentHeight: Int,
            blankContentHeightThreshold: Int
    ): Boolean = !bodyVisibleByText || contentHeight <= blankContentHeightThreshold

    /**
     * DOM text is present and JS layout reports a non-trivial document height, but the native
     * WebView [contentHeight] is still at the blank threshold — typical unpainted-body race on
     * deep tab opens (e.g. bookmarks). Recover with layout/invalidate before blank escalation.
     */
    fun isUnpaintedButLaidOutInDom(
            bodyVisibleByText: Boolean,
            contentHeight: Int,
            jsScrollHeight: Int,
            blankContentHeightThreshold: Int,
            minJsScrollHeight: Int = MIN_UNPAINTED_JS_SCROLL_HEIGHT
    ): Boolean = bodyVisibleByText &&
            contentHeight <= blankContentHeightThreshold &&
            jsScrollHeight >= minJsScrollHeight

    const val MIN_UNPAINTED_JS_SCROLL_HEIGHT = 48

    /**
     * Do not treat the article as blank while the WebView load is still in flight.
     * A slow first [loadDataWithBaseURL] can finish parsing a few ms after the soft timeout;
     * forcing a reload then discards a nearly-ready document and adds ~6s of blank UI.
     *
     * Uses [softTimeoutMs] (not hard) so a stalled load that never fires [onPageFinished]
     * can recover via blank retry instead of leaving the spinner up for the full hard deadline.
     *
     * When the DOM probe is completely empty (no text, zero layout height), the load is not
     * "nearly ready" — waiting until the soft timeout only adds blank UI (see FPDA logs).
     */
    fun shouldWaitForPageFinishBeforeBlankEscalation(
            renderPageFinishedRequestId: Int,
            requestId: Int,
            elapsedMs: Long,
            softTimeoutMs: Long,
            contentLen: Int = 0,
            jsScrollHeight: Int = 0,
    ): Boolean {
        if (isDomCompletelyEmpty(contentLen, jsScrollHeight)) return false
        return renderPageFinishedRequestId != requestId && elapsedMs < softTimeoutMs
    }

    /** True when the WebView has not built any article DOM yet (typical hung loadDataWithBaseURL). */
    fun isDomCompletelyEmpty(contentLen: Int, jsScrollHeight: Int): Boolean =
            contentLen <= 0 && jsScrollHeight <= 0

    /**
     * Recover from a hung first paint: DOM never appeared within [stalledLoadMs].
     * Do not wait for the 6s soft timeout when scrollHeight stays at zero.
     */
    fun shouldForceStalledEmptyDomReload(
            elapsedMs: Long,
            contentLen: Int,
            jsScrollHeight: Int,
            domConfirmedRequestId: Int,
            requestId: Int,
            stalledLoadMs: Long,
    ): Boolean = domConfirmedRequestId != requestId &&
            elapsedMs >= stalledLoadMs &&
            isDomCompletelyEmpty(contentLen, jsScrollHeight)

    /**
     * DOM text is present but the native WebView height is still blank — do not treat this as a
     * missing article and force a destructive reload.
     */
    fun shouldDeferBlankEscalation(
            bodyVisibleByText: Boolean,
            contentLen: Int,
            contentHeight: Int,
            jsScrollHeight: Int,
            blankContentHeightThreshold: Int
    ): Boolean = bodyVisibleByText &&
            contentLen >= MIN_VISIBLE_TEXT_LENGTH &&
            isUnpaintedButLaidOutInDom(
                    bodyVisibleByText = bodyVisibleByText,
                    contentHeight = contentHeight,
                    jsScrollHeight = jsScrollHeight,
                    blankContentHeightThreshold = blankContentHeightThreshold
            )
}

internal data class ArticleVisibilityResult(
        val textLen: Int = 0,
        val contentLen: Int = 0,
        val hasContent: Boolean = false,
        val hasHeader: Boolean = false,
        val scrollHeight: Int = 0,
        val viewportHeight: Int = 0
)
