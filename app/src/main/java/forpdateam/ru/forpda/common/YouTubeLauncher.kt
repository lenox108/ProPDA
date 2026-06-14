package forpdateam.ru.forpda.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

object YouTubeLauncher {
    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    fun openApp(context: Context, videoId: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
            setPackage(YOUTUBE_PACKAGE)
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Timber.i(e, "YouTube app is unavailable for video %s", videoId)
            false
        } catch (e: SecurityException) {
            Timber.w(e, "YouTube app rejected video %s", videoId)
            false
        }
    }
}
