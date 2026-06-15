package forpdateam.ru.forpda.model.data.remote.api.mentions

import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jsoup-based re-implementation of the regex-based [MentionsParser].
 *
 * Per the §2.1 plan, the regex path stays the default until the
 * new implementation is verified against a golden fixture in
 * `app/src/test/resources/parser/mentions/`. A boolean flag in
 * [MentionsParser] switches between the two paths; the regex
 * path is deleted once Jsoup is stable in production.
 *
 * Goal: produce the same [MentionsData] for the same input HTML.
 * The selector strategy targets the `div.topic_title_post` blocks
 * the regex pattern matches, and re-uses the
 * `preferMentionPostUrl` / `patchMentionLinkIfTopicOnly` helpers
 * for the post-link preference logic that was already battle-
 * tested in `MentionUrlPreferenceTest`.
 */
class MentionsJsoupParser : BaseParser() {

    fun parse(response: String): MentionsData {
        val data = MentionsData()
        val document = Jsoup.parse(response)
        val rows = document.select("div.topic_title_post")
        for (row in rows) {
            val item = parseRow(row) ?: continue
            data.items.add(item)
        }
        data.pagination = Pagination.parseForum(response)
        return data
    }

    private fun parseRow(row: Element): MentionItem? {
        // 1) State: class is "topic_title_post" (read) or
        //    "topic_title_post unread" (unread).
        val state = if (row.hasClass("unread")) {
            MentionItem.STATE_UNREAD
        } else {
            MentionItem.STATE_READ
        }

        // 2) Type: the prefix before the first ":".
        //    The regex pattern uses a non-greedy `([^:]*?):`
        //    anchored on the row text.
        val typeText = row.ownText().substringBefore(':', "").trim()
        val type = if (typeText.equals("Форум", ignoreCase = true)) {
            MentionItem.TYPE_TOPIC
        } else {
            MentionItem.TYPE_NEWS
        }

        // 3) Primary href + title: the first <a href> inside the
        //    row's anchor chain. The regex pattern finds the
        //    anchor right after the colon in the title-prefix.
        val primaryAnchor = row.selectFirst("a[href]")
        val primaryHref = primaryAnchor?.attr("href").orEmpty()
        val title = primaryAnchor?.text()?.fromHtml()

        // 4) Date: the anchor inside .post_date (or any post-date
        //    attribute — the regex uses `post_date"…>…<a>(…)</a>`).
        val dateAnchor = row.selectFirst("[class*=post_date] a")
                ?: row.selectFirst(".post_date a")
        val date = dateAnchor?.text()

        // 5) Nick: the anchor whose href contains `showuser=`.
        val nickAnchor = row.selectFirst("a[href*=showuser=]")
        val nick = nickAnchor?.text()?.fromHtml()

        val item = MentionItem().apply {
            this.state = state
            this.type = type
            this.title = title
            this.date = date
            this.nick = nick
        }
        // Link resolution: prefer the post-URL over the topic URL
        // using the same helpers the regex path uses. This
        // delegates the well-tested URL-preference logic to keep
        // behaviour byte-identical.
        val rowHtml = row.outerHtml()
        var link = preferMentionPostUrl(rowHtml, primaryHref)
        link = patchMentionLinkIfTopicOnly(link, rowHtml)
        item.link = link
        return item
    }
}
