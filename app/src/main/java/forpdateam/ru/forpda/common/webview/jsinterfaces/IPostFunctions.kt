package forpdateam.ru.forpda.common.webview.jsinterfaces

import forpdateam.ru.forpda.entity.remote.IBaseForumPost

/**
 * Created by radiationx on 27.04.17.
 */
interface IPostFunctions {
    companion object {
        const val JS_POSTS_FUNCTIONS = "IPostFunctions"
    }

    fun showUserMenu(post: IBaseForumPost)
    fun showReputationMenu(post: IBaseForumPost)
    fun showPostMenu(post: IBaseForumPost)
    fun reportPost(post: IBaseForumPost)
    fun reply(post: IBaseForumPost)
    fun quotePost(text: String, post: IBaseForumPost)
    fun deletePost(post: IBaseForumPost)
    fun editPost(post: IBaseForumPost)
    fun votePost(post: IBaseForumPost, type: Boolean)
    fun changeReputation(post: IBaseForumPost, type: Boolean)
}
