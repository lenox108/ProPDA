package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.app.EditPostSyncData
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Created by radiationx on 15.03.18.
 */
interface ThemeView : IBaseView {

    fun setStyleType(type: String)

    fun syncEditPost(data: EditPostSyncData)

    fun onEventNew(event: TabNotification)
    fun onEventRead(event: TabNotification)

    fun onAddToFavorite(result: Boolean)

    fun onDeleteFromFavorite(result: Boolean)

    fun onUploadFiles(items: List<AttachmentItem>)
    fun onDeleteFiles(items: List<AttachmentItem>)

    fun findNext(next: Boolean)

    fun findText(text: String)

    fun updateShowAvatarState(isShow: Boolean)
    fun updateTypeAvatarState(isCircle: Boolean)
    fun updateScrollButtonState(isEnabled: Boolean)
    fun setFontSize(size: Int)
    fun scrollToAnchor(anchor: String?)
    fun updateHistoryLastHtml()

    fun onLoadData(newPage: ThemePage)
    fun updateView(page: ThemePage)

    fun setMessageRefreshing(isRefreshing: Boolean)

    fun onMessageSent()

    fun showDeleteInFavDialog(page: ThemePage)

    fun showAddInFavDialog(page: ThemePage)

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

    fun insertText(text: String)

    fun deletePost(post: IBaseForumPost)

    fun editPost(post: IBaseForumPost)

    fun votePost(post: IBaseForumPost, type: Boolean)

    fun showChangeReputation(post: IBaseForumPost, type: Boolean)

    fun openAnchorDialog(post: IBaseForumPost, anchorName: String)

    fun openSpoilerLinkDialog(post: IBaseForumPost, spoilNumber: String)

    fun toast(text: String)

    fun log(text: String)
}
