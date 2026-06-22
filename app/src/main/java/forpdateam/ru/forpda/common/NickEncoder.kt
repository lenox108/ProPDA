package forpdateam.ru.forpda.common

/**
 * @deprecated Используйте [Cp1251Codec.encodeSmart]. Оставлено для обратной совместимости.
 *
 * Note (AUDIT-L05): this object only exposes the **encode** direction
 * (CP1251 → URL-safe form) and is **not** an HTML-entity decoder. The
 * "3 HTML decoders" item in the audit's L05 section
 * ([ApiUtils.fromHtml] / [ApiUtils.spannedFromHtml] / `NickEncoder.decode`)
 * is incorrect — `decode` does not exist. Use [ApiUtils.coloredFromHtml]
 * or [ApiUtils.spannedFromHtml] for HTML-entity decoding.
 */
@Deprecated("Use Cp1251Codec.encodeSmart", ReplaceWith("Cp1251Codec.encodeSmart(nick)"))
object NickEncoder {
    fun encodeForSearch(nick: String): String = Cp1251Codec.encodeSmart(nick)
}
