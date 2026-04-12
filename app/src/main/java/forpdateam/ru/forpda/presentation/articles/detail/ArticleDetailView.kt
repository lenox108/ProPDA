package forpdateam.ru.forpda.presentation.articles.detail

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.news.DetailsPage

/**
 * Created by radiationx on 01.01.18.
 */
interface ArticleDetailView : IBaseView {
    fun showArticle(data: DetailsPage)
    fun showArticleImage(imageUrl: String)

    fun showCreateNote(title: String, url: String)
}
