package forpdateam.ru.forpda.common.appicon

import forpdateam.ru.forpda.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppIconsTest {

    @Test
    fun `pixel four is the default icon and splash`() {
        val default = AppIcons.default

        assertEquals("pixel_4", AppIcons.DEFAULT_ID)
        assertEquals("pixel_4", default.id)
        assertEquals("forpdateam.ru.forpda.Launcher.Pixel4", default.alias)
        assertEquals(R.mipmap.ic_launcher_pixel_4, default.iconRes)
        assertEquals(R.style.Theme_ForPDA_Splash_Pixel4, default.splashThemeRes)
    }

    @Test
    fun `missing or unknown saved icon falls back to pixel four`() {
        assertSame(AppIcons.default, AppIcons.byId(null))
        assertSame(AppIcons.default, AppIcons.byId("removed_icon"))
    }

    @Test
    fun `legacy default id still resolves to classic icon and splash`() {
        val classic = AppIcons.byId("default")

        assertEquals(R.mipmap.ic_launcher, classic.iconRes)
        assertEquals(R.style.Theme_ForPDA_Splash, classic.splashThemeRes)
        assertNotEquals(AppIcons.default, classic)
    }

    @Test
    fun `every launcher icon has its own splash theme`() {
        val splashThemes = AppIcons.variants.map { it.splashThemeRes }

        assertEquals(AppIcons.variants.size, splashThemes.distinct().size)
    }
}
