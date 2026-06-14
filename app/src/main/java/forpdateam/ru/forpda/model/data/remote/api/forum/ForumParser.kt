package forpdateam.ru.forpda.model.data.remote.api.forum

import forpdateam.ru.forpda.entity.remote.forum.Announce
import forpdateam.ru.forpda.entity.remote.forum.ForumItemFlat
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.entity.remote.forum.ForumRules
import forpdateam.ru.forpda.entity.remote.forum.IForumItemFlat
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.*
import java.util.regex.Matcher

class ForumParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Forum

    /** Ссылка «отметить прочитанным» — надёжнее, чем первый fromforum в строке (в lastpost бывают другие URL). */
    private val reMarkForumParams = Regex(
            """markforum[^"']*?[&?]f=(\d+)[^"']*?fromforum=(\d+)""",
            RegexOption.IGNORE_CASE
    )
    private val reShowforumTitle = Regex(
            """showforum=(\d+)"[^>]*>([^<]*)</a>""",
            RegexOption.IGNORE_CASE
    )
    private val reCatTitle = Regex(
            """<div class="cat_name">[\s\S]*?<a href="[^"]*showforum=(\d+)"[^>]*>([^<]*)</a>""",
            RegexOption.IGNORE_CASE
    )

    /**
     * Главная форума: блоки [fo_ID] (категории) и [board_forum_row] (подфорумы).
     * Теги [fc_ID] внутри родительского [fo_] задают свёрнутые ссылки, а не дерево для списка:
     * иначе «Android» оказывается внутри «Административный», «Apple» — внутри «Android» (лесенка).
     * Каждая [fo_] в порядке страницы — прямой потомок корня; подфорумы — дети своего [fo_].
     * Страница act=search больше не отдаёт &lt;select name="forums[]"&gt; — старый парсер оставлен как запасной.
     */
    fun parseForums(response: String): ForumItemTree {
        val flat = buildFlatFromForumIndex(response)
        if (flat.isNotEmpty()) {
            return ForumItemTree().also { root -> buildForumTreeFromFlatList(flat, root) }
        }
        return parseForumsFromSearchPage(response)
    }

    private fun buildFlatFromForumIndex(html: String): List<ForumItemFlat> {
        val foBlocks = extractFoBlocks(html)
        if (foBlocks.isEmpty()) return emptyList()

        val flat = mutableListOf<ForumItemFlat>()

        for ((foId, block) in foBlocks) {
            val catTitle = reCatTitle.find(block)?.groupValues?.get(2)?.fromHtml() ?: ""
            val catLevel = 0

            flat.add(ForumItemFlat().apply {
                id = foId
                parentId = -1
                level = catLevel
                title = catTitle
            })

            val blockBeforeFc = substringBeforeDivFc(block)
            flat.addAll(parseBoardForumRows(blockBeforeFc, foId, catLevel))
        }
        return flat
    }

    /**
     * Каждая строка отдельно; пары id/parent только из [board_forum_name] (ссылка markforum + заголовок showforum).
     */
    private fun parseBoardForumRows(blockBeforeFc: String, categoryFoId: Int, catLevel: Int): List<ForumItemFlat> {
        val out = mutableListOf<ForumItemFlat>()
        val openNeedle = """<div class="board_forum_row">"""
        val nameNeedle = """<div class="board_forum_name">"""
        var from = 0
        while (true) {
            val start = blockBeforeFc.indexOf(openNeedle, from)
            if (start < 0) break
            val innerStart = start + openNeedle.length
            val nextRow = blockBeforeFc.indexOf(openNeedle, innerStart)
            val end = if (nextRow < 0) blockBeforeFc.length else nextRow
            val row = blockBeforeFc.substring(start, end)
            from = end

            val namePos = row.indexOf(nameNeedle, ignoreCase = true)
            if (namePos < 0) continue
            val nameInnerStart = namePos + nameNeedle.length
            val nameEnd = row.indexOf("</div>", nameInnerStart)
            if (nameEnd <= nameInnerStart) continue
            val nameHtmlRaw = row.substring(nameInnerStart, nameEnd)
            val nameHtml = nameHtmlRaw.replace("&amp;", "&")

            val markM = reMarkForumParams.find(nameHtml)
            val titleM = reShowforumTitle.find(nameHtml)
            if (markM != null && titleM != null) {
                val sid = markM.groupValues[1].toIntOrNull() ?: continue
                val showId = titleM.groupValues[1].toIntOrNull() ?: continue
                if (sid != showId) continue
                val t = titleM.groupValues.getOrNull(2).fromHtml().orEmpty()
                out.add(ForumItemFlat().apply {
                    id = sid
                    // Родитель по разметке: строка лежит внутри fo_<categoryFoId>, а не по fromforum в markforum
                    // (там «контекст» для отметки прочитанным и может не совпадать с деревом разделов).
                    parentId = categoryFoId
                    level = catLevel + 1
                    title = t
                })
            } else if (titleM != null) {
                // Гость без ссылки markforum — родитель = текущий fo-блок категории
                val sid = titleM.groupValues[1].toIntOrNull() ?: continue
                val t = titleM.groupValues.getOrNull(2).fromHtml().orEmpty()
                out.add(ForumItemFlat().apply {
                    id = sid
                    parentId = categoryFoId
                    level = catLevel + 1
                    title = t
                })
            }
        }
        return out
    }

    /**
     * Разбор id="fo_N" без Regex: на странице бывают другие пробелы/кавычки, мобильная вёрстка совпадает по подстроке.
     */
    private data class FoMarker(val id: Int, val contentStart: Int, val contentEnd: Int)

    private fun findFoMarkers(html: String): List<FoMarker> {
        val result = mutableListOf<FoMarker>()
        val needles = listOf("""id="fo_""", """id='fo_""")
        var from = 0
        while (from < html.length) {
            var pos = -1
            var needle = ""
            for (n in needles) {
                val p = html.indexOf(n, from)
                if (p >= 0 && (pos < 0 || p < pos)) {
                    pos = p
                    needle = n
                }
            }
            if (pos < 0) break
            val idStart = pos + needle.length
            val idEnd = html.indexOfAny(charArrayOf('"', '\''), idStart)
            if (idEnd < 0) {
                from = pos + 1
                continue
            }
            val id = html.substring(idStart, idEnd).toIntOrNull()
            if (id == null) {
                from = pos + 1
                continue
            }
            val gt = html.indexOf('>', idEnd)
            if (gt < 0) break
            val contentStart = gt + 1
            var nextFo = -1
            for (n in needles) {
                val p = html.indexOf(n, contentStart)
                if (p >= 0 && (nextFo < 0 || p < nextFo)) nextFo = p
            }
            val contentEnd = if (nextFo < 0) html.length else nextFo
            result.add(FoMarker(id, contentStart, contentEnd))
            from = contentEnd
        }
        return result
    }

    private fun extractFoBlocks(html: String): List<Pair<Int, String>> {
        return findFoMarkers(html).map { m ->
            m.id to html.substring(m.contentStart, m.contentEnd)
        }
    }

    /** Обрезать блок до свёрнутого подраздела fc_, регистр и пробелы в теге не важны. */
    private fun substringBeforeDivFc(block: String): String {
        val low = block.lowercase()
        val key = """<div id="fc_"""
        val idx = low.indexOf(key)
        return if (idx < 0) block else block.substring(0, idx)
    }

    private fun parseForumsFromSearchPage(response: String): ForumItemTree = ForumItemTree().also { root ->
        patternProvider
                .getPattern(scope.scope, scope.forums_from_search)
                .matcher(response)
                .findOnce { rootMatcher ->
                    val parentsList = ArrayList<ForumItemTree>()
                    var lastParent = root
                    parentsList.add(lastParent)
                    patternProvider
                            .getPattern(scope.scope, scope.forum_item_from_search)
                            .matcher(rootMatcher.group(1).orEmpty())
                            .findAll { matcher ->
                                ForumItemTree().apply {
                                    id = matcher.group(1)?.toIntOrNull() ?: 0
                                    level = (matcher.group(2)?.length ?: 0) / 2
                                    title = matcher.group(3).fromHtml()
                                    if (level <= lastParent.level) {
                                        //Удаление элементов, учитывая случай с резким скачком уровня вложенности
                                        for (i in 0 until lastParent.level - level + 1)
                                            parentsList.removeAt(parentsList.size - 1)
                                        lastParent = parentsList[parentsList.size - 1]
                                    }
                                    parentId = lastParent.id
                                    lastParent.addForum(this)
                                    if (level > lastParent.level) {
                                        lastParent = this
                                        parentsList.add(lastParent)
                                    }
                                }
                            }
                    parentsList.clear()
                }
    }

    fun parseRules(response: String): ForumRules = ForumRules().also { rules ->
        var itemMatcher: Matcher? = null
        patternProvider
                .getPattern(scope.scope, scope.rules_headers)
                .matcher(response)
                .findAll { headerMatcher ->
                    rules.addItem(ForumRules.Item().apply {
                        isHeader = true
                        number = headerMatcher.group(1)
                        text = headerMatcher.group(2)
                    })

                    val itemContent = headerMatcher.group(3).orEmpty()
                    itemMatcher = itemMatcher?.reset(itemContent) ?: patternProvider
                            .getPattern(scope.scope, scope.rules_items)
                            .matcher(itemContent)
                    itemMatcher
                            ?.findAll { itemMatcher ->
                                rules.addItem(ForumRules.Item().apply {
                                    number = itemMatcher.group(1)
                                    text = itemMatcher.group(2)
                                })
                            }
                }
    }

    fun parseAnnounce(response: String): Announce = Announce().also { data ->
        patternProvider
                .getPattern(scope.scope, scope.announce)
                .matcher(response)
                .findOnce {
                    data.title = it.group(1)
                    data.html = it.group(2)
                }
    }
}
