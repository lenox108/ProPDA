package forpdateam.ru.forpda.presentation.theme

/**
 * After rapid [loadDataWithBaseURL] cycles (blank retry + deferred metadata), `DOMContentLoaded`
 * fires but `window.load` / [IBase.onPageLoaded] may never run — [ThemeFragmentWeb.onPageComplete]
 * is skipped, [ThemeWebController.hasCompletedRender] stays false, and skeleton stays visible
 * (log: blankVerifyOk content=2213 renderComplete=false, no onNativePageComplete for theme).
 */
internal object ThemeMissedPageLifecyclePolicy {

    fun shouldProbeMissedPageLifecycle(
            renderGeneration: Int,
            domLifecycleGeneration: Int,
            pageLifecycleGeneration: Int,
    ): Boolean {
        if (renderGeneration <= 0) return false
        if (domLifecycleGeneration != renderGeneration) return false
        if (pageLifecycleGeneration == renderGeneration) return false
        return true
    }
}
