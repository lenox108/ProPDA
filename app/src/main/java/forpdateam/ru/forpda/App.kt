package forpdateam.ru.forpda

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.Messenger
import android.os.PowerManager
import android.os.StrictMode
import android.util.DisplayMetrics
import android.util.TypedValue
import android.webkit.WebSettings
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import timber.log.Timber
import com.google.android.material.color.DynamicColors
import forpdateam.ru.forpda.common.dpToPx
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getDrawableAttr
import forpdateam.ru.forpda.common.getDrawableResAttr
import forpdateam.ru.forpda.common.getToolBarHeight
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.NetworkConnectivityTracker
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.notifications.NotificationsService
import forpdateam.ru.forpda.ui.fragments.TabFragment
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import dagger.hilt.android.HiltAndroidApp
import forpdateam.ru.forpda.appupdates.AppUpdateScheduler
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.ui.TemplateManager
import javax.inject.Inject

/**
 * Главный класс приложения ForPDA.
 * 
 * Улучшения по сравнению с Java-версией:
 * - Thread-safe singleton через companion object с @Volatile
 * - StateFlow вместо Observable для networkForbidden
 * - Lazy initialization зависимостей
 * - Статические поля размеров (px2, px4...) теперь делегаты - вычисляются динамически
 * - Использование Kotlin-идиоматичных конструкций
 * - Lifecycle observer для автоматической очистки ресурсов
 * - Null-safety благодаря Kotlin
 */
@HiltAndroidApp
class App : Application(), androidx.work.Configuration.Provider {

    // region Companion Object - Thread-safe Singleton
    companion object {
        @Volatile
        private var _instance: App? = null

        val instance: App get() = _instance!!
        private const val VERSION_HISTORY_STARTUP_DELAY_MS = 1_500L
    }
    // endregion

    // region DI
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var otherPreferencesHolder: OtherPreferencesHolder
    @Inject lateinit var notificationPreferencesHolder: NotificationPreferencesHolder
    @Inject lateinit var webClient: IWebClient
    @Inject lateinit var templateManager: TemplateManager
    @Inject lateinit var networkState: NetworkStateProvider
    @Inject lateinit var appUpdateScheduler: AppUpdateScheduler
    @Inject lateinit var eventsRepository: EventsRepository
    // endregion

    // region Properties
    private val preferencesLazy = lazy { PreferenceManager.getDefaultSharedPreferences(this) }
    private val webViewFound = AtomicReference<Boolean?>(null)
    private val mServiceBound = AtomicBoolean(false)
    
    @Volatile
    private var mBoundService: Messenger? = null
    
    @javax.inject.Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    // StateFlow вместо SimpleObservable для networkForbidden
    private val _networkForbidden = MutableStateFlow(false)
    val networkForbidden: StateFlow<Boolean> = _networkForbidden.asStateFlow()
    
    private var networkConnectivityTracker: NetworkConnectivityTracker? = null
    private var dozeReceiver: BroadcastReceiver? = null
    private val permissionCallbacks = mutableListOf<Runnable>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val appLifecycleObserver = AppLifecycleObserver()
    
    private val isInitialized = AtomicBoolean(false)
    // endregion

    // region Service Connection
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            mBoundService = null
            mServiceBound.set(false)
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (name?.className == NotificationsService::class.java.name) {
                mBoundService = Messenger(service)
                mServiceBound.set(true)
            }
        }
    }
    // endregion

    // region Lifecycle
    init {
        _instance = this
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate() {
        super.onCreate()
        
        if (isInitialized.getAndSet(true)) {
            return // Предотвращаем двойную инициализацию
        }
        
        _instance = this
        val startTime = System.currentTimeMillis()
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        setupStrictMode()
        setupAppMetrica()
        setupThemeObserver()
        setupVersionHistory()
        setupCoil()
        NotificationsService.createEventChannels(this)
        setupDozeReceiver()
        setupNetworkTracking()
        setupBackgroundEventsCheck()
        setupAppUpdateCheck()
        setupMaterialYou()
        
        // Lifecycle observer для очистки ресурсов
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
        BatteryDebugLogger.logState("App", "created")
        
        if (BuildConfig.DEBUG) {
            Timber.d("TIME APP FINAL ${System.currentTimeMillis() - startTime}ms")
        }
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateResources()
    }

    override fun onTerminate() {
        cleanupApplicationResources()
        super.onTerminate()
    }
    // endregion

    // region Initialization Methods
    private fun setupStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
    
    private fun setupAppMetrica() {
        val config = AppMetricaConfig.newConfigBuilder("a94d9236-cdf3-4a5e-af30-d6dbffaea362").build()
        AppMetrica.activate(applicationContext, config)
        AppMetrica.enableActivityAutoTracking(this)
        
        val defaultUncaught = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                ex?.let {
                    AppMetrica.reportError("uncaught:${thread.name}:${it.message}", it)
                }
            } catch (_: Throwable) {
                // ignore
            }
            defaultUncaught?.uncaughtException(thread, ex)
        }
    }
    
    
    private fun setupThemeObserver() {
        appScope.launch {
            mainPreferencesHolder
                .observeThemeModeFlow()
                .collect { DayNightHelper.applyTheme(it) }
        }
    }
    
    private fun setupVersionHistory() {
        appScope.launch {
            delay(VERSION_HISTORY_STARTUP_DELAY_MS)
            withContext(Dispatchers.IO) {
                runCatching {
                    val inputHistory = otherPreferencesHolder.getAppVersionsHistory()
                    val history = inputHistory.split(";").filter { it.isNotEmpty() }.toMutableList()

                    var lastVNum = 0
                    var disorder = false
                    for (version in history) {
                        val vNum = version.toIntOrNull() ?: continue
                        if (vNum < lastVNum) {
                            disorder = true
                        }
                        lastVNum = vNum
                    }

                    val currentVersion = BuildConfig.VERSION_CODE
                    if (lastVNum < currentVersion) {
                        history.add(currentVersion.toString())
                        otherPreferencesHolder.setAppVersionsHistory(history.joinToString(";"))
                    }

                    check(!disorder) { "Нарушение порядка версий!" }
                }.onFailure { ex ->
                    Timber.e(ex, "Version history error")
                    if (!BuildConfig.DEBUG) {
                        runCatching { AppMetrica.reportError("VERSIONS_HISTORY", ex) }
                    }
                }
            }
        }
    }
    
    private fun setupCoil() {
        ForPdaCoil.init(this, webClient)
    }
    
    private fun updateResources() {
        if (BuildConfig.DEBUG) {
            Timber.d("updateResources")
        }
        
        // Загружаем строки шаблонов (explicit map — без reflection для cold-start)
        val templateStringCache = mapOf(
            "res_s_poll_title" to getString(R.string.res_s_poll_title),
            "res_s_poll_all_votes_count" to getString(R.string.res_s_poll_all_votes_count),
            "res_s_poll_vote_btn" to getString(R.string.res_s_poll_vote_btn),
            "res_s_poll_results_btn" to getString(R.string.res_s_poll_results_btn),
            "res_s_poll_show_btn" to getString(R.string.res_s_poll_show_btn),
            "res_s_poll_unavailable" to getString(R.string.res_s_poll_unavailable),
            "res_s_search_post_btn" to getString(R.string.res_s_search_post_btn),
            "res_s_hat" to getString(R.string.res_s_hat),
            "res_s_avatar" to getString(R.string.res_s_avatar),
            "res_s_reputation" to getString(R.string.res_s_reputation),
            "res_s_post_rating" to getString(R.string.res_s_post_rating),
            "res_s_group" to getString(R.string.res_s_group),
            "res_s_menu" to getString(R.string.res_s_menu),
            "res_s_report" to getString(R.string.res_s_report),
            "res_s_reply" to getString(R.string.res_s_reply),
            "res_s_quote" to getString(R.string.res_s_quote),
            "res_s_vote_good" to getString(R.string.res_s_vote_good),
            "res_s_vote_bad" to getString(R.string.res_s_vote_bad),
            "res_s_vote_post_good" to getString(R.string.vote_post_good),
            "res_s_vote_post_bad" to getString(R.string.vote_post_bad),
            "res_s_delete" to getString(R.string.res_s_delete),
            "res_s_edit" to getString(R.string.res_s_edit),
            "res_s_first" to getString(R.string.res_s_first),
            "res_s_prev" to getString(R.string.res_s_prev),
            "res_s_select_desc" to getString(R.string.res_s_select_desc),
            "res_s_select" to getString(R.string.res_s_select),
            "res_s_next" to getString(R.string.res_s_next),
            "res_s_last" to getString(R.string.res_s_last),
            "res_s_comments" to getString(R.string.res_s_comments),
            "res_s_comments_do_swipe" to getString(R.string.res_s_comments_do_swipe),
            "res_s_forum_blacklist_post_hidden" to getString(R.string.forum_blacklist_post_hidden),
            "res_s_forum_blacklist_posts_hidden" to getString(R.string.forum_blacklist_posts_hidden),
            "news_show_comments" to getString(R.string.news_show_comments),
            "news_inline_comments_description" to getString(R.string.news_inline_comments_description),
            "retry" to getString(R.string.retry),
        )
        templateManager.setStaticStrings(templateStringCache)
    }
    
    private fun setupDozeReceiver() {
        if (dozeReceiver != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val receiver = object : BroadcastReceiver() {
                @RequiresApi(Build.VERSION_CODES.M)
                override fun onReceive(context: Context?, intent: Intent?) {
                    Timber.d("DOZE ON RECEIVE $intent")

                    val pm = context?.getSystemService<PowerManager>() ?: return

                    if (pm.isDeviceIdleMode) {
                        Timber.d("DOZE MODE ENABLYA")
                    } else {
                        Timber.d("DOZE MODE DISABLYA")
                        BatteryDebugLogger.logState("App", "deviceIdleExit", "periodic worker handles background checks")
                    }
                }
            }

            val filter = IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            dozeReceiver = receiver
        }
    }
    
    
    private fun setupNetworkTracking() {
        networkConnectivityTracker?.stop()
        networkConnectivityTracker = NetworkConnectivityTracker(this, networkState).apply {
            start()
        }
    }

    /**
     * Планирует периодический WorkManager для опроса событий в фоне (когда сервис убит).
     * Реактивно реагирует на изменение настроек: вкл/выкл и интервал.
     */
    private fun setupBackgroundEventsCheck() {
        // Перепланировать при старте под текущие настройки.
        rescheduleEventsCheckWorker()
        appScope.launch {
            kotlinx.coroutines.flow.combine(
                    notificationPreferencesHolder.mainEnabledFlow(),
                    notificationPreferencesHolder.bgCheckEnabledFlow(),
                    notificationPreferencesHolder.bgCheckIntervalMinFlow()
            ) { _, _, _ -> }.collect {
                rescheduleEventsCheckWorker()
            }
        }
    }

    private fun rescheduleEventsCheckWorker() {
        val wm = androidx.work.WorkManager.getInstance(this)
        val mainEnabled = notificationPreferencesHolder.getMainEnabled()
        val bgEnabled = notificationPreferencesHolder.getBgCheckEnabled()
        if (!mainEnabled || !bgEnabled || !notificationPreferencesHolder.wantsPushNotifications()) {
            wm.cancelUniqueWork(forpdateam.ru.forpda.notifications.EventsCheckWorker.UNIQUE_NAME)
            Timber.d("EventsCheckWorker: cancelled (main=$mainEnabled bg=$bgEnabled)")
            return
        }
        // Минимум 30 мин: 15 мин при 4 HTTP-запросах = 96 пробуждений/день, что лишнее
        // для push-канала, на котором пользователь может не заметить минутной задержки.
        val intervalMin = notificationPreferencesHolder.getBgCheckIntervalMin().coerceAtLeast(30L)
        val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        val req = androidx.work.PeriodicWorkRequestBuilder<forpdateam.ru.forpda.notifications.EventsCheckWorker>(
                intervalMin, TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                )
                .build()
        wm.enqueueUniquePeriodicWork(
                forpdateam.ru.forpda.notifications.EventsCheckWorker.UNIQUE_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                req
        )
        Timber.d("EventsCheckWorker: scheduled every $intervalMin min")
        BatteryDebugLogger.logState("EventsCheckWorker", "scheduled", "intervalMin=$intervalMin batteryNotLow=true")
    }

    private fun setupAppUpdateCheck() {
        appUpdateScheduler.reschedule()
    }

    /**
     * Material You / Dynamic Color for the native UI shell.
     *
     * Applies the system wallpaper-derived palette to all activities that
     * participate in the dynamic-colors overlay. This is the *native* accent
     * only — the WebView CSS in [TemplateManager] is intentionally not affected,
     * so reading palettes (SEPIA_*, MINIMAL_READER, CLASSIC_4PDA) keep their
     * own background/typography and the dynamic accent is layered on top.
     *
     * §4.1 of REFACTOR_PLAN.md.
     */
    private fun setupMaterialYou() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!mainPreferencesHolder.getUseMaterialYou()) return
        // Only apply to the SYSTEM palette so we don't fight reading palettes.
        val palette = mainPreferencesHolder.getUiPalette()
        if (palette != Preferences.Main.UiPalette.SYSTEM &&
                palette != Preferences.Main.UiPalette.CLASSIC_4PDA) return
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
    // endregion

    // region Public Methods
    /**
     * Проверяет, доступен ли WebView.
     */
    fun isWebViewFound(context: Context): Boolean {
        return webViewFound.get() ?: try {
            WebSettings.getDefaultUserAgent(context)
            webViewFound.set(true)
            true
        } catch (_: Exception) {
            webViewFound.set(false)
            false
        }
    }
    
    
    /**
     * Возвращает ServiceConnection для NotificationsService.
     */
    fun getServiceConnection(): ServiceConnection = mServiceConnection
    
    /**
     * Проверяет, привязан ли сервис уведомлений.
     */
    fun isServiceBound(): Boolean = mServiceBound.get()
    
    /**
     * Возвращает Messenger для связи с сервисом.
     */
    fun getBoundService(): Messenger? = mBoundService
    
    /**
     * Уведомляет слушателей о запрете сети.
     */
    fun notifyForbidden(isForbidden: Boolean) {
        _networkForbidden.value = isForbidden
    }
    
    /**
     * Проверяет разрешение на хранение и выполняет callback если оно есть.
     */
    fun checkStoragePermission(runnable: Runnable?, activity: Activity?) {
        if (runnable == null || activity == null) return
        
        // WRITE_EXTERNAL_STORAGE требуется только для записи в public Downloads до Android 10 (API 29).
        // Начиная с Android 10 используем MediaStore и разрешение не нужно.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    TabFragment.REQUEST_STORAGE
                )
                permissionCallbacks.add(runnable)
                return
            }
        }
        runnable.run()
    }
    
    /**
     * Вызывается при результате запроса разрешений.
     * PLS CALL THIS IN ALL ACTIVITIES
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissions.indices.forEach { i ->
            if (permissions[i] == Manifest.permission.WRITE_EXTERNAL_STORAGE && 
                grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                permissionCallbacks.forEach { runnable ->
                    try {
                        runnable.run()
                    } catch (_: Exception) {
                        // ignore
                    }
                }
                return@forEach
            }
        }
        permissionCallbacks.clear()
    }
    
    /**
     * Получает текущую активность без использования reflection.
     * Рекомендуется передавать Activity явно через параметры.
     */
    fun getActivity(): Activity? {
        // Используем ActivityManager для получения текущей активити
        // Это более безопасный способ чем reflection
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val appTasks = activityManager?.appTasks
            appTasks?.firstOrNull()?.taskInfo?.topActivity?.let {
                // Возвращаем null так как мы не можем получить instance Activity
                // Этот метод deprecated, используйте явную передачу Activity
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    // endregion

    // region Private Methods
    private fun getPreferencesInternal(): SharedPreferences = preferencesLazy.value

    private fun cleanupApplicationResources() {
        networkConnectivityTracker?.stop()
        networkConnectivityTracker = null
        dozeReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
                .onFailure { Timber.w(it, "Doze receiver unregister failed") }
        }
        dozeReceiver = null
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        appScope.cancel()
        _instance = null
    }
    // endregion

    // region Lifecycle Observer
    private inner class AppLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            BatteryDebugLogger.logState("AppLifecycle", "background")
            eventsRepository.setForegroundRealtimeEnabled(false, "process_stop")
        }

        override fun onStart(owner: LifecycleOwner) {
            BatteryDebugLogger.logState("AppLifecycle", "foreground")
            eventsRepository.setForegroundRealtimeEnabled(true, "process_start")
        }
        
        override fun onDestroy(owner: LifecycleOwner) {
            cleanupApplicationResources()
        }
    }
    // endregion
}
