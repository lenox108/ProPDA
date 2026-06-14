package forpdateam.ru.forpda.common.webview

import java.net.URI
import java.net.URISyntaxException

sealed class UrlDecision {
    data class Internal(val normalizedUrl: String) : UrlDecision()
    data class External(val normalizedUrl: String) : UrlDecision()
    object Blocked : UrlDecision()
}

object UrlPolicy {

    fun classify(rawUrl: String?): UrlDecision {
        val value = rawUrl?.trim() ?: return UrlDecision.Blocked
        if (value.isBlank() ||
                value.length > MAX_URL_LENGTH ||
                value.hasControlChars() ||
                value.hasEncodedControlChars() ||
                value.hasEncodedDangerousSchemePrefix()) {
            return UrlDecision.Blocked
        }

        val uri = try {
            URI(value).normalize()
        } catch (_: URISyntaxException) {
            return UrlDecision.Blocked
        } catch (_: IllegalArgumentException) {
            return UrlDecision.Blocked
        }

        val scheme = uri.scheme?.lowercase() ?: return UrlDecision.Blocked
        return when (scheme) {
            "http", "https" -> classifyHttp(uri)
            "mailto" -> UrlDecision.External(uri.toASCIIString())
            "javascript", "file", "data", "content", "about", "app_cache" -> UrlDecision.Blocked
            else -> UrlDecision.Blocked
        }
    }

    private fun classifyHttp(uri: URI): UrlDecision {
        val host = uri.host?.lowercase() ?: return UrlDecision.Blocked
        val normalized = uri.toASCIIString()
        return if (isInternalHost(host)) {
            UrlDecision.Internal(normalized)
        } else {
            UrlDecision.External(normalized)
        }
    }

    private fun isInternalHost(host: String): Boolean {
        return host == "4pda.to" ||
                host.endsWith(".4pda.to") ||
                host == "4pda.ru" ||
                host == "www.4pda.ru"
    }

    private fun String.hasControlChars(): Boolean =
        any { it.code in 0x00..0x1F || it.code == 0x7F }

    private fun String.hasEncodedControlChars(): Boolean {
        var index = 0
        while (index <= length - 3) {
            if (this[index] == '%') {
                val value = substring(index + 1, index + 3).toIntOrNull(16)
                if (value != null && (value in 0x00..0x1F || value == 0x7F)) {
                    return true
                }
                index += 3
            } else {
                index++
            }
        }
        return false
    }

    private fun String.hasEncodedDangerousSchemePrefix(): Boolean {
        val prefix = take(SCHEME_PREFIX_SCAN_LIMIT)
        val decodedPrefix = prefix.percentDecodeAscii()
        return DANGEROUS_SCHEMES.any { scheme ->
            decodedPrefix.startsWith("$scheme:", ignoreCase = true)
        }
    }

    private fun String.percentDecodeAscii(): String {
        val decoded = StringBuilder(length)
        var index = 0
        while (index < length) {
            if (this[index] == '%' && index <= length - 3) {
                val value = substring(index + 1, index + 3).toIntOrNull(16)
                if (value != null) {
                    decoded.append(value.toChar())
                    index += 3
                    continue
                }
            }
            decoded.append(this[index])
            index++
        }
        return decoded.toString()
    }

    private val DANGEROUS_SCHEMES = setOf("javascript", "file", "data", "content", "about", "app_cache")
    private const val SCHEME_PREFIX_SCAN_LIMIT = 32
    private const val MAX_URL_LENGTH = 8_192
}
