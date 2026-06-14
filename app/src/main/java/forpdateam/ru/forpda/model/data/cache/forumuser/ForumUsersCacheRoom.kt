package forpdateam.ru.forpda.model.data.cache.forumuser

import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.db.ForumUserDao
import forpdateam.ru.forpda.entity.db.ForumUserRoom
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser

class ForumUsersCacheRoom(
    private val forumUserDao: ForumUserDao,
    private val userSource: UserSource
) {

    private val requestsInSession = mutableSetOf<String>()

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

    suspend fun getUserByNick(nick: String): ForumUser? {
        val user = forumUserDao.getUserByNick(nick)
        if (user != null) {
            return ForumUser().apply {
                this.id = user.id
                this.nick = user.nick
                this.avatar = user.avatar
            }
        }
        // Fallback to userSource if not in cache
        val users = userSource.getUsers(nick)
        val firstUser = users.getOrNull(0)
        if (firstUser != null) {
            saveUser(firstUser)
        }
        return firstUser
    }
}
