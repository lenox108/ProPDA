package forpdateam.ru.forpda.entity.remote.checker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateDataJson(
    @SerialName("update")
    val update: UpdateJson
)

@Serializable
data class UpdateJson(
    @SerialName("version_code")
    val versionCode: Int = Int.MAX_VALUE,
    @SerialName("version_build")
    val versionBuild: Int = Int.MAX_VALUE,
    @SerialName("version_name")
    val versionName: String = "",
    @SerialName("build_date")
    val buildDate: String = "",
    @SerialName("links")
    val links: List<LinkJson> = emptyList(),
    @SerialName("important")
    val important: List<String> = emptyList(),
    @SerialName("added")
    val added: List<String> = emptyList(),
    @SerialName("fixed")
    val fixed: List<String> = emptyList(),
    @SerialName("changed")
    val changed: List<String> = emptyList(),
    @SerialName("patternsVersion")
    val patternsVersion: Int = 0
)

@Serializable
data class LinkJson(
    @SerialName("name")
    val name: String = "Unknown",
    @SerialName("url")
    val url: String = "",
    @SerialName("type")
    val type: String = "site"
)
