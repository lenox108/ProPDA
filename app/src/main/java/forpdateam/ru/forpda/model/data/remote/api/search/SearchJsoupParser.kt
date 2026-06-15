package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jsoup-based implementation of the news-articles branch of
 * [SearchParser]. The forum-topics / forum-posts branches
 * remain on the regex path because their patterns encode
 * nuanced position-based fields (curator flag, group color,
 * rep-button presence, etc.) that the regex captures via
 * `[\s\S]*?` chains. The news-articles branch is a clean
 * `li > div.photo > a > img + …` block, which Jsoup handles
 * robustly.
 *
 * Selected via the `useJsoup` flag in [SearchParser]; the
 * default stays regex until the new path is verified in
 * production.
 */
class SearchJsoupParser : BaseParser() {

    fun parseArticles(response: String, settings: SearchSettings, into: SearchResult) {
        val document = Jsoup.parse(response)
        val rows = document.select("li").filter { it.selectFirst("div.photo") != null }
        for (row in rows) {
            val item = parseRow(row) ?: continue
            into.items.add(item)
        }
        into.pagination = Pagination.parseNews(response)
        into.settings = settings
    }

    private fun parseRow(row: Element): SearchItem? {
        val photoDiv = row.selectFirst("div.photo") ?: return null
        val topAnchor = photoDiv.selectFirst("a[href]") ?: return null
        val articleId = Regex("""/(\d+)/""").find(topAnchor.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
        val imageUrl = topAnchor.selectFirst("img")?.attr("src")
        val date = row.selectFirst(".date")?.text()?.trim()
                ?: row.selectFirst("[class*=date]")?.text()?.trim()
        val userAnchor = row.selectFirst("a[href*=showuser=]")
        val userId = userAnchor?.attr("href")?.let { href ->
            Regex("""showuser=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
        } ?: return null
        val nick = userAnchor.text().fromHtml()
        val titleAnchor = row.select("h1, h2, h3, h4, h5, h6").firstOrNull()?.selectFirst("a")
        val title = titleAnchor?.text()?.fromHtml()
        val body = row.selectFirst("p")?.selectFirst("a")?.text()

        return SearchItem().apply {
            id = articleId
            this.imageUrl = imageUrl
            this.date = date
            this.userId = userId
            this.nick = nick
            this.title = title
            this.body = body
        }
    }
}
