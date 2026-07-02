package forpdateam.ru.forpda.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent

/**
 * Строит действия шторки («Ответить» / «Прочитано») для уведомления события.
 * Общий для foreground-сервиса и фонового воркера, чтобы кнопки не расходились.
 */
object NotificationActions {

    /** «Ответить» доступно только для QMS (есть adресат userId + тема sourceId). */
    fun replyAction(context: Context, event: NotificationEvent): NotificationCompat.Action? {
        if (!event.fromQms() || event.userId <= 0 || event.sourceId <= 0) return null
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
                .setLabel(context.getString(R.string.notification_action_reply_hint))
                .build()
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_NOTIFY_ID, event.notifyId())
            putExtra(NotificationActionReceiver.EXTRA_USER_ID, event.userId)
            putExtra(NotificationActionReceiver.EXTRA_TOPIC_ID, event.sourceId)
        }
        val pi = PendingIntent.getBroadcast(
                context,
                requestCode(event.notifyId(), REQ_REPLY),
                intent,
                // RemoteInput требует MUTABLE на API 31+, чтобы система дописала введённый текст.
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
        return NotificationCompat.Action.Builder(
                R.drawable.ic_send,
                context.getString(R.string.notification_action_reply),
                pi,
        )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()
    }

    /** «Прочитано» — снимает уведомление; для упоминаний ещё и помечает на сервере. */
    fun markReadAction(context: Context, event: NotificationEvent): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_NOTIFY_ID, event.notifyId())
            putExtra(NotificationActionReceiver.EXTRA_IS_MENTION, event.isMention)
            putExtra(NotificationActionReceiver.EXTRA_TOPIC_ID, event.sourceId)
            putExtra(NotificationActionReceiver.EXTRA_POST_ID, event.messageId)
        }
        val pi = PendingIntent.getBroadcast(
                context,
                requestCode(event.notifyId(), REQ_MARK_READ),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
                R.drawable.ic_toolbar_done,
                context.getString(R.string.notification_action_mark_read),
                pi,
        ).build()
    }

    /**
     * Навешивает действия + группировку на билдер одиночного уведомления.
     * Группировка ([setGroup]) по типу события → Android сам сворачивает
     * однотипные уведомления (QMS/избранное/упоминания/сайт) в один бандл, при
     * этом каждое остаётся отдельно-действенным (свои «Ответить»/«Прочитано»).
     * Явный summary не постим: система авто-генерирует заголовок бандла (N+), а
     * ручной summary конфликтовал бы с legacy stacked-путём.
     */
    fun apply(context: Context, builder: NotificationCompat.Builder, event: NotificationEvent) {
        replyAction(context, event)?.let { builder.addAction(it) }
        builder.addAction(markReadAction(context, event))
        builder.setGroup(groupKeyFor(event))
    }

    fun groupKeyFor(event: NotificationEvent): String = when {
        event.isMention -> "forpda.group.mention"
        event.fromQms() -> "forpda.group.qms"
        event.fromTheme() -> "forpda.group.fav"
        else -> "forpda.group.site"
    }

    private const val REQ_REPLY = 1
    private const val REQ_MARK_READ = 2

    // Уникальный requestCode на (notifyId, тип действия), чтобы PendingIntent'ы не схлопывались.
    private fun requestCode(notifyId: Int, action: Int): Int = notifyId * 10 + action

    private fun mutableFlag(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
}
