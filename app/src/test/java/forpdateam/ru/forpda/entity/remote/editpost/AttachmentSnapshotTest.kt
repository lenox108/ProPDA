package forpdateam.ru.forpda.entity.remote.editpost

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class AttachmentSnapshotTest {

    @Test
    fun `submission snapshot is independent from mutable ui item`() {
        val item = AttachmentItem("photo.png").apply {
            id = 17
            loadState = AttachmentItem.STATE_LOADED
            url = "https://example.com/17"
            sourceUri = "content://documents/17"
        }

        val snapshot = AttachmentSnapshot.from(item)
        item.id = 99
        item.name = "changed.png"
        item.url = "https://example.com/99"

        val requestItem = snapshot.toAttachmentItem()
        assertEquals(17, requestItem.id)
        assertEquals("photo.png", requestItem.name)
        assertEquals("https://example.com/17", requestItem.url)
        assertNotSame(item, requestItem)
    }
}
