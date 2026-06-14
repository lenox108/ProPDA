package forpdateam.ru.forpda.presentation.articles.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NewsCommentsSectionBindingTest {

    @Test
    fun `news js uses document delegation for comments toggle`() {
        val newsJs = newsJsFile().readText()

        assertTrue(newsJs.contains("ensureNewsCommentsSectionClickDelegation"))
        assertTrue(newsJs.contains("newsInlineCommentsHandleToggleClick"))
        assertTrue(newsJs.contains("newsInlineCommentsHandleToggleFromNativeButton"))
        assertTrue(newsJs.contains("newsInlineCommentsShouldIgnoreDuplicateToggle"))
        assertTrue(newsJs.contains("toggle_ignored_duplicate"))
        assertTrue(newsJs.contains("data-fpda-comments-delegation"))
        assertFalse(newsJs.contains("newsCommentsSectionDelegationInstalled"))
        assertTrue(newsJs.contains("target.closest(\"#news-comments-section\")"))
        assertTrue(newsJs.contains("sections[0]"))
        assertTrue(newsJs.contains("newsInlineCommentsInsertFooterHtml"))
        assertTrue(newsJs.contains("newsInlineCommentsEnsureFooter"))
        assertTrue(newsJs.contains("newsInlineCommentsUpdateHeaderCount(commentsCount)"))
        assertTrue(newsJs.contains("newsInlineCommentsUpdateHeaderCount"))
        assertTrue(newsJs.contains("newsInlineCommentsUpdateCount"))
        assertTrue(newsJs.contains("newsInlineCommentsPruneDuplicates"))
        assertTrue(newsJs.contains("nativeEvents.addEventListener(nativeEvents.DOM, newsInlineCommentsPruneDuplicates"))
        assertTrue(newsJs.contains("INews.onCommentsSectionJsEvent"))
        assertTrue(newsJs.contains("INews.onCommentsSectionTapReceived"))
        assertTrue(newsJs.contains("bridge_called"))
        assertFalse(newsJs.contains("expand_dom_stale"))
        assertFalse(newsJs.contains("INews.onLoadInlineCommentsRequested();\n        }\n    }\n}"))
        assertTrue(newsJs.contains("if (needsLoad || domState === \"loading\")"))
        assertTrue(newsJs.contains("domState === \"empty\""))
        assertTrue(newsJs.contains("domState === \"error\""))
        assertTrue(newsJs.contains("newsInlineCommentsRequestExpand"))
        assertTrue(newsJs.contains("newsInlineCommentsSyncDomState"))
        assertTrue(newsJs.contains("load_skipped_reason"))
        assertTrue(newsJs.contains("15000"))
        assertTrue(newsJs.contains("newsInlineCommentsInjectHtml"))
        assertTrue(newsJs.contains("data-fpda-webview-gen"))
        assertTrue(newsJs.contains("inject_gen_resync"))
        assertFalse(newsJs.contains("list.innerHTML = \"\";\n        list.style.display = \"none\";"))
        assertTrue(newsJs.contains("data-fpda-pending-html"))
        assertTrue(newsJs.contains("data-can-load-more"))
        assertTrue(newsJs.contains("newsInlineCommentsMaybeLoadMore"))
        assertTrue(newsJs.contains("newsInlineCommentsRequestLoadMore"))
        assertFalse(newsJs.contains("if (!more || more.hidden)"))
    }

    @Test
    fun `collapsed comments state preserves loaded list dom`() {
        val newsJs = newsJsFile().readText()
        val setStateStart = newsJs.indexOf("function newsInlineCommentsSetState")
        val setStateEnd = newsJs.indexOf("function newsInlineCommentsInjectHtml")
        assertTrue(setStateStart >= 0)
        assertTrue(setStateEnd > setStateStart)
        val setStateBody = newsJs.substring(setStateStart, setStateEnd)

        assertFalse(setStateBody.contains("list.innerHTML = \"\""))
        assertTrue(setStateBody.contains("list.style.display = \"none\""))
        assertTrue(setStateBody.contains("root.setAttribute(\"data-fpda-pending-html\", \"true\")"))
        assertTrue(setStateBody.contains("root.removeAttribute(\"data-fpda-pending-html\")"))
    }

    @Test
    fun `article content gates coordinator dom on current request id`() {
        val fragment = File("src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
        val content = fragment.readText()
        assertTrue(content.contains("domContentLoadedRequestId == articleRequestId"))
        assertTrue(content.contains("scheduleCommentsInjectRetry"))
        assertTrue(content.contains("MAX_COMMENTS_VERIFY_PASSES"))
    }

    @Test
    fun `article details fragment decouples toolbar spinner from comments`() {
        val newsDetails = File("src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/NewsDetailsFragment.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/NewsDetailsFragment.kt")
        val content = newsDetails.readText()
        assertTrue(content.contains("clearArticleWebViewPendingRenderIfDisplayed"))
        assertTrue(content.contains("showWebRender = awaitingWebViewRender.get() && !articleBodyReady"))
    }

    @Test
    fun `article content uses CommentsExpandCoordinator`() {
        val fragment = File("src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
        val content = fragment.readText()
        assertTrue(content.contains("CommentsExpandCoordinator"))
        assertTrue(content.contains("requestCommentsExpand"))
        assertTrue(content.contains("expand_attempt"))
        assertTrue(content.contains("injectCommentsHtml"))
        assertTrue(content.contains("syncCommentsFooterCountFromModel(\"footer_mounted\")"))
        assertTrue(content.contains("InlineCommentsDisplayCount.resolveExpectedCount"))
    }

    @Test
    fun `article content uses inline comments expand fallback`() {
        val fragment = File("src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
        val content = fragment.readText()
        assertTrue(content.contains("INLINE_COMMENTS_EXPAND_JS"))
        assertTrue(content.contains("newsInlineCommentsSetCollapsed(false,false"))
    }

    @Test
    fun `bind comments section ignores stale native collapse`() {
        val newsJs = newsJsFile().readText()
        assertTrue(newsJs.contains("bind_skip_stale_collapse"))
    }

    @Test
    fun `comments expand uses a single bridge load path`() {
        val newsJs = newsJsFile().readText()
        val expandFn = newsJs.substringAfter("function newsInlineCommentsRequestExpand(root)")
                .substringBefore("function newsInlineCommentsHandleRetryClick")

        assertTrue(expandFn.contains("INews.onCommentsSectionTapReceived(\"toggle_expand\")"))
        assertTrue(expandFn.contains("newsInlineCommentsSetState(\"loading\""))
        assertTrue(expandFn.contains("domState === \"error\""))
        assertFalse(expandFn.contains("INews.onLoadInlineCommentsRequested()"))
    }

    @Test
    fun `news template packages direct news module asset`() {
        val template = File("src/main/assets/template_news.html").takeIf { it.isFile }
                ?: File("app/src/main/assets/template_news.html")
        val html = template.readText()
        assertTrue(html.contains("file:///android_asset/forpda/scripts/modules/news.js"))
        assertFalse(html.contains("forpda/scripts/modules/news.min.js"))
    }

    @Test
    fun `news polls bind on initial webview render`() {
        val newsJs = newsJsFile().readText()
        val fragment = File("src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
                .takeIf { it.isFile }
                ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
        val content = fragment.readText()

        assertTrue(newsJs.contains("function bindNewsPolls()"))
        assertTrue(newsJs.contains("nativeEvents.addEventListener(nativeEvents.DOM, bindNewsPolls"))
        assertTrue(newsJs.contains("nativeEvents.addEventListener(nativeEvents.PAGE, bindNewsPolls"))
        assertTrue(content.contains("bindNewsPollsInWebView()"))
        assertTrue(content.contains("if(typeof transformPoll==='function'){transformPoll();}"))
        assertTrue(content.contains("if(typeof bindPollExternalBrowserButtons==='function'){bindPollExternalBrowserButtons();}"))
        assertTrue(content.contains("buildNewsPollBindScript"))
        assertTrue(content.contains("FpdaDebugLog.TAG_ARTICLE_POLL"))
        assertTrue(content.contains("webview_bind_probe"))
        assertTrue(content.contains("vote_submit_result"))
    }

    private fun newsJsFile(): File =
            File("src/main/assets/forpda/scripts/modules/news.js").takeIf { it.isFile }
                    ?: File("app/src/main/assets/forpda/scripts/modules/news.js")
}
