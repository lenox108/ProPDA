package forpdateam.ru.forpda.entity.remote.topics

import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination

/**
 * Created by radiationx on 01.03.17.
 * Converted to Kotlin.
 */
data class TopicsData(
    var canCreateTopic: Boolean = false,
    var id: Int = 0,
    var title: String? = null,
    val topicItems: MutableList<TopicItem> = mutableListOf(),
    val pinnedItems: MutableList<TopicItem> = mutableListOf(),
    val announceItems: MutableList<TopicItem> = mutableListOf(),
    val forumItems: MutableList<TopicItem> = mutableListOf(),
    var pagination: Pagination = Pagination()
) {
    fun addTopicItem(topicItem: TopicItem) {
        topicItems.add(topicItem)
    }

    fun addAnnounceItem(announceItem: TopicItem) {
        announceItems.add(announceItem)
    }

    fun addPinnedItem(pinnedItem: TopicItem) {
        pinnedItems.add(pinnedItem)
    }

    fun addForumItem(forumItem: TopicItem) {
        forumItems.add(forumItem)
    }
}
