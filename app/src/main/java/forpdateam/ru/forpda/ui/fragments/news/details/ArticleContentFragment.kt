package forpdateam.ru.forpda.ui.fragments.news.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.presentation.articles.detail.content.ArticleContentViewModel
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.WebViewTopScroller
import forpdateam.ru.forpda.ui.views.ExtendedWebView

/**
 * Created by radiationx on 03.09.17.
 */

class ArticleContentFragment : Fragment(), TabTopScroller {

    private lateinit var webView: ExtendedWebView
    private lateinit var topScroller: WebViewTopScroller

    private val viewModel: ArticleContentViewModel by viewModels {
        ArticleContentViewModel.Factory(
                (parentFragment as NewsDetailsFragment).provideChildInteractor(),
                App.get().Di().mainPreferencesHolder,
                App.get().Di().templateManager,
                App.get().Di().errorHandler
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        webView = ExtendedWebView(context)
        (parentFragment as? NewsDetailsFragment)?.attachWebView(webView)
        topScroller = WebViewTopScroller(webView, (parentFragment as NewsDetailsFragment).getAppBar())
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                App.get().Di().linkHandler,
                App.get().Di().systemLinkHandler,
                App.get().Di().router
        ))
        registerForContextMenu(webView)
        webView.webViewClient = CustomWebViewClient()
        webView.webChromeClient = CustomWebChromeClient()
        webView.addJavascriptInterface(this, JS_INTERFACE)
        return webView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var lastArticle: DetailsPage? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val article = state.article
                    if (article != null && article !== lastArticle) {
                        lastArticle = article
                        webView.loadDataWithBaseURL(
                                "https://4pda.to/forum/",
                                article.html ?: "",
                                "text/html",
                                "utf-8",
                                null
                        )
                    }
                    webView.evalJs("changeStyleType(\"${state.styleType}\")")
                    webView.setRelativeFontSize(state.fontSize)
                }
            }
        }
    }

    override fun toggleScrollTop() {
        topScroller.toggleScrollTop()
    }

    @JavascriptInterface
    fun toComments() {
        if (context == null)
            return
        webView.runInUiThread { (parentFragment as NewsDetailsFragment).fragmentsPager.currentItem = 1 }
    }

    @JavascriptInterface
    fun sendPoll(id: String, answer: String, from: String) {
        if (context == null)
            return
        webView.runInUiThread {
            val pollId = Integer.parseInt(id)
            val answers = answer.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val answersId = IntArray(answers.size)
            for (i in answers.indices) {
                answersId[i] = Integer.parseInt(answers[i])
            }
            viewModel.sendPoll(from, pollId, answersId)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.endWork()
    }

    companion object {
        const val JS_INTERFACE = "INews"
    }
}
