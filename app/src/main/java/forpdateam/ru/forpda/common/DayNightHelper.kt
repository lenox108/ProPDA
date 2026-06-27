package forpdateam.ru.forpda.common

import android.content.res.Configuration
import android.os.Build
import timber.log.Timber
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DayNightHelper(
        private val defaultMode: Boolean
) {

    companion object {

        fun isUiModeNight(configuration: Configuration): Boolean {
            val currentNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }


        fun applyTheme(prefMode: String) {
            val mode = try {
                Preferences.Main.ThemeMode.valueOf(prefMode)
            } catch (e: IllegalArgumentException) {
                Preferences.Main.ThemeMode.SYSTEM
            }
            applyTheme(mode)
        }

        fun applyTheme(mode: Preferences.Main.ThemeMode) {
            Timber.d("DayNightHelper applyTheme $mode")
            val delegateMode = when (mode) {
                Preferences.Main.ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                Preferences.Main.ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                Preferences.Main.ThemeMode.AMOLED -> AppCompatDelegate.MODE_NIGHT_YES
                Preferences.Main.ThemeMode.SYSTEM,
                Preferences.Main.ThemeMode.SYSTEM_AMOLED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    } else {
                        val night = isUiModeNight(android.content.res.Resources.getSystem().configuration)
                        if (night) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    }
                }
            }
            AppCompatDelegate.setDefaultNightMode(delegateMode)
        }
    }

    private val _isNightFlow = MutableStateFlow(defaultMode)
    val isNightFlow: StateFlow<Boolean> = _isNightFlow.asStateFlow()

    fun isNight(): Boolean = _isNightFlow.value

    fun setIsNight(isNight: Boolean) {
        _isNightFlow.value = isNight
    }

}