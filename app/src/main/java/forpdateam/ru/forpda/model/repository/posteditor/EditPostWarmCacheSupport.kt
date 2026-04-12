package forpdateam.ru.forpda.model.repository.posteditor

import android.os.Parcel
import android.util.Log
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.editpost.EditPostForm

private const val WARM_CACHE_TAG = "ForPDA.EditPostWarm"

internal data class EditPostWarmEntry(
        val form: EditPostForm?,
        val attachments: List<AttachmentItem>?,
        val storedAtMillis: Long,
)

internal fun EditPostWarmEntry.isFresh(ttlMs: Long): Boolean =
        System.currentTimeMillis() - storedAtMillis < ttlMs

internal fun EditPostForm.deepCopyForCache(): EditPostForm {
    val d = EditPostForm()
    d.type = type
    d.errorCode = errorCode
    d.editReason = editReason
    d.message = message
    d.poll = poll
    d.forumId = forumId
    d.topicId = topicId
    d.postId = postId
    d.st = st
    for (a in attachments) {
        d.attachments.add(copyAttachmentParcelable(a))
    }
    return d
}

internal fun copyAttachmentParcelable(src: AttachmentItem): AttachmentItem {
    val p = Parcel.obtain()
    try {
        src.writeToParcel(p, 0)
        p.setDataPosition(0)
        val out = AttachmentItem.CREATOR.createFromParcel(p)
        if (out == null) {
            Log.w(WARM_CACHE_TAG, "createFromParcel returned null, fallback empty item")
            return AttachmentItem()
        }
        return out
    } finally {
        p.recycle()
    }
}

internal fun deepCopyAttachmentList(src: List<AttachmentItem>): List<AttachmentItem> =
        src.map { copyAttachmentParcelable(it) }
