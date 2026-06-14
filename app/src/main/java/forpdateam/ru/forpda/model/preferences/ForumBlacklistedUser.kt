package forpdateam.ru.forpda.model.preferences

data class ForumBlacklistedUser(
        val userId: Int,
        val nick: String
) {
    fun matches(userId: Int, nick: String?): Boolean {
        if (this.userId > 0 && userId > 0) return this.userId == userId
        val normalizedNick = normalizeNick(nick)
        return normalizedNick.isNotEmpty() && normalizeNick(this.nick) == normalizedNick
    }

    fun stableKey(): String =
            userId.takeIf { it > 0 }?.toString() ?: normalizeNick(nick)

    companion object {
        fun normalizeNick(nick: String?): String =
                nick.orEmpty().trim().lowercase()
    }
}

object ForumBlacklistSerializer {
    private const val ITEM_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\t"

    fun serialize(users: Collection<ForumBlacklistedUser>): String =
            users
                    .mapNotNull { user ->
                        val nick = user.nick.trim()
                        if (user.userId <= 0 && nick.isBlank()) {
                            null
                        } else {
                            "${user.userId}$FIELD_SEPARATOR${escape(nick)}"
                        }
                    }
                    .joinToString(ITEM_SEPARATOR)

    fun deserialize(raw: String?): List<ForumBlacklistedUser> =
            raw.orEmpty()
                    .lineSequence()
                    .mapNotNull { line ->
                        val parts = line.split(FIELD_SEPARATOR, limit = 2)
                        val userId = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val nick = unescape(parts.getOrNull(1).orEmpty()).trim()
                        ForumBlacklistedUser(userId, nick)
                                .takeIf { it.userId > 0 || it.nick.isNotBlank() }
                    }
                    .distinctBy { it.stableKey() }
                    .toList()

    fun add(raw: String?, user: ForumBlacklistedUser): String {
        val normalizedUser = ForumBlacklistedUser(user.userId, user.nick.trim())
        val users = deserialize(raw).toMutableList()
        val key = normalizedUser.stableKey()
        if (key.isBlank() || users.any { it.stableKey() == key }) return serialize(users)
        users.add(normalizedUser)
        return serialize(users)
    }

    fun remove(raw: String?, user: ForumBlacklistedUser): String {
        val key = user.stableKey()
        if (key.isBlank()) return raw.orEmpty()
        return serialize(deserialize(raw).filterNot { it.stableKey() == key })
    }

    private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(value: String): String {
        val result = StringBuilder(value.length)
        var escaping = false
        value.forEach { ch ->
            if (escaping) {
                result.append(
                        when (ch) {
                            't' -> '\t'
                            'n' -> '\n'
                            else -> ch
                        }
                )
                escaping = false
            } else if (ch == '\\') {
                escaping = true
            } else {
                result.append(ch)
            }
        }
        if (escaping) result.append('\\')
        return result.toString()
    }
}
