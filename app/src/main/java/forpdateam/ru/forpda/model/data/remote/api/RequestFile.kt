package forpdateam.ru.forpda.model.data.remote.api

import java.io.InputStream

/**
 * Created by radiationx on 12.01.17.
 */
class RequestFile(
    val fileName: String,
    val mimeType: String,
    var fileStream: InputStream,
    val fileSize: Long? = null,
    private val streamProvider: (() -> InputStream)? = null,
    var requestName: String? = null
) {
    constructor(fileName: String, mimeType: String, fileStream: InputStream) : this(fileName, mimeType, fileStream, null)

    fun openStream(): InputStream = fileStream

    fun reopenStream(): InputStream? = streamProvider?.invoke()

    fun canOpenStreamAgain(): Boolean = streamProvider != null

    override fun toString(): String {
        return "RequestFile{$fileName, $mimeType, $requestName, $fileSize, $fileStream}"
    }
}
