package forpdateam.ru.forpda.model.repository.theme

import android.os.SystemClock
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import java.util.Locale

/**
 * Short-lived in-memory cache of parsed [ThemePage] (pre-template) to avoid repeat network
 * round-trips when reopening the same topic page.
 */
class ThemePageMemoryCache(
        private val ttlMs: Long = DEFAULT_TTL_MS,
        private val nowMs: () -> Long = { SystemClock.uptimeMillis() },
        private val maxEntries: Int = 24
) {

    data class Key(
            val topicId: Int,
            val st: Int,
            val hatOpen: Boolean,
            val pollOpen: Boolean
    )

    private data class Entry(val page: ThemePage, val expiresAt: Long)

    private val store = LinkedHashMap<Key, Entry>(maxEntries, 0.75f, true)

    fun keyFrom(url: String, hatOpen: Boolean, pollOpen: Boolean): Key? {
        val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: return null
        if (topicId <= 0) return null
        val st = ThemeApi.extractStFromUrl(url) ?: 0
        return Key(topicId, st, hatOpen, pollOpen)
    }

    fun get(key: Key): ThemePage? {
        pruneExpired()
        val entry = store[key] ?: return null
        if (entry.expiresAt <= nowMs()) {
            store.remove(key)
            return null
        }
        return entry.page.copyForCache()
    }

    fun put(key: Key, page: ThemePage) {
        pruneExpired()
        while (store.size >= maxEntries) {
            val eldest = store.entries.firstOrNull() ?: break
            store.remove(eldest.key)
        }
        store[key] = Entry(page.copyForCache(), nowMs() + ttlMs)
    }

    fun invalidateTopic(topicId: Int) {
        if (topicId <= 0) return
        store.keys.removeAll { it.topicId == topicId }
    }

    fun clear() {
        store.clear()
    }

    private fun pruneExpired() {
        val now = nowMs()
        store.entries.removeAll { it.value.expiresAt <= now }
    }

    companion object {
        const val DEFAULT_TTL_MS = 7 * 60 * 1000L

        fun shouldSkipCache(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            if (lower.contains("act=findpost")) return true
            if (lower.contains("view=findpost")) return true
            if (lower.contains("view=getnewpost")) return true
            if (lower.contains("view=getlastpost")) return true
            if (Regex("""[?&]pid=\d+""").containsMatchIn(lower)) return true
            if (Regex("""[?&]p=\d+""").containsMatchIn(lower)) return true
            return false
        }
    }
}

internal fun ThemePage.copyForCache(): ThemePage {
    val copy = ThemePage()
    copy.title = title
    copy.desc = desc
    copy.html = html
    copy.url = url
    copy.id = id
    copy.forumId = forumId
    copy.favId = favId
    copy.scrollY = scrollY
    copy.anchorPostId = anchorPostId
    copy.anchorOffsetTop = anchorOffsetTop
    copy.scrollRatio = scrollRatio
    copy.wasNearBottom = wasNearBottom
    copy.refreshRestoreId = refreshRestoreId
    copy.refreshRestoreMode = refreshRestoreMode
    copy.refreshRestoreSource = refreshRestoreSource
    copy.renderSignature = renderSignature
    copy.postsFragmentHtml = postsFragmentHtml
    copy.isInFavorite = isInFavorite
    copy.isCurator = isCurator
    copy.canQuote = canQuote
    copy.isHatOpen = isHatOpen
    copy.isInlineHatOpen = isInlineHatOpen
    copy.isPollOpen = isPollOpen
    copy.hasUnreadTarget = hasUnreadTarget
    copy.topicHatPost = topicHatPost
    copy.pagination = pagination
    copy.poll = poll
    copy.anchors.addAll(anchors)
    copy.posts.addAll(posts)
    return copy
}
