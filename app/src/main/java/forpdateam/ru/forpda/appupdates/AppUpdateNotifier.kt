package forpdateam.ru.forpda.appupdates

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.notifications.NotificationsService
import forpdateam.ru.forpda.ui.activities.MainActivity
import timber.log.Timber
import javax.inject.Inject

class AppUpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @SuppressLint("MissingPermission")
    fun showUpdate(result: AppUpdateRepository.CheckResult.UpdateAvailable) {
        ensureChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            log("notification skipped permission_denied version=${result.version} openActionRegistered=false openActionFired=false")
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            log("notification skipped notifications_disabled version=${result.version} openActionRegistered=false openActionFired=false")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            NotificationsService.activityPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
        val content = result.description
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.updater_notification_content_VerName, result.version.toString())

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify_mention)
            .setContentTitle(context.getString(R.string.updater_notification_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
        log("notification posted version=${result.version} url=${result.url} openActionRegistered=true openActionFired=false")
    }

    private fun log(message: String) {
        Log.i(AppUpdateRepository.LOG_TAG, message)
        Timber.tag(AppUpdateRepository.LOG_TAG).i(message)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pref_title_app_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "forpda_channel_app_updates"
        private const val NOTIFICATION_ID = 1121483
    }
}
