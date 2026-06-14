package forpdateam.ru.forpda.ui.fragments.topics

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicListMetadataFormatterTest {

    @Test
    fun `formats author with page count`() {
        assertEquals("Orient9 • 128 стр.", TopicListMetadataFormatter.format("Orient9", 128))
    }

    @Test
    fun `keeps author only for single page`() {
        assertEquals("Orient9", TopicListMetadataFormatter.format("Orient9", 1))
    }

    @Test
    fun `uses pages only when author is absent`() {
        assertEquals("1001 стр.", TopicListMetadataFormatter.format(null, 1001))
    }

    @Test
    fun `prefers last post author over starter`() {
        assertEquals("LastUser • 128 стр.", TopicListMetadataFormatter.format("LastUser", "Starter", 128))
    }

    @Test
    fun `falls back to starter when last post author is absent`() {
        assertEquals("Starter • 128 стр.", TopicListMetadataFormatter.format(" ", "Starter", 128))
    }
}
