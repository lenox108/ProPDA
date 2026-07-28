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

    @Test
    fun `run of gallery pictures is grouped into one grid`() {
        val blocks = listOf(picture(1), picture(2), picture(3), picture(4))

        assertEquals(4, BodyBlockViewFactory.imageGridRunLength(blocks, 0))
    }

    @Test
    fun `two pictures stay at their authored size`() {
        val blocks = listOf(picture(1), picture(2), BodyBlock.Text("после"))

        assertEquals(0, BodyBlockViewFactory.imageGridRunLength(blocks, 0))
    }

    @Test
    fun `text between pictures splits the run`() {
        val blocks = listOf(
                picture(1), picture(2), picture(3),
                BodyBlock.Text("подпись"),
                picture(4), picture(5),
        )

        assertEquals(3, BodyBlockViewFactory.imageGridRunLength(blocks, 0))
        assertEquals(0, BodyBlockViewFactory.imageGridRunLength(blocks, 4))
    }

    @Test
    fun `list glyphs and download banners never enter the grid`() {
        val glyphs = List(4) { picture(it).copy(inlineListIcon = true) }
        val banners = List(4) { picture(it).copy(attachmentButton = true) }

        assertEquals(0, BodyBlockViewFactory.imageGridRunLength(glyphs, 0))
        assertEquals(0, BodyBlockViewFactory.imageGridRunLength(banners, 0))
    }

    @Test
    fun `offsite image is not gridded`() {
        val offsite = List(4) {
            BodyBlock.Image(
                    imageUrl = "https://example.com/pic$it.png",
                    linkUrl = null,
                    displayWidthPx = 0,
                    displayHeightPx = 0,
                    inline = true,
            )
        }

        assertEquals(0, BodyBlockViewFactory.imageGridRunLength(offsite, 0))
    }

    @Test
    fun `column count follows the available width`() {
        // Typical phone column (360dp screen minus card chrome) → 3; narrow → 2; landscape/tablet → more.
        assertEquals(3, BodyBlockViewFactory.resolveImageGridColumns(columnWidthPx = 960, density = 3f))
        assertEquals(2, BodyBlockViewFactory.resolveImageGridColumns(columnWidthPx = 500, density = 3f))
        assertEquals(6, BodyBlockViewFactory.resolveImageGridColumns(columnWidthPx = 2280, density = 3f))
    }

    private fun picture(index: Int) = BodyBlock.Image(
            imageUrl = "https://s.4pda.to/forum/uploads/preview-$index.jpg",
            linkUrl = "https://4pda.to/forum/dl/post/1234567/wallpaper-$index.jpg",
            displayWidthPx = 150,
            displayHeightPx = 267,
            inline = true,
    )

}
