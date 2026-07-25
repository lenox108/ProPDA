package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.app.Activity
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import forpdateam.ru.forpda.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
        sdk = [33],
        qualifiers = "night",
        application = android.app.Application::class,
)
class ActiveSelectionColorsTest {

    private fun blueExpressiveAmoledActivity(): Activity =
            Robolectric.buildActivity(Activity::class.java).get().apply {
                setTheme(R.style.AmoledAppTheme)
                theme.applyStyle(R.style.ThemeOverlay_ForPDA_Accent_Blue_Expressive, true)
            }

    @Test
    fun `blue expressive AMOLED navigation uses blue primary roles`() {
        val activity = blueExpressiveAmoledActivity()
        val indicator = ActiveSelectionColors.indicator(activity)
        val content = ActiveSelectionColors.onIndicator(activity)
        val pinkSecondary = activity.getColor(R.color.accent_blue_expressive_secondary_container)

        assertEquals(
                activity.getColor(R.color.accent_blue_expressive_primary_container),
                indicator,
        )
        assertEquals(
                activity.getColor(R.color.accent_blue_expressive_on_primary_container),
                content,
        )
        assertNotEquals("navigation must not use the pink secondary role", pinkSecondary, indicator)
        assertTrue(
                "active icon must keep accessible contrast on its indicator",
                ColorUtils.calculateContrast(content, indicator) >= 4.5,
        )
    }

    @Test
    fun `active tab row uses twenty percent primary tint`() {
        val activity = blueExpressiveAmoledActivity()
        val primary = activity.getColor(R.color.accent_blue_expressive_primary)
        val row = ActiveSelectionColors.rowBackground(activity)

        assertEquals(primary and 0x00FFFFFF, row and 0x00FFFFFF)
        assertEquals(ActiveSelectionColors.ROW_TINT_ALPHA, Color.alpha(row))
        assertEquals(0x33, ActiveSelectionColors.ROW_TINT_ALPHA)
    }
}
