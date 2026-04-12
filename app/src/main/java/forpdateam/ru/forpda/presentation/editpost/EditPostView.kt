package forpdateam.ru.forpda.presentation.editpost

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm
import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Created by radiationx on 01.01.18.
 */
interface EditPostView : IBaseView {
    fun onPostSend(page: ThemePage, form: EditPostForm)
    fun showForm(form: EditPostForm)

    /** Режим правки: очистить поле и вложения до прихода данных с сервера (нет черновика из темы). */
    fun showEditLoadPlaceholder()

    /** Показать черновик из темы/бандла под индикатором загрузки (пока грузится полная форма с сервера). */
    fun showEditLoadingDraft(form: EditPostForm)

    fun setSendRefreshing(isRefreshing: Boolean)

    fun onUploadFiles(items: List<AttachmentItem>)
    fun onDeleteFiles(items: List<AttachmentItem>)
    /** Снять блокировку панели вложений после delete (в т.ч. при ошибке сети). */
    fun onAttachmentDeleteProgressFinished()

    fun showReasonDialog(form: EditPostForm)
    fun sendMessage()
}
