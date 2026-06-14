package forpdateam.ru.forpda.model.data.remote.api.attachments

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostParser
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeParser
import java.security.MessageDigest
import java.util.Locale

class AttachmentsApi(
        private val webClient: IWebClient,
        private val attachmentsParser: AttachmentsParser
) {

    fun uploadQmsFiles(files: List<RequestFile>, pending: List<AttachmentItem>) =
            uploadFiles(-1, "MSG", files, pending)

    fun uploadTopicFiles(postId: Int, files: List<RequestFile>, pending: List<AttachmentItem>) =
            uploadFiles(postId, null, files, pending)

    fun deleteQmsFiles(items: List<AttachmentItem>) =
            deleteFiles(-1, "MSG", items)

    fun deleteTopicFiles(postId: Int, items: List<AttachmentItem>) =
            deleteFiles(postId, null, items)


    private fun uploadFiles(
            postId: Int,
            relType: String?,
            files: List<RequestFile>,
            pending: List<AttachmentItem>
    ): List<AttachmentItem> {

        val builder = NetworkRequest.Builder()
                .url("https://4pda.to/forum/index.php?act=attach")
                .xhrHeader()
                .formHeader("index", "1")
                .formHeader("maxSize", "134217728")
                .formHeader("allowExt", "")
                .formHeader("forum-attach-files", "")
                .formHeader("code", "check")
        if (postId != -1) {
            builder.formHeader("relId", postId.toString())
        }
        for (i in files.indices) {
            val file = files[i]
            val item = pending[i]

            file.requestName = "FILE_UPLOAD[]"
            val digestResult = calculateDigest(file)
            val hash = digestResult.digest
            val md5 = byteArrayToHexString(hash)
            builder
                    .formHeader("md5", md5)
                    .formHeader("size", (file.fileSize ?: digestResult.size).toString())
                    .formHeader("name", file.fileName)

            var response = webClient.request(builder.build())
            if (response.body == "0") {
                file.fileStream = file.reopenStream()
                    ?: throw IllegalStateException("Unable to reopen upload file stream")
                val uploadRequest = NetworkRequest.Builder()
                        .url("https://4pda.to/forum/index.php?act=attach")
                        .xhrHeader()
                        .formHeader("index", "1")
                        .formHeader("maxSize", "134217728")
                        .formHeader("allowExt", "")
                        .formHeader("forum-attach-files", "")
                        .formHeader("code", "upload")
                        .file(file)

                if (postId != -1) {
                    uploadRequest.formHeader("relId", postId.toString())
                }
                if (relType != null) {
                    uploadRequest.formHeader("relType", relType)
                }

                response = webClient.request(uploadRequest.build(), item.progressListener)
            }
            attachmentsParser.parseAttachment(response.body, item)
            item.status = AttachmentItem.STATUS_UPLOADED
        }
        return pending
    }

    private fun calculateDigest(file: RequestFile): DigestResult {
        val messageDigest = MessageDigest.getInstance("MD5")
        var size = 0L
        file.openStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                messageDigest.update(buffer, 0, read)
                size += read
            }
        }
        if (!file.canOpenStreamAgain()) {
            throw IllegalStateException("Upload file stream cannot be reopened after digest calculation")
        }
        return DigestResult(messageDigest.digest(), size)
    }

    private fun deleteFiles(
            postId: Int,
            relType: String?,
            items: List<AttachmentItem>
    ): List<AttachmentItem> {
        var response: NetworkResponse
        for (item in items) {
            val builder = NetworkRequest.Builder()
                    .url("https://4pda.to/forum/index.php?act=attach")
                    .xhrHeader()
                    .formHeader("index", "1")
                    .formHeader("maxSize", "134217728")
                    .formHeader("allowExt", "")
                    .formHeader("code", "remove")
                    .formHeader("id", Integer.toString(item.id))
            if (postId != -1) {
                builder.formHeader("relId", postId.toString())
            }
            if (relType != null) {
                builder.formHeader("relType", relType)
            }
            response = webClient.request(builder.build())
            if (item.id <= 0) {
                continue
            }
            if (isAttachRemoveResponseSuccess(response.body)) {
                item.status = AttachmentItem.STATUS_REMOVED
                item.isError = false
            } else {
                item.isError = true
            }
        }
        return items
    }

    /**
     * Раньше проверяли только body == "0"; на актуальном форуме ответ AJAX может быть пустым, "1", JSON и т.д.
     * Без этого [AttachmentItem.STATUS_REMOVED] не выставлялся — вложения не пропадали из списка при редактировании.
     */
    private fun isAttachRemoveResponseSuccess(body: String?): Boolean {
        if (body == null) return true
        val b = body.trim()
        if (b.isEmpty()) return true
        val lower = b.lowercase(Locale.ROOT)
        if (lower == "nopermission" || lower.contains("nopermission")) return false
        if (lower.contains("<html") && (lower.contains("error") || lower.contains("exception"))) return false
        if (b == "0" || b == "1") return true
        if (b.equals("ok", ignoreCase = true) || b.equals("true", ignoreCase = true)) return true
        if (b.startsWith("{")) {
            if (lower.contains("\"error\"") || lower.contains("errorcode")) return false
            if (lower.contains("\"status\":\"ok\"") || lower.contains("\"status\": \"ok\"")) return true
            if (lower.contains("\"status\":\"success\"") || lower.contains("\"status\": \"success\"")) return true
            return b.length < 800 && !lower.contains("error") && !lower.contains("fail")
        }
        return b.length <= 96 && !lower.contains("error") && !lower.contains("fail")
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        val hexString = StringBuilder()
        for (aByte in bytes) {
            val hex = Integer.toHexString(aByte.toInt() and 0xFF)
            if (hex.length == 1) {
                hexString.append('0')
            }
            hexString.append(hex)
        }
        return hexString.toString()
    }

    private data class DigestResult(
        val digest: ByteArray,
        val size: Long
    )
}