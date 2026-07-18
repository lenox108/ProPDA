package forpdateam.ru.forpda.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Вендорские экраны «Автозапуска» — отдельный от стандартного Android механизм, из-за
 * которого WorkManager/будильники не будят приложение, даже когда оно исключено из
 * оптимизации батареи. Стандартного интента нет — только известные имена компонентов
 * по прошивкам (MIUI/EMUI/ColorOS/Funtouch/OneUI и т.д.).
 */
object OemAutostartHelper {

    private val KNOWN_COMPONENTS = listOf(
            // Xiaomi / MIUI / HyperOS
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // Huawei / EMUI
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            // Oppo / ColorOS / Realme
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // Vivo / Funtouch
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            // OnePlus
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            // Samsung / OneUI (спящие приложения)
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            // Asus
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
            // Meizu
            ComponentName("com.meizu.safe", "com.meizu.safe.security.SHOW_APPSEC"),
    )

    /** Есть ли на этой прошивке известный экран автозапуска. */
    fun isAvailable(context: Context): Boolean = resolveIntent(context) != null

    /** @return true, если экран удалось открыть. */
    fun open(context: Context): Boolean {
        val intent = resolveIntent(context) ?: return false
        return runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "OemAutostartHelper: open failed") }
                .isSuccess
    }

    private fun resolveIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (component in KNOWN_COMPONENTS) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(intent, 0) != null) return intent
        }
        return null
    }
}
