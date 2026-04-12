package forpdateam.ru.forpda.presentation.mentions

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData

/**
 * Created by radiationx on 01.01.18.
 */
interface MentionsView : IBaseView {
    fun showMentions(data: MentionsData)

    fun showItemDialogMenu(item: MentionItem)

    fun showAddFavoritesDialog(id: Int)

    fun onAddToFavorite(result: Boolean)
}
