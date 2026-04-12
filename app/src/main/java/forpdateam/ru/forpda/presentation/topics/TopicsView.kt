package forpdateam.ru.forpda.presentation.topics

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData

/**
 * Created by radiationx on 01.03.17.
 */
interface TopicsView : IBaseView {
    fun showTopics(data: TopicsData)

    fun updateList()

    fun showItemDialogMenu(item: TopicItem)

    fun onAddToFavorite(result: Boolean)

    fun onMarkRead()
}
