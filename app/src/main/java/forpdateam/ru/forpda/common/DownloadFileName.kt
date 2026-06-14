package forpdateam.ru.forpda.common

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

object DownloadFileName {
    private val windows1251: Charset = Charset.forName(Cp1251Codec.NAME)
    private val windows1252: Charset = Charset.forName("windows-1252")
    private val invalidFileNameChars = Regex("""[\u0000-\u001F\\/:*?"<>|]""")
    private val cp1251MojibakeMarkers = listOf("вЂ", "РЃ", "Рђ", "Р‘", "Р’", "Р“", "Р”", "Р•", "Р–", "Р—", "Р˜", "Р™", "Рљ", "Р›", "Рњ", "Рќ", "Рћ", "Рџ", "Р°", "Р±", "Р²", "Рі", "Рґ", "Рµ", "Р¶", "Р·", "Рё", "Р№", "Рє", "Р»", "Рј", "РЅ", "Рѕ", "Рї", "С€", "С‹", "СЊ", "СЌ", "СЋ", "СЏ")
    private val windows1252Mojibake = Regex("""[ÐÑ][\u0080-\u00BF\u201A-\u201E\u2020-\u2021\u02C6\u2030\u0160\u2039\u0152\u017D]""")

    @JvmStatic
    fun resolve(url: String, inputFileName: String? = null, contentDisposition: String? = null): String {
        parseContentDisposition(contentDisposition)?.let { return sanitize(it) }

        val fromUrl = fromUrl(url)
        if (fromUrl.isNotBlank() && (inputFileName.isNullOrBlank() || isFourPdaDownloadUrl(url) || isBroken(inputFileName))) {
            return sanitize(fromUrl)
        }

        return sanitize(inputFileName?.takeIf { it.isNotBlank() } ?: fromUrl.ifBlank { "download" })
    }

    @JvmStatic
    fun fromUrl(url: String): String {
        val rawSegment = rawLastPathSegment(url).orEmpty()
        if (rawSegment.isBlank()) return ""
        return decodePercentEncoded(rawSegment, plusAsSpace = isFourPdaDownloadUrl(url))
    }

    private fun parseContentDisposition(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) return null

        val filenameStar = Regex("""(?i)(?:^|;)\s*filename\*\s*=\s*([^;]+)""")
            .find(contentDisposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
        if (!filenameStar.isNullOrBlank()) {
            decodeRfc5987(filenameStar)?.takeIf { !isBroken(it) }?.let { return it }
        }

        val filename = Regex("""(?i)(?:^|;)\s*filename\s*=\s*("(?:\\.|[^"])*"|[^;]+)""")
            .find(contentDisposition)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"')
            ?.replace("\\\"", "\"")
        return filename?.takeIf { it.isNotBlank() && !isBroken(it) }
    }

    private fun decodeRfc5987(value: String): String? {
        val firstQuote = value.indexOf('\'')
        val secondQuote = value.indexOf('\'', firstQuote + 1)
        if (firstQuote <= 0 || secondQuote <= firstQuote) return null
        val charsetName = value.substring(0, firstQuote)
        val encoded = value.substring(secondQuote + 1)
        val bytes = percentDecodeToBytes(encoded, plusAsSpace = false)
        return runCatching { Charset.forName(charsetName).decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    }

    private fun rawLastPathSegment(url: String): String? {
        val rawPath = runCatching { URI(url).rawPath }.getOrNull()
            ?: url.substringBefore('#').substringBefore('?')
        return rawPath.substringAfterLast('/', missingDelimiterValue = rawPath).takeIf { it.isNotBlank() }
    }

    private fun decodePercentEncoded(value: String, plusAsSpace: Boolean): String {
        val bytes = percentDecodeToBytes(value, plusAsSpace)
        val utf8 = decodeStrict(bytes, Charsets.UTF_8)
        if (utf8 != null && !isBroken(utf8)) return utf8

        val candidates = buildList {
            utf8?.let {
                add(it)
                repairUtf8Mojibake(it)?.let(::add)
            }
            val cp1251 = windows1251.decode(ByteBuffer.wrap(bytes)).toString()
            add(cp1251)
            repairUtf8Mojibake(cp1251)?.let(::add)
        }
        return candidates.distinct().maxBy { filenameScore(it) }
    }

    private fun percentDecodeToBytes(value: String, plusAsSpace: Boolean): ByteArray {
        val out = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            when {
                ch == '%' && index + 2 < value.length -> {
                    val hex = value.substring(index + 1, index + 3)
                    val byte = hex.toIntOrNull(16)
                    if (byte != null) {
                        out.add(byte.toByte())
                        index += 3
                        continue
                    }
                    out.add(ch.code.toByte())
                }
                ch == '+' && plusAsSpace -> out.add(' '.code.toByte())
                ch.code <= 0x7F -> out.add(ch.code.toByte())
                else -> out.addAll(ch.toString().toByteArray(Charsets.UTF_8).toList())
            }
            index++
        }
        return out.toByteArray()
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun sanitize(fileName: String): String {
        val sanitized = fileName
            .replace(invalidFileNameChars, "_")
            .trim()
            .trim('.')
        return sanitized.ifBlank { "download" }
    }

    private fun repairUtf8Mojibake(value: String): String? {
        if (!hasMojibakeMarkers(value)) return null
        return listOf(windows1252, windows1251)
            .asSequence()
            .mapNotNull { charset ->
                runCatching {
                    val encoded = charset.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(java.nio.CharBuffer.wrap(value))
                    val bytes = ByteArray(encoded.remaining())
                    encoded.get(bytes)
                    decodeStrict(bytes, Charsets.UTF_8)
                }.getOrNull()
            }
            .filter { !isBroken(it) }
            .maxByOrNull { filenameScore(it) }
    }

    private fun filenameScore(value: String): Int {
        var score = 0
        for (ch in value) {
            when (ch) {
                '\uFFFD' -> score -= 200
                in '\u0400'..'\u04FF' -> score += 4
                in 'A'..'Z', in 'a'..'z', in '0'..'9' -> score += 1
                ' ', '.', '-', '_', '(', ')' -> score += 1
            }
        }
        if (hasMojibakeMarkers(value)) score -= 80
        return score
    }

    private fun isBroken(value: String): Boolean {
        return value.contains('\uFFFD') || hasMojibakeMarkers(value)
    }

    private fun hasMojibakeMarkers(value: String): Boolean {
        return cp1251MojibakeMarkers.any { value.contains(it) } || windows1252Mojibake.containsMatchIn(value)
    }

    private fun isFourPdaDownloadUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        val rawPath = uri.rawPath ?: return false
        return SiteUrls.isSiteHost(host) && rawPath.contains("/forum/dl/post/", ignoreCase = true)
    }
}
