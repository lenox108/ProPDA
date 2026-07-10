package forpdateam.ru.forpda.notifications

import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Действие «Прочитано» помечает ответ прочитанным только для упоминаний В ТЕМЕ.
 *
 * MentionsRepository строит локальный ключ как `topic:<id>:post:<id>`. У упоминания в
 * комментарии к новости sourceId — это ID статьи, и такой ключ не совпадёт ни с чем: строка в
 * «Ответах» осталась бы жирной, а в persist-множество прочитанного лёг бы мусор навсегда.
 */
class NotificationActionsMarkReadTest {

    private fun mention(source: NotificationEvent.Source) =
            NotificationEvent(NotificationEvent.Type.MENTION, source).apply {
                sourceId = 100
                messageId = 7
            }

    @Test
    fun themeMention_marksAnswerRead() {
        assertTrue(NotificationActions.marksAnswerReadFor(mention(NotificationEvent.Source.THEME)))
    }

    @Test
    fun siteMention_doesNotMarkAnswerRead() {
        assertFalse(NotificationActions.marksAnswerReadFor(mention(NotificationEvent.Source.SITE)))
    }

    @Test
    fun plainEvents_neverMarkAnswerRead() {
        val qms = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.QMS)
        val fav = NotificationEvent(NotificationEvent.Type.NEW, NotificationEvent.Source.THEME)

        assertFalse(NotificationActions.marksAnswerReadFor(qms))
        assertFalse(NotificationActions.marksAnswerReadFor(fav))
    }
}
