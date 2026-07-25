package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditPostAttachmentsTest {

    @Test
    fun `batch removal removes every selected attachment and preserves other content`() {
        val message = """
            before
            [attachment=12:first.png]
            [img]https://4pda.to/forum/dl/post/34/second.png[/img]
            [attachment=56:keep.png]
            after
        """.trimIndent()

        val result = removeAttachmentReferencesFromBody(message, listOf(12, 34))

        assertFalse(result.contains("12:first.png"))
        assertFalse(result.contains("/34/second.png"))
        assertEquals(true, result.contains("[attachment=56:keep.png]"))
        assertEquals(true, result.contains("before"))
        assertEquals(true, result.contains("after"))
    }

    @Test
    fun `duplicate and invalid ids are harmless`() {
        val message = "[attachment=7:file.zip]"
        assertEquals("", removeAttachmentReferencesFromBody(message, listOf(-1, 7, 7, 0)))
    }
}
