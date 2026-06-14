package forpdateam.ru.forpda.model.interactors.theme

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import forpdateam.ru.forpda.model.repository.reputation.ReputationRepository
import forpdateam.ru.forpda.model.repository.theme.ThemeRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.TabRouter
import javax.inject.Inject

/**
 * Инкапсулирует «социальные» действия в теме:
 * репутация, избранное, голосование, жалоба, удаление поста, буфер обмена.
 *
 * Вынесен из ThemeViewModel для снижения числа зависимостей (SRP).
 */
class ThemeInteractionUseCase @Inject constructor(
        private val reputationRepository: ReputationRepository,
        private val favoritesRepository: FavoritesRepository,
        private val themeRepository: ThemeRepository,
        private val errorHandler: IErrorHandler,
        private val clipboardHelper: ClipboardHelper,
        private val router: TabRouter
) {

    /** Результат изменения репутации */
    sealed class ReputationResult {
        data class Success(val post: IBaseForumPost) : ReputationResult()
        data class Error(val throwable: Throwable) : ReputationResult()
    }

    /** Результат операции с избранным */
    sealed class FavoriteResult {
        data class Add(val success: Boolean) : FavoriteResult()
        data class Delete(val success: Boolean) : FavoriteResult()
        data class Error(val throwable: Throwable) : FavoriteResult()
    }

    /** Результат голосования за пост */
    sealed class VoteResult {
        data class Success(val message: String) : VoteResult()
        data class Error(val throwable: Throwable) : VoteResult()
    }

    /** Результат голосования в опросе */
    sealed class PollSubmitResult {
        data class Success(val page: ThemePage) : PollSubmitResult()
        data class Error(val throwable: Throwable) : PollSubmitResult()
    }

    /** Результат жалобы / удаления */
    sealed class PostActionResult {
        data class ReportSuccess(val topicId: Int, val postId: Int) : PostActionResult()
        data class DeleteSuccess(val post: IBaseForumPost) : PostActionResult()
        data class DeleteFail(val post: IBaseForumPost) : PostActionResult()
        data class Error(val throwable: Throwable) : PostActionResult()
    }

    suspend fun changeReputation(post: IBaseForumPost, type: Boolean, message: String): ReputationResult {
        return try {
            reputationRepository.changeReputation(post.id, post.userId, type, message)
            ReputationResult.Success(post)
        } catch (e: Exception) {
            errorHandler.handle(e)
            ReputationResult.Error(e)
        }
    }

    suspend fun addTopicToFavorite(topicId: Int, subType: String): FavoriteResult {
        return try {
            val ok = favoritesRepository.editFavorites(
                    forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi.ACTION_ADD,
                    -1, topicId, subType
            )
            FavoriteResult.Add(ok)
        } catch (e: Exception) {
            errorHandler.handle(e)
            FavoriteResult.Error(e)
        }
    }

    suspend fun deleteTopicFromFavorite(favId: Int): FavoriteResult {
        return try {
            val ok = favoritesRepository.editFavorites(
                    forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi.ACTION_DELETE,
                    favId, -1, null
            )
            FavoriteResult.Delete(ok)
        } catch (e: Exception) {
            errorHandler.handle(e)
            FavoriteResult.Error(e)
        }
    }

    suspend fun votePost(post: IBaseForumPost, type: Boolean): VoteResult {
        return try {
            val msg = themeRepository.votePost(post.id, type)
            VoteResult.Success(msg)
        } catch (e: Exception) {
            errorHandler.handle(e)
            VoteResult.Error(e)
        }
    }

    suspend fun submitPoll(action: String, method: String, encodedForm: String): PollSubmitResult {
        return try {
            PollSubmitResult.Success(themeRepository.submitPoll(action, method, encodedForm))
        } catch (e: Exception) {
            errorHandler.handle(e)
            PollSubmitResult.Error(e)
        }
    }

    suspend fun reportPost(topicId: Int, post: IBaseForumPost, message: String): PostActionResult {
        return try {
            themeRepository.reportPost(topicId, post.id, message)
            PostActionResult.ReportSuccess(topicId, post.id)
        } catch (e: Exception) {
            errorHandler.handle(e)
            PostActionResult.Error(e)
        }
    }

    suspend fun deletePost(post: IBaseForumPost): PostActionResult {
        return try {
            val success = themeRepository.deletePost(post.id)
            if (success) PostActionResult.DeleteSuccess(post) else PostActionResult.DeleteFail(post)
        } catch (e: Exception) {
            errorHandler.handle(e)
            PostActionResult.Error(e)
        }
    }

    fun copyLink(topicId: Int) {
        clipboardHelper.copyToClipboard("https://4pda.to/forum/index.php?showtopic=$topicId")
    }

    fun copyPostLink(post: IBaseForumPost) {
        clipboardHelper.copyToClipboard(
                "https://4pda.to/forum/index.php?s=&showtopic=${post.topicId}&view=findpost&p=${post.id}"
        )
    }

    fun copyAnchorLink(post: IBaseForumPost, name: String) {
        clipboardHelper.copyToClipboard(
                "https://4pda.to/forum/index.php?showtopic=${post.topicId}&act=findpost&pid=${post.id}&anchor=$name"
        )
    }

    fun copySpoilerLink(post: IBaseForumPost, spoilNumber: String) {
        clipboardHelper.copyToClipboard(
                "https://4pda.to/forum/index.php?showtopic=${post.topicId}&act=findpost&pid=${post.id}&anchor=Spoil-${post.id}-$spoilNumber"
        )
    }

    fun copyText(text: String) {
        clipboardHelper.copyToClipboard(text)
    }

    fun readFromClipboard(): String? = clipboardHelper.readFromClipboard()

    fun showReputationChanged() {
        router.showSystemMessage(R.string.reputation_changed)
    }

    fun showMessageDeleted() {
        router.showSystemMessage(R.string.message_deleted)
    }

    fun showReportSuccess() {
        router.showSystemMessage(R.string.report_post_success)
    }

    fun showQuoteClipboardEmpty() {
        router.showSystemMessage(R.string.quote_clipboard_empty)
    }
}
