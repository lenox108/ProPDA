package forpdateam.ru.forpda.common

import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

object BatteryDebugLogger {
    private const val TAG = "BatteryDebug"

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Timber.tag(TAG).d(message)
        }
    }

    fun logState(component: String, state: String, details: String? = null) {
        if (!BuildConfig.DEBUG) return
        val suffix = details?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        Timber.tag(TAG).d("$component: $state$suffix")
    }
}
