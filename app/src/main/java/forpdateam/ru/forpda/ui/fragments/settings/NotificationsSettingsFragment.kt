package forpdateam.ru.forpda.ui.fragments.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.downloads.DownloadNotifications
import forpdateam.ru.forpda.notifications.NotificationsService
import forpdateam.ru.forpda.ui.activities.SettingsActivity

/**
 * Created by radiationx on 12.07.17.
 */

class NotificationsSettingsFragment : BaseSettingFragment() {

    private var permissionWarningPreference: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences_notifications)
        configureAndroidNotificationSettings()
        configureNestedNotificationDependencies()
        (activity as? SettingsActivity)?.supportActionBar?.title = preferenceScreen.title
    }

    override fun onResume() {
        super.onResume()
        updateVersionAwareUi()
    }

    private fun configureAndroidNotificationSettings() {
        configureVersionAwareUi()
        configureSystemSettingsLinks()
    }

    /**
     * Звук, вибрация и индикатор на Android 8+ — свойства канала уведомлений, приложение их
     * не контролирует. Там показываем ссылки на системные экраны каналов и прячем наши
     * тумблеры. До Oreo каналов нет: тумблеры работают (см. NotificationPublisher), а вот
     * ACTION_CHANNEL_NOTIFICATION_SETTINGS не существует — прячем всю системную категорию.
     */
    private fun configureVersionAwareUi() {
        val hasChannels = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        for (key in LEGACY_ALERT_PREFERENCE_KEYS) {
            preferenceScreen.findPreference<Preference>(key)?.isVisible = !hasChannels
        }
        preferenceScreen.findPreference<PreferenceCategory>("notifications.system.category")?.isVisible = hasChannels
        updateVersionAwareUi()
    }

    private fun configureSystemSettingsLinks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        preferenceScreen.findPreference<Preference>("notifications.system.app")?.setOnPreferenceClickListener {
            openAndroidNotificationSettings()
            true
        }
        configureChannelLink("notifications.system.channel.qms", NotificationsService.CHANNEL_QMS_ID)
        configureChannelLink("notifications.system.channel.mentions", NotificationsService.CHANNEL_MENTION_ID)
        configureChannelLink("notifications.system.channel.favorites", NotificationsService.CHANNEL_FAV_ID)
        configureChannelLink("notifications.system.channel.downloads", DownloadNotifications.CHANNEL_ID)
    }

    private fun configureNestedNotificationDependencies() {
        val mainHandler = Handler(Looper.getMainLooper())
        val mainPreference = preferenceScreen.findPreference<Preference>("notifications.main.enabled")
        val favPreference = preferenceScreen.findPreference<Preference>("notifications.fav.enabled")
        val refresh = {
            val mainEnabled = mainPreference?.sharedPreferences?.getBoolean("notifications.main.enabled", true) ?: true
            val favEnabled = favPreference?.sharedPreferences?.getBoolean("notifications.fav.enabled", true) ?: true
            preferenceScreen.findPreference<Preference>("notifications.fav.only_important")?.isEnabled =
                    mainEnabled && favEnabled
            preferenceScreen.findPreference<Preference>("notifications.fav.live_tab")?.isEnabled =
                    mainEnabled && favEnabled
        }
        mainPreference?.onPreferenceChangeListener = OnPreferenceChangeListener { _, _ ->
            mainHandler.post(refresh)
            true
        }
        favPreference?.onPreferenceChangeListener = OnPreferenceChangeListener { _, _ ->
            mainHandler.post(refresh)
            true
        }
        refresh()
    }

    private fun configureChannelLink(key: String, channelId: String) {
        val preference = preferenceScreen.findPreference<Preference>(key) ?: return
        preference.isVisible = true
        preference.summary = getString(R.string.pref_summary_channel_settings)
        preference.setOnPreferenceClickListener {
            openAndroidChannelSettings(channelId)
            true
        }
    }

    private fun updateVersionAwareUi() {
        updateSystemNotificationStatus()
        updatePermissionWarning()
    }

    private fun updatePermissionWarning() {
        val context = context ?: return
        val shouldShowWarning = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (!shouldShowWarning) {
            permissionWarningPreference?.let(preferenceScreen::removePreference)
            permissionWarningPreference = null
            return
        }
        if (permissionWarningPreference != null) return

        permissionWarningPreference = Preference(context).apply {
            layoutResource = R.layout.preference_custom
            title = getString(R.string.pref_title_post_notifications_denied)
            summary = getString(R.string.pref_summary_post_notifications_denied)
            setOnPreferenceClickListener {
                openAndroidNotificationSettings()
                true
            }
            setIconSpaceReserved(false)
            order = -1
        }.also(preferenceScreen::addPreference)
    }

    private fun updateSystemNotificationStatus() {
        val context = context ?: return
        val appPreference = preferenceScreen.findPreference<Preference>("notifications.system.app") ?: return
        appPreference.summary = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED -> getString(R.string.pref_summary_post_notifications_denied)
            notificationsDisabledBySystem(context) -> getString(R.string.pref_summary_android_notifications_disabled)
            else -> getString(R.string.pref_summary_android_notification_settings)
        }
    }

    private fun notificationsDisabledBySystem(context: Context): Boolean {
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled().not()
    }

    private fun openAndroidNotificationSettings() {
        val context = context ?: return
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        startActivity(intent)
    }

    private fun openAndroidChannelSettings(channelId: String) {
        val context = context ?: return
        ensureChannels(context)
        startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channelId))
    }

    private fun ensureChannels(context: Context) {
        NotificationsService.createEventChannels(context)
        DownloadNotifications.ensureChannel(context)
    }

    companion object {
        const val PREFERENCE_SCREEN_NAME = "notifications"

        /** Применимы только до Android 8; выше ими владеет канал уведомлений. */
        private val LEGACY_ALERT_PREFERENCE_KEYS = listOf(
                "notifications.main.sound_enabled",
                "notifications.main.vibration_enabled",
                "notifications.main.indicator_enabled"
        )
    }
}
