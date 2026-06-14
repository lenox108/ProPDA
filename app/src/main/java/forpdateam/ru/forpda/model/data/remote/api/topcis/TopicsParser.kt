package forpdateam.ru.forpda.model.data.remote.api.topcis

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import timber.log.Timber
import java.util.regex.Pattern

class TopicsParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Topics
    private val showtopicIdRegex: Pattern = Pattern.compile("showtopic=(\\d+)", Pattern.CASE_INSENSITIVE)
    private val showtopicHrefRegex: Pattern = Pattern.compile("href=\"([^\"]*?showtopic=\\d+[^\"]*?)\"", Pattern.CASE_INSENSITIVE)
    private val topicTitleBlockRegex: Pattern = Pattern.compile(
            "<div[^>]*?class=\"topic_title\"[^>]*?>([\\s\\S]*?)<\\/div>",
            Pattern.CASE_INSENSITIVE
    )
    private val showtopicAnchorRegex: Pattern = Pattern.compile(
            "<a[^>]*?href=\"([^\"]*?showtopic=\\d+[^\"]*?)\"[^>]*?>([\\s\\S]*?)<\\/a>",
            Pattern.CASE_INSENSITIVE
    )
    private val topicRowRegex: Pattern = Pattern.compile(
            "<div[^>]*?data-topic=\"\\d+\"[\\s\\S]*?(?=<div[^>]*?data-topic=\"\\d+\"|<div[^>]*?board_forum_row|<div[^>]*?pagination|$)",
            Pattern.CASE_INSENSITIVE
    )
    private val topicPageOffsetRegex: Pattern = Pattern.compile(
            "tpg\\(\\d+\\s*,\\s*(\\d+)\\)",
            Pattern.CASE_INSENSITIVE
    )

    fun parse(response: String, argId: Int): TopicsData = TopicsData().also { data ->
        patternProvider
                .getPattern(scope.scope, scope.title)
                .matcher(response)
                .also { matcher ->
                    if (matcher.find()) {
                        data.id = matcher.group(1)?.toIntOrNull() ?: argId
                        data.title = matcher.group(2)?.fromHtml()
                    } else {
                        data.id = argId
                    }
                }

        patternProvider
                .getPattern(scope.scope, scope.can_new_topic)
                .matcher(response)
                .findOnce { matcher ->
                    data.canCreateTopic = matcher.find()
                }

        patternProvider
                .getPattern(scope.scope, scope.announce)
                .matcher(response)
                .findAll { matcher ->
                    data.addAnnounceItem(TopicItem().apply {
                        isAnnounce = true
                        announceUrl = "https://4pda.to" + (matcher.group(1) ?: "").replace("&amp;", "&", false)
                        title = matcher.group(2)?.fromHtml()
                    })
                }

        val pinnedIds = mutableSetOf<Int>()
        val seenTopicIds = mutableSetOf<Int>()

        patternProvider
                .getPattern(scope.scope, scope.topics)
                .matcher(response)
                .findAll { matcher ->
                    val rawRowHtml = matcher.group(0).orEmpty()
                    val rowHtml = topicRowRegex.matcher(rawRowHtml).let { rowMatcher ->
                        if (rowMatcher.find()) rowMatcher.group(0).orEmpty() else rawRowHtml
                    }
                    val extracted = extractTopicTitleHrefAndTitle(rowHtml)
                    val item = TopicItem().apply {
                        id = matcher.group(1)?.toIntOrNull() ?: 0
                        matcher.group(2)?.also { modifier ->
                            isNew = modifier.contains("+")
                            isPoll = modifier.contains("^")
                            isClosed = modifier.contains("Х")
                            // IPB помечает «тема перенесена» символом » (HTML &raquo;) в `<span class="modifier">`,
                            // но сам символ встречается и в других модификаторах. Подтверждаем перенос
                            // дополнительным маркером в строке: «перемещена» / moved / title="Перемещена".
                            val hasRelocatedChevron = modifier.contains("»") || modifier.contains("&raquo;", ignoreCase = true)
                            val hasRelocatedText = rowHtml.contains("перемещена", ignoreCase = true) ||
                                    rowHtml.contains("moved", ignoreCase = true) ||
                                    rowHtml.contains("title=\"Перемещена\"", ignoreCase = true) ||
                                    rowHtml.contains("title='Перемещена'", ignoreCase = true)
                            isRelocated = hasRelocatedChevron && hasRelocatedText
                        }

                        isPinned = matcher.group(3) != null
                        // В `topic_title` может быть несколько `<a>` (иконки/метки/кнопки) с showtopic.
                        // Нам нужна ссылка именно заголовка темы (с читаемым текстом).
                        val fallbackHref = matcher.group(4)?.takeIf { it.isNotBlank() && it.contains("showtopic=", ignoreCase = true) }
                        listingHref = extracted?.href
                                ?: fallbackHref
                                ?: showtopicHrefRegex.matcher(rowHtml).let { hrefMatcher ->
                                    if (hrefMatcher.find()) hrefMatcher.group(1) else null
                                }
                        title = (extracted?.titleText?.takeIf { it.isNotBlank() } ?: matcher.group(5))?.fromHtml()
                        matcher.group(6)?.also {
                            desc = it.fromHtml()
                        }

                        authorId = matcher.group(7)?.toIntOrNull() ?: 0
                        authorNick = matcher.group(8)?.fromHtml()
                        lastUserId = matcher.group(9)?.toIntOrNull() ?: 0
                        lastUserNick = matcher.group(10)?.fromHtml()
                        date = matcher.group(11)
                        pages = extractPagesCount(rowHtml)
                        matcher.group(12)?.also {
                            curatorId = it.toIntOrNull() ?: 0
                            curatorNick = matcher.group(13)?.fromHtml()
                        }
                    }

                    // Если реальный id темы закодирован в href заголовка, предпочитаем его вместо data-topic.
                    // Это устраняет кейс, когда первая ссылка в `topic_title` — не заголовок (иконка),
                    // и открытие идёт по чужому showtopic.
                    extracted?.showtopicId?.let { hrefTopicId ->
                        if (hrefTopicId > 0 && hrefTopicId != item.id) {
                            if (BuildConfig.DEBUG) {
                                Timber.tag("TopicsParse").w(
                                        "topic id mismatch: data-topic=%d hrefTopicId=%d title=%s href=%s",
                                        item.id,
                                        hrefTopicId,
                                        item.title.orEmpty(),
                                        extracted.href
                                )
                            }
                            item.oldId = item.id
                            item.id = hrefTopicId
                        }
                    }

                    if (BuildConfig.DEBUG) {
                        val ids = collectShowtopicIds(rowHtml)
                        if (ids.size > 1) {
                            Timber.tag("TopicsParse").d(
                                    "multiple showtopic ids in row: data-topic=%d ids=%s chosenHref=%s title=%s row=%s",
                                    matcher.group(1)?.toIntOrNull() ?: 0,
                                    ids.joinToString(","),
                                    item.listingHref.orEmpty(),
                                    item.title.orEmpty(),
                                    rowHtml.take(400)
                            )
                        }
                    }

                    if (item.isPinned) {
                        // IPB может дублировать тему в списке (например, из-за разметки/секций).
                        // Пин должен иметь приоритет: если тема уже попала в обычные — переносим в пины.
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

        patternProvider
                .getPattern(scope.scope, scope.forum)
                .matcher(response)
                .findAll { matcher ->
                    data.addForumItem(TopicItem().apply {
                        id = matcher.group(1)?.toIntOrNull() ?: 0
                        title = matcher.group(2)?.fromHtml()
                        isForum = true
                    })
                }

        data.pagination = Pagination.parseForum(response)
    }

    private data class ExtractedTitleLink(
            val href: String,
            val titleText: String,
            val showtopicId: Int?
    )

    private fun extractTopicTitleHrefAndTitle(rowHtml: String): ExtractedTitleLink? {
        val titleBlock = topicTitleBlockRegex.matcher(rowHtml).let { m ->
            if (m.find()) m.group(1).orEmpty() else return null
        }

        val anchors = mutableListOf<Pair<String, String>>() // href, innerHtml
        val m = showtopicAnchorRegex.matcher(titleBlock)
        while (m.find()) {
            val href = m.group(1)?.trim().orEmpty()
            val inner = m.group(2).orEmpty()
            if (href.isNotEmpty()) {
                anchors += href to inner
            }
        }
        if (anchors.isEmpty()) return null

        // Выбираем ссылку, у которой внутри есть текст (не только <img/> и т.п.).
        val picked = anchors.firstOrNull { (_, inner) -> inner.stripTags().trim().isNotEmpty() } ?: anchors.first()
        val href = picked.first
        val titleText = picked.second.stripTags()
        val showtopicId = showtopicIdRegex.matcher(href).let { idM ->
            if (idM.find()) idM.group(1)?.toIntOrNull() else null
        }
        return ExtractedTitleLink(href = href, titleText = titleText, showtopicId = showtopicId)
    }

    private fun extractPagesCount(rowHtml: String): Int {
        val matcher = topicPageOffsetRegex.matcher(rowHtml)
        var maxOffset = 0
        while (matcher.find()) {
            maxOffset = maxOf(maxOffset, matcher.group(1)?.toIntOrNull() ?: 0)
        }
        return if (maxOffset > 0) maxOffset / DEFAULT_FORUM_POSTS_PER_PAGE + 1 else 0
    }

    private fun collectShowtopicIds(rowHtml: String): List<Int> {
        val ids = mutableListOf<Int>()
        val m = showtopicIdRegex.matcher(rowHtml)
        while (m.find()) {
            m.group(1)?.toIntOrNull()?.let { ids += it }
        }
        return ids
    }

    private fun String.stripTags(): String {
        // Быстрое "плоское" удаление HTML-тегов для выбора правильной ссылки заголовка.
        return replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .trim()
    }

    private companion object {
        const val DEFAULT_FORUM_POSTS_PER_PAGE = 20
    }
}