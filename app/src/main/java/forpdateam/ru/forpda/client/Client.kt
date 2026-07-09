package forpdateam.ru.forpda.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.preference.PreferenceManager
import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.PrivateHeaders
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.MessageCounters
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CountersHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.client.interceptors.AuthInterceptor
import forpdateam.ru.forpda.client.interceptors.CacheControlInterceptor
import forpdateam.ru.forpda.client.interceptors.ErrorInterceptor
import forpdateam.ru.forpda.client.interceptors.ImageLoadingInterceptor
import forpdateam.ru.forpda.client.interceptors.RedirectFragmentInterceptor
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.ConnectionPool
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * HTTP клиент для работы с API 4pda.
 * 
 * Улучшения по сравнению с Java-версией:
 * - Kotlin null-safety
 * - Lazy инициализация OkHttp клиентов
 * - Упрощённая работа с Cookie через MutableMap
 * - Использование OkHttp 4.x API (MediaType.Companion и т.д.)
 * - Улучшенная читаемость через when/if expressions
 */
class Client(
    private val context: Context,
    private val authHolder: AuthHolder,
    private val countersHolder: CountersHolder,
    @forpdateam.ru.forpda.common.di.AppScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : IWebClient {

    /**
     * Проверяет доступность сети перед запросом.
     * @throws IOException если нет подключения к интернету
     */
    /**
     * Must match [forpdateam.ru.forpda.model.system.AppNetworkState]: INTERNET only.
     * Requiring [NetworkCapabilities.NET_CAPABILITY_VALIDATED] caused false "no network"
     * errors on Wi‑Fi while HTTP would still work (validation lags or captive portals).
     */
    private fun checkNetworkAvailable() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val isAvailable = connectivityManager?.let { cm ->
            val network = cm.activeNetwork ?: return@let false
            val capabilities = cm.getNetworkCapabilities(network) ?: return@let false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } ?: false

        if (!isAvailable) {
            // Do not block HTTP: ConnectivityManager often lags behind real reachability on Wi‑Fi/VPN.
            Timber.w("ConnectivityManager reports no INTERNET; proceeding with request anyway")
        }
    }

    companion object {
        private val LOG_TAG = Client::class.java.simpleName

        /** Актуальный мобильный Chrome на Android — ближе к WebView и к типичному браузеру пользователя. */
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MOBILE_COOKIE_NAME = "ngx_mb"
        private const val DESKTOP_MOBILE_COOKIE_VALUE = "0"
        private const val EVENT_WS_URL = "wss://app.4pda.to/ws/"

        /**
         * Foreground WebSocket ping interval. Raised 30s → 45s → 60s as a low-risk
         * battery win (BAT-02): fewer radio wakeups while still detecting stale
         * connections reasonably quickly. The WS only lives while the app is in the
         * foreground (see [EventsRepository]/idle-disconnect), so a slightly longer
         * keepalive has no impact on background push latency.
         */
        private const val WEBSOCKET_PING_INTERVAL_SECONDS = 60L
    }

    // region Properties
    private val cookieManager = CookieManager(context, authHolder, appScope)
    private val authKey: AtomicReference<String> = AtomicReference("0")
    // endregion

    // region Initialization
    init {
        // Загружаем auth_key
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        authKey.set(preferences.getString("auth_key", "0") ?: "0")
    }
    // endregion

    // region OkHttp Clients (Lazy initialization)
    private val cookieJar: CookieJar get() = cookieManager.cookieJar

    private val cachedDns = CachedDns()

    private val cacheDir by lazy { File(context.cacheDir, "http_cache").apply { mkdirs() } }
    private val httpCache by lazy { Cache(cacheDir, 50L * 1024 * 1024) } // 50 MB (was 10) — matches docs/AUDIT_REPORT.md promise

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
            .dns(cachedDns)
            .cookieJar(cookieJar)
            .cache(httpCache)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(ImageLoadingInterceptor { url -> cookieJar.loadForRequest(url).isNotEmpty() })
            .addInterceptor(ErrorInterceptor())
            .addInterceptor(BrotliInterceptor)
            .addNetworkInterceptor(RedirectFragmentInterceptor())
            .addNetworkInterceptor(CacheControlInterceptor())
            .build()
    }

    private val sharedConnectionPool: ConnectionPool get() = client.connectionPool

    private val desktopClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieJar.saveFromResponse(url, cookies)
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookies = cookieJar.loadForRequest(url)
                        .filterNot { it.name.equals(MOBILE_COOKIE_NAME, ignoreCase = true) }
                        .toMutableList()
                    if (url.host.contains("4pda", ignoreCase = true)) {
                        cookies += Cookie.Builder()
                            .name(MOBILE_COOKIE_NAME)
                            .value(DESKTOP_MOBILE_COOKIE_VALUE)
                            // OkHttp 4+ rejects domains with a leading dot (IllegalArgumentException)
                            .domain("4pda.to")
                            .path("/")
                            .build()
                    }
                    return cookies
                }
            })
            .build()
    }

    private val webSocketClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .pingInterval(WEBSOCKET_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(sharedConnectionPool)
            .cookieJar(cookieJar)
            .addInterceptor(AuthInterceptor())
            .build()
    }
    // endregion

    // region IWebClient Implementation
    override fun getAuthKey(): String = authKey.get()

    override fun getClientCookies(): Map<String, Cookie> = cookieManager.getCookies()

    override fun clearCookies() {
        cookieManager.clearCookies()
    }

    override fun clearDnsCache() {
        cachedDns.clearCache()
    }

    /** Общий клиент для HTTP и загрузки изображений (Coil), чтобы cookies совпадали. */
    fun getHttpClient(): OkHttpClient = client

    @Throws(Exception::class)
    override fun get(url: String): NetworkResponse {
        return request(NetworkRequest.Builder().url(url).build())
    }

    @Throws(Exception::class)
    override fun request(request: NetworkRequest): NetworkResponse {
        return request(request, client, null)
    }

    @Throws(Exception::class)
    override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse {
        return request(request, client, progressListener)
    }

    @Throws(Exception::class)
    override fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse {
        val desktopRequest = if (request.headers?.keys?.any { it.equals("User-Agent", ignoreCase = true) } == true) {
            request
        } else {
            NetworkRequest.Builder()
                .copyFrom(request)
                .addHeader("User-Agent", DESKTOP_USER_AGENT)
                .build()
        }
        return request(desktopRequest, desktopClient, null)
    }

    @Throws(Exception::class)
    fun request(
        request: NetworkRequest,
        client: OkHttpClient,
        uploadProgressListener: IWebClient.ProgressListener?
    ): NetworkResponse {
        // Проверяем сеть перед запросом — быстрая проверка без ожидания таймаута
        checkNetworkAvailable()
        
        val redirectFragment = RedirectFragmentInterceptor.State()
        val requestBuilder = prepareRequest(request, uploadProgressListener)
            .tag(RedirectFragmentInterceptor.State::class.java, redirectFragment)
        val response = NetworkResponse(request.url)
        var okHttpResponse: Response? = null
        
        try {
            okHttpResponse = client.newCall(requestBuilder.build()).execute()

            response.code = okHttpResponse.code
            response.message = okHttpResponse.message
            response.redirect = okHttpResponse.request.url.toString()
            response.locationHeader = okHttpResponse.header("Location")
            response.redirectFragment = redirectFragment.lastFragment.get()

            if (!request.isWithoutBody) {
                val bodyString = okHttpResponse.body?.string() ?: ""
                response.body = bodyString
                if (!request.skipCounterUpdate) {
                    getCounts(bodyString)
                }
                // Для тем, которые были перенесены/удалены, сервер нередко отдаёт 404 с HTML-заглушкой,
                // которая может совпасть с паттерном форум-ошибки. В этом случае нам важнее вернуть HTML наверх,
                // чтобы ThemeApi смог извлечь канонический showtopic и повторить запрос.
                if (okHttpResponse.code != 404) {
                    checkForumErrors(bodyString)
                }
            }

            if (BuildConfig.DEBUG) {
                Timber.d("Response: $response")
            }
        } finally {
            okHttpResponse?.close()
        }
        
        return response
    }

    override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(EVENT_WS_URL)
            .build()
        return webSocketClient.newWebSocket(request, webSocketListener)
    }
    // endregion

    // region Request Preparation
    private fun prepareRequest(
        request: NetworkRequest,
        uploadProgressListener: IWebClient.ProgressListener?
    ): Request.Builder {
        var url = request.url
        
        // Исправляем протокол
        if (url.startsWith("//")) {
            url = "https:$url"
        }
        
        if (BuildConfig.DEBUG) {
            Timber.d("Request url ${request.url}")
        }

        val requestBuilder = Request.Builder()
            .url(url)

        // Добавляем пользовательские заголовки
        request.headers?.forEach { (key, value) ->
            if (BuildConfig.DEBUG) {
                val logValue = if (PrivateHeaders.LIST.contains(key)) "private" else value
                Timber.d("Header $key : $logValue")
            }
            requestBuilder.header(key, value)
        }

        // Обрабатываем форму или файл
        if (request.rawBody != null) {
            requestBuilder.post(
                request.rawBody.toRequestBody(request.rawBodyContentType.toMediaTypeOrNull())
            )
        } else if (request.formHeaders != null || request.file != null) {
            if (BuildConfig.DEBUG) {
                Timber.d("Multipart ${request.isMultipartForm}")
                request.formHeaders?.forEach { (key, value) ->
                    val logValue = if (PrivateHeaders.LIST.contains(key)) "private" else value
                    Timber.d("Form header $key : $logValue")
                }
                request.file?.let {
                    Timber.d("Form file $it")
                }
            }

            if (!request.isMultipartForm) {
                // Обычная форма
                request.formHeaders?.let { formHeaders ->
                    val formBuilder = FormBody.Builder()
                    formHeaders.forEach { (key, value) ->
                        if (request.encodedFormHeaders?.contains(key) == true) {
                            formBuilder.addEncoded(key, value)
                        } else {
                            formBuilder.add(key, value)
                        }
                    }
                    requestBuilder.post(formBuilder.build())
                }
            } else {
                // Multipart форма
                val multipartBuilder = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)

                request.formHeaders?.forEach { (key, value) ->
                    multipartBuilder.addFormDataPart(key, value)
                }

                request.file?.let { file ->
                    val mediaType = file.mimeType.toMediaTypeOrNull()
                    val requestBody = file.openStream().toRequestBody(mediaType, file.fileSize)
                    multipartBuilder.addFormDataPart(
                        file.requestName ?: "file",
                        file.fileName,
                        requestBody
                    )
                }

                val multipartBody = multipartBuilder.build()
                val body = if (uploadProgressListener != null) {
                    ProgressRequestBody(multipartBody, uploadProgressListener)
                } else {
                    multipartBody
                }
                requestBuilder.post(body)
            }
        }

        return requestBuilder
    }
    // endregion

    // region Response Processing
    @Throws(Exception::class)
    private fun checkForumErrors(res: String) {
        val errorMatcher = IWebClient.errorPattern.matcher(res)
        if (errorMatcher.find()) {
            val errorText = errorMatcher.group(1)?.let { ApiUtils.fromHtml(it) } ?: ""
            throw OnlyShowException(errorText)
        }
    }

    private fun getCounts(res: String) {
        // Delegate to the shared header-counters parser so the legacy and
        // per-counter regex logic live in exactly one place. See AUDIT-M09.
        val parsed = forpdateam.ru.forpda.notifications.ForumHeaderCounters.parseOptional(res)
        val counters = countersHolder.get()
        var changed = false
        parsed.mentions?.also { counters.mentions = it; changed = true }
        parsed.favorites?.also { counters.favorites = it; changed = true }
        parsed.qms?.also { counters.qms = it; changed = true }
        if (changed) {
            countersHolder.set(counters, source = "index_header")
        }
    }
    // endregion
}
