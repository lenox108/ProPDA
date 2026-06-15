package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher

/**
 * Безопасные extension-функции для извлечения групп из Matcher.
 * Возвращают null вместо краша при отсутствии группы или ошибке парсинга.
 */
private fun Matcher.groupInt(group: Int): Int? {
    val value = this.group(group) ?: return null
    return value.toIntOrNull()
}

private fun Matcher.groupOrEmpty(group: Int): String = this.group(group).orEmpty()

private fun SearchParser.decodeHtmlText(value: String?): String? =
        value?.takeUnless { it.isEmpty() }?.let {
            if (it.indexOf('<') == -1 && it.indexOf('&') == -1) it else it.fromHtml()
        }

class SearchParser(
        private val patternProvider: IPatternProvider,
        private val useJsoup: Boolean = false,
) : BaseParser() {

    private val scope = ParserPatterns.Search
    private val jsoupParser = SearchJsoupParser()

    companion object {
        private val FORUM_POST_TOPIC_IN_MATCH = Regex("""showtopic=(\d+)""")
        private val FORUM_POST_P_IN_MATCH = Regex("""[?&]p=(\d+)""")
    }

    /**
     * В [patterns.json] для forum_posts первая ветка — только &lt;a name="entry…"&gt;; тогда группы showtopic/p пусты,
     * но topic и номер поста можно взять из всего совпадения (cat_name, ссылки в теле).
     */
    private fun Matcher.resolveForumPostTopicIdAndPostId(): Pair<Int, Int> {
        val g2 = group(2)
        val g3 = group(3)
        if (g2 != null && g3 != null) {
            return (g2.toIntOrNull() ?: 0) to (g3.toIntOrNull() ?: 0)
        }
        val block = group(0) ?: return 0 to 0
        val topicFromBlock = FORUM_POST_TOPIC_IN_MATCH.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val entryPostId = group(1)?.toIntOrNull()
        val postFromP = FORUM_POST_P_IN_MATCH.find(block)?.groupValues?.get(1)?.toIntOrNull()
        val postId = entryPostId ?: postFromP ?: 0
        return topicFromBlock to postId
    }

    fun parse(response: String, settings: SearchSettings): SearchResult = SearchResult().also { result ->
        val isNews = settings.resourceType == SearchSettings.RESOURCE_NEWS.first
        val resultTopics = settings.result == SearchSettings.RESULT_TOPICS.first
        if (isNews) {
            if (useJsoup) {
                jsoupParser.parseArticles(response, settings, result)
                return@also
            }
            patternProvider
                    .getPattern(scope.scope, scope.articles)
                    .matcher(response)
                    .findAll { matcher ->
                        result.items.add(SearchItem().apply {
                            id = matcher.groupInt(1) ?: return@findAll
                            imageUrl = matcher.group(2)
                            date = matcher.group(3)
                            userId = matcher.groupInt(4) ?: return@findAll
                            nick = matcher.group(5).fromHtml()
                            title = matcher.group(6).fromHtml()
                            body = matcher.group(7)
                        })
                    }
        } else {
            if (resultTopics) {
                patternProvider
                        .getPattern(scope.scope, scope.forum_topics)
                        .matcher(response)
                        .findAll { matcher ->
                            result.items.add(SearchItem().apply {
                                topicId = matcher.groupInt(1) ?: return@findAll
                                //setId(matcher.group(1).toInt());
                                title = matcher.group(4).fromHtml()
                                desc = matcher.group(5).fromHtml()
                                forumId = matcher.groupInt(6) ?: return@findAll
                                userId = matcher.groupInt(10) ?: return@findAll
                                nick = matcher.group(11).fromHtml()
                                date = matcher.group(12)
                            })
                        }
            } else {
                patternProvider
                        .getPattern(scope.scope, scope.forum_posts)
                        .matcher(response)
                        .findAll { matcher ->
                            result.items.add(SearchItem().apply {
                                val (tid, pid) = matcher.resolveForumPostTopicIdAndPostId()
                                topicId = tid
                                id = pid
                                title = decodeHtmlText(matcher.group(4))
                                date = matcher.group(5)
                                //setNumber(matcher.group(6).toInt());
                                isOnline = matcher.groupOrEmpty(7).contains("green")
                                matcher.group(8)?.also {
                                    if (!it.isEmpty()) {
                                        avatar = "https://s.4pda.to/forum/uploads/$it"
                                    }
                                }
                                nick = decodeHtmlText(matcher.group(9))
                                userId = matcher.groupInt(10) ?: return@findAll
                                isCurator = matcher.group(11) != null
                                groupColor = matcher.group(12)
                                group = matcher.group(13)
                                canMinusRep = matcher.groupOrEmpty(14).isNotEmpty()
                                reputation = matcher.group(15)
                                canPlusRep = matcher.groupOrEmpty(16).isNotEmpty()
                                canReport = matcher.groupOrEmpty(17).isNotEmpty()
                                canEdit = matcher.groupOrEmpty(18).isNotEmpty()
                                canDelete = matcher.groupOrEmpty(19).isNotEmpty()
                                canQuote = matcher.groupOrEmpty(20).isNotEmpty()
                                body = matcher.groupOrEmpty(21)
                            })
                        }
            }
        }

        if (isNews) {
            result.pagination = Pagination.parseNews(response)
        } else {
            result.pagination = Pagination.parseForum(response)
        }
        result.settings = settings
        return result
    }
}