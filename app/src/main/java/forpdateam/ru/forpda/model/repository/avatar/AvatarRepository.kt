package forpdateam.ru.forpda.model.repository.avatar

import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Created by radiationx on 01.01.18.
 *
 * **Public entry points (AUDIT-L09 contract):**
 *
 *  1. [getAvatar] (id + nick) — `suspend`; throws
 *     [AvatarNotFoundException] on miss. Use from coroutine code when
 *     you already have both the user id and the nick and want one
 *     canonical answer.
 *  2. [getAvatar] (id only) — `suspend`; throws on miss. Use when you
 *     only have the id.
 *  3. [getAvatar] (nick only) — `suspend`; throws on miss. Use when
 *     you only have the nick. Internally falls back to
 *     `userSource.getUsers(nick)` via the cache's `getUserByNick`.
 *  4. [getAvatarForWebViewInterceptSync] (nick) — **blocking** but bounded
 *     by [WEBVIEW_LOOKUP_TIMEOUT_MS] (50ms) so the WebView resource
 *     loader can never block for more than 50ms; returns `null` on miss.
 *     **Must not throw** — `CustomWebViewClient.shouldInterceptRequest`
 *     has no try/catch around the lookup.
 *
 * (An unbounded `getAvatarSync(nick)` blocking variant used to exist for a
 * hypothetical Java bridge, but it had no callers and no main-thread guard
 * — an ANR footgun — so it was removed. Use [getAvatar] from coroutines or
 * the time-bounded [getAvatarForWebViewInterceptSync] from the WebView path.)
 *
 * The four entry points are kept separate because the contract
 * (suspending vs blocking, throws vs null, time-bounded vs not)
 * matters at every call site. A future consolidation into a single
 * `AvatarLoader` facade is welcome as long as each of the 4 contracts
 * is preserved (the
 * `AvatarRepository` is *already* the "AvatarLoader" in spirit; the
 * audit's suggested rename is documentation-only and is not done in
 * this pass to keep the diff small).
 */
class AvatarRepository(
        private val forumUsersCache: ForumUsersCacheRoom
) {

    companion object {
        /**
         * Hard upper bound for the WebView intercept path. The lookup
         * runs on the WebView's resource loader thread; if the cache
         * miss + DB roundtrip would take longer than this, we return
         * `null` and let the WebView fall back to its default behaviour
         * (broken-image icon).
         */
        private const val WEBVIEW_LOOKUP_TIMEOUT_MS = 50L

        /**
         * In-memory LRU for nick → avatar URL. Capped at 512 entries
         * (~64 KiB at 128B/URL). The cache is *not* a substitute for
         * the DB cache; it just avoids re-mapping the URL on every
         * list-item rebind.
         */
        private const val AVATAR_URL_CACHE_SIZE = 512
    }

    private val avatarUrlByNick = LruCache<String, String>(AVATAR_URL_CACHE_SIZE)

    suspend fun getAvatar(id: Int, nick: String): String =
            withContext(Dispatchers.IO) {
                getAvatarSync(id, nick) ?: throw AvatarNotFoundException(avatarId = id, nick = nick)
            }

    suspend fun getAvatar(id: Int): String =
            withContext(Dispatchers.IO) {
                getAvatarSync(id) ?: throw AvatarNotFoundException(avatarId = id)
            }

    suspend fun getAvatar(nick: String): String =
            withContext(Dispatchers.IO) {
                fetchAvatarByNick(nick) ?: throw AvatarNotFoundException(nick = nick)
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
