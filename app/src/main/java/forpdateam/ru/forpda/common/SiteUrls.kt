package forpdateam.ru.forpda.common

import android.net.Uri

/** Domains that belong to the app and may be routed inside 4PDA screens. */
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

    @JvmStatic
    fun isSiteUri(uri: Uri): Boolean = isSiteHost(uri.host)
}
