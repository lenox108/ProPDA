package forpdateam.ru.forpda.presentation.theme

/**
 * Колбэки действий из WebView (шаблон темы / поиска). Имя моста в JS — [forpdateam.ru.forpda.ui.fragments.theme.ThemeFragmentWeb.JS_INTERFACE].
 */
interface ThemeWebCallbacks {
    fun onFirstPageClick()
    fun onPrevPageClick()
    fun onNextPageClick()
    fun onLastPageClick()
    fun onSelectPageClick()
    fun onSelectPageInputClick() = onSelectPageClick()
    fun onSearchPageClick(st: Int) = Unit
    fun onInfiniteScrollRequest(direction: String) = Unit
    fun onVisiblePageChanged(pageNumber: Int) = Unit
    fun onInfiniteRetry(direction: String) = Unit

    fun onUserMenuClick(postId: Int)
    fun onReputationMenuClick(postId: Int)
    fun onPostMenuClick(postId: Int)

    fun onReportPostClick(postId: Int)
    fun onReplyPostClick(postId: Int)
    fun onQuotePostClick(postId: Int, text: String, displayedDate: String? = null)
    fun onQuoteFullPostClick(postId: Int, displayedDate: String? = null)
    fun onDeletePostClick(postId: Int)
    fun onEditPostClick(postId: Int)
    fun onVotePostClick(postId: Int, type: Boolean)

    fun setHistoryBody(index: Int, body: String)

    fun copyText(text: String)
    fun shareText(text: String)
    fun toast(text: String)
    fun log(text: String)

    fun onPollResultsClick(url: String?)
    fun onPollClick()
    fun onPollSubmit(action: String, method: String, encodedForm: String)

    fun onSpoilerCopyLinkClick(postId: Int, spoilNumber: String)
    fun onAnchorClick(postId: Int, name: String)

    fun onPollHeaderClick(bValue: Boolean)
    fun onHatHeaderClick(bValue: Boolean)
    fun onHatOverlayInjectionRequested() = Unit
    fun onInlineHatHeaderClick(topicId: Int, bValue: Boolean, persistPreference: Boolean = true) = Unit
    fun onPostContentToggle(postId: Int, expanded: Boolean) = Unit

    fun onOpenLink(url: String)

    fun onScrollCommandComplete(commandId: String, success: Boolean, reason: String = "") = Unit

    fun onLinkSourceAnchorCaptured(payload: String) = Unit

    fun openProfile(postId: Int)
    fun openQms(postId: Int)
    fun openSearchUserTopic(postId: Int)
    fun openSearchInTopic(postId: Int)
    fun openSearchUserMessages(postId: Int)
    fun toggleForumBlacklist(postId: Int)

    fun onChangeReputationClick(postId: Int, type: Boolean)
    fun changeReputation(postId: Int, type: Boolean, message: String)
    fun votePost(postId: Int, type: Boolean)
    fun openReputationHistory(postId: Int)

    fun quoteFromBuffer(postId: Int)
    fun reportPost(postId: Int, message: String)
    fun deletePost(postId: Int)
    fun createNote(postId: Int)
    fun copyPostLink(postId: Int)
    fun sharePostLink(postId: Int)
    fun copyAnchorLink(postId: Int, name: String)
    fun copySpoilerLink(postId: Int, spoilNumber: String)
}
