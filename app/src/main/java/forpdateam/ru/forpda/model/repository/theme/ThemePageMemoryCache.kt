package forpdateam.ru.forpda.model.repository.theme

import android.os.SystemClock
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import java.lang.ref.SoftReference
import java.util.Locale

/**
 * Short-lived in-memory cache of parsed [ThemePage] (pre-template) to avoid repeat network
 * round-trips when reopening the same topic page.
 *
 * **Memory model (AUDIT-L08).** The value side is wrapped in [SoftReference] so the JVM
 * can release cached pages under memory pressure (e.g. when the WebView renderer swells
 * or the system approaches its OOM threshold). The [Key] / expiry metadata is kept as
 * a hard reference so the LRU ordering survives a soft-clear, and the next access to a
 * still-soft-referenced page simply re-fetches from the network.
 *
 * The LRU eviction policy is still active on top of the soft references: [maxEntries]
 * bounds the **map size**, not the live set of pages. The actual heap footprint of
 * this cache is the sum of pages that have not yet been reclaimed by the GC.
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

    private data class Entry(
            val pageRef: SoftReference<ThemePage>,
            val renderSignature: String?,
            val expiresAt: Long
    )

    private val store = LinkedHashMap<Key, Entry>(maxEntries, 0.75f, true)

    /**
     * Last theme/render signature the cache was validated against. When the global signature
     * changes (palette/density/font/dark-mode/avatar/blacklist), every cached page is stale-styled,
     * so [invalidateOnSignatureChange] clears the whole store. `null` means "not yet tracked".
     */
    private var trackedSignature: String? = null

    fun keyFrom(url: String, hatOpen: Boolean, pollOpen: Boolean): Key? {
        val topicId = ThemeApi.extractTopicIdFromUrl(url) ?: return null
        if (topicId <= 0) return null
        val st = ThemeApi.extractStFromUrl(url) ?: 0
        return Key(topicId, st, hatOpen, pollOpen)
    }

    fun get(key: Key): ThemePage? = get(key, expectedSignature = null)

    /**
     * Signature-aware read (Phase 6B). When [expectedSignature] is non-null, a cached entry whose
     * [ThemePage.renderSignature] no longer matches is treated as stale: it is evicted and a miss is
     * returned, so a palette/density/font change can never render a stale-styled page. Passing `null`
     * preserves the legacy behavior (no signature check).
     */
    fun get(key: Key, expectedSignature: String?): ThemePage? {
        pruneExpired()
        val entry = store[key] ?: return null
        if (entry.expiresAt <= nowMs()) {
            store.remove(key)
            return null
        }
        if (expectedSignature != null && entry.renderSignature != expectedSignature) {
            store.remove(key)
            return null
        }
        // SoftReference may have been cleared by the GC between put() and get().
        // Treat that as a miss so the caller re-fetches.
        val page = entry.pageRef.get() ?: run {
            store.remove(key)
            return null
        }
        return page.copyForCache()
    }

    fun put(key: Key, page: ThemePage) {
        pruneExpired()
        while (store.size >= maxEntries) {
            val eldest = store.entries.firstOrNull() ?: break
            store.remove(eldest.key)
        }
        store[key] = Entry(
                pageRef = SoftReference(page.copyForCache()),
                renderSignature = page.renderSignature,
                expiresAt = nowMs() + ttlMs
        )
    }

    fun invalidateTopic(topicId: Int) {
        if (topicId <= 0) return
        store.keys.removeAll { it.topicId == topicId }
    }

    /**
     * Clears the whole cache when the global theme/render [signature] changes (Phase 6B). Call on
     * theme/palette change, density change, font-mode change, avatar-mode change, blacklist change —
     * any global change that restyles every page. Returns true if the cache was cleared.
     *
     * The first call only records the signature (no clear). A `null` signature is ignored so an
     * unknown signature never wipes a valid cache.
     */
    fun invalidateOnSignatureChange(signature: String?): Boolean {
        if (signature == null) return false
        val previous = trackedSignature
        trackedSignature = signature
        if (previous != null && previous != signature) {
            clear()
            return true
        }
        return false
    }

    fun clear() {
        store.clear()
    }

    private fun pruneExpired() {
        val now = nowMs()
        store.entries.removeAll { it.value.expiresAt <= now }
    }

    /**
     * Test/observability hook. Returns the current size of the **map** (not the
     * number of pages the JVM is still holding hard refs to). The map size is
     * bounded by [maxEntries]; the live set may be smaller when soft references
     * have been reclaimed.
     */
    fun size(): Int = store.size

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
    copy.highlightTarget = highlightTarget
    copy.renderGenerationId = renderGenerationId
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
