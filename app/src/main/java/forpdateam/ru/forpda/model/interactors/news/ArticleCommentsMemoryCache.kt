package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.entity.remote.news.Comment

/**
 * In-memory parsed comment trees keyed by article id (per process).
 */
class ArticleCommentsMemoryCache(
        private val maxEntries: Int = 16,
        private val maxAgeMs: Long = 20 * 60 * 1000L
) {

    data class Entry(
            val tree: Comment,
            val storedAtMs: Long
    )

    data class Lookup(
            val entry: Entry?,
            val hit: Boolean,
            val valid: Boolean,
            val reason: String?
    )

    private val entries = LinkedHashMap<Int, Entry>(maxEntries, 0.75f, true)

    @Synchronized
    fun get(articleId: Int, nowMs: Long = System.currentTimeMillis()): Lookup {
        if (articleId <= 0) {
            return Lookup(null, hit = false, valid = false, reason = "missing_id")
        }
        val entry = entries[articleId]
        if (entry == null) {
            log(event = "miss", articleId = articleId, hit = false, valid = false, reason = "not_found")
            return Lookup(null, hit = false, valid = false, reason = "not_found")
        }
        val ageMs = nowMs - entry.storedAtMs
        if (ageMs > maxAgeMs) {
            entries.remove(articleId)
            log(event = "rejected_stale", articleId = articleId, hit = true, valid = false, reason = "expired")
            return Lookup(null, hit = true, valid = false, reason = "expired")
        }
        log(event = "hit", articleId = articleId, hit = true, valid = true, reason = "ok", extra = mapOf("ageMs" to ageMs))
        return Lookup(entry, hit = true, valid = true, reason = null)
    }

    @Synchronized
    fun put(articleId: Int, tree: Comment, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (articleId <= 0 || tree.children.isEmpty()) {
            log(event = "rejected_empty", articleId = articleId, hit = false, valid = false, reason = "empty_tree")
            return false
        }
        entries[articleId] = Entry(tree, nowMs)
        trim()
        log(event = "write_ok", articleId = articleId, hit = false, valid = true, reason = "stored")
        return true
    }

    @Synchronized
    fun invalidate(articleId: Int = -1) {
        if (articleId > 0) {
            entries.remove(articleId)
        } else {
            entries.clear()
        }
    }

    private fun trim() {
        while (entries.size > maxEntries) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            entries.remove(oldestKey)
        }
    }

    private fun log(
            event: String,
            articleId: Int,
            hit: Boolean,
            valid: Boolean,
            reason: String,
            extra: Map<String, Any?> = emptyMap()
    ) {
        ArticleCacheTrace.log(
                event = event,
                articleId = articleId,
                cacheLayer = "comments_tree",
                hit = hit,
                valid = valid,
                reason = reason,
                extra = extra
        )
    }
}
