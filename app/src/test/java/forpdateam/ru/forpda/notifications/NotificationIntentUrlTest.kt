package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Контент-интент уведомления = якорь открытия. Для упоминания он обязан вести на конкретный пост. */
class NotificationIntentUrlTest {

    private fun mention(topicId: Int, postId: Int) = NotificationEvent(
            NotificationEvent.Type.MENTION,
            NotificationEvent.Source.THEME
    ).apply {
        sourceId = topicId
        messageId = postId
    }

    @Test
    fun themeMention_anchorsOnMentionPost() {
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=100&view=findpost&p=555",
                NotificationPublisher.intentUrlFor(mention(100, 555))
        )
    }

    @Test
    fun themeMentionWithoutPost_doesNotBuildFindpostZero() {
        // `p=0` сервер разворачивает в начало темы — это выглядело как «уведомление открыло старые посты».
        val url = NotificationPublisher.intentUrlFor(mention(100, 0))

        assertFalse(url, url.contains("p=0"))
        assertEquals("https://4pda.to/forum/index.php?showtopic=100", url)
    }
}
