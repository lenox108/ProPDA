package forpdateam.ru.forpda.model.repository.avatar

import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Created by radiationx on 01.01.18.
 */
class AvatarRepository(
        private val forumUsersCache: ForumUsersCacheRoom
) {

    companion object {
        private const val WEBVIEW_LOOKUP_TIMEOUT_MS = 50L
        private const val AVATAR_URL_CACHE_SIZE = 512
    }

    private val avatarUrlByNick = LruCache<String, String>(AVATAR_URL_CACHE_SIZE)

    suspend fun getAvatar(id: Int, nick: String): String =
            withContext(Dispatchers.IO) {
                getAvatarSync(id, nick) ?: throw NullPointerException("No avatar/user by id: $id")
            }

    suspend fun getAvatar(id: Int): String =
            withContext(Dispatchers.IO) {
                getAvatarSync(id) ?: throw NullPointerException("No avatar/user by id: $id")
            }

    suspend fun getAvatar(nick: String): String =
            withContext(Dispatchers.IO) {
                fetchAvatarByNick(nick) ?: throw NullPointerException("No avatar/user by nick: $nick")
            }

    /** Блокирующая обёртка для вызова из Java (CustomWebViewClient.shouldInterceptRequest). */
    fun getAvatarSync(nick: String): String? = runBlocking(Dispatchers.IO) {
        getCachedAvatarUrl(nick) ?: forumUsersCache.getUserByNick(nick)?.avatar?.also {
            cacheAvatarUrl(nick, it)
        }
    }

    fun getAvatarForWebViewInterceptSync(nick: String): String? {
        getCachedAvatarUrl(nick)?.let { return it }
        return runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(WEBVIEW_LOOKUP_TIMEOUT_MS) {
                forumUsersCache.getUserByNick(nick)?.avatar?.also {
                    cacheAvatarUrl(nick, it)
                }
            }
        }
    }

    private suspend fun getAvatarSync(id: Int, nick: String): String? {
        val forumUser = forumUsersCache.getUserById(id)
                ?: forumUsersCache.getUserByNick(nick)
        return forumUser?.avatar?.also { cacheAvatarUrl(nick, it) }
    }

    private suspend fun getAvatarSync(id: Int): String? = forumUsersCache.getUserById(id)?.avatar

    private suspend fun fetchAvatarByNick(nick: String): String? =
            getCachedAvatarUrl(nick) ?: forumUsersCache.getUserByNick(nick)?.avatar?.also {
                cacheAvatarUrl(nick, it)
            }

    private fun getCachedAvatarUrl(nick: String): String? = avatarUrlByNick.get(nick)

    private fun cacheAvatarUrl(nick: String, avatarUrl: String) {
        if (nick.isNotBlank() && avatarUrl.isNotBlank()) {
            avatarUrlByNick.put(nick, avatarUrl)
        }
    }
}
