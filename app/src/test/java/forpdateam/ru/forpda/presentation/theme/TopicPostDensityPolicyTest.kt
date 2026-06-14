package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicPostDensityPolicyTest {

    @Test
    fun comfortableDensity_usesComfortableWebClassAndToolbarHeight() {
        assertEquals(
                "density_comfortable",
                TopicPostDensityPolicy.webBodyClass(AppPreferences.Main.TopicPostDensity.COMFORTABLE)
        )
        assertEquals(R.dimen.dp56, TopicPostDensityPolicy.toolbarHeightDimenRes(AppPreferences.Main.TopicPostDensity.COMFORTABLE))
        assertEquals(
                56,
                TopicPostDensityPolicy.topChromePaddingCssPx(
                        AppPreferences.Main.TopicPostDensity.COMFORTABLE,
                        AppPreferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL
                )
        )
    }

    @Test
    fun compactDensity_usesCompactWebClassAndToolbarHeight() {
        assertEquals("density_compact", TopicPostDensityPolicy.webBodyClass(AppPreferences.Main.TopicPostDensity.COMPACT))
        assertEquals(R.dimen.dp48, TopicPostDensityPolicy.toolbarHeightDimenRes(AppPreferences.Main.TopicPostDensity.COMPACT))
        assertEquals(
                48,
                TopicPostDensityPolicy.topChromePaddingCssPx(
                        AppPreferences.Main.TopicPostDensity.COMPACT,
                        AppPreferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL
                )
        )
    }

    @Test
    fun superCompactDensity_usesSuperCompactWebClassAndToolbarHeight() {
        assertEquals(
                "density_super_compact",
                TopicPostDensityPolicy.webBodyClass(AppPreferences.Main.TopicPostDensity.SUPER_COMPACT)
        )
        assertEquals(R.dimen.dp40, TopicPostDensityPolicy.toolbarHeightDimenRes(AppPreferences.Main.TopicPostDensity.SUPER_COMPACT))
        assertEquals(
                40,
                TopicPostDensityPolicy.topChromePaddingCssPx(
                        AppPreferences.Main.TopicPostDensity.SUPER_COMPACT,
                        AppPreferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL
                )
        )
    }

    @Test
    fun fixedToolbarBehavior_usesToolbarHeightTopChromePadding() {
        assertEquals(
                48,
                TopicPostDensityPolicy.topChromePaddingCssPx(
                        AppPreferences.Main.TopicPostDensity.COMPACT,
                        AppPreferences.Main.TopicToolbarBehavior.PINNED
                )
        )
    }
}
