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
     * Percent-decode значения, кодировка которого заранее НЕ известна: 4PDA — legacy cp1251, но часть
     * ссылок сайт отдаёт в UTF-8 (ссылки-теги под постом: `act=search&…&query=<utf8>`). Слепой
     * [decode] превращал их в мохибейк («Р»РёС‚РµСЂР°С‚СѓСЂР°» вместо «литература») — поиск по тегу
     * уходил на сервер с мусором и ничего не находил.
     *
     * Байты cp1251-кириллицы (0xC0–0xFF подряд) почти никогда не образуют валидную UTF-8
     * последовательность, поэтому UTF-8 декодер помечает их U+FFFD — по этому и различаем: чистый
     * UTF-8 декод берём как есть, иначе откатываемся на cp1251.
     */
    @JvmStatic
    fun decodeAuto(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val utf8 = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrNull()
        if (!utf8.isNullOrEmpty() && !utf8.contains('\uFFFD')) return utf8
        return decode(value)
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

    /**
     * Кодирует значение form-поля (тело комментария) для отправки на 4pda: символы, непредставимые
     * в windows-1251 (эмодзи и прочий Unicode вне cp1251), заменяются на HTML numeric character
     * reference `&#NNNN;` — ровно так, как это делает БРАУЗЕР при сабмите формы с
     * `accept-charset=windows-1251`. 4pda хранит и рендерит эмодзи именно как `&#NNNN;` (проверено:
     * в исходнике страницы эмодзи-комменты = numeric entities, НЕ сырой UTF-8).
     *
     * Без этого обычный [encode] (URLEncoder в cp1251) подставлял для эмодзи `0x1A` (SUB) — невидимый
     * управляющий символ, и смайлы в комментах пропадали при отправке.
     */
    @JvmStatic
    fun encodeFormValueWithEntities(value: String?): String = encode(escapeNonCp1251(value))

    /**
     * Заменяет непредставимые в windows-1251 символы (эмодзи и прочий Unicode вне cp1251) на
     * `&#NNNN;` — без percent-encoding. Для multipart-форм (тело поста форума), где значение уходит
     * телом части, а не percent-encoded парой; см. [encodeFormValueWithEntities] для обычных форм.
     */
    @JvmStatic
    fun escapeNonCp1251(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        val encoder = charset.newEncoder()
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            val count = Character.charCount(cp)
            val piece = value.substring(i, i + count)
            if (encoder.canEncode(piece)) {
                sb.append(piece)
            } else {
                sb.append("&#").append(cp).append(';')
            }
            i += count
        }
        return sb.toString()
    }

    private fun isRepresentableInCp1251(s: String): Boolean =
        charset.newEncoder().canEncode(s)
}
