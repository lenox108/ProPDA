package forpdateam.ru.forpda.ui.fragments.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.ui.activities.SettingsActivity
import io.reactivex.disposables.Disposable

/**
 * Created by radiationx on 25.12.16.
 */

class SettingsFragment : BaseSettingFragment() {
    private val authRepository = App.get().Di().authRepository
    private val authHolder = App.get().Di().authHolder
    private val mainPreferencesHolder = App.get().Di().mainPreferencesHolder
    private var disposable: Disposable? = null
    private val prefs by lazy { App.get().Di().preferences }
    private val quickBindings = mutableListOf<Pair<String, forpdateam.ru.forpda.ui.views.SwitchPreference>>()

    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        quickBindings.firstOrNull { it.first == key }?.also { (k, pref) ->
            pref.isChecked = prefs.getBoolean(k, pref.isChecked)
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)

        addQuickSection()

        if (authHolder.get().isAuth()) {
            findPreference<Preference>("auth.action.logout")?.apply {
                setOnPreferenceClickListener {
                    AlertDialog.Builder(activity!!)
                            .setMessage(R.string.ask_logout)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                logoutRequest()
                            }
                            .setNegativeButton(R.string.no, null)
                            .show()
                    false
                }
            }
        } else {
            findPreference<Preference>("auth.action.logout")?.apply {
                isEnabled = false
            }
        }

        findPreference<Preference>("clear_menu_sequence")?.apply {
            setOnPreferenceClickListener {
                AlertDialog.Builder(activity!!)
                        .setMessage("Подтвердите действие")
                        .setPositiveButton(R.string.ok) { _, _ ->
                            App.get().Di().preferences.edit().remove("menu_items_sequence").apply()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                false
            }
        }

        findPreference<Preference>("about.application")?.apply {
            summary = String.format(getString(R.string.version_Build), BuildConfig.VERSION_NAME)
        }

        // Форк: скрываем/убираем встроенную проверку обновлений
        findPreference<Preference>("about.check_update")?.apply {
            isVisible = false
        }

        findPreference<Preference>(Preferences.Main.WEBVIEW_FONT_SIZE)?.apply {
            setOnPreferenceClickListener { _ ->

                val dialogView = activity!!.layoutInflater.inflate(R.layout.dialog_font_size, null)!!
                val seekBar = dialogView.findViewById<View>(R.id.value_seekbar) as SeekBar
                val textView = dialogView.findViewById<View>(R.id.value_textview) as TextView

                seekBar.progress = mainPreferencesHolder.getWebViewFontSize() - 1 - 7

                textView.text = (seekBar.progress + 1 + 7).toString()
                textView.textSize = (seekBar.progress + 1 + 7).toFloat()

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                        textView.text = (i + 1 + 7).toString()
                        textView.textSize = (i + 1 + 7).toFloat()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
                AlertDialog.Builder(activity!!)
                        .setTitle(R.string.text_size)
                        .setView(dialogView)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            mainPreferencesHolder.setWebViewFontSize(seekBar.progress + 1 + 7)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.reset, null)
                        .show()
                        .getButton(DialogInterface.BUTTON_NEUTRAL)
                        .setOnClickListener {
                            seekBar.progress = 16 - 1 - 7
                            mainPreferencesHolder.setWebViewFontSize(16)
                        }

                false
            }
        }

        findPreference<Preference>("open_notifications")?.apply {
            setOnPreferenceClickListener {
                val intent = Intent(activity, SettingsActivity::class.java)
                intent.putExtra(SettingsActivity.ARG_NEW_PREFERENCE_SCREEN, NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME)
                startActivity(intent)
                true
            }
        }

        findPreference<ListPreference>(Preferences.Main.UI_PALETTE)?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            true
        }
    }

    private fun logoutRequest() {
        disposable?.dispose()
        disposable = authRepository
                .signOut()
                .subscribe({
                    if (it) {
                        Toast.makeText(App.getContext(), "Logout complete", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(App.getContext(), "Logout error", Toast.LENGTH_LONG).show()
                    }
                }, {
                    Toast.makeText(App.getContext(), "Logout error: $it", Toast.LENGTH_LONG).show()
                })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.dispose()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun addQuickSection() {
        val ctx = preferenceManager.context
        val quickCategory = findPreference<PreferenceCategory>("quick_settings_category") ?: return

        fun addMirrorSwitch(targetKey: String, titleRes: Int, summaryRes: Int?) {
            val sp = forpdateam.ru.forpda.ui.views.SwitchPreference(ctx).apply {
                title = getString(titleRes)
                if (summaryRes != null) summary = getString(summaryRes)
                // Без key: не создаём дубликаты Preferences в дереве, синхроним вручную
                key = null
                isPersistent = false
                isChecked = prefs.getBoolean(targetKey, false)
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.edit().putBoolean(targetKey, (newValue as? Boolean) == true).apply()
                    true
                }
            }
            quickBindings.add(targetKey to sp)
            quickCategory.addPreference(sp)
        }

        // Быстрые тумблеры (самые частые)
        addMirrorSwitch(
            targetKey = "main.is_system_downloader",
            titleRes = R.string.pref_title_system_downloader,
            summaryRes = R.string.pref_summary_system_downloader
        )
        addMirrorSwitch(
            targetKey = "main.show_bottom_arrow",
            titleRes = R.string.pref_title_show_bottom_arrow,
            summaryRes = R.string.pref_summary_show_bottom_arrow
        )
        addMirrorSwitch(
            targetKey = "message_panel.is_monospace",
            titleRes = R.string.pref_title_monospace,
            summaryRes = R.string.pref_summary_monospace
        )

        // Быстрые переходы (без дублирования ключей)
        Preference(ctx).apply {
            title = getString(R.string.pref_title_theme_mode)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                findPreference<ListPreference>("main.theme.mode")?.performClick()
                true
            }
        }.also { quickCategory.addPreference(it) }

        Preference(ctx).apply {
            title = getString(R.string.pref_title_ui_palette)
            summary = getString(R.string.pref_summary_ui_palette)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                findPreference<ListPreference>(Preferences.Main.UI_PALETTE)?.performClick()
                true
            }
        }.also { quickCategory.addPreference(it) }

        Preference(ctx).apply {
            title = getString(R.string.pref_title_text_size)
            summary = getString(R.string.pref_summary_text_size)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                findPreference<Preference>(Preferences.Main.WEBVIEW_FONT_SIZE)?.performClick()
                true
            }
        }.also { quickCategory.addPreference(it) }
    }
}
