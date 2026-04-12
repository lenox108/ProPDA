package forpdateam.ru.forpda.presentation.favorites

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.favorites.FavData
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.model.data.remote.api.favorites.Sorting

/**
 * Created by radiationx on 01.01.18.
 */
interface FavoritesView : IBaseView {
    fun initSorting(sorting: Sorting)
    fun onLoadFavorites(data: FavData)
    fun onShowFavorite(items: List<FavItem>)

    fun showItemDialogMenu(item: FavItem)

    fun showSubscribeDialog(item: FavItem)

    fun onChangeFav(result: Boolean)

    fun onMarkAllRead()

    fun setShowDot(enabled: Boolean)
    fun setUnreadTop(unreadTop: Boolean)
}
