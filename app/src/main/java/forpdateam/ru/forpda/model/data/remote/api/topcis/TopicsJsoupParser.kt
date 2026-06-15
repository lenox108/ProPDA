package forpdateam.ru.forpda.model.data.remote.api.topcis

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import timber.log.Timber

/**
 * Jsoup-based re-implementation of the regex-based [TopicsParser].
 *
 * The §2.1 plan migrates parsers incrementally behind a `useJsoup`
 * flag. This class targets the dominant `data-topic` row layout
 * used by the topics list. The regex path retains the rest of the
 * fields (curator, lastUser, multi-link fallback heuristics) until
 * the Jsoup path has been validated in production.
 *
 * The flag is a per-instance boolean, defaulting to false, so the
 * production behaviour is unchanged.
 */
class TopicsJsoupParser : BaseParser() {

    fun parse(response: String, argId: Int): TopicsData {
        val data = TopicsData()
        data.id = argId
        data.title = null

        val document = Jsoup.parse(response)
        document.selectFirst("h1#topic-title")?.text()?.let { data.title = it.fromHtml() }

        val announceLinks = document.select("a[href*=\"showtopic=\"][href*=\"/announce-\"]")
        for (link in announceLinks) {
            val href = link.attr("href")
            val titleText = link.text()
            if (href.isBlank() || titleText.isBlank()) continue
            data.addAnnounceItem(TopicItem().apply {
                isAnnounce = true
                announceUrl = if (href.startsWith("http")) href else "https://4pda.to" + href
                title = titleText.fromHtml()
            })
        }

        val pinnedIds = mutableSetOf<Int>()
        val seenTopicIds = mutableSetOf<Int>()

        val rows = document.select("div[data-topic]")
        for (row in rows) {
            val topicId = row.attr("data-topic").toIntOrNull() ?: continue
            val modifier = row.selectFirst("span.modifier")?.text().orEmpty()
            val isPinnedRow = row.hasClass("pinned") ||
                    row.selectFirst("[class*=\"pinned\"]") != null ||
                    row.attr("data-pinned") == "1"
            val isNewRow = modifier.contains("+")
            val isPollRow = modifier.contains("^")
            val isClosedRow = modifier.contains("Х")
            val hasRelocatedChevron = modifier.contains("»") || modifier.contains("&raquo;")
            val hasRelocatedText = row.html().contains("перемещена", ignoreCase = true) ||
                    row.html().contains("title=\"Перемещена\"", ignoreCase = true)
            val isRelocatedRow = hasRelocatedChevron && hasRelocatedText

            val titleAnchor = pickTitleAnchor(row)
            val titleHref = titleAnchor?.attr("href")
            val titleText = titleAnchor?.text()?.fromHtml()
            val hrefTopicId = titleHref?.extractShowtopicId() ?: topicId

            val description = row.selectFirst("div.topic_desc, .desc")?.text()?.fromHtml()
            val authorAnchor = row.selectFirst("a.author, .topic_author a[href*=\"showuser=\"]")
            val lastUserAnchor = row.selectFirst("a.last_user, .topic_last_user a[href*=\"showuser=\"]")
            val dateEl = row.selectFirst(".topic_date, .date, abbr")

            val item = TopicItem().apply {
                id = hrefTopicId
                isNew = isNewRow
                isPoll = isPollRow
                isClosed = isClosedRow
                isRelocated = isRelocatedRow
                isPinned = isPinnedRow
                listingHref = titleHref
                title = titleText
                desc = description
                authorId = authorAnchor?.attr("href")?.extractShowuserId() ?: 0
                authorNick = authorAnchor?.text()?.fromHtml()
                lastUserId = lastUserAnchor?.attr("href")?.extractShowuserId() ?: 0
                lastUserNick = lastUserAnchor?.text()?.fromHtml()
                date = dateEl?.text()
            }

            if (hrefTopicId != topicId) {
                if (BuildConfig.DEBUG) {
                    Timber.tag("TopicsJsoup").w(
                            "topic id mismatch: data-topic=%d hrefTopicId=%d title=%s",
                            topicId, hrefTopicId, item.title.orEmpty()
                    )
                }
                item.oldId = topicId
            }

            if (isPinnedRow) {
                if (pinnedIds.add(item.id)) {
                    data.topicItems.removeAll { it.id == item.id }
                    data.addPinnedItem(item)
                }
                seenTopicIds.add(item.id)
            } else {
                if (item.id !in pinnedIds && seenTopicIds.add(item.id)) {
                    data.addTopicItem(item)
                }
            }
        }

        document.select("div.board_forum_row, a.forum_link, [data-forum-id]").forEach { el ->
            val forumId = el.attr("data-forum-id").toIntOrNull() ?: 0
            val forumTitle = (el.selectFirst(".forum_title, .title")?.text() ?: el.text())
            if (forumId <= 0 || forumTitle.isBlank()) return@forEach
            data.addForumItem(TopicItem().apply {
                id = forumId
                title = forumTitle.fromHtml()
                isForum = true
            })
        }

        data.pagination = Pagination.parseForum(response)
        return data
    }

    private fun pickTitleAnchor(row: Element): org.jsoup.nodes.Element? {
        val candidates = row.select("a[href*=\"showtopic=\"]")
        return candidates.firstOrNull { it.text().isNotBlank() } ?: candidates.firstOrNull()
    }

    private fun String.extractShowtopicId(): Int? {
        val matcher = Regex("""showtopic=(\d+)""", RegexOption.IGNORE_CASE).find(this)
        return matcher?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun String.extractShowuserId(): Int? {
        val matcher = Regex("""showuser=(\d+)""", RegexOption.IGNORE_CASE).find(this)
        return matcher?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
