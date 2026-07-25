package forpdateam.ru.forpda.entity.remote.editpost

/**
 * Immutable attachment value used by drafts and submission snapshots.
 *
 * [AttachmentItem] is intentionally mutable because upload progress is rendered directly in the UI.
 * Passing those live objects to an asynchronous send used to let a later upload/selection update
 * silently change the request that had already been submitted.
 */
data class AttachmentSnapshot(
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
    val sourceUri: String?,
    val sourceMimeType: String?,
    val sourceFileSize: Long?,
) {
    val isPendingUpload: Boolean
        get() = loadState != AttachmentItem.STATE_LOADED

    val canRestoreUpload: Boolean
        get() = !sourceUri.isNullOrBlank()

    fun toAttachmentItem(): AttachmentItem = AttachmentItem().also { item ->
        item.id = id
        item.name = name
        item.extension = extension
        item.weight = weight
        item.typeFile = typeFile
        item.loadState = if (isPendingUpload) AttachmentItem.STATE_NOT_LOADED else loadState
        item.status = status
        item.imageUrl = imageUrl
        item.url = url
        item.width = width
        item.height = height
        item.md5 = md5
        item.setError(isError || isPendingUpload)
        item.errorText = errorText
        item.sourceUri = sourceUri
        item.sourceMimeType = sourceMimeType
        item.sourceFileSize = sourceFileSize
    }

    companion object {
        fun from(item: AttachmentItem): AttachmentSnapshot = AttachmentSnapshot(
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
