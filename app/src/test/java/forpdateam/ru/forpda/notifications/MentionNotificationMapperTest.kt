package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionNotificationMapperTest {

    @Test
    fun mentionKey_forumTopicPost() {
        val item = MentionItem().apply {
            type = MentionItem.TYPE_TOPIC
            link = "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=77"
        }
        assertEquals("topic:3:post:77", MentionNotificationMapper.mentionKey(item))
    }

    @Test
    fun toNotificationEvent_forumMention() {
        val item = MentionItem().apply {
            type = MentionItem.TYPE_TOPIC
            link = "https://4pda.to/forum/index.php?showtopic=5&view=findpost&p=12"
            title = "Some topic"
            nick = "alice"
        }
        val event = requireNotNull(MentionNotificationMapper.toNotificationEvent(item))
        assertTrue(event.isMention)
        assertTrue(event.fromTheme())
        assertEquals(5, event.sourceId)
        assertEquals(12, event.messageId)
        assertEquals("Some topic", event.sourceTitle)
        assertEquals("alice", event.userNick)
    }

    @Test
    fun toNotificationEvent_newsMentionWithArticleAndComment() {
        val item = MentionItem().apply {
            type = MentionItem.TYPE_NEWS
            link = "https://4pda.to/index.php?p=42#comment-99"
            title = "News title"
            nick = "bob"
        }
        val event = requireNotNull(MentionNotificationMapper.toNotificationEvent(item))
        assertTrue(event.fromSite())
        assertEquals(42, event.sourceId)
        assertEquals(99, event.messageId)
        assertEquals(
                "https://4pda.to/index.php?p=42#comment-99",
                MentionNotificationMapper.intentUrl(item, event)
        )
    }

    @Test
    fun toNotificationEvent_newsMentionWithoutNumericIds_usesLinkHash() {
        val item = MentionItem().apply {
            type = MentionItem.TYPE_NEWS
            link = "/news/some-article/"
            title = "News"
        }
        val event = requireNotNull(MentionNotificationMapper.toNotificationEvent(item))
        assertTrue(event.fromSite())
        assertTrue(event.sourceId > 0)
        assertEquals(0, event.messageId)
        assertEquals(
                "https://4pda.to/news/some-article/",
                MentionNotificationMapper.intentUrl(item, event)
        )
    }

    @Test
    fun intentUrl_forumFindpost() {
        val item = MentionItem().apply {
            type = MentionItem.TYPE_TOPIC
            link = "index.php?showtopic=1&view=findpost&p=2"
        }
        val event = requireNotNull(MentionNotificationMapper.toNotificationEvent(item))
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=2",
                MentionNotificationMapper.intentUrl(item, event)
        )
    }

    @Test
    fun toNotificationEvent_newsMentionWithForumPermalinkFallsBackToHash() {
        // Regression test for AUDIT-L03: a malformed source URL containing `/forum/`
        // must not be parsed as a news article even if it has `?p=<id>`; the
        // mapper should fall back to the link-hash sourceId.
        val item = MentionItem().apply {
            type = MentionItem.TYPE_NEWS
            link = "https://4pda.to/forum/index.php?showtopic=3&view=findpost&p=77#comment-99"
            title = "Should not be opened as news"
        }
        val event = requireNotNull(MentionNotificationMapper.toNotificationEvent(item))
        assertTrue(event.fromSite())
        // Neither the news article id (77) nor the comment id (99) should be trusted.
        assertEquals(0, event.messageId)
        assertTrue(
            "expected a stable hash sourceId, got ${event.sourceId}",
            event.sourceId > 0
        )
    }
}
