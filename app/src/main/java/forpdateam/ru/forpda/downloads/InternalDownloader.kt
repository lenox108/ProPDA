package forpdateam.ru.forpda.downloads

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.BatteryDebugLogger
import forpdateam.ru.forpda.common.PermissionHelper
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Internal downloader с DI.
 * Заменяет object InternalDownloader на класс с @Inject.
 */
@Singleton
class InternalDownloader @Inject constructor(
    private val permissionHelper: PermissionHelper,
    private val mainPreferencesHolder: MainPreferencesHolder,
    @Named("data_storage") private val dataStoragePreferences: SharedPreferences
) {
    fun enqueue(context: Context, url: String, fileName: String, mime: String?) {
        val downloadFolderUri = validDownloadFolderUri(context)
        // Для API < 29 запись в public Downloads требует WRITE_EXTERNAL_STORAGE (runtime).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && downloadFolderUri == null) {
            val activity = context as? android.app.Activity
            if (activity != null) {
                permissionHelper.checkStoragePermission(Runnable { enqueueInternal(context, url, fileName, mime, null) }, activity)
                return
            } else {
                Toast.makeText(context, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
                return
            }
        }
        enqueueInternal(context, url, fileName, mime, downloadFolderUri)
    }

    private fun enqueueInternal(context: Context, url: String, fileName: String, mime: String?, downloadFolderUri: String?) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val notificationId = 10000 + (System.currentTimeMillis() % 50000).toInt()
        val input = Data.Builder()
            .putString(DownloadWorker.KEY_URL, url)
            .putString(DownloadWorker.KEY_FILE_NAME, fileName)
            .putString(DownloadWorker.KEY_MIME, mime)
            .putInt(DownloadWorker.KEY_NOTIFICATION_ID, notificationId)
            .apply {
                if (!downloadFolderUri.isNullOrBlank()) {
                    putString(DownloadWorker.KEY_DOWNLOAD_FOLDER_URI, downloadFolderUri)
                }
            }
            .build()

        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(input)
            .addTag(DownloadWorker.WORK_TAG)
            .build()

        // WorkInfo не содержит inputData, поэтому сохраняем метаданные локально по UUID.
        DownloadStore(dataStoragePreferences).put(req.id, url, fileName, mime)

        // Unique per URL+name. REPLACE позволяет перезапускать загрузку при повторном клике.
        val uniqueName = "download:${fileName}:${url.hashCode()}"
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, req)
        BatteryDebugLogger.logState("DownloadWorker", "enqueued", "uniqueName=$uniqueName batteryNotLow=true")
    }

    private fun validDownloadFolderUri(context: Context): String? {
        val uriString = mainPreferencesHolder.getDownloadFolderUri()?.takeIf { it.isNotBlank() } ?: return null
        val uri = Uri.parse(uriString)
        val hasPersistedPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        val folder = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
        if (hasPersistedPermission && folder?.exists() == true && folder.canWrite()) {
            return uriString
        }
        Toast.makeText(context, R.string.download_folder_unavailable, Toast.LENGTH_LONG).show()
        return null
    }
}

