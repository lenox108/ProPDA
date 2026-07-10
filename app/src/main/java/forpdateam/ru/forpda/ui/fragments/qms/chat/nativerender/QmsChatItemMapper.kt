package forpdateam.ru.forpda.ui.fragments.qms.chat.nativerender

import forpdateam.ru.forpda.entity.remote.qms.QmsMessage
import forpdateam.ru.forpda.ui.fragments.theme.nativerender.PostBodyRenderer

/**
 * Maps the parsed [QmsMessage] window into renderable [QmsChatItem]s, segmenting each message body
 * into [forpdateam.ru.forpda.ui.fragments.theme.nativerender.BodyBlock]s.
 *
 * Pure and Android-free (Jsoup only, via [PostBodyRenderer]) so it is JVM-unit-testable.
 *
 * The whole visible window is re-mapped on every emission (a new message, a read-status flip, an
 * upward page of history), so bodies are memoised by message id + the fields that affect the mapped
 * output. Without the cache, appending one message would re-parse the other 30 bodies on every
 * WebSocket tick. Entries for messages that left the window are dropped, keeping the cache bounded.
 */
class QmsChatItemMapper {

    private val renderer = PostBodyRenderer()
    private val cache = HashMap<Long, QmsChatItem.Message>()

    fun map(messages: List<QmsMessage>): List<QmsChatItem> {
        val out = ArrayList<QmsChatItem>(messages.size)
        val liveKeys = HashSet<Long>(messages.size)
        for (message in messages) {
            if (message.isDate) {
                out.add(QmsChatItem.DateDivider(message.date.orEmpty()))
                continue
            }
            val html = message.content.orEmpty()
            val key = cacheKey(message, html)
            liveKeys.add(key)
            val item = cache.getOrPut(key) {
                QmsChatItem.Message(
                        id = message.id,
                        isMine = message.isMyMessage,
                        isUnread = !message.readStatus,
                        time = message.time.orEmpty(),
                        contentHtml = html,
                        blocks = renderer.render(html),
                )
            }
            out.add(item)
        }
        cache.keys.retainAll(liveKeys)
        return out
    }

    /** Keyed on every [QmsMessage] field the mapped item derives from, so a stale body never sticks. */
    private fun cacheKey(message: QmsMessage, html: String): Long {
        var state = html.hashCode()
        state = 31 * state + message.time.orEmpty().hashCode()
        state = 31 * state + (if (message.isMyMessage) 1 else 0)
        state = 31 * state + (if (message.readStatus) 1 else 0)
        return (message.id.toLong() shl 32) or (state.toLong() and 0xFFFF_FFFFL)
    }
}
