package forpdateam.ru.forpda.ui.fragments.other

import android.net.Uri
import android.os.Bundle
import timber.log.Timber
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.common.showSnackbar

import java.util.regex.Pattern

import forpdateam.ru.forpda.common.webview.CustomWebViewClient
import forpdateam.ru.forpda.common.webview.DialogsHelper
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.fragments.TabFragment
import android.webkit.WebView
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.WebViewSecurityProfile
import forpdateam.ru.forpda.model.repository.avatar.AvatarRepository
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by radiationx on 09.11.17.
 */

@AndroidEntryPoint
class GoogleCaptchaFragment : TabFragment() {
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var router: TabRouter
    @Inject lateinit var systemLinkHandler: ISystemLinkHandler
    @Inject lateinit var webClient: IWebClient
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var avatarRepository: AvatarRepository

    private lateinit var webView: ExtendedWebView
    private var content = ""

    init {
        configuration.defaultTitle = "Проверка"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            content = getString("content", "1")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        webView = ExtendedWebView(requireContext()).also {
            it.systemLinkHandler = systemLinkHandler
            it.init(WebViewSecurityProfile.UNTRUSTED_EXTERNAL)
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
        setSubtitle("Это из-за VPN/Proxy и т.д.")
        webView.webViewClient = CaptchaWebViewClient()
        webView.loadDataWithBaseURL("https://4pda.to/forum/", content, "text/html", "utf-8", null)
    }

    internal inner class CaptchaWebViewClient : CustomWebViewClient(avatarRepository, linkHandler, systemLinkHandler) {
        override fun handleUri(view: WebView, uri: Uri): Boolean {
            if (Pattern.compile("https://4pda.to/cdn-cgi/l/chk_captcha").matcher(uri.toString()).find()) {
                val nr = NetworkRequest.Builder().url(uri.toString()).withoutBody().build()
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            webClient.request(nr)
                        }
                        onResponse()
                    } catch (e: Exception) {
                        Timber.e(e, "Captcha request failed")
                    }
                }
            }
            return true
        }
    }

    private fun onResponse() {
        showSnackbar("Приложение будет перезапущено")
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            val activity = activity
            if (activity == null) {
                showSnackbar("Перезапустите приложение")
            } else {
                MainActivity.restartApplication(activity)
            }
        }
    }
}
