package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentSnapshot

/** Полный черновик редактора, а не только его текстовое поле. */
data class PostDraft(
    val message: String,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val attachments: List<DraftAttachment> = emptyList(),
    val editorMode: String = "",
    /** For edit-post drafts, an empty list can intentionally mean “all attachments removed”. */
    val attachmentsChanged: Boolean = false,
) {
    val isEmpty: Boolean
        get() = message.isBlank() && attachments.isEmpty() && !attachmentsChanged

    companion object {
        fun create(
            message: String,
            selectionStart: Int,
            selectionEnd: Int,
            attachments: List<AttachmentItem>,
            editorMode: String = "",
        ) = createFromSnapshots(
            message = message,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            attachments = attachments.map(AttachmentSnapshot::from),
            editorMode = editorMode,
        )

        fun createFromSnapshots(
            message: String,
            selectionStart: Int,
            selectionEnd: Int,
            attachments: List<AttachmentSnapshot>,
            editorMode: String = "",
            attachmentsChanged: Boolean = attachments.isNotEmpty(),
        ) = PostDraft(
            message = message,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            // A local upload is restorable only when its source URI was persisted. Otherwise it
            // would come back as a permanent spinner with no possible retry action.
            attachments = attachments
                .filter { it.loadState == AttachmentItem.STATE_LOADED || it.canRestoreUpload }
                .map(DraftAttachment::from),
            editorMode = editorMode,
            attachmentsChanged = attachmentsChanged,
        )
    }
}

data class DraftAttachment(
    val id: Int,
    val name: String?,
    val extension: String?,
    val weight: String?,
    val typeFile: Int,
    val loadState: Int,
    val status: Int,
    val imageUrl: String?,
    val url: String?,
    val width: Int,
    val height: Int,
    val md5: String?,
    val isError: Boolean,
    val errorText: String?,
    val sourceUri: String? = null,
    val sourceMimeType: String? = null,
    val sourceFileSize: Long? = null,
) {
    fun toAttachmentItem() = toSnapshot().toAttachmentItem()

    fun toSnapshot() = AttachmentSnapshot(
        id = id,
        name = name,
        extension = extension,
        weight = weight,
        typeFile = typeFile,
        loadState = loadState,
        status = status,
        imageUrl = imageUrl,
        url = url,
        width = width,
        height = height,
        md5 = md5,
        isError = isError,
        errorText = errorText,
        sourceUri = sourceUri,
        sourceMimeType = sourceMimeType,
        sourceFileSize = sourceFileSize,
    )

    companion object {
        fun from(item: AttachmentItem) = from(AttachmentSnapshot.from(item))

        fun from(item: AttachmentSnapshot) = DraftAttachment(
            id = item.id,
            name = item.name,
            extension = item.extension,
            weight = item.weight,
            typeFile = item.typeFile,
            loadState = item.loadState,
            status = item.status,
            imageUrl = item.imageUrl,
            url = item.url,
            width = item.width,
            height = item.height,
            md5 = item.md5,
            isError = item.isError,
            errorText = item.errorText,
            sourceUri = item.sourceUri,
            sourceMimeType = item.sourceMimeType,
            sourceFileSize = item.sourceFileSize,
        )
    }
}
