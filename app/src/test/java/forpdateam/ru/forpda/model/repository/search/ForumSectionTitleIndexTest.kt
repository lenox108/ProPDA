package forpdateam.ru.forpda.model.repository.search

import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumSectionTitleIndexTest {

    @Test
    fun typoQueryFindsLocalTitleSuggestionInSameForumSection() {
        val index = ForumSectionTitleIndex()
        index.record(10, topicsData(10, topic(101, "AdGuard для Android")))

        val suggestions = index.suggestions(sectionTitleSettings(10, "adguuard"), emptyList())

        assertEquals(listOf("AdGuard для Android"), suggestions.map { it.title })
        assertEquals(listOf(101), suggestions.map { it.topicId })
        assertEquals(ForumSectionTitleIndex.SUGGESTION_LABEL, suggestions.single().desc)
    }

    @Test
    fun otherForumSectionSuggestionsAreExcluded() {
        val index = ForumSectionTitleIndex()
        index.record(10, topicsData(10, topic(101, "AdGuard")))
        index.record(20, topicsData(20, topic(201, "AdGuard")))

        val suggestions = index.suggestions(sectionTitleSettings(10, "adguuard"), emptyList())

        assertEquals(listOf(101), suggestions.map { it.topicId })
    }

    @Test
    fun suggestionsAreNotGeneratedForPostsSourceAllSourceContentOrGlobalSearch() {
        val index = ForumSectionTitleIndex()
        index.record(10, topicsData(10, topic(101, "AdGuard")))

        val posts = sectionTitleSettings(10, "adguuard").apply { result = SearchSettings.RESULT_POSTS.first }
        val sourceAll = sectionTitleSettings(10, "adguuard").apply { source = SearchSettings.SOURCE_ALL.first }
        val sourceContent = sectionTitleSettings(10, "adguuard").apply { source = SearchSettings.SOURCE_CONTENT.first }
        val global = sectionTitleSettings(10, "adguuard").apply { forums.clear() }

        assertTrue(index.suggestions(posts, emptyList()).isEmpty())
        assertTrue(index.suggestions(sourceAll, emptyList()).isEmpty())
        assertTrue(index.suggestions(sourceContent, emptyList()).isEmpty())
        assertTrue(index.suggestions(global, emptyList()).isEmpty())
    }

    @Test
    fun duplicateServerTopicIsRemovedFromSuggestions() {
        val index = ForumSectionTitleIndex()
        index.record(10, topicsData(
                10,
                topic(101, "AdGuard"),
                topic(102, "AdGuard VPN")
        ))

        val suggestions = index.suggestions(
                sectionTitleSettings(10, "adguuard"),
                listOf(SearchItem().apply { topicId = 101 })
        )

        assertEquals(listOf(102), suggestions.map { it.topicId })
    }

    private fun sectionTitleSettings(forumId: Int, query: String): SearchSettings = SearchSettings().apply {
        resourceType = SearchSettings.RESOURCE_FORUM.first
        result = SearchSettings.RESULT_TOPICS.first
        source = SearchSettings.SOURCE_TITLES.first
        subforums = SearchSettings.SUB_FORUMS_FALSE
        this.query = query
        addForum(forumId.toString())
    }

    private fun topicsData(forumId: Int, vararg topics: TopicItem): TopicsData = TopicsData(id = forumId).apply {
        topicItems.addAll(topics)
    }

    private fun topic(id: Int, title: String): TopicItem = TopicItem(id = id, title = title)
}
