package forpdateam.ru.forpda.appupdates

/** Ссылка на APK-ассет релиза GitHub. */
data class DownloadLink(
    val url: String,
    val fileName: String,
    val sizeBytes: Long? = null
)

/** Кандидат обновления, собранный из последнего релиза на GitHub. */
data class Candidate(
    val version: SemanticVersion,
    val url: String,
    val description: String? = null,
    val downloads: List<DownloadLink> = emptyList()
)
