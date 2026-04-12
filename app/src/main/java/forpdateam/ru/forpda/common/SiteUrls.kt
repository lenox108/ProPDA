package forpdateam.ru.forpda.common

/** Домен сайта — только 4pda.to. */
object SiteUrls {
    const val HOST_PRIMARY = "4pda.to"
    const val BASE_HTTPS = "https://4pda.to"

    private val KNOWN_HOSTS = setOf(
            "4pda.to",
            "www.4pda.to"
    )

    @JvmStatic
    fun isSiteHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return KNOWN_HOSTS.contains(host.lowercase())
    }
}
