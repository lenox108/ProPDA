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
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.animation.BounceInterpolator
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.databinding.ActivityMainBinding
import forpdateam.ru.forpda.notifications.NotificationsService
import forpdateam.ru.forpda.presentation.main.MainActivityCallbacks
import forpdateam.ru.forpda.presentation.main.MainViewModel
import forpdateam.ru.forpda.ui.DimensionHelper
import forpdateam.ru.forpda.ui.UiThemeStyles
import forpdateam.ru.forpda.ui.activities.updatechecker.SimpleUpdateChecker
import forpdateam.ru.forpda.ui.navigation.TabNavigator
import forpdateam.ru.forpda.ui.views.drawers.BottomDrawer
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlin.math.max

class MainActivity : AppCompatActivity(), MainActivityCallbacks {
    val removeTabListener: (View) -> Unit = { backHandler(true) }

    private lateinit var binding: ActivityMainBinding

    private var checkWebView = true

    private lateinit var bottomDrawer: BottomDrawer
    private var firstStartAnimator: ObjectAnimator? = null

    val tabNavigator = TabNavigator(this, R.id.fragments_container)
    private val dimensionsProvider = App.get().Di().dimensionsProvider
    private val disposables = CompositeDisposable()
    private val notificationPreferencesRepository = App.get().Di().notificationPreferencesHolder
    private val mainPreferencesRepository = App.get().Di().mainPreferencesHolder
    private val checkerRepository = App.get().Di().checkerRepository
    private val updateChecker by lazy { SimpleUpdateChecker(checkerRepository) }

    private var lang: String? = null

    private lateinit var appliedUiPalette: Preferences.Main.UiPalette

    private val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
    ) { /* пользователь мог отказать — уведомления просто не покажутся */ }

    private val presenter: MainViewModel by viewModels {
        MainViewModel.Factory(
                App.get().Di().router,
                App.get().Di().authHolder,
                App.get().Di().linkHandler,
                App.get().Di().menuRepository,
                App.get().Di().qmsInteractor,
                App.get().Di().otherPreferencesHolder,
                App.get().Di().webClient
        )
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
        App.get().Di().dayNightHelper.setIsNight(DayNightHelper.isUiModeNight(resources.configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedUiPalette = mainPreferencesRepository.getUiPalette()
        setTheme(UiThemeStyles.mainNoActionBar(appliedUiPalette))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()
        // Иначе с adjustResize получается двойной учёт области IME (система сжимает окно + мы бы ещё паддингом).
        WindowCompat.setDecorFitsSystemWindows(window, true)

        if (intent != null) {
            checkWebView = intent.getBooleanExtra(ARG_CHECK_WEBVIEW, checkWebView)
        }
        if (checkWebView) {
            disposables.add(Single
                    .fromCallable { App.get().isWebViewFound(this) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { aBoolean ->
                        if (!aBoolean) {
                            startActivity(Intent(App.getContext(), WebVewNotFoundActivity::class.java))
                            finish()
                        }
                    })
        }

        presenter.setIsRestored(savedInstanceState != null)
        intent?.data?.also {
            presenter.setStartLink(it.toString())
        }

        bottomDrawer = BottomDrawer(
                this,
                binding,
                tabNavigator,
                App.get().Di().router,
                App.get().Di().menuRepository,
                App.get().Di().mainPreferencesHolder
        )
        bottomDrawer.setListener(object : BottomDrawer.DrawerListener {
            @Suppress("DEPRECATION")
            override fun onHide() {
                hideKeyboard()
                cancelStartAnimation()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = 0
                }
            }

            override fun onShow() {
                cancelStartAnimation()
            }

            @Suppress("DEPRECATION")
            override fun onSlide(slideOffset: Float) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window.navigationBarDividerColor == 0) {
                    window.navigationBarDividerColor = App.getColorFromAttr(this@MainActivity, R.attr.divider_line_bottom_nav)
                }
                val container = binding.fragmentsContainer
                val translate = -slideOffset * 0.1f * container.height
                container.translationY = translate
                cancelStartAnimation()
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(LOG_TAG, "onBackPressed")
                if (bottomDrawer.isShown()) {
                    bottomDrawer.hide()
                } else {
                    backHandler(false)
                }
            }
        })

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

        disposables.add(
                dimensionsProvider
                        .observeDimensions()
                        .subscribe { dimensions ->
                            binding.bottomMenuRecycler.post {
                                binding.fragmentsContainer.also { updateDimens(dimensions) }
                            }
                        }
        )

        if (notificationPreferencesRepository.getUpdateEnabled()) {
            updateChecker.checkUpdate()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (notificationPreferencesRepository.wantsPushNotifications() &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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

    private fun updateDimens(dimensions: DimensionHelper.Dimensions) {
        binding.fragmentsContainer.apply {
            // Только при реальном IME: adjustResize уже сжимает окно — не дублируем отступом.
            // isFakeKeyboardShow (BBCode/нижний sheet без IME) не обнуляем: иначе панель +/скрепка
            // оказывается под нижней навигацией приложения.
            //
            // Высота «полоски» вкладок = базовая (иконки) + navigationBars — синхронно с peek bottom sheet
            // (см. BottomDrawer). Раньше br.height без nav на части OEM давал контент под системной панелью
            // или лишний зазор при двойном учёте insets.
            val baseBar = resources.getDimensionPixelSize(R.dimen.dp52)
            val pb = if (dimensions.isKeyboardShow()) {
                0
            } else {
                baseBar + dimensions.navigationBar
            }
            setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    max(pb, 0)
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(LOG_TAG, "onNewIntent " + intent.toString())
        checkIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        bottomDrawer.onStart()
        // UI поток: стартуем сервис, а bind пусть делает сам сервис при необходимости.
        // Это же снижает риск ANR на некоторых устройствах при старте/возврате в приложение.
        NotificationsService.startAndCheckNoBind()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        Log.d(LOG_TAG, "onResumeFragments")
        App.get().Di().navigatorHolder.setNavigator(tabNavigator)
    }

    override fun onResume() {
        super.onResume()
        Log.d(LOG_TAG, "onResume")
        val paletteNow = mainPreferencesRepository.getUiPalette()
        if (::appliedUiPalette.isInitialized && paletteNow != appliedUiPalette) {
            appliedUiPalette = paletteNow
            recreate()
            return
        }
        presenter.onActivityResume()
        if (lang == null) {
            lang = LocaleHelper.getLanguage(this)
        }
        if (false && LocaleHelper.getLanguage(this) != lang) {
            val newContext = LocaleHelper.onAttach(this)
            AlertDialog.Builder(this)
                    .setMessage(newContext.getString(R.string.lang_changed))
                    .setPositiveButton(newContext.getString(R.string.ok)) { _, _ -> MainActivity.restartApplication(this@MainActivity) }
                    .setNegativeButton(newContext.getString(R.string.cancel), null)
                    .show()
        }
    }

    override fun onPause() {
        App.get().Di().navigatorHolder.removeNavigator()
        super.onPause()
        Log.d(LOG_TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        bottomDrawer.onStop()
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, "onDestroy")
        presenter.detachView()
        super.onDestroy()
        disposables.dispose()
        bottomDrawer.destroy()
        updateChecker.destroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        App.get().onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun checkIntent(intent: Intent?) {
        if (intent == null || intent.data == null) {
            return
        }
        val url: String = intent.data?.toString().orEmpty()

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

    private fun backHandler(fromToolbar: Boolean) {
        val active = tabNavigator.getCurrentFragment()
        if (active == null) {
            App.get().Di().router.exit()
            return
        }
        if (fromToolbar || !active.onBackPressed()) {
            hideKeyboard()
            tabNavigator.close(active.tag)
        }
    }

    companion object {
        val LOG_TAG = MainActivity::class.java.simpleName
        val DEF_TITLE = "ForPDA"
        val ARG_CHECK_WEBVIEW = "CHECK_WEBVIEW"

        fun restartApplication(activity: Activity) {
            val mStartActivity = Intent(activity, MainActivity::class.java)
            val mPendingIntentId = 123456
            val piFlags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val mPendingIntent = PendingIntent.getActivity(activity, mPendingIntentId, mStartActivity, piFlags)
            val mgr = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent)
            activity.finish()
            System.exit(0)
        }

        fun setLightStatusBar(activity: Activity, value: Boolean) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = value
        }

        fun getDefaultLightStatusBar(context: Activity): Boolean {
            val typedValue = TypedValue()
            // В проде нельзя падать из-за отсутствующего атрибута темы.
            // Безопасный дефолт: false (тёмные иконки статус-бара выключены).
            if (!context.theme.resolveAttribute(R.attr.is_use_light_status_bar, typedValue, true)) {
                return false
            }
            return when (typedValue.type) {
                TypedValue.TYPE_INT_BOOLEAN -> typedValue.data != 0
                // Иногда OEM/темы кладут int-ресурс; трактуем как boolean по "0/не 0".
                TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> typedValue.data != 0
                else -> false
            }
        }
    }
}
