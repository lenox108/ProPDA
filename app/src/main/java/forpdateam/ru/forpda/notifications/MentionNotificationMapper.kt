package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.common.SiteUrls
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem

/**
 * Maps rows from [forpdateam.ru.forpda.model.data.remote.api.mentions.MentionsApi]
 * to [NotificationEvent] for background push delivery.
 */
object MentionNotificationMapper {

    fun mentionKey(item: MentionItem): String? {
        val normalizedLink = item.link
                ?.replace("&amp;", "&")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null
        return topicPostKey(normalizedLink) ?: "${item.type}:$normalizedLink"
    }

    fun toNotificationEvent(item: MentionItem): NotificationEvent? {
        val normalizedLink = item.link
                ?.replace("&amp;", "&")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

        val source = if (item.type == MentionItem.TYPE_NEWS) {
            NotificationEvent.Source.SITE
        } else {
            NotificationEvent.Source.THEME
        }
        val event = NotificationEvent(NotificationEvent.Type.MENTION, source)
        event.sourceTitle = item.title.orEmpty().ifBlank { item.desc.orEmpty() }
        event.userNick = item.nick.orEmpty()

        if (source == NotificationEvent.Source.THEME) {
            val topicId = TOPIC_ID_PATTERN.find(normalizedLink)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: return null
            val postId = extractPostId(normalizedLink) ?: return null
            event.sourceId = topicId
            event.messageId = postId
        } else {
            val articleId = ARTICLE_ID_PATTERN.find(normalizedLink)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val commentId = extractCommentId(normalizedLink)
            if (articleId != null && commentId != null) {
                event.sourceId = articleId
                event.messageId = commentId
            } else {
                // News rows may link to /news/... instead of index.php?p= — use stable hash for notifyId.
                event.sourceId = normalizedLink.hashCode().and(0x7FFFFFFF)
                event.messageId = 0
            }
        }
        return event
    }

    fun intentUrl(item: MentionItem, event: NotificationEvent): String {
        val normalizedLink = item.link
                ?.replace("&amp;", "&")
                ?.trim()
                .orEmpty()
        if (normalizedLink.isNotEmpty()) {
            if (normalizedLink.startsWith("http://", ignoreCase = true) ||
                    normalizedLink.startsWith("https://", ignoreCase = true)) {
                return normalizedLink
            }
            if (normalizedLink.startsWith("/")) {
                return SiteUrls.BASE_HTTPS + normalizedLink
            }
            return "${SiteUrls.BASE_HTTPS}/forum/$normalizedLink"
        }
        if (event.fromTheme() && event.sourceId > 0 && event.messageId > 0) {
            return "https://4pda.to/forum/index.php?showtopic=${event.sourceId}&view=findpost&p=${event.messageId}"
        }
        if (event.fromSite() && event.sourceId > 0 && event.messageId > 0) {
            return "https://4pda.to/index.php?p=${event.sourceId}/#comment${event.messageId}"
        }
        return "https://4pda.to/forum/index.php?act=mentions"
    }

    private fun topicPostKey(link: String): String? {
        val topicId = TOPIC_ID_PATTERN.find(link)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        val postId = extractPostId(link) ?: return null
        return "topic:$topicId:post:$postId"
    }

    private fun extractPostId(link: String): Int? =
            POST_ID_PATTERN.find(link)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun extractCommentId(link: String): Int? =
            COMMENT_ID_PATTERN.find(link)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private val TOPIC_ID_PATTERN = Regex("""(?i)[?&]showtopic=(\d+)""")
    // News permalinks have the form `…/index.php?p=<id>` and are NEVER routed through
    // `/forum/`. The previous `[?&]p=(\d+)` was too greedy: it matched forum
    // `findpost&p=<id>` URLs, which would have caused mention taps on news items
    // to open a random forum post. See AUDIT-L03.
    private val ARTICLE_ID_PATTERN = Regex("""(?i)^(?:(?!/forum/)[^?#]*[?&])p=(\d+)""")
    private val POST_ID_PATTERN = Regex("""(?i)(?:[?&](?:p|pid)=|[/#]entry)(\d+)""")
    private val COMMENT_ID_PATTERN = Regex("""(?i)#comment[-_]?(\d+)""")
}
