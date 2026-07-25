package forpdateam.ru.forpda.common.bbcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BbcodeRegistryTest {

    @Test
    fun `all editor tools are recognized by syntax highlighter`() {
        assertTrue(
            BbcodeRegistry.editorTools
                .map { it.tag.lowercase() }
                .all(BbcodeRegistry.syntaxTags::contains)
        )
    }

    @Test
    fun `preview only declares tags it actually renders`() {
        assertTrue("quote" in BbcodeRegistry.previewTags)
        assertFalse("attachment" in BbcodeRegistry.previewTags)
        assertFalse("releaser" in BbcodeRegistry.previewTags)
    }

    @Test
    fun `tool lookup is case insensitive`() {
        assertEquals(BbcodeRegistry.Tool.QUOTE, BbcodeRegistry.findTool("quote"))
    }
}
