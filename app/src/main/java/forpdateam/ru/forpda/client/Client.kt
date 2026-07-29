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
import forpdateam.ru.forpda.blocklist.BlocklistGuard
import forpdateam.ru.forpda.client.interceptors.AuthInterceptor
import forpdateam.ru.forpda.client.interceptors.BlocklistInterceptor
import forpdateam.ru.forpda.client.interceptors.CacheControlInterceptor
import forpdateam.ru.forpda.client.interceptors.ErrorInterceptor
import forpdateam.ru.forpda.client.interceptors.ImageLoadingInterceptor
import forpdateam.ru.forpda.client.interceptors.RedirectFragmentInterceptor
import forpdateam.ru.forpda.client.interceptors.RequestGovernorInterceptor
import forpdateam.ru.forpda.client.proxy.BlockedTopicRegistry
import forpdateam.ru.forpda.client.proxy.ProxyConfig
import forpdateam.ru.forpda.client.proxy.ProxyRouter
import forpdateam.ru.forpda.client.proxy.ProxySettings
import forpdateam.ru.forpda.client.proxy.ProxyType
import forpdateam.ru.forpda.client.proxy.SocksProxyAuth
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.Credentials
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
    private val blocklistGuard: BlocklistGuard,
    private val userHolder: forpdateam.ru.forpda.entity.app.profile.IUserHolder,
    @forpdateam.ru.forpda.common.di.AppScope private val appScope: kotlinx.coroutines.CoroutineScope,
    private val proxySettingsStore: ProxySettings,
    private val blockedTopicsStore: BlockedTopicRegistry,
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
        private const val PROXY_LOG_TAG = "ProxyRoute"

        /** Ответ ProxySelector'а «идти напрямую». */
        private val DIRECT = listOf(java.net.Proxy.NO_PROXY)

        /** Актуальный мобильный Chrome на Android — ближе к WebView и к типичному браузеру пользователя. */
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MOBILE_COOKIE_NAME = "ngx_mb"
        private const val DESKTOP_MOBILE_COOKIE_VALUE = "0"
        // Realtime-эндпоинт 4PDA: app.4pda.to:993 — НЕСТАНДАРТНЫЙ WebSocket: голый TCP,
        // БЕЗ TLS и БЕЗ HTTP-рукопожатия, сырые WS-фреймы сразу (сервер отвечает pong на ping
        // и `[0,2]` на текст; проверено 20.07.2026). Порты 80/443 мертвы (timeout). Поэтому
        // сюда нельзя ходить OkHttp'ом (он начинает с upgrade/TLS → вечный
        // SSLHandshakeException у всех, на любой сети) — используется [RawWebSocket].
        // Установлено декомпиляцией офф-клиента ru.fourpda.client 1.9.43 + живыми пробами.
        private const val EVENT_WS_HOST = "app.4pda.to"
        private const val EVENT_WS_PORT = 993
        // Синтетический URL только для okhttp3.Request (диагностика/логи), не для запросов.
        private const val EVENT_WS_URL = "http://app.4pda.to:993/ws/"

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

    override suspend fun awaitAuthCookiesHydrated(timeoutMs: Long): Boolean =
            cookieManager.awaitHydration(timeoutMs)

    override fun reinitAuthCookies() = cookieManager.reinitializeCookies()

    override fun isSecureCookieStoreFallback(): Boolean =
            forpdateam.ru.forpda.common.SecureCookiesPreferences.getInstance(context).isUsingFallback

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
            .proxySelector(allTrafficProxySelector)
            .proxyAuthenticator(allTrafficProxyAuthenticator)
            .addInterceptor(BlocklistInterceptor(authHolder, userHolder, blocklistGuard))
            .addInterceptor(AuthInterceptor())
            .addInterceptor(ImageLoadingInterceptor { url -> cookieJar.loadForRequest(url).isNotEmpty() })
            .addInterceptor(ErrorInterceptor())
            .addInterceptor(BrotliInterceptor)
            .addNetworkInterceptor(RedirectFragmentInterceptor())
            .addNetworkInterceptor(CacheControlInterceptor())
            .addNetworkInterceptor(RequestGovernorInterceptor())
            .build()
            .also { built ->
                // Смена настроек должна применяться сразу: живые соединения пула переживают
                // переключение маршрута (адрес клиента не меняется), и картинки ещё несколько минут
                // грузились бы старым путём. Закрываем простаивающие — активные доработают сами.
                proxySettingsStore.addChangeListener { built.connectionPool.evictAll() }
            }
    }

    // region Прокси
    /**
     * Часть тем 4PDA закрыта для российских IP: origin отдаёт заглушку, и вернуть контент может
     * только запрос с другого адреса. Системный VPN для этого не обязателен — достаточно пустить
     * через прокси трафик одного приложения (а в режиме «только заблокированные темы» — вообще
     * только их). Прокси-клиенты собираются из [client]/[desktopClient] через `newBuilder()`,
     * поэтому у них ТОТ ЖЕ cookieJar: сессия общая для обоих маршрутов, иначе через прокси мы
     * приходили бы гостем и закрытая тема всё равно не открылась бы.
     *
     * Клиент пересобирается при смене настроек ([ProxySettings.version]) — без перезапуска приложения.
     */
    private class ProxyClients(
        val configVersion: Int,
        val config: ProxyConfig,
        val mobile: OkHttpClient,
        val desktop: OkHttpClient,
    )

    @Volatile
    private var proxyClients: ProxyClients? = null

    /** Глобальный [java.net.Authenticator] для SOCKS ставится один раз — см. [installSocksAuthenticator]. */
    private val socksAuthenticatorInstalled = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Список тем, ходящих через прокси — для экрана настроек и автоповтора в ThemeApi. */
    fun blockedTopicRegistry(): BlockedTopicRegistry = blockedTopicsStore

    fun proxySettings(): ProxySettings = proxySettingsStore

    override fun isProxyConfigured(): Boolean = proxySettingsStore.config() != null

    private fun currentProxyClients(): ProxyClients? {
        // Вторая, независимая проверка оплаты (первая — в ProxySettings.config): как и у push,
        // патч одной точки не должен открывать функцию целиком.
        if (!forpdateam.ru.forpda.pro.ProLicense.isUnlocked(context)) return null
        val config = proxySettingsStore.config() ?: return null
        val version = proxySettingsStore.version
        proxyClients?.let { if (it.configVersion == version && it.config == config) return it }
        return synchronized(this) {
            proxyClients?.let { if (it.configVersion == version && it.config == config) return it }
            val built = ProxyClients(
                configVersion = version,
                config = config,
                mobile = buildProxyClient(client, config),
                desktop = buildProxyClient(desktopClient, config),
            )
            proxyClients = built
            Timber.tag(PROXY_LOG_TAG).i("proxy client rebuilt: %s", config.describe())
            built
        }
    }

    private fun buildProxyClient(base: OkHttpClient, config: ProxyConfig): OkHttpClient =
        base.newBuilder()
            .proxy(config.toJavaProxy())
            // Проверка активации на КАЖДЫЙ запрос, уже внутри собранного клиента: снять маршрут
            // одним патчем «разрешающего» метода не выйдет — этот интерцептор спрашивает
            // независимую реализацию (LicenseGuard), и без него прокси-клиент просто не отвечает.
            .addInterceptor { chain ->
                if (!forpdateam.ru.forpda.pro.LicenseGuard.allowed(context)) {
                    throw IOException("proxy route unavailable")
                }
                chain.proceed(chain.request())
            }
            .apply {
                if (config.hasCredentials) {
                    // SOCKS5 с логином/паролем OkHttp сам не умеет — только HTTP-прокси через
                    // proxyAuthenticator. Для SOCKS ставим глобальный Authenticator (JDK-путь).
                    if (config.type == ProxyType.HTTP) {
                        proxyAuthenticator { _, response ->
                            // Не зацикливаемся: если сервер снова просит авторизацию — сдаёмся.
                            if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
                            response.request.newBuilder()
                                .header("Proxy-Authorization", Credentials.basic(config.login, config.password))
                                .build()
                        }
                    } else {
                        installSocksAuthenticator()
                    }
                }
            }
            .build()

    /**
     * SOCKS5-авторизация в Java идёт через глобальный [java.net.Authenticator] — другого способа
     * передать логин/пароль в SOCKS-хендшейк у OkHttp нет.
     *
     * Authenticator ставим ОДИН на процесс, а логин с паролем он берёт из настроек в момент
     * вопроса ([SocksProxyAuth]): иначе кнопка «Проверить» с другим адресом оставляла бы за собой
     * чужой экземпляр, а сохранённый прокси молча ходил бы без пароля. Настройки читаем без учёта
     * выключателя — той же пробе прокси ещё не включён.
     */
    private fun installSocksAuthenticator() {
        if (!socksAuthenticatorInstalled.compareAndSet(false, true)) return
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? {
                val credentials = SocksProxyAuth.credentialsFor(
                    config = proxySettingsStore.configIgnoringEnabled(),
                    requestingProtocol = requestingProtocol,
                    requestingHost = requestingHost,
                    requestingPort = requestingPort,
                ) ?: return null
                return java.net.PasswordAuthentication(credentials.first, credentials.second.toCharArray())
            }
        })
        Timber.tag(PROXY_LOG_TAG).i("SOCKS authenticator installed")
    }

    /**
     * Маршрут для клиентов, которые не разбирают запрос сами: картинки (Coil) и загрузка файлов
     * (DownloadWorker) берут ОДИН клиент на весь процесс, а настройка обещает «через прокси идёт
     * весь трафик приложения». Поэтому решение принимается не при сборке клиента, а на каждое
     * соединение — иначе прокси включался бы для картинок только после перезапуска.
     *
     * В режиме «только заблокированные темы» отдаём системный маршрут: по URL картинки тему не
     * определить, а гнать через чужой прокси всё подряд пользователь не просил. Именно системный,
     * а не `NO_PROXY` — иначе мы бы сломали тех, у кого прокси задан в настройках Wi-Fi Android.
     */
    private val allTrafficProxySelector = object : java.net.ProxySelector() {

        override fun select(uri: java.net.URI?): List<java.net.Proxy> {
            val config = ProxyRouter.proxyForAllTraffic(proxySettingsStore.config(), proxySettingsStore.mode)
                    ?: return systemSelect(uri)
            if (config.type == ProxyType.SOCKS5 && config.hasCredentials) installSocksAuthenticator()
            if (BuildConfig.DEBUG) Timber.tag(PROXY_LOG_TAG).d("via proxy (all traffic): %s", uri)
            return listOf(config.toJavaProxy())
        }

        override fun connectFailed(uri: java.net.URI?, address: java.net.SocketAddress?, failure: IOException?) {
            Timber.tag(PROXY_LOG_TAG).w(failure, "connect failed: %s via %s", uri, address)
        }

        private fun systemSelect(uri: java.net.URI?): List<java.net.Proxy> {
            val system = getDefault() ?: return DIRECT
            return runCatching { uri?.let { system.select(it) } }.getOrNull()?.takeIf { it.isNotEmpty() } ?: DIRECT
        }
    }

    /**
     * Логин/пароль для HTTP-прокси на том же «всём трафике». Срабатывает только на 407 от прокси,
     * поэтому клиенту без прокси ничего не стоит. SOCKS сюда не попадает — там свой путь
     * ([installSocksAuthenticator]).
     */
    private val allTrafficProxyAuthenticator = okhttp3.Authenticator { _, response ->
        // Не зацикливаемся: если прокси снова просит авторизацию — сдаёмся.
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        val config = proxySettingsStore.config()?.takeIf { it.type == ProxyType.HTTP && it.hasCredentials }
                ?: return@Authenticator null
        response.request.newBuilder()
                .header("Proxy-Authorization", Credentials.basic(config.login, config.password))
                .build()
    }

    /**
     * Клиент для этого запроса. Решение принимает [ProxyRouter]: прямой маршрут по умолчанию,
     * прокси — по флагу запроса, по режиму «весь трафик» или потому, что тема в списке закрытых.
     */
    private fun clientFor(request: NetworkRequest, desktop: Boolean): OkHttpClient {
        val direct = if (desktop) desktopClient else client
        // Третья независимая проверка оплаты на пути запроса (см. также ProxySettings.config и
        // интерцептор прокси-клиента): точки намеренно разные и спрашивают разные реализации.
        if (!forpdateam.ru.forpda.pro.LicenseGuard.allowed(context)) return direct
        val proxied = currentProxyClients() ?: return direct
        val useProxy = ProxyRouter.shouldUseProxy(
            hasConfig = true,
            mode = proxySettingsStore.mode,
            forceProxy = request.forceProxy,
            url = request.url,
            formTopicId = ProxyRouter.extractTopicIdFromForm(request.formHeaders),
            isTopicBlocked = { blockedTopicsStore.isBlocked(it) },
        )
        if (!useProxy) return direct
        if (BuildConfig.DEBUG) Timber.tag(PROXY_LOG_TAG).d("via proxy: %s", request.url)
        return if (desktop) proxied.desktop else proxied.mobile
    }

    /**
     * Проба прокси для кнопки «Проверить» в настройках: запрашивает лёгкую страницу форума ЧЕРЕЗ
     * указанный прокси, не трогая сохранённые настройки и не меняя маршрут остальных запросов.
     */
    fun probeProxy(config: ProxyConfig): ProxyProbeResult {
        val started = System.currentTimeMillis()
        return try {
            val probeClient = buildProxyClient(client, config).newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .cache(null) // проба должна реально сходить в сеть, а не ответить из кэша
                .build()
            val request = Request.Builder().url(IWebClient.COUNTERS_REFRESH_URL).build()
            probeClient.newCall(request).execute().use { response ->
                ProxyProbeResult(
                    ok = response.isSuccessful,
                    code = response.code,
                    elapsedMs = System.currentTimeMillis() - started,
                )
            }
        } catch (e: Exception) {
            Timber.tag(PROXY_LOG_TAG).w(e, "proxy probe failed")
            ProxyProbeResult(
                ok = false,
                code = 0,
                elapsedMs = System.currentTimeMillis() - started,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    data class ProxyProbeResult(
        val ok: Boolean,
        val code: Int,
        val elapsedMs: Long,
        val error: String? = null,
    )
    // endregion

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

    /**
     * GET, читающий не более [maxBytes] байт декодированного тела. Мы буферизуем только `maxBytes+1`
     * байт из источника и закрываем ответ — остаток скачивания обрывается, гигантская страница целиком
     * в память не грузится. cp1251 — однобайтовая кодировка, поэтому обрезка по байтам безопасна (не
     * рвёт символ). Счётчики/проверку форум-ошибок на (возможно частичном) теле намеренно пропускаем.
     */
    @Throws(Exception::class)
    override fun getCapped(url: String, maxBytes: Long): NetworkResponse {
        checkNetworkAvailable()
        val netRequest = NetworkRequest.Builder().url(url).build()
        val requestBuilder = prepareRequest(netRequest, null)
        val response = NetworkResponse(netRequest.url)
        var okHttpResponse: Response? = null
        try {
            okHttpResponse = clientFor(netRequest, desktop = false).newCall(requestBuilder.build()).execute()
            response.code = okHttpResponse.code
            response.message = okHttpResponse.message
            response.redirect = okHttpResponse.request.url.toString()
            val body = okHttpResponse.body
            if (body != null) {
                val source = body.source()
                source.request(maxBytes + 1) // подтягиваем максимум cap+1 байт, чтобы увидеть переполнение
                val buffered = source.buffer
                val truncated = buffered.size > maxBytes
                val toRead = if (truncated) maxBytes else buffered.size
                val bytes = buffered.readByteArray(toRead)
                val charset = body.contentType()?.charset() ?: Charsets.UTF_8
                response.body = String(bytes, charset)
                response.truncated = truncated
            }
            if (BuildConfig.DEBUG) {
                Timber.d("getCapped: code=${response.code} bytes=${response.body.length} truncated=${response.truncated}")
            }
        } finally {
            okHttpResponse?.close()
        }
        return response
    }

    @Throws(Exception::class)
    override fun request(request: NetworkRequest): NetworkResponse {
        return request(request, clientFor(request, desktop = false), null)
    }

    @Throws(Exception::class)
    override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse {
        return request(request, clientFor(request, desktop = false), progressListener)
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
        return request(desktopRequest, clientFor(desktopRequest, desktop = true), null)
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
            .tag(RequestPriority::class.java, request.priority)
        val response = NetworkResponse(request.url)
        var okHttpResponse: Response? = null
        
        try {
            okHttpResponse = client.newCall(requestBuilder.build()).execute()

            response.code = okHttpResponse.code
            response.message = okHttpResponse.message
            response.redirect = okHttpResponse.request.url.toString()
            response.locationHeader = okHttpResponse.header("Location")
            response.redirectFragment = redirectFragment.lastFragment.get()
            response.etag = okHttpResponse.header("ETag")
            response.lastModified = okHttpResponse.header("Last-Modified")

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
        // Request чисто синтетический — рукопожатия нет, заголовки серверу не уходят.
        val request = Request.Builder()
            .url(EVENT_WS_URL)
            .build()
        return RawWebSocket(
                host = EVENT_WS_HOST,
                port = EVENT_WS_PORT,
                originalRequest = request,
                listener = webSocketListener,
                connectTimeoutMs = 30_000,
                pingIntervalMs = WEBSOCKET_PING_INTERVAL_SECONDS * 1000L
        ).also { it.connect() }
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
