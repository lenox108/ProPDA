package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyBlockImageLayoutTest {

    @Test
    fun `known image dimensions reserve authored aspect ratio before load`() {
        val box = BodyBlockViewFactory.resolveStableInlineImageBox(
                displayWidthPx = 800,
                displayHeightPx = 1200,
                density = 1f,
                columnWidthPx = 400,
                maxHeightPx = 1000,
        )

        assertEquals(400, box.widthPx)
        assertEquals(600, box.heightPx)
    }

    @Test
    fun `known image is scaled by both column width and viewport height`() {
        val box = BodyBlockViewFactory.resolveStableInlineImageBox(
                displayWidthPx = 600,
                displayHeightPx = 1800,
                density = 2f,
                columnWidthPx = 900,
                maxHeightPx = 1200,
        )

        assertEquals(400, box.widthPx)
        assertEquals(1200, box.heightPx)
    }

    @Test
    fun `small intrinsic image stays small instead of being upscaled to column width`() {
        val box = BodyBlockViewFactory.resolveStableInlineImageBox(
                displayWidthPx = 32,
                displayHeightPx = 24,
                density = 3f,
                columnWidthPx = 900,
                maxHeightPx = 2000,
        )

        assertEquals(96, box.widthPx)
        assertEquals(72, box.heightPx)
    }

}
