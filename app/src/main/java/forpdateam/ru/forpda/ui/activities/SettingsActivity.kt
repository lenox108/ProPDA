package forpdateam.ru.forpda.ui.activities

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.preference.PreferenceFragmentCompat
import android.view.MenuItem
import android.view.Menu
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.ui.EdgeToEdge
import forpdateam.ru.forpda.ui.FontController
import forpdateam.ru.forpda.ui.FlatUi
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.UiThemeStyles
import forpdateam.ru.forpda.ui.AccentApplier
import forpdateam.ru.forpda.ui.ContrastApplier
import forpdateam.ru.forpda.ui.MaterialYouApplier
import forpdateam.ru.forpda.ui.fragments.settings.BaseSettingFragment
import forpdateam.ru.forpda.ui.fragments.settings.ForumSettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.NotificationsSettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.ProxySettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.RecentSettings
import forpdateam.ru.forpda.ui.fragments.settings.SettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.SettingsSearchIndex
import forpdateam.ru.forpda.ui.fragments.settings.SettingsSection
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.datastore.MainDataStore
import forpdateam.ru.forpda.common.PermissionHelper
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
/**
 * Created by radiationx on 25.12.16.
 */

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    @Inject lateinit var mainPreferencesHolder: MainPreferencesHolder
    @Inject lateinit var permissionHelper: PermissionHelper

    private var searchQuery: String? = null
    private var searchMenuItem: MenuItem? = null

    private lateinit var appliedUiPalette: Preferences.Main.UiPalette
    private lateinit var appliedFontMode: forpdateam.ru.forpda.ui.AppFontMode
    private var appliedMaterialYou: Boolean = false
    private var appliedFlatUi: Boolean = false
    private lateinit var appliedAccent: Preferences.Main.AccentPalette
    private var appliedAccentStyle: Preferences.Main.AccentStyle = Preferences.Main.AccentStyle.TONAL

    /**
     * Любое изменение настройки → в «Недавно изменённые» на корневом экране. Слушаем здесь, а не
     * во фрагменте: так попадают правки со всех экранов настроек, включая уведомления и прокси.
     * Фильтр по индексу отсекает служебные ключи (состояние UI, внутренние флаги) — в блоке
     * должны быть только настоящие настройки.
     */
    private val recentSettingsListener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                val settingKey = key ?: return@OnSharedPreferenceChangeListener
                if (SettingsSearchIndex.find(this, settingKey) != null) {
                    RecentSettings.record(this, settingKey)
                }
            }

    override fun attachBaseContext(base: Context) {
        val localizedContext = LocaleHelper.onAttach(base)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Get theme settings directly from SharedPreferences mirror before super.onCreate() (DI not available yet)
        // Using getThemeModeImmediate() and getUiPaletteImmediate() for synchronous read without blocking UI
        val tempDataStore = MainDataStore(this)
        appliedUiPalette = try {
            tempDataStore.getUiPaletteImmediate()
        } catch (e: Exception) {
            Preferences.Main.UiPalette.SYSTEM
        }
        val themeMode = try {
            tempDataStore.getThemeModeImmediate()
        } catch (e: Exception) {
            Preferences.Main.ThemeMode.SYSTEM
        }
        appliedFontMode = tempDataStore.getAppFontModeImmediate()
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
        setTheme(UiThemeStyles.settingsPreferenceScreen(appliedUiPalette, themeMode, resources.configuration))
        FontController.applyNativeTheme(this, appliedFontMode)
        // Material You (Dynamic Color) must be layered on top of the just-set theme
        // (setTheme wipes any overlay applied earlier by the global applier). The
        // per-Activity applier is the canonical entry point — see MaterialYouApplier KDoc.
        MaterialYouApplier.applyIfEnabled(this)
        AccentApplier.applyIfEnabled(this)
        appliedFlatUi = FlatUi.applyThemeOverlay(this)
        // Последний слой: усиление контраста по системной настройке (a11y, Android 14+).
        ContrastApplier.applyIfAvailable(this)
        super.onCreate(savedInstanceState)
        // Заголовки в индексе поиска — уже готовые строки: после смены языка/темы их надо пересобрать.
        SettingsSearchIndex.invalidate()
        val barColor = chromeCanvasColor(R.attr.main_toolbar_accent_surface)
        setContentView(R.layout.activity_settings)
        // activity_settings корень (settings_root) в XML держит статический
        // colorSurfaceContainerLowest — под Material You перекрашиваем его в полотно
        // обоев (ChromeCanvas), вне MY fallback = тот же Lowest.
        val canvas = chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest)
        findViewById<View>(R.id.settings_root)?.setBackgroundColor(canvas)
        findViewById<View>(R.id.fragment_content)?.setBackgroundColor(canvas)
        EdgeToEdge.apply(
                this,
                findViewById(R.id.settings_root),
                padTop = true,
                padBottom = false,
                topUnderlayColor = barColor,
                topUnderlayTag = STATUS_BAR_UNDERLAY_TAG
        )
        syncTopBarSystemBars(barColor)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            setTitle(R.string.activity_title_settings)
            setBackgroundDrawable(ColorDrawable(barColor))
            elevation = 0f
        }

        setupRootSearchBar()

        if (savedInstanceState == null) {
            val highlightKey = intent?.getStringExtra(ARG_HIGHLIGHT_KEY)
            val fragment: PreferenceFragmentCompat = when (intent?.getStringExtra(ARG_NEW_PREFERENCE_SCREEN)) {
                NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME -> NotificationsSettingsFragment()
                ForumSettingsFragment.PREFERENCE_SCREEN_NAME -> ForumSettingsFragment()
                ProxySettingsFragment.PREFERENCE_SCREEN_NAME -> ProxySettingsFragment()
                else -> SettingsFragment.newInstance(SettingsSection.ROOT)
            }
            if (highlightKey != null && fragment.arguments?.containsKey(BaseSettingFragment.ARG_HIGHLIGHT_KEY) != true) {
                fragment.arguments = (fragment.arguments ?: Bundle()).apply {
                    putString(BaseSettingFragment.ARG_HIGHLIGHT_KEY, highlightKey)
                }
            }
            supportFragmentManager.beginTransaction().replace(R.id.fragment_content, fragment).commit()
        }

        // Экран меняется без пересоздания активити (вложенные разделы) — хром обновляем по бэкстеку.
        supportFragmentManager.addOnBackStackChangedListener { onSettingsScreenChanged() }
    }

    override fun onResume() {
        super.onResume()
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(recentSettingsListener)
        val paletteNow = mainPreferencesHolder.getUiPalette()
        val fontModeNow = FontController.getCurrentFontMode(mainPreferencesHolder)
        if (::appliedUiPalette.isInitialized && paletteNow != appliedUiPalette) {
            appliedUiPalette = paletteNow
            recreate()
            return
        }
        if (::appliedFontMode.isInitialized && fontModeNow != appliedFontMode) {
            appliedFontMode = fontModeNow
            recreate()
            return
        }
        val materialYouNow = mainPreferencesHolder.getUseMaterialYou()
        if (materialYouNow != appliedMaterialYou) {
            appliedMaterialYou = materialYouNow
            recreate()
            return
        }
        val flatUiNow = FlatUi.isEnabled(this)
        if (flatUiNow != appliedFlatUi) {
            appliedFlatUi = flatUiNow
            recreate()
            return
        }
        val accentNow = mainPreferencesHolder.getAccentPalette()
        if (::appliedAccent.isInitialized && accentNow != appliedAccent) {
            appliedAccent = accentNow
            recreate()
            return
        }
        val accentStyleNow = mainPreferencesHolder.getAccentStyle()
        if (accentStyleNow != appliedAccentStyle) {
            appliedAccentStyle = accentStyleNow
            recreate()
            return
        }
        val barColor = chromeCanvasColor(R.attr.main_toolbar_accent_surface)
        syncTopBarSystemBars(barColor)
    }

    override fun onPause() {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(recentSettingsListener)
        super.onPause()
    }

    private fun syncTopBarSystemBars(barColor: Int) {
        SystemBarAppearance.syncStatusBar(this, barColor)
        SystemBarAppearance.syncNavigationBar(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
                return true
            }
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        val item = menu.findItem(R.id.action_search)
        searchMenuItem = item
        val sv = item.actionView as? SearchView
        sv?.queryHint = getString(R.string.search)
        sv?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                (supportFragmentManager.findFragmentById(R.id.fragment_content) as? BaseSettingFragment)
                        ?.applySearchQuery(newText)
                return true
            }
        })
        item.isVisible = !isRootScreen()
        return true
    }

    // region Поиск на корневом экране

    /**
     * Постоянная строка поиска корневого экрана: ищет сразу по всем разделам (см.
     * SettingsSearchIndex). На вложенных экранах она скрыта — там остаётся лупа в тулбаре,
     * которая фильтрует открытый список.
     */
    private fun setupRootSearchBar() {
        findViewById<EditText>(R.id.settingsSearchInput)?.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            searchQuery = query
            findViewById<View>(R.id.settingsSearchClear)?.visibility =
                    if (query.isEmpty()) View.GONE else View.VISIBLE
            rootFragment()?.applyGlobalSearch(query)
        }
        findViewById<View>(R.id.settingsSearchClear)?.setOnClickListener {
            findViewById<EditText>(R.id.settingsSearchInput)?.setText("")
        }
    }

    /** Вызывается экранами настроек: показать/скрыть строку поиска и лупу под текущий экран. */
    fun onSettingsScreenChanged() {
        val root = isRootScreen()
        findViewById<View>(R.id.settings_search_container)?.visibility =
                if (root) View.VISIBLE else View.GONE
        searchMenuItem?.isVisible = !root
        if (root) {
            // Вернулись на корень с непустым запросом — выдача должна остаться прежней.
            searchQuery?.takeIf { it.isNotBlank() }?.let { rootFragment()?.applyGlobalSearch(it) }
        } else {
            hideKeyboard()
        }
    }

    private fun rootFragment(): SettingsFragment? =
            (supportFragmentManager.findFragmentById(R.id.fragment_content) as? SettingsFragment)
                    ?.takeIf { it.section == SettingsSection.ROOT }

    private fun isRootScreen(): Boolean = rootFragment() != null

    private fun hideKeyboard() {
        val input = findViewById<EditText>(R.id.settingsSearchInput) ?: return
        input.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    // endregion

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        const val ARG_NEW_PREFERENCE_SCREEN = "new_preference_screen"

        /** Ключ настройки, к которой надо прокрутить и подсветить (переход из поиска). */
        const val ARG_HIGHLIGHT_KEY = "highlight_key"

        private const val STATUS_BAR_UNDERLAY_TAG = "settings_status_bar_underlay"
    }
}
