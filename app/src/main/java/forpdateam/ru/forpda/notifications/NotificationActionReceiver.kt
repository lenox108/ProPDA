package forpdateam.ru.forpda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.interactors.qms.QmsInteractor
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Обрабатывает действия из системной шторки уведомлений (§ уведомления):
 *  - «Прочитано» — снимает уведомление, а для упоминаний ещё и помечает ответ
 *    прочитанным на сервере ([MentionsRepository.markAnswerRead]).
 *  - «Ответить» — RemoteInput из шторки → отправка сообщения QMS
 *    ([QmsInteractor.sendMessage]) в фоне, без открытия приложения.
 *
 * Работа выполняется через [goAsync] + короткоживущий IO-scope: broadcast сам по
 * себе синхронный, но сеть/БД асинхронны.
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var qmsInteractor: QmsInteractor
    @Inject lateinit var mentionsRepository: MentionsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 0)
        when (intent.action) {
            ACTION_MARK_READ -> handleMarkRead(context, intent, notifyId)
            ACTION_REPLY -> handleReply(context, intent, notifyId)
        }
    }

    private fun handleMarkRead(context: Context, intent: Intent, notifyId: Int) {
        NotificationManagerCompat.from(context).cancel(notifyId)
        val isMention = intent.getBooleanExtra(EXTRA_IS_MENTION, false)
        val topicId = intent.getIntExtra(EXTRA_TOPIC_ID, 0)
        val postId = intent.getIntExtra(EXTRA_POST_ID, 0)
        if (!isMention || topicId <= 0 || postId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                mentionsRepository.markAnswerRead(topicId, postId)
            } catch (t: Throwable) {
                Timber.e(t, "Notification mark-read failed topic=$topicId post=$postId")
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleReply(context: Context, intent: Intent, notifyId: Int) {
        val text = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence(KEY_REPLY_TEXT)?.toString()?.trim()
        if (text.isNullOrEmpty()) return
        val userId = intent.getIntExtra(EXTRA_USER_ID, 0)
        val themeId = intent.getIntExtra(EXTRA_TOPIC_ID, 0)
        if (userId <= 0 || themeId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                qmsInteractor.sendMessage(userId, themeId, text, emptyList())
                NotificationManagerCompat.from(context).cancel(notifyId)
            } catch (t: Throwable) {
                Timber.e(t, "Notification quick-reply failed user=$userId theme=$themeId")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_READ = "forpdateam.ru.forpda.notifications.MARK_READ"
        const val ACTION_REPLY = "forpdateam.ru.forpda.notifications.REPLY"
        const val KEY_REPLY_TEXT = "key_reply_text"
        const val EXTRA_NOTIFY_ID = "extra_notify_id"
        const val EXTRA_TOPIC_ID = "extra_topic_id"
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_IS_MENTION = "extra_is_mention"
    }
}
