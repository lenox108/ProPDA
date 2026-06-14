package forpdateam.ru.forpda.ui.fragments.other

import javax.inject.Inject
import forpdateam.ru.forpda.common.getVecDrawable
import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.widget.ImageView

import android.widget.LinearLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope

import androidx.lifecycle.repeatOnLifecycle

import java.util.ArrayList
import kotlinx.coroutines.flow.collect

import kotlinx.coroutines.launch
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp48
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.common.webview.CustomWebChromeClient
import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.entity.remote.forum.ForumRules
import forpdateam.ru.forpda.presentation.forumrules.ForumRulesViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.WebViewTopScroller
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.WebViewSecurityProfile
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter

/**
 * Created by radiationx on 16.10.17.
 *
 * Fragment для отображения правил форума.
 *
 * JS Bridge обоснование:
 * - copyRule(): копирование текста правил в буфер обмена (read-only операция)
 *
 * Контент: локально генерируемый HTML из API правил форума (доверенный статический контент).
 * Профиль безопасности: TRUSTED_STATIC_ARTICLE (JS включён, базовый bridge запрещён, допускаются узкоспециализированные интерфейсы).
 */
@AndroidEntryPoint
class ForumRulesFragment : TabFragment(), TabTopScroller {
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var avatarRepository: AvatarRepository


    private var searchViewTag = 0
    private lateinit var webView: ExtendedWebView
    private lateinit var topScroller: WebViewTopScroller

    private val viewModel: ForumRulesViewModel by viewModels()

    init {
        configuration.defaultTitle = "Правила форума"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        webView = ExtendedWebView(requireContext()).also {
            it.systemLinkHandler = systemLinkHandler
            it.init(WebViewSecurityProfile.TRUSTED_STATIC_ARTICLE)
        }
        webView.setDialogsHelper(DialogsHelper(
                webView.context,
                linkHandler,
                systemLinkHandler,
                router,
                clipboardHelper
        ))
        attachWebView(webView)
        fragmentContent.addView(webView)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView.addJavascriptInterface(this, JS_INTERFACE)
        webView.webViewClient = CustomWebViewClient(avatarRepository, linkHandler, systemLinkHandler)
        webView.webChromeClient = CustomWebChromeClient()
        webView.setJsLifeCycleListener(object : ExtendedWebView.JsLifeCycleListener {
            override fun onDomContentComplete(actions: ArrayList<String>) {
                setRefreshing(false)
            }

            override fun onPageComplete(actions: ArrayList<String>) {

            }
        })
        topScroller = WebViewTopScroller(webView, appBarLayout)

        var lastRules: ForumRules? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    setRefreshing(state.loading)
                    val rules = state.rules
                    if (rules != null && rules !== lastRules) {
                        lastRules = rules
                        webView.loadDataWithBaseURL(
                                "https://4pda.to/forum/",
                                rules.html ?: "",
                                "text/html",
                                "utf-8",
                                null
                        )
                    }
                    webView.evalJs("changeStyleType(\"${state.styleType}\")")
                    webView.setRelativeFontSize(state.fontSize)
                    webView.setAppFontMode(state.appFontMode)
                }
            }
        }
    }

    override fun toggleScrollTop() {
        if (!::topScroller.isInitialized) return
        topScroller.toggleScrollTop()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        addSearchOnPageItem(menu)
    }

    @JavascriptInterface
    fun copyRule(text: String) {
        if (context == null)
            return
        runInUiThread(Runnable {
            if (context == null)
                return@Runnable
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Скопировать правило в буфер обмена?")
                    .setPositiveButton(R.string.ok) { _, _ ->
                        clipboardHelper.copyToClipboard(text)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .showWithStyledButtons()
        })
    }

    private fun addSearchOnPageItem(menu: Menu) {
        toolbar.inflateMenu(R.menu.theme_search_menu)
        val searchOnPageMenuItem = menu.findItem(R.id.action_search)
        searchOnPageMenuItem.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        val searchView = searchOnPageMenuItem.actionView as? SearchView ?: SearchView(requireContext()).also {
            searchOnPageMenuItem.actionView = it
        }
        searchView.tag = searchViewTag

        searchView.setOnSearchClickListener { _ ->
            if (searchView.tag == searchViewTag) {
                val searchClose = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                if (searchClose != null)
                    (searchClose.parent as? ViewGroup ?: throw IllegalStateException("parent not ViewGroup")).removeView(searchClose)

                val navButtonsParams = ViewGroup.LayoutParams(dp48, dp48)
                val outValue = TypedValue()
                context?.theme?.resolveAttribute(android.R.attr.actionBarItemBackground, outValue, true)

                val btnNext = AppCompatImageButton(searchView.context)
                btnNext.setImageDrawable(requireContext().getVecDrawable(R.drawable.ic_toolbar_search_next))
                btnNext.setBackgroundResource(outValue.resourceId)

                val btnPrev = AppCompatImageButton(searchView.context)
                btnPrev.setImageDrawable(requireContext().getVecDrawable(R.drawable.ic_toolbar_search_prev))
                btnPrev.setBackgroundResource(outValue.resourceId)

                (searchView.getChildAt(0) as LinearLayout).addView(btnPrev, navButtonsParams)
                (searchView.getChildAt(0) as LinearLayout).addView(btnNext, navButtonsParams)

                btnNext.setOnClickListener { findNext(true) }
                btnPrev.setOnClickListener { findNext(false) }
                searchViewTag++
            }
        }

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
        if (searchManager != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
        }

        searchView.setIconifiedByDefault(true)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                findText(newText)
                return false
            }
        })
    }

    private fun findNext(next: Boolean) {
        webView.findNext(next)
    }

    private fun findText(text: String) {
        webView.findAllAsync(text)
    }

    override fun onDestroyView() {
        webView.removeJavascriptInterface(JS_INTERFACE)
        webView.setJsLifeCycleListener(null)
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.endWork()
    }

    companion object {
        const val JS_INTERFACE = "IRules"
    }
}
