package forpdateam.ru.forpda.presentation.theme

/**
 * Native [android.webkit.WebView.getContentHeight] can briefly read 0 right after
 * [android.webkit.WebView.loadDataWithBaseURL] while DOM posts are already present
 * (log: pageComplete content=0 renderComplete=false, dom_posts verified).
 */
internal object ThemeRenderCompletePolicy {

    fun hasCompletedRender(
            renderKey: String,
            completedRenderKey: String?,
            completedRenderHasPosts: Boolean,
            jsReady: Boolean,
            hasParent: Boolean,
            contentHeight: Int,
            blankContentThreshold: Int,
    ): Boolean {
        if (renderKey.isBlank() || renderKey != completedRenderKey) return false
        if (!completedRenderHasPosts || !jsReady || !hasParent) return false
        if (contentHeight > blankContentThreshold) return true
        return completedRenderHasPosts
    }
}
