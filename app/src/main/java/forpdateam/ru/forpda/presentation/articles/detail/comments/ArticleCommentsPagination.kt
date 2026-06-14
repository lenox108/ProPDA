package forpdateam.ru.forpda.presentation.articles.detail.comments

/**
 * WordPress-style comment pagination for 4pda news articles.
 *
 * Server serves comment HTML in pages; mobile phase-1 may embed the first lazy batch (~10 nodes).
 * Further batches are fetched with the `cp` query parameter (1-indexed comment page).
 */
object ArticleCommentsPagination {

    /** Matches typical WP / 4pda comment-page size and [InlineCommentsBatchConfig.BATCH_SIZE]. */
    const val COMMENTS_PER_PAGE = InlineCommentsBatchConfig.BATCH_SIZE

    private val commentPagePathRegex = Regex("""(?i)/comment-page-(\d+)/?""")
    private val repeatedSlashRegex = Regex("(?<!:)/+")

    /**
     * Builds a URL for a specific comment page. Page 1 is the article URL without `cp`.
     */
    fun withCommentPage(baseUrl: String, page: Int): String {
        val trimmed = baseUrl.trim().substringBefore("#").trimEnd('/')
        if (page <= 1) {
            return stripCommentPage(trimmed)
        }
        val withoutCp = stripCommentPage(trimmed)
        if (withoutCp.contains("index.php", ignoreCase = true)) {
            return appendOrReplaceQueryParam(ensureAbsolute(withoutCp), "cp", page.toString())
        }
        val slugBase = commentPagePathRegex.replace(withoutCp, "").trimEnd('/')
        return "$slugBase/comment-page-$page/"
    }

    fun hasMore(loadedCount: Int, totalExpected: Int): Boolean =
            when {
                totalExpected > loadedCount -> true
                // Badge under-counted vs loaded batch (nested replies / stale counter).
                loadedCount > totalExpected && loadedCount >= COMMENTS_PER_PAGE -> true
                // Badge missing: a full WP page (~20) usually means more batches exist.
                totalExpected <= 0 && loadedCount >= COMMENTS_PER_PAGE -> true
                else -> false
            }

    /**
     * True when [loadedCount] looks like an in-progress paginated batch (mobile ~10 or WP ~20),
     * not a completed parse. Used to avoid clamping the badge count down during pagination.
     */
    fun isLikelyPaginatedPartialBatch(loadedCount: Int, totalExpected: Int): Boolean {
        if (totalExpected <= 0 || loadedCount <= 0 || loadedCount >= totalExpected) return false
        return loadedCount <= COMMENTS_PER_PAGE
    }

    /**
     * True when the article badge should stay at [totalExpected] instead of being clamped to
     * [loadedCount]. Paginated sessions may flatten nested replies above [COMMENTS_PER_PAGE].
     */
    fun shouldPreserveExpectedCount(
            loadedCount: Int,
            totalExpected: Int,
            paginatedSessionActive: Boolean = false,
    ): Boolean {
        if (paginatedSessionActive && hasMore(loadedCount, totalExpected)) return true
        if (isLikelyPaginatedPartialBatch(loadedCount, totalExpected)) return true
        // Nested replies can push flattened count above [COMMENTS_PER_PAGE] on page 1 while the
        // badge still reflects many more top-level pages (e.g. 27 loaded vs 181 expected).
        return totalExpected > loadedCount &&
                loadedCount > COMMENTS_PER_PAGE &&
                totalExpected <= loadedCount * 7
    }

    fun nextPageAfter(currentPage: Int): Int = (currentPage + 1).coerceAtLeast(1)

    fun extractCommentPageFromUrl(url: String?): Int {
        if (url.isNullOrBlank()) return 1
        commentPagePathRegex.find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        return queryParam(url, "cp")?.toIntOrNull()?.takeIf { it > 0 } ?: 1
    }

    private fun stripCommentPage(url: String): String {
        var result = commentPagePathRegex.replace(url, "/")
        result = repeatedSlashRegex.replace(result, "/")
        return removeQueryParam(result, "cp").trimEnd('/')
    }

    private fun appendOrReplaceQueryParam(url: String, name: String, value: String): String {
        val without = removeQueryParam(url, name)
        val separator = if (without.contains('?')) "&" else "?"
        return "$without$separator$name=$value"
    }

    private fun removeQueryParam(url: String, paramName: String): String {
        val hashIndex = url.indexOf('#')
        val base = if (hashIndex >= 0) url.substring(0, hashIndex) else url
        val fragment = if (hashIndex >= 0) url.substring(hashIndex) else ""
        val queryIndex = base.indexOf('?')
        if (queryIndex < 0) return url
        val path = base.substring(0, queryIndex)
        val query = base.substring(queryIndex + 1)
        val kept = query.split('&')
                .filter { part ->
                    val key = part.substringBefore('=')
                    key.isNotBlank() && !key.equals(paramName, ignoreCase = true)
                }
        val rebuilt = if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
        return rebuilt + fragment
    }

    private fun queryParam(url: String, name: String): String? {
        val query = url.substringAfter('?', missingDelimiterValue = "")
                .substringBefore('#')
                .takeIf { it.isNotBlank() }
                ?: return null
        return query.split('&')
                .mapNotNull { part ->
                    val key = part.substringBefore('=')
                    if (!key.equals(name, ignoreCase = true)) return@mapNotNull null
                    part.substringAfter('=', missingDelimiterValue = "")
                }
                .firstOrNull()
    }

    private fun ensureAbsolute(url: String): String =
            if (url.startsWith("http", ignoreCase = true)) url else "https://4pda.to/${url.trimStart('/')}"
}
