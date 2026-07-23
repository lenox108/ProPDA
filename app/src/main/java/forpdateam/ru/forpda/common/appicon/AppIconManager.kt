package forpdateam.ru.forpda.common.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.common.Preferences
import timber.log.Timber

/**
 * Смена иконки запуска: включаем нужный `activity-alias` и гасим остальные.
 *
 * Источник правды — настройка [Preferences.Main.APP_ICON]. Сам выбор ([select])
 * только пишет настройку; к состоянию компонентов её приводит [applyIfNeeded]
 * при уходе приложения в фон.
 *
 * Почему не сразу: выключение псевдонима, с которого запущена текущая задача,
 * рушит её — даже с `DONT_KILL_APP`. Пользователь, меняющий иконку в настройках,
 * при мгновенном применении оказывался на рабочем столе. В фоне рушить нечего,
 * а увидеть новую иконку всё равно можно только выйдя из приложения.
 */
object AppIconManager {

    fun selected(context: Context): AppIconVariant =
            AppIcons.byId(PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(Preferences.Main.APP_ICON, null))

    /** Запоминает выбор; применится при следующем уходе в фон. */
    fun select(context: Context, variant: AppIconVariant) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(Preferences.Main.APP_ICON, variant.id)
                .apply()
    }

    /** Приводит псевдонимы к сохранённому выбору. Ничего не делает, если всё совпадает. */
    fun applyIfNeeded(context: Context) = apply(context, selected(context), disableOthers = true)

    /**
     * Аварийная страховка на старте процесса: если у приложения не осталось ни
     * одного включённого псевдонима, включает выбранный. Ничего не выключает,
     * поэтому текущей задаче не грозит ничего.
     *
     * Нужна, когда сборка теряет вариант, который выбрал пользователь (иконку
     * убрали из [AppIcons]): его псевдоним исчезает из манифеста, псевдоним по
     * умолчанию остаётся явно выключенным, и ярлыка нет вообще — запустить
     * приложение, чтобы это исправить, уже нечем.
     *
     * Включать безусловно НЕЛЬЗЯ: если состояние компонентов сбросилось к
     * манифестным значениям (сброс/восстановление из бэкапа, `allowBackup`
     * возвращает настройку, а состояние компонентов — нет), дефолтный псевдоним
     * снова `enabled="true"`, и включение выбранного давало ВТОРОЙ ярлык до
     * первого ухода в фон. Пока хоть один псевдоним включён, ярлык есть —
     * приводить состояние к настройке будет [applyIfNeeded] в фоне.
     */
    fun ensureLauncherPresent(context: Context) {
        val pm = context.packageManager
        val pkg = context.packageName
        val anyEnabled = AppIcons.variants.any {
            isEnabled(pm, ComponentName(pkg, it.alias), manifestDefault = it.isDefault)
        }
        if (anyEnabled) return
        apply(context, selected(context), disableOthers = false)
    }

    private fun apply(context: Context, target: AppIconVariant, disableOthers: Boolean) {
        val pm = context.packageManager
        val pkg = context.packageName
        // Сначала включаем нужный, потом гасим остальные: между двумя вызовами у пакета
        // всегда есть хотя бы один включённый LAUNCHER-компонент. В обратном порядке
        // лаунчер на мгновение видит приложение «без ярлыка» и может убрать его с рабочего стола.
        setEnabled(pm, ComponentName(pkg, target.alias), enabled = true, manifestDefault = target.isDefault)
        if (!disableOthers) return
        AppIcons.variants.filter { it.id != target.id }.forEach {
            setEnabled(pm, ComponentName(pkg, it.alias), enabled = false, manifestDefault = it.isDefault)
        }
    }

    /**
     * Включён ли компонент фактически. `STATE_DEFAULT` = «как в манифесте»: у иконки
     * по умолчанию `enabled="true"`, у остальных `false`. Различать важно, иначе на
     * штатной конфигурации PackageManager дёргается при каждом уходе в фон.
     */
    private fun isEnabled(pm: PackageManager, component: ComponentName, manifestDefault: Boolean) =
            when (runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> manifestDefault
            }

    private fun setEnabled(
            pm: PackageManager,
            component: ComponentName,
            enabled: Boolean,
            manifestDefault: Boolean,
    ) {
        try {
            if (isEnabled(pm, component, manifestDefault) == enabled) return
            pm.setComponentEnabledSetting(
                    component,
                    if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    // Без DONT_KILL_APP система убивает процесс прямо посреди смены иконки.
                    PackageManager.DONT_KILL_APP,
            )
        } catch (e: Exception) {
            // Некоторые прошивки запрещают менять состояние компонентов из приложения.
            // Иконка просто останется прежней — падать из-за этого нельзя.
            Timber.w(e, "Не удалось переключить псевдоним иконки %s", component.className)
        }
    }

    private val AppIconVariant.isDefault: Boolean get() = id == AppIcons.DEFAULT_ID
}
