package forpdateam.ru.forpda.ui.activities.updatechecker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.repository.checker.CheckerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 23.07.17.
 */
class SimpleUpdateChecker(
        private val checkerRepository: CheckerRepository
) {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    fun checkUpdate() {
        scope.launch {
            runCatching { checkerRepository.checkUpdate(true) }
                    .onSuccess { showUpdateData(it) }
                    .onFailure { it.printStackTrace() }
        }
    }

    fun destroy() {
        job.cancel()
    }

    @SuppressLint("NewApi")
    private fun showUpdateData(update: UpdateData) {
        val currentVersionCode = BuildConfig.VERSION_CODE

        if (update.code > currentVersionCode) {
            val context: Context = App.getContext()
            val channelId = "forpda_channel_updates"
            val channelName = context.getString(R.string.updater_notification_title)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }

            val mBuilder = NotificationCompat.Builder(context, channelId)

            val mNotificationManager = NotificationManagerCompat.from(context)

            mBuilder.setSmallIcon(R.drawable.ic_notify_mention)

            mBuilder.setContentTitle(context.getString(R.string.updater_notification_title))
            mBuilder.setContentText(String.format(context.getString(R.string.updater_notification_content_VerName), update.name))

            mBuilder.setChannelId(channelId)


            val notifyIntent = Intent(context, UpdateCheckerActivity::class.java)
            notifyIntent.action = Intent.ACTION_VIEW
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            val notifyPendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, piFlags)
            mBuilder.setContentIntent(notifyPendingIntent)

            mBuilder.setAutoCancel(true)

            mBuilder.priority = NotificationCompat.PRIORITY_DEFAULT
            mBuilder.setCategory(NotificationCompat.CATEGORY_EVENT)

            var defaults = 0
            //defaults = defaults or NotificationCompat.DEFAULT_SOUND
            defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
            mBuilder.setDefaults(defaults)

            mNotificationManager.notify(update.code, mBuilder.build())
        }
    }
}
