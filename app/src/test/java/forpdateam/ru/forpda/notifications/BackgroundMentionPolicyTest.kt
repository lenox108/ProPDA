package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тип MENTION рождается только в WebSocket-канале, а `act=inspector` отдаёт исключительно NEW.
 * Поэтому в фоне упоминания не приходили вовсе, и [EventsCheckWorker] опрашивает `act=mentions`
 * отдельно. Здесь проверяется решение о том, что показывать, а что уже показывали.
 */
class BackgroundMentionPolicyTest {

    private fun topicMention(topicId: Int, postId: Int, read: Boolean = false) = MentionItem().apply {
        type = MentionItem.TYPE_TOPIC
        state = if (read) MentionItem.STATE_READ else MentionItem.STATE_UNREAD
        link = "index.php?showtopic=$topicId&view=findpost&p=$postId"
        title = "Тема $topicId"
        nick = "somebody"
    }

    @Test
    fun firstRun_seedsKeysWithoutNotifying() {
        val items = listOf(topicMention(100, 1), topicMention(200, 2))

        val decision = BackgroundMentionPolicy.decide(items, alreadyNotifiedKeys = emptySet(), seeded = false)

        assertTrue("первый проход обязан молчать, иначе высыпет всю историю", decision.toNotify.isEmpty())
        assertEquals(2, decision.keysToPersist.size)
        assertTrue(decision.markSeeded)
    }

    @Test
    fun secondRun_notifiesOnlyAboutUnseenMentions() {
        val old = topicMention(100, 1)
        val fresh = topicMention(200, 2)
        val seenKey = MentionNotificationMapper.mentionKey(old)!!

        val decision = BackgroundMentionPolicy.decide(
                items = listOf(old, fresh),
                alreadyNotifiedKeys = setOf(seenKey),
                seeded = true
        )

        assertEquals(listOf(fresh), decision.toNotify)
        assertFalse(decision.markSeeded)
    }

    @Test
    fun readMentions_areNeitherNotifiedNorPersisted() {
        val read = topicMention(100, 1, read = true)
        val unread = topicMention(200, 2)

        val decision = BackgroundMentionPolicy.decide(listOf(read, unread), emptySet(), seeded = true)

        assertEquals(listOf(unread), decision.toNotify)
        // Прочитанное выпадает из сохраняемого множества — так оно и не растёт без границы.
        assertEquals(setOf(MentionNotificationMapper.mentionKey(unread)), decision.keysToPersist)
    }

    @Test
    fun mutedTopic_isNotNotifiedButStillRemembered() {
        val muted = topicMention(100, 1)
        val other = topicMention(200, 2)

        val decision = BackgroundMentionPolicy.decide(
                items = listOf(muted, other),
                alreadyNotifiedKeys = emptySet(),
                seeded = true,
                mutedTopicIds = setOf(100)
        )

        assertEquals(listOf(other), decision.toNotify)
        assertTrue(
                "заглушённое упоминание всё равно нельзя показать позже",
                MentionNotificationMapper.mentionKey(muted) in decision.keysToPersist
        )
    }

    @Test
    fun nothingNew_notifiesNothing() {
        val item = topicMention(100, 1)
        val key = MentionNotificationMapper.mentionKey(item)!!

        val decision = BackgroundMentionPolicy.decide(listOf(item), setOf(key), seeded = true)

        assertTrue(decision.toNotify.isEmpty())
        assertEquals(setOf(key), decision.keysToPersist)
    }
}
