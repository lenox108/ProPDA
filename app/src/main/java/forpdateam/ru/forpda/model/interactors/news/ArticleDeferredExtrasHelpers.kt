package forpdateam.ru.forpda.model.interactors.news

import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.data.remote.api.news.ArticleFetchResult

/**
 * Pure helpers used by the deferred-extras phase in
 * [ArticleInteractor] (god-class §1.1). Splitting them out keeps the
 * interactor focused on orchestration while these helpers are easy
 * to unit test in isolation.
 */
object ArticleDeferredExtrasHelpers {

    /**
     * Builds a new fetch result whose [ArticleFetchResult.page] is a
     * shallow copy of [fetch] but uses the parser's body snapshot
     * ([ArticleFetchResult.parsedBodyHtml]) as the page's HTML. This
     * is required before the poll merge step: otherwise the merge
     * would mutate the original phase-1 HTML and the "is body
     * unchanged" check would always succeed.
     */
    fun buildDeferredExtrasFetch(fetch: ArticleFetchResult): ArticleFetchResult {
        val parsedBody = fetch.parsedBodyHtml.ifBlank { fetch.page.html.orEmpty() }
        val extrasPage = DetailsPage().apply {
            id = fetch.page.id
            title = fetch.page.title
            url = fetch.page.url
            imgUrl = fetch.page.imgUrl
            date = fetch.page.date
            author = fetch.page.author
            authorId = fetch.page.authorId
            commentsCount = fetch.page.commentsCount
            commentsSource = fetch.page.commentsSource
            desktopCommentsSource = fetch.page.desktopCommentsSource
            category = fetch.page.category
            karmaMap = fetch.page.karmaMap
            html = parsedBody
        }
        return fetch.copy(page = extrasPage)
    }

    /** True when the article HTML mentions a poll in any of the three legacy shapes. */
    fun hasPollBodyMarker(html: String?): Boolean {
        val source = html.orEmpty()
        return source.contains("poll", ignoreCase = true) ||
                source.contains("answer[]", ignoreCase = true) ||
                source.contains("pages/poll", ignoreCase = true)
    }

    /** True when the article HTML has the normalized poll block we ship from the template. */
    fun hasNormalizedPollBodyMarker(html: String?): Boolean {
        val source = html.orEmpty()
        return source.contains("news-poll-normalized", ignoreCase = true) ||
                source.contains("data-normalized-poll", ignoreCase = true) ||
                source.contains("data-poll-fallback", ignoreCase = true)
    }
}
