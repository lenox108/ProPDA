package forpdateam.ru.forpda.model.data.remote.api.favorites

import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jsoup-based re-implementation of the regex-based [FavoritesParser].
 *
 * Per the §2.1 plan, the regex path stays the default until the new
 * implementation is verified against the favorites golden fixtures
 * and the existing [FavoritesParserTest] behavioral tests. A boolean
 * flag in [FavoritesParser] switches between the two paths.
 *
 * The selector strategy mirrors the regex pattern's anchors:
 * - the outer row is `<div data-item-fid="...">` (the modern
 *   data-item-fid layout the regex falls back to)
 * - the title link is the first `<a href*="showtopic=">` inside the
 *   row
 * - the modifier strip is `<span class="modifier">` (or
 *   `class="modifier unread"`)
 *
 * Forum-list rows and topic body blocks (regex group 19+) are
 * intentionally left for the regex path; they appear on a
 * separate "forum in favorites" layout that the Jsoup path does
 * not cover yet. Topic rows already cover the dominant case in
 * current production.
 */
class FavoritesJsoupParser : BaseParser() {

    fun parseFavorites(response: String): FavData {
        val data = FavData()
        val document = Jsoup.parse(response)
        val rows = document.select("div[data-item-fid]")
        for (row in rows) {
            val item = parseTopicRow(row) ?: continue
            data.items.add(item)
        }
        data.pagination = Pagination.parseForum(response)
        data.sorting = Sorting.parse(response)
        return data
    }

    private fun parseTopicRow(row: Element): FavItem? {
        val favId = row.attr("data-item-fid").toIntOrNull() ?: return null
        val trackType = row.attr("data-item-track")
        val isPin = row.attr("data-item-pin") == "1"

        val modifierEl = row.selectFirst("span.modifier")
        val modifierText = modifierEl?.text() ?: ""
        val isPoll = modifierText.contains("^")
        val isClosed = modifierText.contains("Х")

        val titleAnchor = row.selectFirst("a[href*=\"showtopic=\"]")
        val topicId = titleAnchor?.attr("href")?.extractShowTopicId() ?: 0
        val topicTitle = titleAnchor?.text()?.fromHtml()

        val listingHref = row.selectFirst("a[href*=\"showtopic=\"]")?.attr("href")
        val unread = FavoritesParser.detectFavoriteRowUnread(
                rowHtml = row.outerHtml(),
                modifierRegion = modifierEl?.outerHtml().orEmpty(),
                modifierText = modifierText
        )
        val plusDigits = unread.plusCount

        // Body block is a sibling element (regex captures it via a
        // separate "topic_body" pattern group). For the Jsoup path we
        // keep the topic-only metadata: page offsets, last user, author,
        // etc., live in the topic_body block. We surface stParam from
        // the tpg(N,offset) anchor in the row if present.
        val offsetMatch = Regex("tpg\\((\\d+),(\\d+)\\)")
                .find(row.outerHtml())
        val stParam = offsetMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val perPage = offsetMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 20
        val pages = if (stParam > 0) stParam / perPage + 1 else 0

        return FavItem().apply {
            this.favId = favId
            this.trackType = trackType
            this.isPin = isPin
            this.isPoll = isPoll
            this.isClosed = isClosed
            this.topicId = topicId
            this.topicTitle = topicTitle
            this.listingHref = listingHref
            this.stParam = stParam
            this.pages = pages
            this.readState = unread.readState
            this.isNew = unread.readState == forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.UNREAD
            this.unreadPostCount = when {
                unread.readState == forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.UNREAD && plusDigits != null -> plusDigits
                unread.readState == forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState.UNREAD -> 1
                else -> 0
            }
        }
    }

    private fun String.extractShowTopicId(): Int? {
        val matcher = Regex("""showtopic=(\d+)""", RegexOption.IGNORE_CASE).find(this)
        return matcher?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
