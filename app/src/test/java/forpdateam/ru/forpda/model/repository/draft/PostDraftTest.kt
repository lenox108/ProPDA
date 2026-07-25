package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDraftTest {

    @Test
    fun `draft preserves message verbatim`() {
        val message = "  first line\nsecond line  \n"
        val draft = PostDraft.create(message, 2, 7, emptyList())

        assertEquals(message, draft.message)
        assertEquals(2, draft.selectionStart)
        assertEquals(7, draft.selectionEnd)
    }

    @Test
    fun `draft excludes uploads that cannot be resumed after process death`() {
        val pending = AttachmentItem("pending.png").apply {
            id = -1
            loadState = AttachmentItem.STATE_LOADING
        }
        val uploaded = AttachmentItem("uploaded.png").apply {
            id = 42
            loadState = AttachmentItem.STATE_LOADED
        }

        val draft = PostDraft.create("", 0, 0, listOf(pending, uploaded))

        assertEquals(listOf(42), draft.attachments.map { it.id })
    }

    @Test
    fun `draft preserves pending upload when source uri can reopen it`() {
        val pending = AttachmentItem("pending.png").apply {
            id = -1
            sourceUri = "content://documents/pending"
            sourceMimeType = "image/png"
            sourceFileSize = 42L
        }

        val restored = PostDraft.create("", 0, 0, listOf(pending))
            .attachments
            .single()
            .toAttachmentItem()

        assertEquals("content://documents/pending", restored.sourceUri)
        assertEquals(AttachmentItem.STATE_NOT_LOADED, restored.loadState)
        assertTrue(restored.isError)
    }

    @Test
    fun `changed empty attachments remain a meaningful edit draft`() {
        val draft = PostDraft.createFromSnapshots(
            message = "",
            selectionStart = 0,
            selectionEnd = 0,
            attachments = emptyList<AttachmentSnapshot>(),
            attachmentsChanged = true,
        )

        assertFalse(draft.isEmpty)
        assertTrue(draft.attachmentsChanged)
    }

    @Test
    fun `loaded qms image with zero id stays sendable after restore`() {
        val image = AttachmentItem("image.png").apply {
            id = 0
            loadState = AttachmentItem.STATE_LOADED
            url = "https://image.example/full.png"
        }

        val restored = PostDraft.create("", 0, 0, listOf(image))
            .attachments
            .single()
            .toAttachmentItem()

        assertEquals(0, restored.id)
        assertEquals(AttachmentItem.STATE_LOADED, restored.loadState)
        assertFalse(restored.isError)
    }
}
