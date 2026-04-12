package forpdateam.ru.forpda.presentation.search

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.search.SearchItem
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings

interface SearchSiteView : IBaseView {
    fun setStyleType(type: String)

    fun updateShowAvatarState(isShow: Boolean)
    fun updateTypeAvatarState(isCircle: Boolean)
    fun updateScrollButtonState(isEnabled: Boolean)
    fun setFontSize(size: Int)

    fun showData(searchResult: SearchResult)
    fun fillSettingsData(settings: SearchSettings, fields: Map<String, List<String>>)
    fun onStartSearch(settings: SearchSettings)
    fun showItemDialogMenu(item: SearchItem, settings: SearchSettings)
    fun setNewsMode()
    fun setForumMode()

    fun onAddToFavorite(result: Boolean)

    fun showAddInFavDialog(item: IBaseForumPost)

    fun showNoteCreate(title: String, url: String)

    fun firstPage()

    fun prevPage()

    fun nextPage()

    fun lastPage()

    fun selectPage()

    fun deletePostUi(post: IBaseForumPost)

    fun showUserMenu(post: IBaseForumPost)

    fun showReputationMenu(post: IBaseForumPost)

    fun showPostMenu(post: IBaseForumPost)

    fun reportPost(post: IBaseForumPost)

    fun deletePost(post: IBaseForumPost)

    fun editPost(post: IBaseForumPost)

    fun votePost(post: IBaseForumPost, type: Boolean)

    fun showChangeReputation(post: IBaseForumPost, type: Boolean)

    fun openAnchorDialog(post: IBaseForumPost, anchorName: String)

    fun openSpoilerLinkDialog(post: IBaseForumPost, spoilNumber: String)

    fun toast(text: String)

    fun log(text: String)
}
