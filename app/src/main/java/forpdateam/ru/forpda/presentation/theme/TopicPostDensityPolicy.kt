package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences as AppPreferences

/**
 * Shared topic density contract for native toolbar height and WebView body classes.
 */
object TopicPostDensityPolicy {

    fun webBodyClass(density: AppPreferences.Main.TopicPostDensity): String = when (density) {
        AppPreferences.Main.TopicPostDensity.SUPER_COMPACT -> "density_super_compact"
        AppPreferences.Main.TopicPostDensity.COMPACT -> "density_compact"
        AppPreferences.Main.TopicPostDensity.COMFORTABLE -> "density_comfortable"
    }

    fun toolbarHeightDimenRes(density: AppPreferences.Main.TopicPostDensity): Int = when (density) {
        AppPreferences.Main.TopicPostDensity.SUPER_COMPACT -> R.dimen.dp40
        AppPreferences.Main.TopicPostDensity.COMPACT -> R.dimen.dp48
        AppPreferences.Main.TopicPostDensity.COMFORTABLE -> R.dimen.dp56
    }

    fun topChromePaddingCssPx(
            density: AppPreferences.Main.TopicPostDensity,
            @Suppress("UNUSED_PARAMETER") toolbarBehavior: AppPreferences.Main.TopicToolbarBehavior,
    ): Int = when (density) {
        AppPreferences.Main.TopicPostDensity.SUPER_COMPACT -> 40
        AppPreferences.Main.TopicPostDensity.COMPACT -> 48
        AppPreferences.Main.TopicPostDensity.COMFORTABLE -> 56
    }
}
