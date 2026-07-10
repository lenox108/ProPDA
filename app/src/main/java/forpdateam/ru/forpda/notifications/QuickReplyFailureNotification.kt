package forpdateam.ru.forpda.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.activities.MainActivity
import timber.log.Timber

/**
 * Ответ из шторки уходит в сеть без открытия приложения, и отправка может не удаться.
 * Уведомление к этому моменту уже свёрнуто системой, поэтому без явного сигнала пользователь
 * остаётся в уверенности, что сообщение доставлено, а введённый текст теряется навсегда.
 *
 * Показываем неотправленный текст обратно и даём два выхода: «Повторить» (текст едет в extra,
 * заново набирать не нужно) и тап по уведомлению — открыть диалог в приложении.
 */
object QuickReplyFailureNotification {

    @SuppressLint("MissingPermission")
    fun show(context: Context, originalNotifyId: Int, userId: Int, themeId: Int, text: String) {
        NotificationsService.createEventChannels(context)

        val builder = NotificationCompat.Builder(context, NotificationsService.CHANNEL_QMS_ID)
                .setSmallIcon(R.drawable.ic_notify_qms)
                .setContentTitle(context.getString(R.string.notification_quick_reply_failed_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openChatIntent(context, userId, themeId))
                .addAction(retryAction(context, originalNotifyId, userId, themeId, text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_ERROR)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Timber.w("Quick-reply failure notification skipped: POST_NOTIFICATIONS denied")
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(failureNotifyId(originalNotifyId), builder.build())
        }.onFailure { Timber.e(it, "Quick-reply failure notification skipped") }
    }

    private fun retryAction(
            context: Context,
            originalNotifyId: Int,
            userId: Int,
            themeId: Int,
            text: String
    ): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            data = Uri.parse("forpda://notification/${failureNotifyId(originalNotifyId)}/retry")
            putExtra(NotificationActionReceiver.EXTRA_NOTIFY_ID, failureNotifyId(originalNotifyId))
            putExtra(NotificationActionReceiver.EXTRA_USER_ID, userId)
            putExtra(NotificationActionReceiver.EXTRA_TOPIC_ID, themeId)
            putExtra(NotificationActionReceiver.EXTRA_PENDING_TEXT, text)
        }
        val pi = PendingIntent.getBroadcast(
                context,
                failureNotifyId(originalNotifyId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
                R.drawable.ic_send,
                context.getString(R.string.notification_action_retry),
                pi,
        ).build()
    }

    private fun openChatIntent(context: Context, userId: Int, themeId: Int): PendingIntent {
        val url = "https://4pda.to/forum/index.php?act=qms&mid=$userId&t=$themeId"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .setClass(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
                context,
                themeId,
                intent,
                NotificationsService.activityPendingIntentFlags(0)
        )
    }

    /**
     * Отдельный ID, чтобы уведомление о неудаче не затирало (и не затиралось) уведомлением о
     * самом входящем сообщении. NotificationEvent.notifyId() всегда неотрицателен, поэтому
     * отрицательный поддиапазон гарантированно свободен: пересечься можно только с другой
     * неудачей ответа, а у неё и должен быть свой ID.
     */
    private fun failureNotifyId(originalNotifyId: Int): Int =
            -(originalNotifyId and FAILURE_ID_MASK) - FAILURE_ID_OFFSET

    private const val FAILURE_ID_MASK = 0x3FFFFFFF
    /** Ниже заняты служебные ID: stacked QMS (-123), stacked избранное (-234), FGS (-345). */
    private const val FAILURE_ID_OFFSET = 1000
}
