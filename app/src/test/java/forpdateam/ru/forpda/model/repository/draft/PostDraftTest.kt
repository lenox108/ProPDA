package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import org.junit.Assert.assertEquals
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
        val pending = AttachmentItem("pending.png").apply { id = -1 }
        val uploaded = AttachmentItem("uploaded.png").apply { id = 42 }

        val draft = PostDraft.create("", 0, 0, listOf(pending, uploaded))

        assertEquals(listOf(42), draft.attachments.map { it.id })
    }
}
