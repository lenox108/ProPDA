package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.diagnostic.ArticleCacheTrace
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.news.ARTICLE_PARSER_VERSION
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleHtmlValidator

/**
 * In-memory mapped article cache (per process). Never stores empty/login/error pages.
 */
class ArticleMemoryCache(
        private val maxEntries: Int = 12,
        private val maxAgeMs: Long = 30 * 60 * 1000L
) {

    data class Entry(
            val page: DetailsPage,
            val parserVersion: Int,
            val storedAtMs: Long,
            val deferredExtrasPending: Boolean = false
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
            ArticleCacheTrace.log(
                    event = "miss",
                    articleId = articleId,
                    cacheLayer = "memory",
                    hit = false,
                    valid = false,
                    reason = "not_found"
            )
            return Lookup(null, hit = false, valid = false, reason = "not_found")
        }
        val verdict = ArticleHtmlValidator.validateCached(
                page = entry.page,
                parserVersion = entry.parserVersion,
                storedAtMs = entry.storedAtMs,
                maxAgeMs = maxAgeMs,
                nowMs = nowMs
        )
        if (!verdict.valid) {
            entries.remove(articleId)
            ArticleCacheTrace.log(
                    event = "rejected_invalid",
                    articleId = articleId,
                    cacheLayer = "memory",
                    hit = true,
                    valid = false,
                    mappedHtmlLen = entry.page.html?.length,
                    reason = verdict.reason
            )
            return Lookup(null, hit = true, valid = false, reason = verdict.reason)
        }
        ArticleCacheTrace.log(
                event = "hit",
                articleId = articleId,
                cacheLayer = "memory",
                hit = true,
                valid = true,
                mappedHtmlLen = entry.page.html?.length,
                reason = "ok",
                extra = mapOf("ageMs" to (nowMs - entry.storedAtMs))
        )
        return Lookup(
                entry = entry,
                hit = true,
                valid = true,
                reason = if (entry.deferredExtrasPending) "deferred_extras_pending" else null
        )
    }

    @Synchronized
    fun put(page: DetailsPage, nowMs: Long = System.currentTimeMillis()): Boolean {
        val id = page.id
        if (id <= 0) {
            ArticleCacheTrace.log(
                    event = "rejected_empty",
                    articleId = id,
                    cacheLayer = "memory",
                    hit = false,
                    valid = false,
                    reason = "missing_id"
            )
            return false
        }
        if (!ArticleHtmlValidator.hasNonEmptyParsedBody(page)) {
            ArticleCacheTrace.log(
                    event = "rejected_empty",
                    articleId = id,
                    cacheLayer = "memory",
                    hit = false,
                    valid = false,
                    mappedHtmlLen = page.html?.length,
                    reason = "empty_body"
            )
            return false
        }
        if (page.title.isNullOrBlank()) {
            ArticleCacheTrace.log(
                    event = "rejected_empty",
                    articleId = id,
                    cacheLayer = "memory",
                    hit = false,
                    valid = false,
                    reason = "missing_title"
            )
            return false
        }
        entries[id] = Entry(
                page = page,
                parserVersion = ARTICLE_PARSER_VERSION,
                storedAtMs = nowMs,
                deferredExtrasPending = ArticleDeferredExtrasMerger.needsDeferredExtras(page)
        )
        trim()
        ArticleCacheTrace.log(
                event = "write_ok",
                articleId = id,
                cacheLayer = "memory",
                hit = false,
                valid = true,
                mappedHtmlLen = page.html?.length,
                reason = "stored"
        )
        return true
    }

    @Synchronized
    fun invalidate(articleId: Int = -1) {
        if (articleId > 0) {
            entries.remove(articleId)
            ArticleCacheTrace.log(
                    event = "invalidate",
                    articleId = articleId,
                    cacheLayer = "memory",
                    hit = false,
                    valid = false,
                    reason = "explicit"
            )
        } else {
            entries.clear()
            ArticleCacheTrace.log(
                    event = "invalidate",
                    cacheLayer = "memory",
                    hit = false,
                    valid = false,
                    reason = "clear_all"
            )
        }
    }

    private fun trim() {
        while (entries.size > maxEntries) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            entries.remove(oldestKey)
        }
    }
}
