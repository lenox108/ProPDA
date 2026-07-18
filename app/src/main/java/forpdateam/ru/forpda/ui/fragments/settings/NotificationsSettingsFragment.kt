package forpdateam.ru.forpda.ui.fragments.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.net.Uri
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
        configureBatteryOptimizationLink()
    }

    /**
     * Исключение из Doze/оптимизации батареи — главный рычаг надёжной доставки, когда
     * приложение закрыто: без него система откладывает периодическую проверку на часы.
     */
    private fun configureBatteryOptimizationLink() {
        val preference = preferenceScreen.findPreference<Preference>("notifications.bg.battery_optimization")
                ?: return
        preference.setOnPreferenceClickListener {
            requestIgnoreBatteryOptimizations()
            true
        }
        updateBatteryOptimizationSummary()
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun updateBatteryOptimizationSummary() {
        val context = context ?: return
        val preference = preferenceScreen.findPreference<Preference>("notifications.bg.battery_optimization")
                ?: return
        val exempt = isIgnoringBatteryOptimizations(context)
        preference.summary = getString(
                if (exempt) R.string.pref_summary_battery_optimization_granted
                else R.string.pref_summary_battery_optimization_needed
        )
        // Когда исключение уже выдано — оставляем пункт информативным, но не ведём в никуда.
        preference.isEnabled = !exempt
    }

    /**
     * Сначала пытаемся показать прямой системный диалог по имени пакета; если конкретная
     * прошивка его не поддерживает (ActivityNotFound) — открываем общий список приложений.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val context = context ?: return
        if (isIgnoringBatteryOptimizations(context)) {
            updateBatteryOptimizationSummary()
            return
        }
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
        try {
            startActivity(direct)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ignored: Exception) {
                // Ни один экран недоступен — оставляем пользователя без действия, summary не меняем.
            }
        }
    }

    /**
     * Звук, вибрация и индикатор — свойства канала уведомлений, приложение их не контролирует.
     * Пользователь настраивает их в системных экранах каналов, ссылки на которые ниже.
     */
    private fun configureVersionAwareUi() {
        preferenceScreen.findPreference<PreferenceCategory>("notifications.system.category")?.isVisible = true
        updateVersionAwareUi()
    }

    private fun configureSystemSettingsLinks() {
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
        updateBatteryOptimizationSummary()
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
    }
}
