package forpdateam.ru.forpda.ui.activities
import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import timber.log.Timber
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.ui.FavoriteShortcuts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import android.view.animation.BounceInterpolator
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.databinding.ActivityMainBinding
import forpdateam.ru.forpda.notifications.NotificationsService
import forpdateam.ru.forpda.presentation.main.MainActivityCallbacks
import forpdateam.ru.forpda.presentation.main.MainViewModel
import forpdateam.ru.forpda.ui.BottomNavWindowInset
import forpdateam.ru.forpda.ui.EdgeToEdge
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.ui.FontController
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.UiThemeStyles
import forpdateam.ru.forpda.ui.navigation.TabNavigator
import forpdateam.ru.forpda.ui.views.drawers.BottomDrawer
import forpdateam.ru.forpda.presentation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import dagger.hilt.android.AndroidEntryPoint
import com.github.terrakok.cicerone.NavigatorHolder
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.preferences.ListsPreferencesHolder
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.DimensionsProvider
import forpdateam.ru.forpda.common.PermissionHelper
import forpdateam.ru.forpda.common.WebViewChecker
import forpdateam.ru.forpda.ui.AccentApplier
import forpdateam.ru.forpda.ui.ContrastApplier
import forpdateam.ru.forpda.ui.MaterialYouApplier
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), MainActivityCallbacks {
    @Inject lateinit var dayNightHelper: DayNightHelper
    @Inject lateinit var dimensionsProvider: DimensionsProvider
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var menuRepository: MenuRepository
    @Inject lateinit var listsPreferencesHolder: ListsPreferencesHolder
    @Inject lateinit var navigatorHolder: NavigatorHolder
    @Inject lateinit var notificationPreferencesHolder: NotificationPreferencesHolder
    @Inject lateinit var router: TabRouter
    @Inject lateinit var authHolder: forpdateam.ru.forpda.model.AuthHolder
    @Inject lateinit var userHolder: forpdateam.ru.forpda.entity.app.profile.IUserHolder
    @Inject lateinit var webViewChecker: WebViewChecker
    @Inject lateinit var permissionHelper: PermissionHelper
    @Inject lateinit var favoritesCacheRoom: forpdateam.ru.forpda.model.data.cache.favorites.FavoritesCacheRoom
    @Inject lateinit var eventsRepository: forpdateam.ru.forpda.model.repository.events.EventsRepository

    val removeTabListener: (View) -> Unit = { backHandler() }

    private lateinit var binding: ActivityMainBinding

    private var checkWebView = true

    private lateinit var bottomDrawer: BottomDrawer
    private var firstStartAnimator: ObjectAnimator? = null

    val tabNavigator by lazy { TabNavigator(this, R.id.fragments_container) }

    private var lang: String? = null

    private lateinit var appliedUiPalette: Preferences.Main.UiPalette
    private lateinit var appliedFontMode: forpdateam.ru.forpda.ui.AppFontMode
    private var appliedAppFontSize: Int = 16
    private var appliedMaterialYou: Boolean = false
    private lateinit var appliedAccent: Preferences.Main.AccentPalette
    private var appliedAccentStyle: Preferences.Main.AccentStyle = Preferences.Main.AccentStyle.TONAL

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(NOTIFICATIONS_LOG_TAG, "POST_NOTIFICATIONS granted")
            NotificationsService.createEventChannels(this)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) &&
                    notificationPreferencesHolder.wantsPushNotifications() && authHolder.get().isAuth()) {
                NotificationsService.startAndCheckNoBind(this)
            }
        } else {
            Log.w(NOTIFICATIONS_LOG_TAG, "POST_NOTIFICATIONS denied by user")
        }
    }

    private val presenter: MainViewModel by viewModels()

    override fun attachBaseContext(base: Context) {
        val localizedContext = LocaleHelper.onAttach(base)
        // App-wide font size (отдельно от «размера в темах»): масштабируем ВЕСЬ интерфейс через
        // Configuration.fontScale — множим на выбранный размер (16 = 100%), поверх системного scale.
        val appFontSize = try { MainDataStore(base).getAppFontSizeImmediate() } catch (e: Exception) { 16 }
        val ctx = if (appFontSize != 16) {
            val cfg = android.content.res.Configuration(localizedContext.resources.configuration)
            cfg.fontScale = cfg.fontScale * (appFontSize / 16f)
            localizedContext.createConfigurationContext(cfg)
        } else {
            localizedContext
        }
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ SplashScreen API (backported via androidx.core:core-splashscreen).
        // Должен вызываться ПЕРВЫМ — до super.onCreate() и setContentView.
        // installSplashScreen() сам переключает тему на postSplashScreenTheme;
        // ниже мы всё равно перекрываем её палитро-специфичной mainNoActionBar(...).
        installSplashScreen()
        if (BuildConfig.DEBUG) Timber.d("[INTENT] onCreate: hasData=${intent?.data != null}, action=${intent?.action}, hasExtras=${intent?.extras != null}")
        // Get theme settings directly from DataStore before super.onCreate() (DI not available yet)
        // Using SharedPreferences mirror for instant synchronous read (<1ms vs 50-200ms DataStore)
        val tempDataStore = MainDataStore(this)
        appliedUiPalette = try {
            tempDataStore.getUiPaletteImmediate()
        } catch (e: Exception) {
            Preferences.Main.UiPalette.SYSTEM
        }
        val themeMode = try {
            tempDataStore.getThemeModeImmediate()  // Reads from SharedPreferences mirror first
        } catch (e: Exception) {
            Preferences.Main.ThemeMode.SYSTEM
        }
        appliedFontMode = tempDataStore.getAppFontModeImmediate()
        appliedAppFontSize = tempDataStore.getAppFontSizeImmediate()
        // Запоминаем Material You + accent, чтобы onResume пересоздал активити при
        // их изменении (как уже делается для палитры/шрифта) — иначе переключение
        // в настройках не подхватывалось бы на уже открытых экранах без рестарта.
        appliedMaterialYou = try {
            tempDataStore.getUseMaterialYouImmediate()
        } catch (e: Exception) {
            false
        }
        appliedAccent = try {
            tempDataStore.getAccentPaletteImmediate()
        } catch (e: Exception) {
            Preferences.Main.AccentPalette.NEUTRAL
        }
        appliedAccentStyle = try {
            tempDataStore.getAccentStyleImmediate()
        } catch (e: Exception) {
            Preferences.Main.AccentStyle.TONAL
        }
        setTheme(UiThemeStyles.mainNoActionBar(appliedUiPalette, themeMode, resources.configuration))
        FontController.applyNativeTheme(this, appliedFontMode)
        // Material You (Dynamic Color) must be layered on top of the just-set theme
        // (setTheme wipes any overlay applied earlier by the global applier). The
        // per-Activity applier is the canonical entry point and is what makes the
        // Material You toggle visually change the colors on the native UI shell.
        MaterialYouApplier.applyIfEnabled(this)
        // Курируемый акцент («смена цвета») — после Material You; взаимоисключающи
        // по AccentPolicy (Material You приоритетнее, когда реально доступен).
        AccentApplier.applyIfEnabled(this)
        // Последний слой: усиление контраста по системной настройке (a11y, Android 14+).
        ContrastApplier.applyIfAvailable(this)
        super.onCreate(savedInstanceState)
        dayNightHelper.setIsNight(DayNightHelper.isUiModeNight(resources.configuration))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // ChromeCanvas: контейнер вкладок — часть полотна; под Material You тонируется
        // обоями (единый тон с шапкой/таббаром), вне MY = XML-значение background_for_lists.
        binding.fragmentsContainer.setBackgroundColor(chromeCanvasColor(R.attr.background_for_lists))

        // Enable edge-to-edge; insets (statusBar top, navBar bottom) applied per-container in updateDimens()
        // Use the unified EdgeToEdge.apply helper so behaviour is consistent across all 4 Activities.
        EdgeToEdge.apply(this, binding.root, padTop = false, padBottom = false)

        syncStatusBarIconContrast()
        syncNavigationBarAppearance()

        if (intent != null) {
            checkWebView = intent.getBooleanExtra(ARG_CHECK_WEBVIEW, checkWebView)
        }
        if (checkWebView) {
            lifecycleScope.launch {
                val found = withContext(Dispatchers.IO) { webViewChecker.isWebViewFound() }
                if (!found) {
                    startActivity(Intent(this@MainActivity, WebVewNotFoundActivity::class.java))
                    finish()
                }
            }
        }

        if (savedInstanceState == null) {
            maybeOfferCrashReport()
        }

        presenter.setIsRestored(savedInstanceState != null)
        intent?.data?.also {
            presenter.setStartLink(it.toString())
        }

        // Динамические App Shortcuts на топ-избранные темы — long-press по иконке →
        // сразу в активную ветку. Сначала публикуем кэш из БД (чтобы ярлыки были и
        // без открытия вкладки избранного), затем следим за изменениями.
        lifecycleScope.launch {
            runCatching { favoritesCacheRoom.ensureItemsPublished() }
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                // StateFlow сам дедупает — отдельный distinctUntilChanged не нужен.
                favoritesCacheRoom.observeItems()
                        .collect { FavoriteShortcuts.update(this@MainActivity, it) }
            }
        }
        // Check initial intent (when app is launched from notification)
        if (intent != null && intent.data != null) {
            if (BuildConfig.DEBUG) Timber.d("[INTENT] onCreate: calling checkIntent for initial intent")
            checkIntent(intent)
        }

        bottomDrawer = BottomDrawer(
                this,
                binding,
                tabNavigator,
                router,
                menuRepository,
                mainPreferencesHolder,
                listsPreferencesHolder,
                authHolder,
                userHolder
        )
        bottomDrawer.setListener(object : BottomDrawer.DrawerListener {
            override fun onHide() {
                hideKeyboard()
                cancelStartAnimation()
                syncNavigationBarAppearance()
            }

            override fun onShow() {
                cancelStartAnimation()
            }

            override fun onSlide(slideOffset: Float) {
                val container = binding.fragmentsContainer
                val translate = -slideOffset * 0.1f * container.height
                container.translationY = translate
                cancelStartAnimation()
            }
        })

        onBackPressedDispatcher.addCallback(this, mainBackCallback)
        updateBackDispatchEnabled()

        // Открытие/закрытие вкладки происходит программно (например, тап по теме в
        // Избранном) — уже ПОСЛЕ ACTION_DOWN тапа, поэтому onUserInteraction на этот
        // момент считает back-состояние по старому стеку и может ложно выключить
        // mainBackCallback. Без последующего касания (юзер не скроллит) флаг остаётся
        // устаревшим, и системный back-жест уводит на рабочий стол вместо возврата в
        // Избранное. subscribersFlow эмитит в конце updateFragmentsState (стек уже
        // авторитетен) → пересчитываем enabled на каждое изменение стека.
        lifecycleScope.launch {
            tabNavigator.subscribersFlow.collect { updateBackDispatchEnabled() }
        }

        val defaultStatusBarHeight = resources.getDimensionPixelSize(R.dimen.default_statusbar_height)
        val defaultKeyboardHeight = resources.getDimensionPixelSize(R.dimen.default_keyboard_height)

        DimensionHelper(
                binding.measureView,
                binding.measureRootContent,
                object : DimensionHelper.DimensionsListener {
                    override fun onDimensionsChange(dimensions: DimensionHelper.Dimensions) {
                        dimensionsProvider.update(dimensions)
                    }
                },
                defaultStatusBarHeight,
                defaultKeyboardHeight
        )

        lifecycleScope.launch {
            dimensionsProvider.dimensionsFlow.collect { dimensions ->
                binding.bottomMenuRecycler.post {
                    binding.fragmentsContainer.also { updateDimens(dimensions) }
                }
            }
        }
        // Сторонние клавиатуры / OEM: IME приходит в root insets позже или не попадает в DimensionHelper.
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentsContainer) { _, insets ->
            binding.bottomMenuRecycler.post {
                updateDimens(dimensionsProvider.getDimensions())
            }
            // Переустанавливаем цвет навбара при любом изменении insets (клавиатура, жесты и т.д.)
            syncNavigationBarAppearance()
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (notificationPreferencesHolder.wantsPushNotifications() &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(NOTIFICATIONS_LOG_TAG, "Requesting POST_NOTIFICATIONS permission")
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        presenter.attachView(this)
        presenter.start()
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.also { tabNavigator.onRestoreInstanceState(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.also { tabNavigator.onSaveInstanceState(it) }
    }

    override fun showFirstStartAnimation() {
        val height = resources.getDimensionPixelSize(R.dimen.dp48)
        firstStartAnimator = ObjectAnimator.ofFloat(binding.bottomSheet2, "translationY", 0f, -height.toFloat(), 0f).apply {
            interpolator = BounceInterpolator()
            startDelay = 500
            duration = 1500
            repeatCount = 2
            start()
        }
    }

    private fun cancelStartAnimation() {
        firstStartAnimator?.cancel()
        binding.bottomSheet2.translationY = 0f
    }

    fun updateDimens(dimensions: DimensionHelper.Dimensions) {
        val rootInsets = ViewCompat.getRootWindowInsets(binding.fragmentsContainer)
        bottomDrawer.syncBottomChromeWithInsets(rootInsets)
        binding.fragmentsContainer.apply {
            val baseBar = resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height)
            val basePb = BottomNavWindowInset.fragmentsBottomPaddingPx(
                    baseBar,
                    rootInsets,
                    dimensions.navigationBar
            )
            val currentFragment = tabNavigator.getCurrentFragment()
            // Theme WebView рисует под нижней панелью и сам добавляет spacer в HTML:
            // иначе parent padding виден как цветная полоса после нижней пагинации.
            // При открытой клавиатуре (особенно на OEM, где adjustResize не работает как раньше)
            // нижний таббар всё равно перекрыт IME. Если оставить резерв (basePb), появляется
            // «пустая полоса» между панелью ввода и клавиатурой.
            val pb = when {
                dimensions.isKeyboardShow() -> 0
                currentFragment?.shouldDrawBehindBottomNav() == true -> 0
                else -> basePb
            }
            val statusTopPx = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            val topInset = maxOf(dimensions.statusBar, statusTopPx, 0)
            setPadding(
                    paddingLeft,
                    topInset,
                    paddingRight,
                    max(pb, 0)
            )
            currentFragment?.onBottomChromePaddingChanged(basePb)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Timber.d("[INTENT] onNewIntent: hasData=${intent.data != null}, action=${intent.action}, hasExtras=${intent.extras != null}")
        checkIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        bottomDrawer.onStart()
        NotificationsService.createEventChannels(this)
        // UI поток: стартуем сервис, а bind пусть делает сам сервис при необходимости.
        // Это же снижает риск ANR на некоторых устройствах при старте/возврате в приложение.
        // Проверяем, включены ли уведомления перед запуском сервиса
        if (notificationPreferencesHolder.wantsPushNotifications() && authHolder.get().isAuth()) {
            NotificationsService.startAndCheckNoBind(this)
        } else {
            Timber.d("MainActivity.onStart: notifications disabled or no push families, skipping service start")
        }
        BatteryDebugLogger.logState("MainActivity", "start")
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        Timber.d("onResumeFragments")
        navigatorHolder.setNavigator(tabNavigator)
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        val paletteNow = mainPreferencesHolder.getUiPalette()
        val fontModeNow = FontController.getCurrentFontMode(mainPreferencesHolder)
        if (::appliedUiPalette.isInitialized && paletteNow != appliedUiPalette) {
            appliedUiPalette = paletteNow
            if (BuildConfig.DEBUG) Timber.d("activityRecreated=true reason=uiPalette")
            recreate()
            return
        }
        if (::appliedFontMode.isInitialized && fontModeNow != appliedFontMode) {
            appliedFontMode = fontModeNow
            if (BuildConfig.DEBUG) Timber.d("activityRecreated=true reason=fontMode selectedFontMode=%s", fontModeNow)
            recreate()
            return
        }
        val appFontSizeNow = mainPreferencesHolder.getAppFontSize()
        if (appFontSizeNow != appliedAppFontSize) {
            appliedAppFontSize = appFontSizeNow
            if (BuildConfig.DEBUG) Timber.d("activityRecreated=true reason=appFontSize size=%s", appFontSizeNow)
            recreate()
            return
        }
        val materialYouNow = mainPreferencesHolder.getUseMaterialYou()
        if (materialYouNow != appliedMaterialYou) {
            appliedMaterialYou = materialYouNow
            if (BuildConfig.DEBUG) Timber.d("activityRecreated=true reason=materialYou")
            recreate()
            return
        }
        val accentNow = mainPreferencesHolder.getAccentPalette()
        if (::appliedAccent.isInitialized && accentNow != appliedAccent) {
            appliedAccent = accentNow
            if (BuildConfig.DEBUG) Timber.d("activityRecreated=true reason=accent")
            recreate()
            return
        }
        val accentStyleNow = mainPreferencesHolder.getAccentStyle()
        if (accentStyleNow != appliedAccentStyle) {
            appliedAccentStyle = accentStyleNow
            if (BuildConfig.DEBUG) Timber.d("activityRecreated=true reason=accentStyle")
            recreate()
            return
        }
        syncStatusBarIconContrast()
        syncNavigationBarAppearance()
        presenter.onActivityResume()
        // После смены вкладки/восстановления — освежить enabled предиктивного back.
        updateBackDispatchEnabled()
        if (lang == null) {
            lang = LocaleHelper.getLanguage(this)
        }

        if (false && LocaleHelper.getLanguage(this) != lang) {
            val newContext = LocaleHelper.onAttach(this)
            MaterialAlertDialogBuilder(this)
                    .setMessage(newContext.getString(R.string.lang_changed))
                    .setPositiveButton(newContext.getString(R.string.ok)) { _, _ -> MainActivity.restartApplication(this@MainActivity) }
                    .setNegativeButton(newContext.getString(R.string.cancel), null)
                    .showWithStyledButtons()
        }
    }

    /**
     * Если в прошлый раз приложение аварийно закрылось ([CrashReporter] записал отчёт),
     * предлагаем отправить его. Отчёт уже лежит файлом в `Android/data/<package>/files/crash/`,
     * а отсюда уходит как обычный текст через системный share-лист (без бэкенда и FileProvider).
     */
    private fun maybeOfferCrashReport() {
        // Если включена автоотправка в Telegram — отчёт уйдёт сам в фоне ([App]); ручной диалог
        // не показываем, чтобы не дублировать.
        if (forpdateam.ru.forpda.diagnostic.CrashTelegramUploader.isAutoSendActive(this)) return
        val report = forpdateam.ru.forpda.diagnostic.CrashReporter.consumePendingReport(this) ?: return
        MaterialAlertDialogBuilder(this)
                .setTitle(R.string.crash_report_dialog_title)
                .setMessage(R.string.crash_report_dialog_message)
                .setPositiveButton(R.string.crash_report_send) { _, _ ->
                    val share = forpdateam.ru.forpda.diagnostic.CrashReporter.buildShareIntent(report)
                    runCatching {
                        startActivity(Intent.createChooser(share, getString(R.string.crash_report_share_chooser)))
                    }
                }
                .setNegativeButton(R.string.crash_report_later, null)
                .showWithStyledButtons()
    }

    override fun onPause() {
        navigatorHolder.removeNavigator()
        super.onPause()
        Timber.d("onPause")
    }

    override fun onStop() {
        super.onStop()
        bottomDrawer.onStop()
        BatteryDebugLogger.logState("MainActivity", "stop")
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        presenter.detachView()
        bottomDrawer.cleanup()
        super.onDestroy()
        bottomDrawer.destroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun checkIntent(intent: Intent?) {
        if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent called: hasData=${intent?.data != null}, action=${intent?.action}")
        if (intent == null) {
            if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent: intent is null, returning")
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_FORUM_RULES, false)) {
            if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent: opening forum rules screen")
            router.navigateTo(Screen.ForumRules())
            setIntent(null)
            return
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_FORUM_BLACKLIST, false)) {
            if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent: opening forum blacklist screen")
            router.navigateTo(Screen.ForumBlackList())
            setIntent(null)
            return
        }
        // Проверяем специальный параметр для открытия экрана загрузок
        if (intent.getBooleanExtra("open_downloads", false)) {
            if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent: opening downloads screen")
            router.navigateTo(Screen.Downloads())
            setIntent(null)
            return
        }
        if (intent.data == null) {
            if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent: intent.data is null, returning")
            return
        }
        val url: String = intent.data?.toString().orEmpty()
        if (BuildConfig.DEBUG) Timber.d("[INTENT] checkIntent: opening url")
        presenter.openLink(url)
        setIntent(null)
    }

    fun hideKeyboard() {
        val view = currentFocus ?: window.decorView
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
        val iim = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        iim?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun showKeyboard(view: View) {
        view.requestFocus()
        WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
        val iim = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        iim?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * Единственный `OnBackPressedCallback` приложения (pull-based диспетчер:
     * фрагмент решает через [TabFragment.onBackPressed]). Его `enabled` теперь
     * ДИНАМИЧЕСКИЙ — см. [updateBackDispatchEnabled]: когда следующий «назад»
     * гарантированно выйдет из приложения, callback выключается, и Android 13+
     * показывает предиктивную анимацию отслаивания к лаунчеру. Пока enabled —
     * поведение ровно прежнее (backHandler всегда делает корректное действие),
     * поэтому «ложно-enabled» не даёт регрессий, а «ложно-disabled» исключён
     * консервативным [backWillExitApp] + пересчётом на onUserInteraction/onResume.
     */
    private val mainBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Timber.d("onBackPressed")
            try {
                if (::bottomDrawer.isInitialized && bottomDrawer.isShown()) {
                    bottomDrawer.hide()
                } else {
                    backHandler()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in onBackPressed")
                finishAffinity()
            } finally {
                updateBackDispatchEnabled()
            }
        }
    }

    /**
     * Выйдет ли следующий «назад» из приложения. КОНСЕРВАТИВНО: возвращает true
     * (выход → показать предиктивную анимацию) только когда мы уверены, что
     * перехватывать нечего. Любая неопределённость → false (остаёмся enabled,
     * прежнее поведение, без регрессий).
     */
    private fun backWillExitApp(): Boolean {
        if (::bottomDrawer.isInitialized && bottomDrawer.isShown()) return false
        val active = tabNavigator.getCurrentFragment() ?: return true
        // Есть куда навигировать назад (не последняя вкладка) → не выход.
        if (tabNavigator.tabController.getList().size > 1) return false
        // Единственная (корневая) вкладка: выход, только если фрагмент сейчас
        // ничего не перехватывает (read-only запрос, без сайд-эффектов).
        return !active.hasBackHandling()
    }

    /** Пересчитать enabled главного back-callback. Дёшево; безопасно звать часто. */
    private fun updateBackDispatchEnabled() {
        mainBackCallback.isEnabled = !backWillExitApp()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Любое изменение back-состояния (вход в selection/поиск/панель, показ
        // drawer) инициируется касанием → к следующему back-жесту enabled свеж.
        updateBackDispatchEnabled()
        // Касание = активность пользователя: снимаем idle-паузу realtime-WS (если была) и
        // сбрасываем отсчёт бездействия. Дёшево — обычно лишь обновление метки времени.
        if (::eventsRepository.isInitialized) {
            eventsRepository.notifyUserActive()
        }
    }

    private fun backHandler() {
        try {
            val active = tabNavigator.getCurrentFragment()
            Log.i(
                    "ThemeHistory",
                    "activity back active=${active?.javaClass?.simpleName} tag=${active?.tag} setting=${mainPreferencesHolder.getTopicBackBehavior()}"
            )
            if (active == null) {
                router.exit()
                return
            }
            // И системная «назад», и стрелка в toolbar — один путь: фрагмент решает (диалоги, pop внутренних стеков).
            if (!active.onBackPressed()) {
                hideKeyboard()
                // Защита от NPE: active.tag может быть null если фрагмент не полностью инициализирован
                active.tag?.let { tag ->
                    if (!closeTopicChainToOriginIfEnabled(tag)) {
                        tabNavigator.close(tag)
                    }
                } ?: router.exit()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in backHandler")
            finishAffinity()
        }
    }

    private fun closeTopicChainToOriginIfEnabled(tag: String): Boolean {
        if (mainPreferencesHolder.getTopicBackBehavior() != Preferences.Main.TopicBackBehavior.ORIGIN) {
            return false
        }
        return tabNavigator.closeThemeChainToOrigin(tag)
    }

    fun selectTopicOriginOrParent(tag: String?): Boolean {
        if (tag == null) return false
        if (mainPreferencesHolder.getTopicBackBehavior() == Preferences.Main.TopicBackBehavior.ORIGIN &&
                tabNavigator.closeThemeChainToOrigin(tag)) {
            return true
        }
        return tabNavigator.selectParentOf(tag)
    }

    private fun syncStatusBarIconContrast() {
        SystemBarAppearance.syncStatusBarIconContrast(this)
    }

    private fun syncNavigationBarAppearance() {
        // Согласовано с fragments_container и нижним sheet (background_for_lists), иначе полоска другого тона.
        SystemBarAppearance.syncNavigationBar(this)
    }

    companion object {
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        val LOG_TAG = MainActivity::class.java.simpleName
        val DEF_TITLE = "ProPDA"
        val ARG_CHECK_WEBVIEW = "CHECK_WEBVIEW"
        const val EXTRA_OPEN_FORUM_RULES = "open_forum_rules"
        const val EXTRA_OPEN_FORUM_BLACKLIST = "open_forum_blacklist"

        fun restartApplication(activity: Activity) {
            val mStartActivity = Intent(activity, MainActivity::class.java)
            val mPendingIntentId = 123456
            val piFlags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val mPendingIntent = PendingIntent.getActivity(activity, mPendingIntentId, mStartActivity, piFlags)
            val mgr = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent)
            activity.finishAffinity()
        }

        fun setLightStatusBar(activity: Activity, value: Boolean) {
            SystemBarAppearance.setLightStatusBar(activity, value)
        }

        fun getDefaultLightStatusBar(context: Activity): Boolean {
            return SystemBarAppearance.getDefaultLightSystemBar(context)
        }

        const val EXTRA_RESTART_WITH_NEW_THEME = "restart_with_new_theme"
    }
}
