package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Realtime-упоминание публикуется в шторку с якорем `view=findpost&p=<messageId>`, поэтому подмена
 * `messageId` строкой из отставшего `act=mentions` = тап открывает чужой (более старый) пост.
 */
class ForegroundMentionEnrichPolicyTest {

    private fun wsMention(topicId: Int, postId: Int) = NotificationEvent(
            NotificationEvent.Type.MENTION,
            NotificationEvent.Source.THEME
    ).apply {
        sourceId = topicId
        messageId = postId
    }

    private fun row(topicId: Int, postId: Int, nick: String, title: String = "Тема $topicId") = MentionItem().apply {
        type = MentionItem.TYPE_TOPIC
        state = MentionItem.STATE_UNREAD
        link = "index.php?showtopic=$topicId&view=findpost&p=$postId"
        this.title = title
        this.nick = nick
    }

    @Test
    fun exactPostMatch_takesNickAndTitleFromRow() {
        val enriched = ForegroundMentionEnrichPolicy.enrich(
                wsMention(topicId = 100, postId = 555),
                listOf(row(100, 555, "Автор"), row(100, 111, "Кто-то давно"))
        )

        assertEquals(555, enriched.messageId)
        assertEquals("Автор", enriched.userNick)
        assertEquals("Тема 100", enriched.sourceTitle)
    }

    @Test
    fun staleList_keepsEventPostId_andDoesNotBorrowForeignNick() {
        // act=mentions ещё не знает про пост 555 (кэш CDN / удержанный при сбое список).
        val enriched = ForegroundMentionEnrichPolicy.enrich(
                wsMention(topicId = 100, postId = 555),
                listOf(row(100, 111, "Кто-то давно"))
        )

        assertEquals("якорь обязан вести на пост из события, а не на прошлое упоминание", 555, enriched.messageId)
        assertEquals("", enriched.userNick)
        // Заголовок темы одинаков для всех строк темы — его брать безопасно.
        assertEquals("Тема 100", enriched.sourceTitle)
    }

    @Test
    fun rowsOfOtherTopics_areIgnored() {
        val enriched = ForegroundMentionEnrichPolicy.enrich(
                wsMention(topicId = 100, postId = 555),
                listOf(row(200, 555, "Тёзка поста", title = "Другая тема"))
        )

        assertEquals(555, enriched.messageId)
        assertEquals(100, enriched.sourceId)
        assertEquals("", enriched.userNick)
        assertEquals("", enriched.sourceTitle)
    }

    @Test
    fun eventWithoutPostId_fallsBackToFreshestRowOfTopic() {
        val enriched = ForegroundMentionEnrichPolicy.enrich(
                wsMention(topicId = 100, postId = 0),
                listOf(row(100, 777, "Свежий"), row(100, 111, "Старый"))
        )

        assertEquals(777, enriched.messageId)
        assertEquals("Свежий", enriched.userNick)
    }

    @Test
    fun emptyList_publishesEventAsIs() {
        val enriched = ForegroundMentionEnrichPolicy.enrich(wsMention(100, 555), emptyList())

        assertEquals(555, enriched.messageId)
        assertEquals(100, enriched.sourceId)
    }
}
