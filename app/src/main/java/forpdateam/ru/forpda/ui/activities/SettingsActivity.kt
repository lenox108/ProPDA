package forpdateam.ru.forpda.ui.activities

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.preference.PreferenceFragmentCompat
import android.view.MenuItem
import android.view.Menu
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.LocaleHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.ui.EdgeToEdge
import forpdateam.ru.forpda.ui.UiThemeStyles
import forpdateam.ru.forpda.ui.fragments.settings.NotificationsSettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.SettingsFragment
import forpdateam.ru.forpda.ui.fragments.settings.BaseSettingFragment
/**
 * Created by radiationx on 25.12.16.
 */

class SettingsActivity : AppCompatActivity() {
    private var searchQuery: String? = null

    private lateinit var appliedUiPalette: Preferences.Main.UiPalette

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedUiPalette = App.get().Di().mainPreferencesHolder.getUiPalette()
        setTheme(UiThemeStyles.settingsPreferenceScreen(appliedUiPalette))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        EdgeToEdge.apply(this, findViewById(R.id.fragment_content), padTop = true, padBottom = false)

        supportActionBar?.apply {
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            setTitle(R.string.activity_title_settings)
            elevation = 0f
        }


        val fragment: PreferenceFragmentCompat = if (intent?.getStringExtra(ARG_NEW_PREFERENCE_SCREEN) == NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME) {
            NotificationsSettingsFragment()
        } else {
            SettingsFragment()
        }

        supportFragmentManager.beginTransaction().replace(R.id.fragment_content, fragment).commit()
    }


    override fun onResume() {
        super.onResume()
        val paletteNow = App.get().Di().mainPreferencesHolder.getUiPalette()
        if (::appliedUiPalette.isInitialized && paletteNow != appliedUiPalette) {
            appliedUiPalette = paletteNow
            recreate()
            return
        }
        updateStatusBar()
    }

    private fun updateStatusBar() {
        val defaultSb = MainActivity.getDefaultLightStatusBar(this)
        MainActivity.setLightStatusBar(this, defaultSb)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home)
            finish()
        return true
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
        App.get().onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        const val ARG_NEW_PREFERENCE_SCREEN = "new_preference_screen"
    }
}
