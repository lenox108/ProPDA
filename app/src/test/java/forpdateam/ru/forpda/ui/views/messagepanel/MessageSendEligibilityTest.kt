package forpdateam.ru.forpda.ui.views.messagepanel

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSendEligibilityTest {

    @Test
    fun `empty editor cannot be submitted`() {
        assertEquals(
            MessageSendBlockReason.EMPTY,
            MessageSendEligibility.evaluate(" \n", emptyList()),
        )
    }

    @Test
    fun `pending and failed uploads cannot be submitted`() {
        assertEquals(
            MessageSendBlockReason.UPLOAD_IN_PROGRESS,
            MessageSendEligibility.evaluate("text", listOf(attachment(id = -1, loadState = AttachmentItem.STATE_LOADING))),
        )
        assertEquals(
            MessageSendBlockReason.UPLOAD_FAILED,
            MessageSendEligibility.evaluate("text", listOf(attachment(id = -1, loadState = AttachmentItem.STATE_NOT_LOADED))),
        )
    }

    @Test
    fun `loaded attachment can be submitted without body text`() {
        assertEquals(
            MessageSendBlockReason.NONE,
            MessageSendEligibility.evaluate("", listOf(attachment(id = 0, loadState = AttachmentItem.STATE_LOADED))),
        )
    }

    private fun attachment(id: Int, loadState: Int) = AttachmentSnapshot(
        id = id,
        name = "file",
        extension = null,
        weight = null,
        typeFile = AttachmentItem.TYPE_FILE,
        loadState = loadState,
        status = AttachmentItem.STATUS_READY,
        imageUrl = null,
        url = null,
        width = 0,
        height = 0,
        md5 = null,
        isError = false,
        errorText = null,
        sourceUri = null,
        sourceMimeType = null,
        sourceFileSize = null,
    )
}
