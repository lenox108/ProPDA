package forpdateam.ru.forpda.entity.remote.editpost

import java.util.ArrayList

/**
 * Created by radiationx on 10.01.17.
 */

class EditPostForm {
    var type = TYPE_NEW_POST
    var errorCode = 0
    val attachments = ArrayList<AttachmentItem>()
    var editReason = ""
    var message = ""
    var poll: EditPoll? = null

    var forumId = 0
    var topicId = 0
    var postId = 0
    var st = 0

    /**
     * Восстановленный пользовательский черновик правки (TYPE_EDIT_POST), отличный от чистого
     * серверного текста ([message]). Транзиентное поле: заполняется ViewModel из БД, показывается
     * поверх [message], тогда как baseline «грязности» остаётся на [message]. null — черновика нет.
     */
    var restoredEditDraft: String? = null

    fun addAttachment(item: AttachmentItem) {
        attachments.add(item)
    }

    companion object {
        val ARG_TYPE = "type"
        val TYPE_NEW_POST = 0
        val TYPE_EDIT_POST = 1
        val ERROR_NONE = 0
        val ERROR_NO_PERMISSION = 1
    }
}
