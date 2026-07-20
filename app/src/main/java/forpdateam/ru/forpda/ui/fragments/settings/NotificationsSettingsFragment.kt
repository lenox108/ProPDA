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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

    private val realtimeStatusHandler = Handler(Looper.getMainLooper())
    private val realtimeStatusTick = object : Runnable {
        override fun run() {
            updateDiagnosticsSummary()
            // Живое обновление, пока экран открыт: статус «Подключается» → «Недоступен» иначе
            // не переключился бы, ведь строка — снимок. Лёгкое: только чтение volatile + summary.
            realtimeStatusHandler.postDelayed(this, REALTIME_STATUS_REFRESH_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        updateVersionAwareUi()
        realtimeStatusHandler.postDelayed(realtimeStatusTick, REALTIME_STATUS_REFRESH_MS)
    }

    override fun onPause() {
        super.onPause()
        realtimeStatusHandler.removeCallbacks(realtimeStatusTick)
    }

    private fun configureAndroidNotificationSettings() {
        configureVersionAwareUi()
        configureSystemSettingsLinks()
        configureBatteryOptimizationLink()
        configureOemAutostartLink()
        configureBgCheckDiagnostics()
    }

    /**
     * Вендорский экран «Автозапуска» (MIUI/EMUI/ColorOS/...) — отдельный от battery-exemption
     * рубильник, без которого система не будит приложение в фоне. Пункт виден только на
     * прошивках, где такой экран есть.
     */
    private fun configureOemAutostartLink() {
        val preference = preferenceScreen.findPreference<Preference>("notifications.bg.oem_autostart") ?: return
        val context = context
        val available = context != null &&
                forpdateam.ru.forpda.notifications.OemAutostartHelper.isAvailable(context)
        preference.isVisible = available
        if (!available) return
        preference.setOnPreferenceClickListener {
            this.context?.let { ctx -> forpdateam.ru.forpda.notifications.OemAutostartHelper.open(ctx) }
            true
        }
    }

    /**
     * Объединённая «Диагностика уведомлений»: статус мгновенного канала + проверка сети канала
     * + журнал фоновых проверок ([forpdateam.ru.forpda.notifications.NotifDiagLog]) — всё в одном
     * диалоге, работает и в release. Раньше это были два отдельных пункта.
     */
    private fun configureBgCheckDiagnostics() {
        preferenceScreen.findPreference<Preference>("notifications.bg.diagnostics")?.setOnPreferenceClickListener {
            // Страховка на гонку: тик гасит пункт раз в 3 с, но между выключением уведомлений и
            // тиком тап ещё проходит — сетевую пробу при выключенных уведомлениях не запускаем.
            if (notificationsActive()) showDiagnosticsDialog()
            true
        }
    }

    private fun showDiagnosticsDialog() {
        val context = context ?: return
        val density = resources.displayMetrics.density
        val pad = (density * 16).toInt()

        // Строка 1: статус мгновенного канала (тот же текст, что был в отдельном пункте).
        val statusText = android.widget.TextView(context).apply {
            text = getString(R.string.diagnostics_section_channel) + ": " +
                    (realtimeStatusText() ?: getString(R.string.realtime_status_connecting))
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        // Кнопка запуска сетевой пробы (DNS → TCP → ping/pong → вердикт).
        val checkButton = android.widget.Button(context).apply {
            text = getString(R.string.diagnostics_check_network)
        }
        val logLabel = android.widget.TextView(context).apply {
            text = getString(R.string.diagnostics_section_log)
            setPadding(pad, pad, pad, pad / 4)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val logText = android.widget.TextView(context).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(pad, 0, pad, pad / 2)
            text = forpdateam.ru.forpda.notifications.NotifDiagLog.read(context)
                    .ifBlank { getString(R.string.bg_check_diagnostics_empty) }
        }
        val logScroll = android.widget.ScrollView(context).apply {
            addView(logText)
            post { fullScroll(android.view.View.FOCUS_DOWN) }
        }
        val content = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(statusText)
            addView(checkButton, android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = pad; marginEnd = pad })
            addView(logLabel)
            // Журнал занимает оставшуюся высоту, но не выталкивает статус/кнопку за экран.
            addView(logScroll, android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        checkButton.setOnClickListener {
            checkButton.isEnabled = false
            statusText.text = getString(R.string.diagnostics_section_channel) + ": " +
                    getString(R.string.realtime_probe_running)
            runRealtimeChannelProbe { report ->
                val ctx = this.context ?: return@runRealtimeChannelProbe
                statusText.text = getString(R.string.diagnostics_section_channel) + ":\n" + report.verdict
                logText.text = forpdateam.ru.forpda.notifications.NotifDiagLog.read(ctx)
                        .ifBlank { getString(R.string.bg_check_diagnostics_empty) }
                logScroll.post { logScroll.fullScroll(android.view.View.FOCUS_DOWN) }
                checkButton.isEnabled = true
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_bg_check_diagnostics)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.bg_check_diagnostics_copy) { _, _ ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(
                            "notif_diag", forpdateam.ru.forpda.notifications.NotifDiagLog.read(context)))
                    android.widget.Toast.makeText(context, R.string.bg_check_diagnostics_copied, android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.bg_check_diagnostics_clear) { _, _ ->
                    forpdateam.ru.forpda.notifications.NotifDiagLog.clear(context)
                }
                .show()
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
        preference.summary = channelSummary(channelId)
        preference.setOnPreferenceClickListener {
            openAndroidChannelSettings(channelId)
            true
        }
    }

    /**
     * Выключенный в Android канал — тихая потеря: notify() на него молча ничего не показывает.
     * Показываем это прямо в строке канала, чтобы «не приходят уведомления» находилось глазами.
     */
    private fun channelSummary(channelId: String): String {
        val context = context ?: return getString(R.string.pref_summary_channel_settings)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(channelId)
        return if (channel != null && channel.importance == android.app.NotificationManager.IMPORTANCE_NONE) {
            getString(R.string.pref_summary_channel_disabled)
        } else {
            getString(R.string.pref_summary_channel_settings)
        }
    }

    private fun updateChannelSummaries() {
        preferenceScreen.findPreference<Preference>("notifications.system.channel.qms")
                ?.summary = channelSummary(NotificationsService.CHANNEL_QMS_ID)
        preferenceScreen.findPreference<Preference>("notifications.system.channel.mentions")
                ?.summary = channelSummary(NotificationsService.CHANNEL_MENTION_ID)
        preferenceScreen.findPreference<Preference>("notifications.system.channel.favorites")
                ?.summary = channelSummary(NotificationsService.CHANNEL_FAV_ID)
        preferenceScreen.findPreference<Preference>("notifications.system.channel.downloads")
                ?.summary = channelSummary(DownloadNotifications.CHANNEL_ID)
    }

    private fun updateVersionAwareUi() {
        updateSystemNotificationStatus()
        updatePermissionWarning()
        updateBatteryOptimizationSummary()
        updateBgStalledWarning()
        updateChannelSummaries()
        updateDiagnosticsSummary()
    }

    /**
     * Честный статус «мгновенного канала» (WebSocket) для summary объединённого пункта
     * «Диагностика уведомлений». Полевые кейсы: у части пользователей сеть/VPN режет
     * ws-соединение — уведомления при этом ДОХОДЯТ (резервный опрос), но с задержкой, и люди
     * считают «не работает». Приложение объясняет это само. null — репозиторий недоступен.
     */
    private fun realtimeStatusText(): String? {
        val repo = (activity?.application as? forpdateam.ru.forpda.App)?.eventsRepository ?: return null
        val sp = preferenceScreen.sharedPreferences
        val intervalMin = (sp?.getString("notifications.bg.interval_min", null)?.toLongOrNull() ?: 30L)
                .coerceAtLeast(15L)
        return when {
            repo.isWebSocketConnected() -> getString(R.string.realtime_status_connected)
            // isWsLikelyBlocked / isWsCoolingDown вместо только кулдауна: открытый экран настроек
            // держит приложение в foreground и сбрасывает кулдаун, поэтому «Недоступен» надо
            // определять и по счётчику неудач, иначе строка вечно висит «Подключается».
            repo.isWsLikelyBlocked() || repo.isWsCoolingDown() ->
                getString(R.string.realtime_status_blocked, intervalMin)
            else -> getString(R.string.realtime_status_connecting)
        }
    }

    /**
     * Уведомления включены (мастер-тумблер + хотя бы одно семейство push). Совпадает с
     * wantsPushNotifications() в коде доставки: именно при этом условии живёт WS/воркер.
     */
    private fun notificationsActive(): Boolean {
        val sp = preferenceScreen.sharedPreferences ?: return true
        if (!sp.getBoolean("notifications.main.enabled", true)) return false
        return sp.getBoolean("notifications.fav.enabled", true) ||
                sp.getBoolean("notifications.qms.enabled", true) ||
                sp.getBoolean("notifications.mentions.enabled", true) ||
                sp.getBoolean("notifications.hat.enabled", true)
    }

    /**
     * Живой статус канала выносим в summary пункта диагностики (обновляется тиком, пока экран
     * открыт). Когда уведомления выключены — пункт гасим и НЕ опрашиваем состояние WS: диагностике
     * нечего показывать (канал не поднят), а лишний опрос раз в 3 с — впустую жёг бы батарею.
     */
    private fun updateDiagnosticsSummary() {
        val preference = preferenceScreen.findPreference<Preference>("notifications.bg.diagnostics") ?: return
        if (!notificationsActive()) {
            preference.isEnabled = false
            preference.summary = getString(R.string.pref_summary_diagnostics_disabled)
            return
        }
        preference.isEnabled = true
        preference.summary = realtimeStatusText() ?: getString(R.string.pref_summary_bg_check_diagnostics)
    }

    private var probeInProgress = false

    /**
     * Сетевая проба мгновенного канала: DNS → TCP :993 → raw-WS ping/pong → вердикт. Результат
     * пишется в журнал построчно и отдаётся в [onDone] (на Main) — вызывающий диалог обновляет
     * свои поля. Повторный запуск, пока идёт проверка, игнорируется.
     */
    private fun runRealtimeChannelProbe(onDone: (forpdateam.ru.forpda.notifications.RealtimeChannelProbe.ProbeReport) -> Unit) {
        if (probeInProgress) return
        probeInProgress = true
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val report = runCatching { forpdateam.ru.forpda.notifications.RealtimeChannelProbe.run() }
                    .getOrElse {
                        forpdateam.ru.forpda.notifications.RealtimeChannelProbe.ProbeReport(
                                listOf("probe error: ${it.javaClass.simpleName}"), it.message ?: "Ошибка проверки")
                    }
            // В журнал — каждой строкой: следующий полевой лог принесёт вердикт вместе с историей.
            report.lines.forEach { forpdateam.ru.forpda.notifications.NotifDiagLog.log(appContext, "probe: $it") }
            forpdateam.ru.forpda.notifications.NotifDiagLog.log(appContext, "probe: verdict: ${report.verdict}")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                probeInProgress = false
                onDone(report)
            }
        }
    }

    private var bgStalledWarningPreference: Preference? = null

    /**
     * Самодиагностика фона: если запланированный воркер давно не запускался — система его
     * блокирует (Doze/OEM). Показываем предупреждение с переходом к исправлению, чтобы
     * пользователь не гадал, почему «не приходят уведомления».
     */
    private fun updateBgStalledWarning() {
        val context = context ?: return
        val sp = preferenceScreen.sharedPreferences ?: return
        val bgEnabled = sp.getBoolean("notifications.main.enabled", true) &&
                sp.getBoolean("notifications.bg.enabled", true)
        val intervalMin = (sp.getString("notifications.bg.interval_min", null)?.toLongOrNull() ?: 30L)
                .coerceAtLeast(15L)
        val lastRun = sp.getLong("notifications.bg.last_worker_run_at", 0L)
        val scheduledAt = sp.getLong("notifications.bg.scheduled_at", 0L)
        val referencePoint = maxOf(lastRun, scheduledAt)
        val now = System.currentTimeMillis()
        // Порог с запасом (3 интервала + 10 мин): легитимные задержки Doze — не повод пугать.
        val thresholdMs = intervalMin * 3 * 60_000L + 10 * 60_000L
        val stalled = bgEnabled && referencePoint > 0L && now - referencePoint > thresholdMs

        if (!stalled) {
            bgStalledWarningPreference?.let(preferenceScreen::removePreference)
            bgStalledWarningPreference = null
            return
        }
        val gapMin = (now - referencePoint) / 60_000L
        val summaryText = getString(R.string.pref_summary_bg_stalled_warning, gapMin, intervalMin)
        bgStalledWarningPreference?.let {
            it.summary = summaryText
            return
        }
        bgStalledWarningPreference = Preference(context).apply {
            layoutResource = R.layout.preference_custom
            title = getString(R.string.pref_title_bg_stalled_warning)
            summary = summaryText
            setOnPreferenceClickListener {
                // Сначала battery exemption; если есть вендорский автозапуск — он тоже виден рядом.
                requestIgnoreBatteryOptimizations()
                true
            }
            setIconSpaceReserved(false)
            order = -2
        }.also(preferenceScreen::addPreference)
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
        /** Период живого обновления статуса «Мгновенный канал», пока экран открыт. */
        private const val REALTIME_STATUS_REFRESH_MS = 3_000L
    }
}
