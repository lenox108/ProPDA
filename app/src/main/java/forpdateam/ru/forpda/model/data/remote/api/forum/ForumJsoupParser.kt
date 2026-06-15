package forpdateam.ru.forpda.model.data.remote.api.forum

import forpdateam.ru.forpda.entity.remote.forum.ForumItemFlat
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jsoup-based re-implementation of the regex-based [ForumParser]
 * for the forum index page.
 *
 * Per the §2.1 plan, the regex path stays the default until the
 * new implementation is verified against the forum fixtures. A
 * boolean flag in [ForumParser] switches between the two paths.
 *
 * Selector strategy:
 * - category: `div[id^="fo_"]` (one block per category, with the
 *   id in `fo_<num>` form)
 * - subforum: `div.board_forum_row` (only the rows that come
 *   before the first `div[id^="fc_"]` are kept; collapsed
 *   children in IPB are emitted as a flat parent link and are
 *   not part of the row list)
 * - title: the first `a[href*="showforum="]` inside
 *   `div.board_forum_name`
 */
class ForumJsoupParser : BaseParser() {

    fun parseForums(response: String): ForumItemTree {
        val document = Jsoup.parse(response)
        val flat = buildFlatFromIndex(document)
        if (flat.isNotEmpty()) {
            return ForumItemTree().also { root -> buildForumTreeFromFlatList(flat, root) }
        }
        // Fallback to the search-page layout is regex-only for now.
        return ForumItemTree()
    }

    private fun buildFlatFromIndex(document: org.jsoup.nodes.Document): List<ForumItemFlat> {
        val out = mutableListOf<ForumItemFlat>()
        val categories = document.select("div[id^=\"fo_\"]")
        for (cat in categories) {
            val catId = cat.id().removePrefix("fo_").toIntOrNull() ?: continue
            val catTitleAnchor = cat.selectFirst("div.cat_name a[href*=\"showforum=\"]")
            val catTitle = catTitleAnchor?.text()?.fromHtml().orEmpty()

            out.add(ForumItemFlat().apply {
                id = catId
                parentId = -1
                level = 0
                title = catTitle
            })

            val rowsBeforeFc = rowsBeforeFirstFc(cat)
            for (row in rowsBeforeFc) {
                val item = parseBoardForumRow(row) ?: continue
                item.parentId = catId
                item.level = 1
                out.add(item)
            }
        }
        return out
    }

    private fun rowsBeforeFirstFc(cat: Element): List<Element> {
        // IPB collapses nested forums as a `div[id^="fc_"]` block.
        // The board_forum_row elements emitted before the first fc_
        // block are direct children of the category in the index.
        val all = cat.select("div.board_forum_row")
        val firstFc = cat.selectFirst("div[id^=\"fc_\"]")
        if (firstFc == null) return all
        val rows = mutableListOf<Element>()
        for (row in all) {
            if (row.parents().any { it === firstFc }) continue
            rows.add(row)
        }
        return rows
    }

    private fun parseBoardForumRow(row: Element): ForumItemFlat? {
        val nameDiv = row.selectFirst("div.board_forum_name") ?: return null
        val titleAnchor = nameDiv.selectFirst("a[href*=\"showforum=\"]") ?: return null
        val href = titleAnchor.attr("href")
        val showId = Regex("""showforum=(\d+)""", RegexOption.IGNORE_CASE)
                .find(href)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val rowTitle = titleAnchor.text().fromHtml()
        return ForumItemFlat().apply {
            id = showId
            title = rowTitle
        }
    }

    private fun buildForumTreeFromFlatList(flat: List<ForumItemFlat>, root: ForumItemTree) {
        // Each flat row at level 0 is a category, direct children of root.
        // Each flat row at level 1 is a child of the most recent level 0.
        // Deeper levels (collapsed fc_ blocks) are not emitted by the
        // Jsoup path; the regex path remains the source of truth.
        for (item in flat) {
            when (item.level) {
                0 -> root.addForum(ForumItemTree().apply {
                    id = item.id
                    parentId = -1
                    level = 0
                    title = item.title
                })
                1 -> {
                    val parent = root.forums?.lastOrNull { it.level == 0 } ?: continue
                    val tree = ForumItemTree().apply {
                        id = item.id
                        parentId = parent.id
                        level = 1
                        title = item.title
                    }
                    parent.addForum(tree)
                }
            }
        }
    }
}
