package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.presentation.articles.detail.comments.InlineCommentsDisplayCount

/**
 * Merges phase-2 deferred extras without remapping article HTML when body is unchanged.
 */
object ArticleDeferredExtrasMerger {

    data class Patch(
            val articleId: Int,
            val commentsCount: Int,
            val hasCommentsSource: Boolean
    )

    fun isBodyUnchanged(beforeRaw: String?, afterRaw: String?): Boolean =
            bodyFingerprint(beforeRaw) == bodyFingerprint(afterRaw)

    fun applyMetadata(target: DetailsPage, source: DetailsPage, parsedDomCount: Int? = null) {
        val merged = InlineCommentsDisplayCount.mergeMetadataCount(target.commentsCount, source.commentsCount, parsedDomCount)
        if (merged != target.commentsCount) {
            target.commentsCount = merged
        }
        if (!source.commentsSource.isNullOrBlank()) {
            target.commentsSource = source.commentsSource
        }
        if (!source.desktopCommentsSource.isNullOrBlank()) {
            target.desktopCommentsSource = source.desktopCommentsSource
        }
        if (source.category != null) {
            target.category = source.category
        }
        if (!source.url.isNullOrBlank()) {
            target.url = source.url
        }
        if (source.karmaMap != null && source.karmaMap.size() > 0) {
            target.karmaMap = source.karmaMap
        }
    }

    fun buildPatch(page: DetailsPage): Patch = Patch(
            articleId = page.id,
            commentsCount = page.commentsCount,
            hasCommentsSource = !page.commentsSource.isNullOrBlank()
    )

    /** True when phase-2 desktop extras still need a network refetch after a fast/cache open. */
    fun needsDeferredExtras(
            article: DetailsPage,
            hintCommentsCount: Int = 0,
            hintCommentId: Int = 0
    ): Boolean {
        if (needsCommentsMetadataDeferredExtras(article, hintCommentsCount, hintCommentId)) {
            return true
        }
        return needsPollDeferredExtras(article)
    }

    fun needsCommentsMetadataDeferredExtras(
            article: DetailsPage,
            hintCommentsCount: Int = 0,
            hintCommentId: Int = 0
    ): Boolean = article.desktopCommentsSource.isNullOrBlank() &&
            !article.commentsSource.isNullOrBlank() &&
            (article.commentsCount > 0 || hintCommentsCount > 0 || hintCommentId > 0)

    fun needsPollDeferredExtras(article: DetailsPage): Boolean =
            needsPollNormalization(article.html) || needsPollFromTitle(article)

    /** Poll-news title but mapped body still lacks app-normalized poll block (mobile stripped markup). */
    private fun needsPollFromTitle(article: DetailsPage): Boolean {
        if (!article.title.orEmpty().contains("опрос", ignoreCase = true)) return false
        return !hasRenderablePollMarkup(article.html)
    }

    private fun hasRenderablePollMarkup(html: String?): Boolean {
        val source = html.orEmpty()
        return source.contains("news-poll-normalized", ignoreCase = true) ||
                source.contains("data-normalized-poll", ignoreCase = true) ||
                source.contains("data-poll-fallback", ignoreCase = true)
    }

    /** Mobile HTML may carry raw poll-ajax-frame markup that is not yet app-normalized. */
    private fun needsPollNormalization(html: String?): Boolean {
        val source = html.orEmpty()
        if (source.isBlank()) return false
        val hasPollCandidate = source.contains("poll-ajax-frame", ignoreCase = true) ||
                source.contains("pages/poll", ignoreCase = true) ||
                source.contains("data-site-poll", ignoreCase = true) ||
                source.contains("poll-frame", ignoreCase = true) ||
                source.contains("answer[]", ignoreCase = true)
        if (!hasPollCandidate) return false
        return !hasRenderablePollMarkup(source)
    }

    private fun bodyFingerprint(html: String?): String =
            html.orEmpty()
                    .replace(Regex("\\s+"), " ")
                    .trim()
}
