package forpdateam.ru.forpda.model.repository.search

import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.api.search.SearchTitleSimilarity

class ForumSectionTitleIndex {

    // Bounded LRU: this index accumulates the titles of every forum section the user browses for the whole
    // session, purely to offer «Похожие темы» suggestions. Unbounded it was a slow memory creep over a long
    // session. accessOrder=true keeps the recently-visited forums; the eldest forum is evicted past the cap,
    // and each forum's own title map is likewise capped (see [boundedTitleMap]).
    private val topicsByForum = object : LinkedHashMap<Int, LinkedHashMap<Int, IndexedTitle>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, LinkedHashMap<Int, IndexedTitle>>): Boolean =
                size > MAX_FORUMS
    }

    private fun boundedTitleMap(): LinkedHashMap<Int, IndexedTitle> =
            object : LinkedHashMap<Int, IndexedTitle>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, IndexedTitle>): Boolean =
                        size > MAX_TOPICS_PER_FORUM
            }

    @Synchronized
    fun record(forumId: Int, data: TopicsData) {
        val resolvedForumId = data.id.takeIf { it > 0 } ?: forumId
        if (resolvedForumId <= 0) return

        val forumTopics = topicsByForum.getOrPut(resolvedForumId) { boundedTitleMap() }
        (data.pinnedItems + data.topicItems).forEach { item ->
            if (item.id > 0 && !item.title.isNullOrBlank()) {
                forumTopics[item.id] = item.toIndexedTitle(resolvedForumId, isSubForum = false)
            }
        }
    }

    @Synchronized
    fun suggestions(settings: SearchSettings, serverItems: List<SearchItem>, limit: Int = DEFAULT_LIMIT): List<SearchItem> {
        if (!settings.supportsForumSectionTitleSuggestions()) return emptyList()
        val forumId = settings.forums.single().toIntOrNull() ?: return emptyList()
        val normalizedQuery = SearchTitleSimilarity.normalize(settings.query)
        if (normalizedQuery.isEmpty()) return emptyList()

        val serverTopicIds = serverItems.mapTo(mutableSetOf()) { it.topicId }
        return topicsByForum[forumId]
                .orEmpty()
                .values
                .asSequence()
                .filterNot { it.topicId in serverTopicIds }
                .mapNotNull { indexed ->
                    val rank = SearchTitleSimilarity.rank(normalizedQuery, indexed.title)
                    if (!rank.isMatch) null else RankedIndexedTitle(indexed, rank)
                }
                .sortedWith(compareBy<RankedIndexedTitle> { it.rank.priority }.thenBy { it.rank.distance }.thenBy { it.indexed.title })
                .take(limit)
                .map { it.indexed.toSearchItem() }
                .toList()
    }

    private fun TopicItem.toIndexedTitle(forumId: Int, isSubForum: Boolean) = IndexedTitle(
            forumId = forumId,
            topicId = id,
            title = title.orEmpty(),
            description = SUGGESTION_LABEL,
            isSubForum = isSubForum
    )

    private fun SearchSettings.supportsForumSectionTitleSuggestions(): Boolean =
            resourceType == SearchSettings.RESOURCE_FORUM.first &&
                    result == SearchSettings.RESULT_TOPICS.first &&
                    source == SearchSettings.SOURCE_TITLES.first &&
                    subforums == SearchSettings.SUB_FORUMS_FALSE &&
                    forums.size == 1 &&
                    topics.isEmpty() &&
                    query.isNotBlank()

    private fun IndexedTitle.toSearchItem(): SearchItem = SearchItem().apply {
        topicId = this@toSearchItem.topicId
        forumId = this@toSearchItem.forumId
        title = this@toSearchItem.title
        desc = this@toSearchItem.description
    }

    private data class IndexedTitle(
            val forumId: Int,
            val topicId: Int,
            val title: String,
            val description: String,
            val isSubForum: Boolean
    )

    private data class RankedIndexedTitle(
            val indexed: IndexedTitle,
            val rank: SearchTitleSimilarity.Rank
    )

    companion object {
        const val SUGGESTION_LABEL = "Похожие темы"
        private const val DEFAULT_LIMIT = 5

        /** Max forum sections kept in the in-memory suggestion index (LRU). */
        private const val MAX_FORUMS = 24

        /** Max indexed topic titles per forum section (LRU). */
        private const val MAX_TOPICS_PER_FORUM = 400
    }
}
