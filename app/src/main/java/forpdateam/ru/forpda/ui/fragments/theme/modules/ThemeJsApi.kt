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

    /** Execute JS immediately. */
    fun eval(script: String) {
        webView.evaluateJavascript(script, null)
    }

    /** Execute JS immediately with a result callback. */
    fun evalWithResult(script: String, callback: ValueCallback<String>) {
        webView.evaluateJavascript(script, callback)
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
        return """(function(){
            var name=$quoted;
            var el=document.getElementsByName(name)[0];
            if(!el){var id=String(name).replace(/^entry/i,'');el=document.getElementById('entry'+id);}
            if(!el){return -1;}
            var top=el.getBoundingClientRect().top+(window.pageYOffset||document.documentElement.scrollTop||0);
            var offset=(typeof window.loadAnchorOffsetTop==='number'&&isFinite(window.loadAnchorOffsetTop))?window.loadAnchorOffsetTop:45;
            var y=Math.max(0,Math.round(top-offset));
            window.scrollTo(0,y);
            return y;
        })();"""
    }

    fun clearUnreadAnchorHybridGuard(reason: String): String {
        val quoted = JSONObject.quote(reason)
        return "if(typeof clearUnreadAnchorHybridGuard==='function'){clearUnreadAnchorHybridGuard($quoted);}"
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

    fun revealAfterFirstRestore(id: String): String {
        val idJs = JSONObject.quote(id)
        return "if(typeof revealThemeAfterFirstRestore==='function'){revealThemeAfterFirstRestore($idJs);}"
    }

    fun setBottomChromePaddingImmediate(paddingCssPx: Float): String {
        return "if(typeof setBottomChromePadding==='function'){setBottomChromePadding($paddingCssPx);}"
    }
}
