package forpdateam.ru.forpda.common

/**
 * Основной домен сайта — 4pda.to (зеркало 4pda.ru).
 */
object SiteUrls {
    const val HOST_PRIMARY = "4pda.to"
    const val BASE_HTTPS = "https://4pda.to"

    private val KNOWN_HOSTS = setOf(
            "4pda.to",
            "www.4pda.to",
            "4pda.ru",
            "www.4pda.ru"
    )

    @JvmStatic
    fun isSiteHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return KNOWN_HOSTS.contains(host.lowercase())
    }
}
