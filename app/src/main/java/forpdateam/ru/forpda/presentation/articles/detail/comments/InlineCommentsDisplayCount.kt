package forpdateam.ru.forpda.presentation.articles.detail.comments

/**
 * Keeps article metadata comment totals from overwriting a lower DOM/parsed count.
 */
object InlineCommentsDisplayCount {

    /** Authoritative total for badges and pagination (article parse vs list navigation hint). */
    fun resolveExpectedCount(articleCount: Int, hintCount: Int = 0): Int =
            maxOf(articleCount.coerceAtLeast(0), hintCount.coerceAtLeast(0))

    fun mergeMetadataCount(currentCount: Int, metadataCount: Int, domCount: Int?): Int {
        val expected = resolveExpectedCount(currentCount, metadataCount)
        val ceiling = domCount?.takeIf { it > 0 } ?: return expected
        if (metadataCount <= currentCount) return currentCount.coerceAtLeast(ceiling)
        if (metadataCount > ceiling &&
                ArticleCommentsPagination.isLikelyPaginatedPartialBatch(ceiling, metadataCount)
        ) {
            // First lazy batch (~20 nodes) must not cap list/article totals (e.g. 33 expected).
            return expected
        }
        return if (metadataCount > ceiling) currentCount.coerceAtLeast(ceiling) else expected
    }

    fun shouldPatchWebViewAfterPrefetch(articleCount: Int, parsedCount: Int): Boolean =
            parsedCount > 0 && articleCount >= parsedCount
}
