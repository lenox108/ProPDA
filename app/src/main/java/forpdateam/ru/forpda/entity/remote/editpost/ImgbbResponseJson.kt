package forpdateam.ru.forpda.entity.remote.editpost

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImgbbResponseJson(
    @SerialName("status_code")
    val statusCode: Int = 0,
    @SerialName("image")
    val image: ImageJson? = null
)

@Serializable
data class ImageJson(
    @SerialName("filename")
    val filename: String = "",
    @SerialName("extension")
    val extension: String = "",
    @SerialName("size_formatted")
    val sizeFormatted: String = "",
    @SerialName("medium")
    val medium: ImageUrlJson? = null,
    @SerialName("image")
    val image: ImageUrlJson? = null
)

@Serializable
data class ImageUrlJson(
    @SerialName("url")
    val url: String = ""
)
