package forpdateam.ru.forpda.model.data.remote.api.search

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.remote.ParserPatterns
import forpdateam.ru.forpda.model.data.remote.parser.BaseParser
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import java.util.regex.Matcher

class SearchParser(
        private val patternProvider: IPatternProvider
) : BaseParser() {

    private val scope = ParserPatterns.Search

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
            return g2.toInt() to g3.toInt()
        }
        val block = group(0) ?: return 0 to 0
        val topicFromBlock = FORUM_POST_TOPIC_IN_MATCH.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val postFromP = FORUM_POST_P_IN_MATCH.find(block)?.groupValues?.get(1)?.toIntOrNull()
        val entryPostId = group(1)?.toIntOrNull()
        val postId = postFromP ?: entryPostId ?: 0
        return topicFromBlock to postId
    }

    fun parse(response: String, settings: SearchSettings): SearchResult = SearchResult().also { result ->
        val isNews = settings.resourceType == SearchSettings.RESOURCE_NEWS.first
        val resultTopics = settings.result == SearchSettings.RESULT_TOPICS.first
        if (isNews) {
            patternProvider
                    .getPattern(scope.scope, scope.articles)
                    .matcher(response)
                    .findAll { matcher ->
                        result.items.add(SearchItem().apply {
                            id = matcher.group(1).toInt()
                            imageUrl = matcher.group(2)
                            date = matcher.group(3)
                            userId = matcher.group(4).toInt()
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
                                topicId = matcher.group(1).toInt()
                                //setId(matcher.group(1).toInt());
                                title = matcher.group(4).fromHtml()
                                desc = matcher.group(5).fromHtml()
                                forumId = matcher.group(6).toInt()
                                userId = matcher.group(10).toInt()
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
                                title = matcher.group(4)?.fromHtml()
                                date = matcher.group(5)
                                //setNumber(matcher.group(6).toInt());
                                isOnline = matcher.group(7).contains("green")
                                matcher.group(8)?.also {
                                    if (!it.isEmpty()) {
                                        avatar = "https://s.4pda.to/forum/uploads/$it"
                                    }
                                }
                                nick = matcher.group(9).fromHtml()
                                userId = matcher.group(10).toInt()
                                isCurator = matcher.group(11) != null
                                groupColor = matcher.group(12)
                                group = matcher.group(13)
                                canMinusRep = !matcher.group(14).isEmpty()
                                reputation = matcher.group(15)
                                canPlusRep = !matcher.group(16).isEmpty()
                                canReport = !matcher.group(17).isEmpty()
                                canEdit = !matcher.group(18).isEmpty()
                                canDelete = !matcher.group(19).isEmpty()
                                canQuote = !matcher.group(20).isEmpty()
                                body = matcher.group(21)
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