package forpdateam.ru.forpda.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.DayNightHelper
import forpdateam.ru.forpda.common.getColorFromAttr

object SystemBarAppearance {

    fun syncStatusBar(activity: Activity, backgroundColor: Int) {
        activity.window.statusBarColor = backgroundColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
        }
        syncStatusBarIconContrast(activity, backgroundColor)
    }

    fun syncStatusBarIconContrast(activity: Activity) {
        setLightStatusBar(activity, getDefaultLightSystemBar(activity))
    }

    fun syncStatusBarIconContrast(activity: Activity, backgroundColor: Int) {
        val opaqueBackground = if (Color.alpha(backgroundColor) < 255) {
            ColorUtils.compositeColors(backgroundColor, activity.getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        } else {
            backgroundColor
        }
        setLightStatusBar(activity, ColorUtils.calculateLuminance(opaqueBackground) > 0.5)
    }

    fun syncNavigationBar(activity: Activity, @AttrRes colorAttr: Int = R.attr.background_for_lists) {
        // ChromeCanvas: системный нав-бар — часть полотна; под Material You тонируется
        // обоями вместе с нижним таббаром, вне MY — ровно colorAttr (прежнее поведение).
        activity.window.navigationBarColor = activity.chromeCanvasColor(colorAttr)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isNavigationBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightNavigationBars = getDefaultLightSystemBar(activity)
    }

    fun setLightStatusBar(activity: Activity, value: Boolean) {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars = value
    }

    fun getDefaultLightSystemBar(activity: Activity): Boolean {
        val typedValue = TypedValue()
        if (activity.theme.resolveAttribute(R.attr.is_use_light_status_bar, typedValue, true)) {
            return when (typedValue.type) {
                TypedValue.TYPE_INT_BOOLEAN -> typedValue.data != 0
                TypedValue.TYPE_INT_DEC, TypedValue.TYPE_INT_HEX -> typedValue.data != 0
                else -> fallbackLightSystemBars(activity)
            }
        }
        // Without an explicit attr (OEM/stripped theme), follow day/night to avoid light icons on white bars.
        return fallbackLightSystemBars(activity)
    }

    private fun fallbackLightSystemBars(activity: Activity): Boolean =
            !DayNightHelper.isUiModeNight(activity.resources.configuration)
}
