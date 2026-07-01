package forpdateam.ru.forpda.ui.activities

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.preference.PreferenceFragmentCompat
import android.view.MenuItem
import android.view.Menu
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.EdgeToEdge
import forpdateam.ru.forpda.ui.FontController
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.UiThemeStyles
import forpdateam.ru.forpda.ui.AccentApplier
import forpdateam.ru.forpda.ui.MaterialYouApplier
import forpdateam.ru.forpda.ui.fragments.settings.NotificationsSettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.ForumSettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.SettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.BaseSettingFragment
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

    private lateinit var appliedUiPalette: Preferences.Main.UiPalette
    private lateinit var appliedFontMode: forpdateam.ru.forpda.ui.AppFontMode
    private var appliedMaterialYou: Boolean = false
    private lateinit var appliedAccent: Preferences.Main.AccentPalette
    private var appliedAccentVibrant: Boolean = false

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
        appliedAccentVibrant = try {
            tempDataStore.getAccentVibrantImmediate()
        } catch (e: Exception) {
            false
        }
        setTheme(UiThemeStyles.settingsPreferenceScreen(appliedUiPalette, themeMode, resources.configuration))
        FontController.applyNativeTheme(this, appliedFontMode)
        // Material You (Dynamic Color) must be layered on top of the just-set theme
        // (setTheme wipes any overlay applied earlier by the global applier). The
        // per-Activity applier is the canonical entry point — see MaterialYouApplier KDoc.
        MaterialYouApplier.applyIfEnabled(this)
        AccentApplier.applyIfEnabled(this)
        super.onCreate(savedInstanceState)
        val barColor = getColorFromAttr(R.attr.main_toolbar_accent_surface)
        setContentView(R.layout.activity_settings)
        EdgeToEdge.apply(
                this,
                findViewById(R.id.fragment_content),
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


        val fragment: PreferenceFragmentCompat = when (intent?.getStringExtra(ARG_NEW_PREFERENCE_SCREEN)) {
            NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME -> NotificationsSettingsFragment()
            ForumSettingsFragment.PREFERENCE_SCREEN_NAME -> ForumSettingsFragment()
            else -> SettingsFragment()
        }

        supportFragmentManager.beginTransaction().replace(R.id.fragment_content, fragment).commit()
    }


    override fun onResume() {
        super.onResume()
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
        val accentNow = mainPreferencesHolder.getAccentPalette()
        if (::appliedAccent.isInitialized && accentNow != appliedAccent) {
            appliedAccent = accentNow
            recreate()
            return
        }
        val vibrantNow = mainPreferencesHolder.getAccentVibrant()
        if (vibrantNow != appliedAccentVibrant) {
            appliedAccentVibrant = vibrantNow
            recreate()
            return
        }
        val barColor = getColorFromAttr(R.attr.main_toolbar_accent_surface)
        syncTopBarSystemBars(barColor)
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
        val sv = item.actionView as? SearchView
        sv?.queryHint = getString(R.string.search)
        sv?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                searchQuery = newText
                (supportFragmentManager.findFragmentById(R.id.fragment_content) as? BaseSettingFragment)
                        ?.applySearchQuery(newText)
                return true
            }
        })
        // Если вернулись в активити с уже набранным запросом
        searchQuery?.takeIf { it.isNotBlank() }?.also {
            item.expandActionView()
            sv?.setQuery(it, false)
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        const val ARG_NEW_PREFERENCE_SCREEN = "new_preference_screen"
        private const val STATUS_BAR_UNDERLAY_TAG = "settings_status_bar_underlay"
    }
}
