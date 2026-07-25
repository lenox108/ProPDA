package forpdateam.ru.forpda.model.repository.draft

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem

/** Полный черновик редактора, а не только его текстовое поле. */
data class PostDraft(
    val message: String,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val attachments: List<DraftAttachment> = emptyList(),
    val editorMode: String = "",
) {
    val isEmpty: Boolean
        get() = message.isBlank() && attachments.isEmpty()

    companion object {
        fun create(
            message: String,
            selectionStart: Int,
            selectionEnd: Int,
            attachments: List<AttachmentItem>,
            editorMode: String = "",
        ) = PostDraft(
            message = message,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            // Незавершённую локальную загрузку нельзя возобновить после смерти процесса без
            // исходного URI/RequestFile. Не восстанавливаем такой элемент как вечный spinner.
            attachments = attachments
                .filter { it.id > 0 }
                .map(DraftAttachment::from),
            editorMode = editorMode,
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
) {
    fun toAttachmentItem() = AttachmentItem().also { item ->
        item.id = id
        item.name = name
        item.extension = extension
        item.weight = weight
        item.typeFile = typeFile
        item.loadState = loadState
        item.status = status
        item.imageUrl = imageUrl
        item.url = url
        item.width = width
        item.height = height
        item.md5 = md5
        item.setError(isError)
        item.errorText = errorText
    }

    companion object {
        fun from(item: AttachmentItem) = DraftAttachment(
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
        )
    }
}
