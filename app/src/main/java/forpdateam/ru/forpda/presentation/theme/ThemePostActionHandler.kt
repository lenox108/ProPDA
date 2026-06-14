package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.BaseForumPost
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.interactors.theme.ThemeInteractionUseCase
import forpdateam.ru.forpda.model.interactors.theme.ThemeNavigationUseCase
import forpdateam.ru.forpda.presentation.TabRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class ThemePostActionHandler(
        private val scope: CoroutineScope,
        private val interactionUseCase: ThemeInteractionUseCase,
        private val navigationUseCase: ThemeNavigationUseCase,
        private val uiEvents: MutableSharedFlow<ThemeUiEvent>,
        private val router: TabRouter,
        private val getPostById: (Int) -> IBaseForumPost?,
        private val getCurrentPage: () -> ThemePage?,
        private val shareText: (String) -> Unit,
        private val logThemeQuote: (String, Array<out Any?>) -> Unit
) {

    private val replyActionLauncher = ThemeReplyActionLauncher(
            uiEvents = uiEvents,
            getPostById = getPostById,
            logThemeQuote = logThemeQuote
    )

    fun showReportPost(postId: Int) {
        getPostById(postId)?.let { uiEvents.tryEmit(ThemeUiEvent.ReportPost(it)) }
    }

    fun replyPost(postId: Int) = replyActionLauncher.openReply(postId)

    fun quotePost(postId: Int, text: String, displayedDate: String? = null) =
            replyActionLauncher.openQuote(postId, text, displayedDate)

    fun quoteFullPost(postId: Int, displayedDate: String?) =
            replyActionLauncher.openFullQuote(postId, displayedDate)

    fun showDeletePost(postId: Int) {
        getPostById(postId)?.let { uiEvents.tryEmit(ThemeUiEvent.DeletePost(it)) }
    }

    fun showEditPost(postId: Int) = replyActionLauncher.openEdit(postId)

    fun showVotePost(postId: Int, type: Boolean) {
        getPostById(postId)?.let { uiEvents.tryEmit(ThemeUiEvent.VotePost(it, type)) }
    }

    fun showChangeReputation(postId: Int, type: Boolean) {
        getPostById(postId)?.let { scope.launch { uiEvents.emit(ThemeUiEvent.ShowChangeReputation(it, type)) } }
    }

    fun changeReputation(postId: Int, type: Boolean, message: String) {
        getPostById(postId)?.let { post ->
            scope.launch {
                when (interactionUseCase.changeReputation(post, type, message)) {
                    is ThemeInteractionUseCase.ReputationResult.Success ->
                        interactionUseCase.showReputationChanged()
                    is ThemeInteractionUseCase.ReputationResult.Error -> { /* handled in UseCase */ }
                }
            }
        }
    }

    fun votePost(postId: Int, type: Boolean) {
        getPostById(postId)?.let { post ->
            scope.launch {
                when (val result = interactionUseCase.votePost(post, type)) {
                    is ThemeInteractionUseCase.VoteResult.Success -> {
                        updatePostRatingAfterVote(post, type)
                        val updated = getPostById(postId) ?: post
                        uiEvents.emit(
                                ThemeUiEvent.PatchPostRatingUi(
                                        postId = post.id,
                                        ratingText = updated.postRating.orEmpty(),
                                        canPlus = updated.canPlusPostRating,
                                        canMinus = updated.canMinusPostRating
                                )
                        )
                        router.showSystemMessage(result.message)
                    }
                    is ThemeInteractionUseCase.VoteResult.Error -> { /* handled in UseCase */ }
                }
            }
        }
    }

    fun openProfile(postId: Int) {
        getPostById(postId)?.let { navigationUseCase.openProfile(it.userId) }
    }

    fun openQms(postId: Int) {
        getPostById(postId)?.let { navigationUseCase.openQms(it.userId) }
    }

    fun openSearchUserTopic(postId: Int) {
        getPostById(postId)?.let { navigationUseCase.openSearchUserTopics(it.nick ?: "", it.userId) }
    }

    fun openSearchInTopic(postId: Int) {
        getPostById(postId)?.let { navigationUseCase.openSearchInTopic(it.forumId, it.topicId, it.nick ?: "", it.userId) }
    }

    fun openSearchUserMessages(postId: Int) {
        getPostById(postId)?.let { navigationUseCase.openSearchUserMessages(it.nick ?: "", it.userId) }
    }

    fun openReputationHistory(postId: Int) {
        getPostById(postId)?.let { navigationUseCase.openReputationHistory(it.userId) }
    }

    fun quoteFromBuffer(postId: Int) {
        getPostById(postId) ?: return
        val text = interactionUseCase.readFromClipboard()
        if (text.isNullOrEmpty()) {
            interactionUseCase.showQuoteClipboardEmpty()
            return
        }
        quotePost(postId, text)
    }

    fun reportPost(postId: Int, message: String) {
        val page = getCurrentPage() ?: return
        getPostById(postId)?.let { post ->
            scope.launch {
                when (interactionUseCase.reportPost(page.id, post, message)) {
                    is ThemeInteractionUseCase.PostActionResult.ReportSuccess ->
                        interactionUseCase.showReportSuccess()
                    is ThemeInteractionUseCase.PostActionResult.DeleteSuccess,
                    is ThemeInteractionUseCase.PostActionResult.DeleteFail,
                    is ThemeInteractionUseCase.PostActionResult.Error -> { /* handled in UseCase */ }
                }
            }
        }
    }

    fun deletePost(postId: Int) {
        getPostById(postId)?.let { post ->
            scope.launch {
                if (BuildConfig.DEBUG) Timber.d("deletePost: requested postId=${post.id} action=delete")
                when (val result = interactionUseCase.deletePost(post)) {
                    is ThemeInteractionUseCase.PostActionResult.DeleteSuccess -> {
                        if (BuildConfig.DEBUG) Timber.d("deletePost: success postId=${post.id}")
                        uiEvents.emit(ThemeUiEvent.DeletePostUi(post))
                        interactionUseCase.showMessageDeleted()
                    }
                    is ThemeInteractionUseCase.PostActionResult.DeleteFail -> {
                        if (BuildConfig.DEBUG) Timber.w("deletePost: failed without exception postId=${post.id}")
                    }
                    is ThemeInteractionUseCase.PostActionResult.Error -> {
                        if (BuildConfig.DEBUG) Timber.w(result.throwable, "deletePost: failed postId=${post.id}")
                        /* handled in UseCase */
                    }
                    else -> {}
                }
            }
        }
    }

    fun createNote(postId: Int) {
        getPostById(postId)?.let {
            val themeTitle = getCurrentPage()?.title.orEmpty()
            val title = "пост $themeTitle ${it.nick} ${it.id}"
            val url = "https://4pda.to/forum/index.php?s=&showtopic=${it.topicId}&view=findpost&p=${it.id}"
            scope.launch { uiEvents.emit(ThemeUiEvent.ShowNoteCreate(title, url)) }
        }
    }

    fun copyPostLink(postId: Int) {
        getPostById(postId)?.let { interactionUseCase.copyPostLink(it) }
    }

    fun sharePostLink(postId: Int) {
        getPostById(postId)?.let {
            val url = "https://4pda.to/forum/index.php?s=&showtopic=${it.topicId}&view=findpost&p=${it.id}"
            shareText(url)
        }
    }

    fun copyAnchorLink(postId: Int, name: String) {
        getPostById(postId)?.let { interactionUseCase.copyAnchorLink(it, name) }
    }

    fun copySpoilerLink(postId: Int, spoilNumber: String) {
        getPostById(postId)?.let { interactionUseCase.copySpoilerLink(it, spoilNumber) }
    }

    private fun updatePostRatingAfterVote(post: IBaseForumPost, type: Boolean) {
        val mutablePost = post as? BaseForumPost ?: return
        val current = post.postRating
                ?.replace("+", "")
                ?.replace("−", "-")
                ?.replace("–", "-")
                ?.toIntOrNull()
                ?: 0
        val updated = current + if (type) 1 else -1
        mutablePost.postRating = when {
            updated > 0 -> "+$updated"
            updated < 0 -> updated.toString()
            else -> "0"
        }
        if (type) {
            mutablePost.canPlusPostRating = false
        } else {
            mutablePost.canMinusPostRating = false
        }
    }
}
