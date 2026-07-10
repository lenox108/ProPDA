package forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender

import forpdateam.ru.forpda.ui.fragments.theme.nativerender.BodyBlock

/**
 * One row of the native QMS chat list, mapped from [forpdateam.ru.forpda.entity.remote.qms.QmsMessage]
 * by [QmsChatItemMapper].
 *
 * The server emits QMS message bodies with the SAME `.post-block` markup as forum posts, so the
 * message body is a plain [BodyBlock] list produced by the shared
 * [forpdateam.ru.forpda.ui.fragments.theme.nativerender.PostBodyRenderer] — quotes, spoilers, code,
 * images and file attachments all render natively without a per-screen renderer.
 */
sealed interface QmsChatItem {

    /** A «сегодня / 12 мая» separator row the server injects between message groups. */
    data class DateDivider(val date: String) : QmsChatItem

    /**
     * A chat bubble. [isMine] picks the side and the tonal fill; [isUnread] shows the red status dot
     * (WebView parity: `.mess_container.unread .status`). [contentHtml] is kept verbatim so the
     * long-press «Копировать» can produce the message's plain text without re-walking the blocks.
     */
    data class Message(
            val id: Int,
            val isMine: Boolean,
            val isUnread: Boolean,
            val time: String,
            val contentHtml: String,
            val blocks: List<BodyBlock>,
    ) : QmsChatItem
}
