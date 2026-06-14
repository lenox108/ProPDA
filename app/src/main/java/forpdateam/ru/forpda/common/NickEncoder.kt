package forpdateam.ru.forpda.common

/**
 * @deprecated Используйте [Cp1251Codec.encodeSmart]. Оставлено для обратной совместимости.
 */
@Deprecated("Use Cp1251Codec.encodeSmart", ReplaceWith("Cp1251Codec.encodeSmart(nick)"))
object NickEncoder {
    fun encodeForSearch(nick: String): String = Cp1251Codec.encodeSmart(nick)
}
