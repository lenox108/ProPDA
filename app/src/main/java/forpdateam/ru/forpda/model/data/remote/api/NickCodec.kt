package forpdateam.ru.forpda.model.data.remote.api

private val base64NickRegex = Regex("""^[A-Za-z0-9+/]+={0,2}$""")

/**
 * 4pda иногда отдаёт ник автора в base64 (например `0JfQsNGHPw==` → `Зач?`) — и в строках списка
 * «Ответы», и в inline-комментариях к новостям. Без декодирования он показывался как сырой base64.
 * Декодируем только когда значение однозначно base64: канонический вид, длина кратна 4, ≥8 символов,
 * и декодируется в валидный UTF-8 текст с буквой. Обычные ники (`karton1`, `26d9`, `iSpark`) не
 * проходят одну из проверок и возвращаются как есть.
 */
internal fun decodeNickIfBase64(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.length < 8 || s.length % 4 != 0 || !base64NickRegex.matches(s)) return raw
    return try {
        val bytes = java.util.Base64.getDecoder().decode(s)
        val decoded = String(bytes, Charsets.UTF_8)
        val looksLikeText = decoded.isNotBlank() &&
                decoded.none { it.code < 0x20 && it != '\n' && it != '\t' } &&
                decoded.none { it == '�' } &&
                decoded.any { it.isLetter() }
        // Канонический round-trip отсекает ники, которые декодируются лишь по совпадению.
        if (looksLikeText && java.util.Base64.getEncoder().encodeToString(bytes) == s) decoded else raw
    } catch (e: IllegalArgumentException) {
        raw
    }
}
