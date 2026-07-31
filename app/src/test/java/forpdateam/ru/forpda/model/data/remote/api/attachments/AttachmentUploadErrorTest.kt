package forpdateam.ru.forpda.model.data.remote.api.attachments

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentUploadErrorTest {

    @Test
    fun `bare server refusal names the unsupported format`() {
        // Именно так форум отвечает на animated WebP — телом «1».
        val message = AttachmentUploadError.describe("1", "sticker.webp")
        assertTrue(message, message.contains("WebP"))
        assertTrue(message, message.contains("GIF"))
        assertTrue("сырой ответ показывать нельзя", !message.trim().equals("1"))
    }

    @Test
    fun `empty response falls back to a generic explanation`() {
        val message = AttachmentUploadError.describe("", "report.docx")
        assertTrue(message, message.contains(".docx"))
    }

    @Test
    fun `unknown file without extension still gets a readable message`() {
        val message = AttachmentUploadError.describe("0", "снимок")
        assertTrue(message, message.contains("Форум отклонил файл"))
    }

    @Test
    fun `meaningful server text wins`() {
        val message = AttachmentUploadError.describe(
            "<div>Превышен максимальный размер файла</div>",
            "big.gif",
        )
        assertEquals("Превышен максимальный размер файла", message)
    }

    @Test
    fun `html noise is stripped and trimmed`() {
        val message = AttachmentUploadError.describe("<b>  Нет   прав  </b>", "a.png")
        assertEquals("Нет прав", message)
    }
}
