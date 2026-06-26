package forpdateam.ru.forpda.ui.fragments.theme.modules

import android.webkit.ValueCallback
import forpdateam.ru.forpda.presentation.theme.ThemeScrollCommand
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import org.json.JSONObject

/**
 * Centralized, type-safe JavaScript API for theme WebView.
 * All string arguments are passed through [JSONObject.quote] to prevent injection.
 */
class ThemeJsApi(
    private val webView: ExtendedWebView,
) {
    // ── Direct JS function calls (no string quoting needed) ──

    fun copySelectedText() = eval("copySelectedText()")

    fun selectionToQuote() = eval("selectionToQuote()")

    fun selectAllPostText() = eval("selectAllPostText()")

    fun shareSelectedText() = eval("shareSelectedText()")

    fun onProgressChanged() = eval("onProgressChanged()")

    fun deletePost(postId: Int) = eval("deletePost($postId)")

    fun scrollToPage(pageNumber: Int) =
        eval("if(typeof scrollToThemePage==='function'){scrollToThemePage($pageNumber);}")

    fun scrollToPageAndBottom(pageNumber: Int, commandId: String) {
        val commandIdJs = JSONObject.quote(commandId)
        eval(
                "window.__themeScrollCommandId=$commandIdJs;" +
                        // R-04: capture the live render generation so this command's
                        // completion is dropped if a reload bumps the generation first.
                        "window.__themeScrollCommandGenerationAtExec=(Number(window.__themeScrollCommandGeneration)||0);" +
                        "if(typeof scrollToThemePageAndBottom==='function'){scrollToThemePageAndBottom($pageNumber);}" +
                        "else{if(typeof scrollToThemePage==='function'){scrollToThemePage($pageNumber);}" +
                        "if(typeof scrollToThemeBottomWithRetries==='function'){scrollToThemeBottomWithRetries();}}"
        )
    }

    fun scrollToBottom() =
        eval("if(typeof scrollToThemeBottomWithRetries==='function'){scrollToThemeBottomWithRetries();}else{window.scrollTo(0,document.documentElement.scrollHeight);}")

    fun closeToolbarOverlaysForNavigation() =
        eval("if(typeof closeThemeToolbarOverlaysForNavigation==='function'){closeThemeToolbarOverlaysForNavigation(false);}")

    fun cancelScrollRetries() =
        eval("if(typeof cancelThemeAnchorScrollRetries==='function'){cancelThemeAnchorScrollRetries();}")

    // ── String arguments (safely quoted) ──

    fun changeStyleType(type: String) {
        val quoted = JSONObject.quote(type)
        eval("changeStyleType($quoted)")
    }

    fun updateShowAvatarState(isShow: Boolean) =
        eval("if(typeof updateShowAvatarState==='function'){updateShowAvatarState($isShow);}")

    fun updateTypeAvatarState(isCircle: Boolean) =
        eval("if(typeof updateTypeAvatarState==='function'){updateTypeAvatarState($isCircle);}")

    fun applyInfinitePage(direction: String, html: String) {
        val directionJs = JSONObject.quote(direction)
        val htmlJs = JSONObject.quote(html)
        eval("if(typeof applyThemeInfinitePage==='function'){applyThemeInfinitePage($directionJs,$htmlJs);}")
    }

    fun setInfiniteState(direction: String, state: String, message: String?) {
        val directionJs = JSONObject.quote(direction)
        val stateJs = JSONObject.quote(state)
        val messageJs = JSONObject.quote(message ?: "")
        eval("if(typeof setThemeInfiniteState==='function'){setThemeInfiniteState($directionJs,$stateJs,$messageJs);}")
    }

    fun applyPostRatingPatch(postId: Int, ratingText: String, canPlus: Boolean, canMinus: Boolean) {
        val idJs = JSONObject.quote(postId.toString())
        val textJs = JSONObject.quote(ratingText)
        eval("if(typeof applyPostRatingPatch==='function'){applyPostRatingPatch($idJs,$textJs,$canPlus,$canMinus);}")
    }

    fun applyUserPostCountPatch(postId: Int, userPostCount: Int) {
        val idJs = JSONObject.quote(postId.toString())
        eval("if(typeof applyUserPostCountPatch==='function'){applyUserPostCountPatch($idJs,$userPostCount);}")
    }

    fun stripPrependedTopicHatFromList(hatPostId: Int) {
        val idJs = JSONObject.quote(hatPostId.toString())
        eval("if(typeof stripPrependedTopicHatFromList==='function'){stripPrependedTopicHatFromList($idJs);}")
    }

    fun injectTopicHatOverlayHost(overlayHostHtml: String, openAfterInject: Boolean, callback: ValueCallback<String>? = null) {
        val htmlJs = JSONObject.quote(overlayHostHtml)
        val script = "(function(){try{if(typeof injectTopicHatOverlayHost==='function'){return injectTopicHatOverlayHost($htmlJs,$openAfterInject)===true;}return false;}catch(e){return false;}})()"
        if (callback != null) {
            evalWithResult(script, callback)
        } else {
            eval(script)
        }
    }

    fun applyPostsPatch(payload: JSONObject, callback: ValueCallback<String>) {
        evalWithResult(
            "if(window.ThemeDomPatch&&typeof window.ThemeDomPatch.applyPostsPatch==='function'){window.ThemeDomPatch.applyPostsPatch(${payload});}else{JSON.stringify({ok:false,reason:'api_missing'});}",
            callback
        )
    }

    // ── Lifecycle / batch helpers ──

    /** Queue a JS script for batch execution (flushed on next frame). */
    fun queue(script: String) {
        webView.evalJs(script)
    }

    /**
     * Fire-and-forget JS execution. Phase 5: routed through [ExtendedWebView.evalJsNow] instead of
     * a raw [android.webkit.WebView.evaluateJavascript]. This preserves the previous IMMEDIATE
     * timing/ordering (so it stays correct relative to [evalWithResult] sequences) while gaining
     * the shared post-destroy guard and batch metrics. Pure batched queuing is available via
     * [queue] for callers that explicitly want coalescing.
     */
    fun eval(script: String) {
        webView.evalJsNow(script)
    }

    /** Execute JS immediately with a result callback (must not be batched). */
    fun evalWithResult(script: String, callback: ValueCallback<String>) {
        webView.evalJs(script, callback)
    }

    fun flushQueued() {
        webView.flushQueuedJs()
    }

    // ── Lifecycle action string builders (return safe JS snippets) ──

    fun setLoadAction(action: String): String {
        val quoted = JSONObject.quote(action)
        return "setLoadAction($quoted);"
    }

    fun setLoadScrollY(scrollY: Int): String {
        return "setLoadScrollY($scrollY);"
    }

    fun setLoadAnchorPostId(anchor: String): String {
        val quoted = JSONObject.quote(anchor)
        return "setLoadAnchorPostId($quoted);"
    }

    fun setLoadAnchorUnreadTarget(hasUnreadTarget: Boolean): String =
            "setLoadAnchorUnreadTarget(${if (hasUnreadTarget) "true" else "false"});"

    fun setLoadAmbiguousAllReadBottom(isAmbiguous: Boolean): String =
            "setLoadAmbiguousAllReadBottom(${if (isAmbiguous) "true" else "false"});"

    fun setLoadOpenSessionKind(sessionKind: String?): String {
        val quoted = JSONObject.quote(sessionKind.orEmpty())
        return "setLoadOpenSessionKind($quoted);"
    }

    fun scheduleSoftLoadAnchorScroll(anchorPostId: String): String {
        val quoted = JSONObject.quote(anchorPostId)
        return "if(typeof scheduleSoftLoadAnchorScroll==='function'){scheduleSoftLoadAnchorScroll($quoted);}"
    }

    /**
     * Soft scroll that bottom-aligns on the resolved post (and falls back to document bottom) for
     * all-read bottom-redirect resume on the last page. Mirrors END navigation landing.
     */
    fun scheduleSoftLoadAnchorBottomScroll(anchorPostId: String): String {
        val quoted = JSONObject.quote(anchorPostId)
        return "if(typeof scheduleSoftLoadAnchorBottomScroll==='function'){scheduleSoftLoadAnchorBottomScroll($quoted);}else if(typeof scheduleSoftLoadAnchorScroll==='function'){scheduleSoftLoadAnchorScroll($quoted);}"
    }

    /** CSS-pixel scroll target for native WebView fallback when JS INITIAL_ANCHOR stalls (log 033). */
    fun nativeScrollToAnchorPost(anchorName: String): String {
        val quoted = JSONObject.quote(anchorName)
        // S2 (top-clip fix, log 25_06-16-26): align the post TOP to the visible top, consistent with
        // the JS [doScroll] / end-anchor placement which subtract `topChromePadding` (the sticky
        // top toolbar). A fresh first-unread / explicit open has no saved viewport offset
        // (`loadAnchorOffsetTop === null`); the old hardcoded `45` over/under-subtracted vs the real
        // chrome height, leaving the post half ABOVE the viewport top (clipped). Prefer a genuine
        // saved offset (back/restore resumes the user's exact viewport), else use the real
        // `topChromePadding`, else 0 — never the magic 45.
        return """(function(){
            var name=$quoted;
            var el=document.getElementsByName(name)[0];
            if(!el){var id=String(name).replace(/^entry/i,'');el=document.getElementById('entry'+id);}
            if(!el){return -1;}
            var top=el.getBoundingClientRect().top+(window.pageYOffset||document.documentElement.scrollTop||0);
            var topReserve=(typeof topChromePadding!=='undefined')?Math.max(0,Number(topChromePadding)||0):0;
            var offset=(typeof window.loadAnchorOffsetTop==='number'&&isFinite(window.loadAnchorOffsetTop))?window.loadAnchorOffsetTop:topReserve;
            var y=Math.max(0,Math.round(top-offset));
            window.scrollTo(0,y);
            return y;
        })();"""
    }

    fun clearUnreadAnchorHybridGuard(reason: String): String {
        val quoted = JSONObject.quote(reason)
        return "if(typeof clearUnreadAnchorHybridGuard==='function'){clearUnreadAnchorHybridGuard($quoted);}"
    }

    /**
     * S-01 / R-03: announce that Kotlin's INITIAL_ANCHOR [ThemeScrollCommand] will
     * own the initial-anchor scroll for this page-load. Issued in the DOM-content
     * batch BEFORE `nativeEvents.onNativeDomComplete()` runs the legacy DOM
     * listener, so the JS DOM-anchor path becomes fallback-only: it yields for up
     * to [windowMs] ms for the Kotlin command and only runs if none arrives.
     * A non-positive [windowMs] disarms the handshake (no command expected).
     */
    fun setThemeInitialAnchorExpected(windowMs: Int): String {
        return "if(typeof setThemeInitialAnchorExpected==='function'){setThemeInitialAnchorExpected($windowMs);}"
    }

    fun setLoadAnchorOffsetTop(offsetTop: Double?): String {
        return if (offsetTop == null) {
            "setLoadAnchorOffsetTop(null);"
        } else {
            "setLoadAnchorOffsetTop($offsetTop);"
        }
    }

    fun setLoadScrollRatio(ratio: Double?): String {
        return if (ratio == null) {
            "setLoadScrollRatio(null);"
        } else {
            "setLoadScrollRatio($ratio);"
        }
    }

    fun setLoadWasNearBottom(wasNearBottom: Boolean): String {
        return "setLoadWasNearBottom($wasNearBottom);"
    }

    fun setBottomChromePaddingInline(paddingCssPx: Float): String {
        return "if(typeof setBottomChromePadding==='function'){setBottomChromePadding($paddingCssPx);}"
    }

    fun setTopChromePaddingInline(paddingCssPx: Float): String {
        return "if(typeof setTopChromePadding==='function'){setTopChromePadding($paddingCssPx);}"
    }

    fun setRefreshRestoreRequest(id: String?, mode: String?, source: String? = null): String {
        val idJs = JSONObject.quote(id ?: "")
        val modeJs = JSONObject.quote(mode ?: "")
        val sourceJs = JSONObject.quote(source ?: "")
        return "if(typeof setRefreshRestoreRequest==='function'){setRefreshRestoreRequest($idJs,$modeJs,$sourceJs);}"
    }

    fun setThemeScrollAnchorDiag(enabled: Boolean): String {
        return "window.__themeScrollAnchorDiag=$enabled;window.__themePageSyncDebug=$enabled;"
    }

    fun scrollToElementWithRetriesIfAvailable(): String {
        return "if (typeof PageInfo !== 'undefined' && PageInfo.elemToScroll && PageInfo.elemToScroll.length && typeof scrollToElementWithRetries === 'function') { scrollToElementWithRetries(PageInfo.elemToScroll); } else if (typeof PageInfo !== 'undefined' && PageInfo.elemToScroll && PageInfo.elemToScroll.length) { scrollToElement(PageInfo.elemToScroll); }"
    }

    fun scrollToAnchorPostIdIfAvailable(): String {
        return "else if (window.loadAnchorPostId && window.loadAnchorPostId.length && typeof scrollToElementWithRetries === 'function') { scrollToElementWithRetries(window.loadAnchorPostId); } else if (window.loadAnchorPostId && window.loadAnchorPostId.length) { scrollToElement(window.loadAnchorPostId); }"
    }

    fun scrollToBottomWithRetries(): String {
        return "if(typeof scrollToThemeBottomWithRetries==='function'){scrollToThemeBottomWithRetries();}else{window.scrollTo(0,document.documentElement.scrollHeight);}"
    }

    fun restoreToBottomAfterRefreshWithRetries(): String {
        return "if(typeof restoreThemeToBottomAfterRefreshWithRetries==='function'){restoreThemeToBottomAfterRefreshWithRetries();}else if(typeof scrollToThemeBottomWithRetries==='function'){scrollToThemeBottomWithRetries();}else{window.scrollTo(0,document.documentElement.scrollHeight);}"
    }

    fun executeScrollCommand(command: ThemeScrollCommand): String {
        val payloadJs = JSONObject.quote(command.toPayloadJson())
        return "if(typeof executeThemeScrollCommand==='function'){executeThemeScrollCommand($payloadJs);}"
    }

    fun restoreRefreshScrollWithRetries(): String {
        return "if(typeof restoreThemeRefreshScrollAnchorWithRetries==='function'){restoreThemeRefreshScrollAnchorWithRetries();}"
    }

    /**
     * Re-applies the topic-post highlight from native code once the WebView is
     * interactive. The static class is already embedded in [template_theme.html]
     * by the renderer, but this call re-asserts it after any post-render DOM
     * mutation (e.g. smart-patch, infinite-scroll append). Generation id matches
     * [HighlightTarget] / `renderGenerationId` so stale callbacks (e.g. superseded
     * renders) are filtered out by the JS guard.
     */
    fun applyHighlight(postId: Long, type: String, generationId: Int): String {
        val postIdJs = postId.toString()
        val typeJs = JSONObject.quote(type)
        return "if(typeof window.PPDA_applyHighlight==='function'){window.PPDA_applyHighlight($postIdJs,$typeJs,$generationId);}"
    }

    /**
     * Arms the JS-side fade-out timer that strips the visible highlight class
     * after [delayMs] milliseconds for a given render [generationId].
     *
     * Called once per render event (topic open / page change / refresh) from
     * [forpdateam.ru.forpda.ui.fragments.theme.modules.ThemeWebController]
     * right after [applyHighlight]. The JS implementation:
     *  - cancels any prior pending timer so a stale fadeout can never fire
     *    on top of a fresh render;
     *  - sets a new 2-second (configurable via [delayMs]) setTimeout that adds
     *    `post-highlight-fading` (CSS animates opacity to 0 over ~300ms) and
     *    strips the base class on `transitionend`;
     *  - reports back via `IThemePresenter.highlightFadeoutCompleted(generationId)`
     *    so the `highlight_fadeout_completed` diagnostic can be emitted.
     *
     * Idempotent for the same generation: re-calling with the same id before
     * the timer fires does NOT extend the deadline (the existing timer is
     * preserved). This guards against a JS re-apply from clobbering a
     * mid-fade transition.
     */
    fun scheduleHighlightFadeout(generationId: Int, delayMs: Int): String {
        return "if(typeof window.PPDA_scheduleHighlightFadeout==='function'){window.PPDA_scheduleHighlightFadeout($generationId,$delayMs);}"
    }

    fun setReadPosObserverEnabled(enabled: Boolean): String {
        return "if(typeof window.PPDA_enableReadPosObserver==='function'){window.PPDA_enableReadPosObserver($enabled);}"
    }

    fun setBottomChromePaddingImmediate(paddingCssPx: Float): String {
        return "if(typeof setBottomChromePadding==='function'){setBottomChromePadding($paddingCssPx);}"
    }
}
