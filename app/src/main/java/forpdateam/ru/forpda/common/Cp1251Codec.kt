package forpdateam.ru.forpda.common

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * Единая точка (de)кодирования строк в windows-1251 для запросов к 4PDA.
 *
 * 4PDA — legacy-движок IPB на windows-1251. Отправка form-полей и query-параметров,
 * содержащих русские буквы, должна быть в cp1251; иначе сервер не находит совпадений
 * (проблема: Uri.Builder / URLEncoder по умолчанию кодируют в UTF-8).
 *
 * [encodeSmart]/[decodeSmart] — для полей, где могут быть эмодзи/прочие символы вне cp1251:
 * если строка представима в cp1251 — кодирует в cp1251, иначе fallback в UTF-8.
 */
object Cp1251Codec {

    const val NAME = "windows-1251"
    private val charset: Charset = Charset.forName(NAME)

    /** Percent-encode в windows-1251. Не бросает исключений. null → пустая строка. */
    @JvmStatic
    fun encode(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return try {
            URLEncoder.encode(value, NAME)
        } catch (_: Exception) {
            value
        }
    }

    /** Percent-decode из windows-1251. Не бросает исключений. null → пустая строка. */
    @JvmStatic
    fun decode(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        return try {
            URLDecoder.decode(value, NAME)
        } catch (_: Exception) {
            value
        }
    }

    /**
     * Кодирует с fallback в UTF-8 для символов, отсутствующих в cp1251
     * (актуально для ников с эмодзи).
     */
    @JvmStatic
    fun encodeSmart(value: String): String {
        val targetCharset = if (isRepresentableInCp1251(value)) NAME else "UTF-8"
        return try {
            URLEncoder.encode(value, targetCharset)
        } catch (_: Exception) {
            value
        }
    }

    /**
     * Декодирует URL-параметр, который мог быть закодирован как legacy cp1251 или как UTF-8
     * fallback для emoji-ников.
     */
    @JvmStatic
    fun decodeSmart(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val utf8Decoded = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()
        if (!utf8Decoded.isNullOrEmpty() &&
                !utf8Decoded.contains('\uFFFD') &&
                isRepresentableInCp1251(utf8Decoded).not()) {
            return utf8Decoded
        }
        return decode(value)
    }

    private fun isRepresentableInCp1251(s: String): Boolean =
        charset.newEncoder().canEncode(s)
}
