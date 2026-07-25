package forpdateam.ru.forpda.model.data.remote.api

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestFileTest {

    @Test
    fun `reset stream makes a consumed upload retryable`() {
        val file = RequestFile(
            fileName = "file.txt",
            mimeType = "text/plain",
            fileStream = ByteArrayInputStream("content".toByteArray()),
            streamProvider = { ByteArrayInputStream("content".toByteArray()) },
        )
        file.openStream().readBytes()

        assertTrue(file.resetStream())
        assertEquals("content", file.openStream().reader().readText())
    }

    @Test
    fun `stream without provider cannot be reset`() {
        val file = RequestFile("file.txt", "text/plain", ByteArrayInputStream(byteArrayOf(1)))

        assertFalse(file.resetStream())
    }
}
