package forpdateam.ru.forpda.ui.views.messagepanel

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem

/**
 * Неизменяемое состояние редактора для отправки, черновика и безопасной очистки после ответа сервера.
 */
data class MessagePanelSnapshot(
    val message: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val attachments: List<AttachmentItem>,
    val attachmentIdentities: List<AttachmentIdentity>,
)

data class AttachmentIdentity(
    val id: Int,
    val name: String?,
    val loadState: Int,
    val status: Int,
    val url: String?,
    val imageUrl: String?,
)

internal fun AttachmentItem.toIdentity(): AttachmentIdentity = AttachmentIdentity(
    id = id,
    name = name,
    loadState = loadState,
    status = status,
    url = url,
    imageUrl = imageUrl,
)
