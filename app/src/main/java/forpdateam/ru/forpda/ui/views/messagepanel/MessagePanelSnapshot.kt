package forpdateam.ru.forpda.ui.views.messagepanel

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentSnapshot

/**
 * Неизменяемое состояние редактора для отправки, черновика и безопасной очистки после ответа сервера.
 */
data class MessagePanelSnapshot(
    val message: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val attachments: List<AttachmentSnapshot>,
) {
    fun attachmentItems() = attachments.map(AttachmentSnapshot::toAttachmentItem)
}
