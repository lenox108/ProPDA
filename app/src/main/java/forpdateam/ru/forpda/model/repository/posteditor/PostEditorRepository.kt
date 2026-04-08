package forpdateam.ru.forpda.model.repository.posteditor

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCache
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentsApi
import forpdateam.ru.forpda.model.data.remote.api.editpost.EditPostApi
import forpdateam.ru.forpda.model.repository.BaseRepository
import io.reactivex.Observable
import io.reactivex.Single
import java.util.concurrent.TimeUnit

/**
 * Created by radiationx on 01.01.18.
 */

class PostEditorRepository(
        private val schedulers: SchedulersProvider,
        private val editPostApi: EditPostApi,
        private val attachmentsApi: AttachmentsApi,
        private val forumUsersCache: ForumUsersCache
) : BaseRepository(schedulers) {

    /** Только страница редактирования — BBCode и поля формы; не блокируется вложениями. */
    fun loadForm(postId: Int): Single<EditPostForm> = Single
            .fromCallable { editPostApi.loadForm(postId) }
            .timeout(60, TimeUnit.SECONDS, schedulers.io())
            .runInIoToUi()

    /** Отдельно от [loadForm], чтобы зависание/долгий ответ attach не держали спиннер и BBCode. */
    fun loadEditAttachments(postId: Int): Single<List<AttachmentItem>> = Single
            .fromCallable { editPostApi.loadEditAttachments(postId) }
            .timeout(12, TimeUnit.SECONDS, schedulers.io())
            .onErrorReturn { emptyList() }
            .runInIoToUi()

    fun uploadFiles(id: Int, files: List<RequestFile>, pending: List<AttachmentItem>): Single<List<AttachmentItem>> = Single
            .fromCallable { attachmentsApi.uploadTopicFiles(id, files, pending) }
            .runInIoToUi()

    fun deleteFiles(id: Int, items: List<AttachmentItem>): Single<List<AttachmentItem>> = Single
            .fromCallable { attachmentsApi.deleteTopicFiles(id, items) }
            .runInIoToUi()

    fun sendPost(form: EditPostForm): Single<ThemePage> = Single
            .fromCallable { editPostApi.sendPost(form) }
            .doOnSuccess { saveUsers(it) }
            .runInIoToUi()

    private fun saveUsers(page: ThemePage) {
        val forumUsers = page.posts.map { post ->
            ForumUser().apply {
                id = post.userId
                nick = post.nick
                avatar = post.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

}
