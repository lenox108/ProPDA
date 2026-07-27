package forpdateam.ru.forpda.presentation.qms.chat

/**
 * История ников, которым уже создавали темы QMS. Хранится одной строкой (ники через '\n'),
 * свежие первыми; форма создания темы подставляет её в выпадающий список поля ника.
 */
object QmsNickHistory {

    /** Сколько ников помним. Дальше список в выпадашке перестаёт быть обозримым. */
    const val MAX_SIZE = 15

    private const val SEPARATOR = '\n'

    fun parse(raw: String?): List<String> = raw
            .orEmpty()
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_SIZE)

    fun serialize(nicks: List<String>): String = nicks.joinToString(SEPARATOR.toString())

    /**
     * Добавляет ник в начало списка. Повтор (без учёта регистра) поднимается наверх в том виде,
     * в каком его ввели сейчас, а не дублируется.
     */
    fun add(raw: String?, nick: String): String {
        val trimmed = nick.trim()
        if (trimmed.isEmpty()) return raw.orEmpty()
        val rest = parse(raw).filterNot { it.equals(trimmed, ignoreCase = true) }
        return serialize((listOf(trimmed) + rest).take(MAX_SIZE))
    }
}
