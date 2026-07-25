package forpdateam.ru.forpda.ui.views.messagepanel

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentSnapshot

enum class MessageSendBlockReason {
    NONE,
    EMPTY,
    UPLOAD_IN_PROGRESS,
    UPLOAD_FAILED,
}

object MessageSendEligibility {
    fun evaluate(
        message: CharSequence,
        attachments: List<AttachmentSnapshot>,
    ): MessageSendBlockReason {
        if (attachments.any {
                it.loadState == AttachmentItem.STATE_NOT_LOADED || it.isError
            }
        ) {
            return MessageSendBlockReason.UPLOAD_FAILED
        }
        if (attachments.any {
                it.loadState != AttachmentItem.STATE_LOADED
            }
        ) {
            return MessageSendBlockReason.UPLOAD_IN_PROGRESS
        }
        if (message.isBlank() && attachments.isEmpty()) {
            return MessageSendBlockReason.EMPTY
        }
        return MessageSendBlockReason.NONE
    }
}
