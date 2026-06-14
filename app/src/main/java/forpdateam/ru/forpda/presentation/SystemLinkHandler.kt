package forpdateam.ru.forpda.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber
import android.widget.Toast
import io.appmetrica.analytics.AppMetrica
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DownloadFileName
import forpdateam.ru.forpda.common.ExternalBrowserLauncher
import forpdateam.ru.forpda.common.MimeTypeUtil
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.webview.UrlDecision
import forpdateam.ru.forpda.common.webview.UrlPolicy
import forpdateam.ru.forpda.downloads.InternalDownloader
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SystemLinkHandler(
        private val context: Context,
        private val mainPreferencesHolder: MainPreferencesHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder,
        private val router: TabRouter,
        private val authHolder: AuthHolder,
        private val webClient: IWebClient,
        private val internalDownloader: InternalDownloader
) : ISystemLinkHandler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val redirectCache = mutableMapOf<String, String>()
    private val selectableDownloadMethods = arrayOf(
            Preferences.Main.DownloadMethod.SYSTEM,
            Preferences.Main.DownloadMethod.EXTERNAL_MANAGER,
            Preferences.Main.DownloadMethod.BROWSER
    )

    override fun handle(url: String) {
        val safeUrl = when (val decision = UrlPolicy.classify(url)) {
            UrlDecision.Blocked -> {
                Timber.w("Blocked unsafe system URL")
                return
            }
            is UrlDecision.Internal -> decision.normalizedUrl
            is UrlDecision.External -> decision.normalizedUrl
        }
        try {
            ExternalBrowserLauncher.open(context, safeUrl)
        } catch (e: Exception) {
            AppMetrica.reportError(e.message.orEmpty(), e)
            Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            //ACRA.getErrorReporter().handleException(e)
        }
    }

    override fun handleDownload(url: String, inputFileName: String?, uiContext: Context?, contentDisposition: String?) {
        val safeUrl = when (val decision = UrlPolicy.classify(url)) {
            UrlDecision.Blocked -> {
                Timber.w("Blocked unsafe download URL")
                return
            }
            is UrlDecision.Internal -> decision.normalizedUrl
            is UrlDecision.External -> decision.normalizedUrl
        }
        val fileName = DownloadFileName.resolve(safeUrl, inputFileName, contentDisposition)
        val activity = uiContext?.asActivity() ?: context.asActivity()
        if (activity?.canShowDialogs() == true) {
            MaterialAlertDialogBuilder(activity)
                    .setMessage(activity.getString(R.string.load_file, fileName))
                    .setPositiveButton(activity.getString(R.string.ok)) { _, _ -> redirectDownload(fileName, safeUrl, activity) }
                    .setNegativeButton(activity.getString(R.string.cancel), null)
                    .showWithStyledButtons()
        } else {
            redirectDownload(fileName, safeUrl, null)
        }
    }

    private fun redirectDownload(fileName: String, url: String, uiContext: Context?) {
        if (!authHolder.get().isAuth()) {
            (uiContext?.asActivity() ?: context.asActivity())?.also { activity ->
                Utils.showNeedAuthDialog(activity, router)
            }
            return
        }
        Toast.makeText(context, String.format(context.getString(R.string.perform_request_link), fileName), Toast.LENGTH_SHORT).show()
        scope.launch {
            try {
                // Проверяем кэш для redirect URL (ключ - исходный URL)
                val cachedUrl = redirectCache[url]
                val downloadUrl: String = if (cachedUrl != null) {
                    if (BuildConfig.DEBUG) Timber.d("SystemLinkHandler: using cached redirect URL")
                    cachedUrl
                } else {
                    val request = NetworkRequest.Builder().url(url).withoutBody().build()
                    // Добавляем таймаут 30 секунд для запроса redirect URL
                    val response = withTimeout(30_000) {
                        webClient.request(request)
                    }
                    val resolvedUrl = withContext(Dispatchers.Main) {
                        if (response.url == null) {
                            Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                            return@withContext null
                        }
                        val redirectUrl = response.redirect
                        // Сохраняем в кэш по исходному URL (ограничиваем размер до 50 записей)
                        if (redirectCache.size >= 50) {
                            redirectCache.remove(redirectCache.keys.first())
                        }
                        redirectCache[url] = redirectUrl
                        redirectUrl
                    }
                    resolvedUrl ?: return@launch
                }

                withContext(Dispatchers.Main) {
                    val safeDownloadUrl = when (val decision = UrlPolicy.classify(downloadUrl)) {
                        UrlDecision.Blocked -> {
                            Timber.w("Blocked unsafe resolved download URL")
                            Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                            return@withContext
                        }
                        is UrlDecision.Internal -> decision.normalizedUrl
                        is UrlDecision.External -> decision.normalizedUrl
                    }
                    try {
                        val method = mainPreferencesHolder.getDownloadMethod()
                        val mime = MimeTypeUtil.getType(fileName)
                        dispatchDownloadMethod(method, fileName, safeDownloadUrl, url, mime, uiContext)
                    } catch (ex: Exception) {
                        AppMetrica.reportError(ex.message.orEmpty(), ex)
                        //ACRA.getErrorReporter().handleException(ex)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "System link handle error")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun dispatchDownloadMethod(
            method: Preferences.Main.DownloadMethod,
            fileName: String,
            url: String,
            originalUrl: String,
            mime: String?,
            uiContext: Context? = null
    ) {
        when (method) {
            Preferences.Main.DownloadMethod.SYSTEM -> {
                try {
                    systemDownloader(fileName, url, mime)
                } catch (exception: Exception) {
                    Toast.makeText(context, R.string.perform_loading_error, Toast.LENGTH_SHORT).show()
                    openInBrowser(url)
                }
            }
            Preferences.Main.DownloadMethod.EXTERNAL_MANAGER -> {
                if (isProtectedSiteDownload(originalUrl, url)) {
                    showExternalAuthWarning(uiContext) {
                        externalDownloadManager(fileName, url, originalUrl, mime)
                    }
                } else {
                    externalDownloadManager(fileName, url, originalUrl, mime)
                }
            }
            Preferences.Main.DownloadMethod.BROWSER -> openInBrowser(url)
            Preferences.Main.DownloadMethod.ASK -> showDownloadMethodDialog(fileName, url, originalUrl, mime, uiContext)
        }
    }

    private fun showDownloadMethodDialog(fileName: String, url: String, originalUrl: String, mime: String?, uiContext: Context?) {
        val activity = uiContext?.asActivity() ?: context.asActivity()
        if (activity?.canShowDialogs() != true) {
            Timber.w("SystemLinkHandler: cannot show download method dialog without Activity context")
            Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
            return
        }
        val titles = selectableDownloadMethods.map { downloadMethodTitle(it) }.toTypedArray()
        MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.download_method_title)
                .setItems(titles) { _, which ->
                    dispatchDownloadMethod(selectableDownloadMethods[which], fileName, url, originalUrl, mime, activity)
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun downloadMethodTitle(method: Preferences.Main.DownloadMethod): String {
        val resId = when (method) {
            Preferences.Main.DownloadMethod.SYSTEM -> R.string.download_method_system
            Preferences.Main.DownloadMethod.EXTERNAL_MANAGER -> R.string.download_method_external_manager
            Preferences.Main.DownloadMethod.BROWSER -> R.string.download_method_browser
            Preferences.Main.DownloadMethod.ASK -> R.string.download_method_ask
        }
        return context.getString(resId)
    }

    private fun systemDownloader(fileName: String, url: String) {
        systemDownloader(fileName, url, MimeTypeUtil.getType(fileName))
    }

    private fun systemDownloader(fileName: String, url: String, mime: String?) {
        // Новый встроенный менеджер закачек: WorkManager + OkHttp (cookies, ретраи, прогресс).
        internalDownloader.enqueue(
            context = context,
            url = url,
            fileName = fileName,
            mime = mime
        )
    }

    private fun externalDownloadManager(fileName: String, url: String, originalUrl: String, mime: String?) {
        try {
            val intent = createExternalDownloadIntent(fileName, url, originalUrl, mime)
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.load_with)).addFlags(FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            AppMetrica.reportError(e.message.orEmpty(), e)
            Toast.makeText(context, R.string.download_external_not_found, Toast.LENGTH_SHORT).show()
            openInBrowser(url)
            //ACRA.getErrorReporter().handleException(e)
        }
    }

    private fun createExternalDownloadIntent(fileName: String, url: String, originalUrl: String, mime: String?): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mime ?: "application/octet-stream")
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_TITLE, fileName)
            putExtra("android.intent.extra.SUBJECT", fileName)
            putExtra("android.intent.extra.FILENAME", fileName)
            putExtra("android.intent.extra.FILE_NAME", fileName)
            putExtra("filename", fileName)
            putExtra("fileName", fileName)
            putExtra("mimetype", mime)
            putExtra("mimeType", mime)
            putExtra("User-Agent", USER_AGENT)
            putExtra("user_agent", USER_AGENT)
            putExtra("Referer", refererFor(originalUrl))
            putExtra("referer", refererFor(originalUrl))

            val headers = Bundle().apply {
                putString("User-Agent", USER_AGENT)
                putString("Referer", refererFor(originalUrl))
                cookieHeaderFor(url)?.let { putString("Cookie", it) }
            }
            putExtra(Browser.EXTRA_HEADERS, headers)
            cookieHeaderFor(url)?.let {
                putExtra("Cookie", it)
                putExtra("cookie", it)
            }
        }
    }

    private fun openInBrowser(url: String) {
        val safeUrl = when (val decision = UrlPolicy.classify(url)) {
            UrlDecision.Blocked -> {
                Timber.w("Blocked unsafe browser URL")
                return
            }
            is UrlDecision.Internal -> decision.normalizedUrl
            is UrlDecision.External -> decision.normalizedUrl
        }
        try {
            ExternalBrowserLauncher.open(context, safeUrl)
        } catch (e: Exception) {
            AppMetrica.reportError(e.message.orEmpty(), e)
            Toast.makeText(context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExternalAuthWarning(uiContext: Context?, onContinue: () -> Unit) {
        val activity = uiContext?.asActivity() ?: context.asActivity()
        if (activity?.canShowDialogs() != true) {
            Toast.makeText(context, R.string.download_external_auth_warning, Toast.LENGTH_LONG).show()
            onContinue()
            return
        }
        MaterialAlertDialogBuilder(activity)
                .setMessage(R.string.download_external_auth_warning)
                .setPositiveButton(R.string.ok) { _, _ -> onContinue() }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun isProtectedSiteDownload(originalUrl: String, resolvedUrl: String): Boolean {
        return authHolder.get().isAuth() && (isSiteUrl(originalUrl) || isSiteUrl(resolvedUrl))
    }

    private fun isSiteUrl(url: String): Boolean {
        return runCatching { SiteUrls.isSiteUri(Uri.parse(url)) }.getOrDefault(false)
    }

    private fun cookieHeaderFor(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val cookieHeader = webClient.getClientCookies().values
                .filter { it.matches(httpUrl) }
                .joinToString("; ") { "${it.name}=${it.value}" }
        return cookieHeader.ifBlank { null }
    }

    private fun refererFor(originalUrl: String): String {
        return if (isSiteUrl(originalUrl)) {
            "https://4pda.to/forum/"
        } else {
            originalUrl
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

private tailrec fun Context.asActivity(): android.app.Activity? {
    return when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.asActivity()
        else -> null
    }
}

private fun android.app.Activity.canShowDialogs(): Boolean {
    return !isFinishing && !isDestroyed
}