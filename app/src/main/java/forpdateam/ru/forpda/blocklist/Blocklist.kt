package forpdateam.ru.forpda.blocklist

/**
 * Набор забаненных аккаунтов: по стабильному user id и/или по нику (в нижнем
 * регистре). Ник удобнее для ведения списка, id надёжнее (ник на 4pda можно сменить).
 */
data class Blocklist(
    val ids: Set<Int> = emptySet(),
    /** Ники в нижнем регистре — сравнение регистронезависимое. */
    val nicks: Set<String> = emptySet(),
) {
    fun isEmpty(): Boolean = ids.isEmpty() && nicks.isEmpty()

    fun matches(userId: Int, nick: String?): Boolean {
        if (userId != forpdateam.ru.forpda.entity.common.AuthData.NO_ID && ids.contains(userId)) return true
        val n = nick?.trim()?.lowercase()
        return !n.isNullOrEmpty() && nicks.contains(n)
    }
}
