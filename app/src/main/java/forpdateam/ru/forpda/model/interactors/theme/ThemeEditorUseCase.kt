package forpdateam.ru.forpda.model.interactors.theme

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.repository.posteditor.PostEditorRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import javax.inject.Inject

/**
 * Инкапсулирует действия редактора в теме:
 * отправка поста, загрузка/удаление вложений, прогрев кэша форм.
 *
 * Вынесен из ThemeViewModel для снижения числа зависимостей (SRP).
 */
class ThemeEditorUseCase @Inject constructor(
        private val editorRepository: PostEditorRepository,
        private val errorHandler: IErrorHandler
) {

    sealed class SendResult {
        data class Success(val page: ThemePage) : SendResult()
        data class Error(val throwable: Throwable) : SendResult()
    }

    sealed class UploadResult {
        data class Success(val items: List<AttachmentItem>) : UploadResult()
        data class Error(val throwable: Throwable) : UploadResult()
    }

    sealed class DeleteResult {
        data class Success(val items: List<AttachmentItem>) : DeleteResult()
        data class Error(val throwable: Throwable) : DeleteResult()
    }

    fun bumpEditPrefetchGeneration() {
        editorRepository.bumpEditPrefetchGeneration()
    }

    fun kickWarmNetworkLoad(postId: Int) {
        editorRepository.kickWarmNetworkLoad(postId)
    }

    fun prefetchEditForPosts(postIds: Iterable<Int>) {
        editorRepository.prefetchEditForPosts(postIds)
    }

    fun invalidateEditCache(postId: Int) {
        editorRepository.invalidateEditCache(postId)
    }

    suspend fun sendPost(form: EditPostForm, scrollTraceId: String? = null): SendResult {
        return try {
            val page = editorRepository.sendPost(form, scrollTraceId)
            SendResult.Success(page)
        } catch (e: Exception) {
            errorHandler.handle(e)
            SendResult.Error(e)
        }
    }

    suspend fun uploadFiles(id: Int, files: List<RequestFile>, pending: List<AttachmentItem>): UploadResult {
        return try {
            val items = editorRepository.uploadFiles(id, files, pending)
            UploadResult.Success(items)
        } catch (e: Exception) {
            errorHandler.handle(e)
            UploadResult.Error(e)
        }
    }

    suspend fun deleteFiles(id: Int, items: List<AttachmentItem>): DeleteResult {
        return try {
            val result = editorRepository.deleteFiles(id, items)
            DeleteResult.Success(result)
        } catch (e: Exception) {
            errorHandler.handle(e)
            DeleteResult.Error(e)
        }
    }
}
