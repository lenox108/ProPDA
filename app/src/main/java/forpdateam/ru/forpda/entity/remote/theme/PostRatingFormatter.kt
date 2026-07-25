package forpdateam.ru.forpda.entity.remote.theme

/**
 * Canonical display format for a post rating.
 *
 * The forum can return a positive rating both with and without a leading plus, depending on the
 * HTML endpoint. Keep the sign independent from that source: positive values use `+`, negative
 * values use `-`, and zero remains unsigned.
 */
object PostRatingFormatter {

    fun normalize(raw: String?): String? {
        val compact = raw
                ?.filterNot { it.isWhitespace() }
                ?.replace('−', '-')
                ?.replace('–', '-')
                ?.takeIf { it.isNotBlank() }
                ?: return null
        return parse(compact)?.let(::format) ?: compact
    }

    fun parse(raw: String?): Int? {
        return raw
                ?.filterNot { it.isWhitespace() }
                ?.replace('−', '-')
                ?.replace('–', '-')
                ?.toIntOrNull()
    }

    fun format(value: Int): String = if (value > 0) "+$value" else value.toString()
}
