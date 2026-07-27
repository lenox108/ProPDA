package forpdateam.ru.forpda.model.data.cache.forumuser

import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.db.ForumUserDao
import forpdateam.ru.forpda.entity.db.ForumUserRoom
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import java.util.concurrent.ConcurrentHashMap

class ForumUsersCacheRoom(
    private val forumUserDao: ForumUserDao,
    private val userSource: UserSource
) {

    /**
     * Негативный кэш промахов по нику. Без него КАЖДЫЙ ник, которого нет ни в Room, ни в
     * QMS-автокомплите (переименованные/удалённые/нестандартные ники), уходил в сеть при каждой
     * загрузке списка новостей — залп `qms-xhr&action=autocomplete-username`, за который 4pda
     * отвечает 429. Промах помним [NEGATIVE_TTL_MS]; явный пользовательский поиск ходит с
     * `useNegativeCache = false` и кэш промахов игнорирует.
     */
    private val missedNicks = ConcurrentHashMap<String, Long>()

    suspend fun saveUser(forumUser: ForumUser) = saveUsers(listOf(forumUser))

    suspend fun saveUsers(forumUsers: List<ForumUser>) {
        val forumUsersRoom = forumUsers.map {
            if (BuildConfig.DEBUG) {
                Timber.d("saveUser hasNick=${!it.nick.isNullOrBlank()}")
            }
            ForumUserRoom(
                id = it.id,
                nick = it.nick,
                avatar = it.avatar
            )
        }
        forumUserDao.insertUsers(forumUsersRoom)
    }

    suspend fun getUserById(id: Int): ForumUser? {
        val user = forumUserDao.getUserById(id) ?: return null
        return toForumUser(user)
    }

    suspend fun getUsersByIds(ids: Collection<Int>): Map<Int, ForumUser> {
        if (ids.isEmpty()) return emptyMap()
        return forumUserDao.getUsersByIds(ids.distinct())
                .associate { user -> user.id to toForumUser(user) }
    }

    private fun toForumUser(user: ForumUserRoom): ForumUser =
            ForumUser().apply {
                id = user.id
                nick = user.nick
                avatar = user.avatar
            }

    @JvmOverloads
    suspend fun getUserByNick(
            nick: String,
            useNegativeCache: Boolean = true,
            background: Boolean = false
    ): ForumUser? {
        val user = forumUserDao.getUserByNick(nick)
        if (user != null) {
            missedNicks.remove(nick)
            return ForumUser().apply {
                this.id = user.id
                this.nick = user.nick
                this.avatar = user.avatar
            }
        }
        if (useNegativeCache && isRecentMiss(nick)) {
            return null
        }
        // Fallback to userSource if not in cache
        val users = userSource.getUsers(nick, background)
        val firstUser = users.getOrNull(0)
        if (firstUser != null) {
            saveUser(firstUser)
            missedNicks.remove(nick)
        } else {
            rememberMiss(nick)
        }
        return firstUser
    }

    /** Сбрасывает память о промахах (например, после логина/смены аккаунта). */
    fun clearNickMisses() {
        missedNicks.clear()
    }

    private fun isRecentMiss(nick: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val missedAt = missedNicks[nick] ?: return false
        if (nowMs - missedAt > NEGATIVE_TTL_MS) {
            missedNicks.remove(nick, missedAt)
            return false
        }
        return true
    }

    private fun rememberMiss(nick: String, nowMs: Long = System.currentTimeMillis()) {
        if (nick.isBlank()) return
        if (missedNicks.size >= MAX_MISSED_NICKS) {
            // Дешёвая эвикция: чистим протухшие, иначе — самый старый.
            missedNicks.entries
                    .filter { nowMs - it.value > NEGATIVE_TTL_MS }
                    .forEach { missedNicks.remove(it.key, it.value) }
            if (missedNicks.size >= MAX_MISSED_NICKS) {
                missedNicks.entries.minByOrNull { it.value }?.let { missedNicks.remove(it.key, it.value) }
            }
        }
        missedNicks[nick] = nowMs
    }

    private companion object {
        /** Промах живёт 6 часов: ник, появившийся на форуме, подхватится в тот же день. */
        const val NEGATIVE_TTL_MS = 6 * 60 * 60 * 1000L
        const val MAX_MISSED_NICKS = 512
    }
}
