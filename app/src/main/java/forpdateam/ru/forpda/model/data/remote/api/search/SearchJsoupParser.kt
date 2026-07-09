package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Jsoup-based search parser for all three result kinds (news articles, forum topics, forum posts).
 *
 * Replaces the fragile 21-positional-group regexes in [SearchParser]: those encode the DOM implicitly and
 * broke silently on markup drift (the news-articles regex already parsed 0 items from live 4pda HTML → «ничего
 * не найдено» несмотря на реальные результаты). CSS selectors read the SAME fields the NATIVE search UI
 * actually renders (Фаза 7: WebView-движок постов удалён, действия над постом/репутация/группа больше не
 * показываются), so we deliberately parse only: topics → topicId/title/desc/forumId + last-poster nick/date;
 * posts → topicId/postId/topic-title/date/avatar/nick/body; news → id/image/date/user/title/body.
 *
 * Selected via `useJsoup` in [SearchParser].
 */
class SearchJsoupParser : BaseParser() {

    // ---- News articles ------------------------------------------------------------------------------------

    fun parseArticles(response: String, settings: SearchSettings, into: SearchResult) {
        val document = Jsoup.parse(response)
        val rows = document.select("li").filter { it.selectFirst("div.photo") != null }
        for (row in rows) {
            val item = parseArticleRow(row) ?: continue
            into.items.add(item)
        }
        into.pagination = Pagination.parseNews(response)
        into.settings = settings
    }

    private fun parseArticleRow(row: Element): SearchItem? {
        val photoDiv = row.selectFirst("div.photo") ?: return null
        val topAnchor = photoDiv.selectFirst("a[href]") ?: return null
        // 4pda news url: https://4pda.to/YYYY/MM/DD/<articleId>/<slug>/ — the id is the segment AFTER the date,
        // not the first number (a bare /(\d+)/ grabbed the year «2026»). Fall back to the last numeric segment.
        val href = topAnchor.attr("href")
        val articleId = ARTICLE_ID_DATED.find(href)?.groupValues?.get(1)?.toIntOrNull()
                ?: ARTICLE_ID_LAST.findAll(href).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
                ?: return null
        val imageUrl = photoDiv.selectFirst("img")?.attr("src")
        val date = row.selectFirst(".date")?.text()?.trim()
        val userAnchor = row.selectFirst("a[href*=showuser=]")
        val userId = userAnchor?.let { idFromQuery(it.attr("href"), "showuser") } ?: return null
        val nick = userAnchor.text()
        val title = row.select("h1, h2, h3, h4, h5, h6").firstOrNull()?.selectFirst("a")?.text()
        val body = row.selectFirst("div.description p")?.selectFirst("a")?.text()
                ?: row.selectFirst("p")?.selectFirst("a")?.text()

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

    // ---- Forum topics -------------------------------------------------------------------------------------

    fun parseForumTopics(response: String, settings: SearchSettings, into: SearchResult) {
        val document = Jsoup.parse(response)
        for (row in document.select("div[data-topic]")) {
            val item = parseTopicRow(row) ?: continue
            into.items.add(item)
        }
        into.pagination = Pagination.parseForum(response)
        into.settings = settings
    }

    private fun parseTopicRow(row: Element): SearchItem? {
        val topicId = row.attr("data-topic").toIntOrNull()?.takeIf { it > 0 } ?: return null
        // First showtopic anchor in the title block is the topic link itself (the rest are page shortcuts).
        val title = row.selectFirst("div.topic_title a[href*=showtopic=]")?.text() ?: return null
        val body = row.selectFirst("div.topic_body") ?: row

        // «форум:» line: everything before it in that .topic_desc is the (optional) topic description.
        val forumDesc = body.select("span.topic_desc").firstOrNull { it.text().contains("форум:") }
        val desc = forumDesc?.text()?.substringBefore("форум:")?.trim()?.takeIf { it.isNotEmpty() }
        val forumId = body.selectFirst("a[href*=showforum=]")?.let { idFromQuery(it.attr("href"), "showforum") } ?: 0

        // The list shows the LAST poster (parity with the legacy regex): the showuser anchor right after the
        // «Послед.:» (view=getlastpost) marker; the trailing text node after it is the date. Куратор lives in a
        // separate .forumdesc span, so anchoring on getlastpost avoids picking it up.
        val lastPostAnchor = body.selectFirst("a[href*=getlastpost]")?.nextElementSibling()
                ?.takeIf { it.tagName() == "a" && it.attr("href").contains("showuser=") }
        val userId = lastPostAnchor?.let { idFromQuery(it.attr("href"), "showuser") } ?: 0
        val lastNick = lastPostAnchor?.text()
        val date = (lastPostAnchor?.nextSibling() as? TextNode)?.text()?.trim()?.takeIf { it.isNotEmpty() }

        return SearchItem().apply {
            this.topicId = topicId
            this.title = title
            this.desc = desc
            this.forumId = forumId
            this.userId = userId
            this.nick = lastNick
            this.date = date
        }
    }

    // ---- Forum posts («поиск по сообщениям») --------------------------------------------------------------

    fun parseForumPosts(response: String, settings: SearchSettings, into: SearchResult) {
        val document = Jsoup.parse(response)
        for (post in document.select("div[data-post]")) {
            val item = parsePostRow(post) ?: continue
            into.items.add(item)
        }
        into.pagination = Pagination.parseForum(response)
        into.settings = settings
    }

    private fun parsePostRow(post: Element): SearchItem? {
        val postId = post.attr("data-post").toIntOrNull()?.takeIf { it > 0 } ?: return null
        val postDate = post.selectFirst("span.post_date")

        // Topic id: prefer the «открыть тему» link inside the post header (always present per post); fall back
        // to the preceding .cat_name header (which groups a result by its topic).
        val catName = post.previousElementSibling()?.takeIf { it.hasClass("cat_name") }
        val topicId = postDate?.selectFirst("a[href*=showtopic=]")?.let { idFromQuery(it.attr("href"), "showtopic") }
                ?: catName?.selectFirst("a[href*=showtopic=]")?.let { idFromQuery(it.attr("href"), "showtopic") }
                ?: 0
        val title = catName?.selectFirst("a")?.text()
        // «12.05.26, 11:09 | открыть тему» → date is the own-text before the '|' separator.
        val date = postDate?.ownText()?.substringBefore("|")?.trim()?.takeIf { it.isNotEmpty() }

        val nickAnchor = post.selectFirst("span.post_nick a[data-av]") ?: post.selectFirst("a[data-av]")
        val nick = nickAnchor?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
        val userId = nickAnchor?.let { idFromQuery(it.attr("href"), "showuser") } ?: 0
        val avatar = nickAnchor?.attr("data-av")?.takeIf { it.isNotEmpty() }
                ?.let { "https://s.4pda.to/forum/uploads/$it" }
        val body = post.selectFirst("div.post_body")?.html()

        return SearchItem().apply {
            this.topicId = topicId
            this.id = postId
            this.title = title
            this.date = date
            this.nick = nick
            this.userId = userId
            this.avatar = avatar
            this.body = body
        }
    }

    private fun idFromQuery(url: String, key: String): Int =
            Regex("$key=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private companion object {
        private val ARTICLE_ID_DATED = Regex("""/\d{4}/\d{2}/\d{2}/(\d+)/""")
        private val ARTICLE_ID_LAST = Regex("""/(\d+)/""")
    }
}
