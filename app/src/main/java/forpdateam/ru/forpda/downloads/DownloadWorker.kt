package forpdateam.ru.forpda.downloads

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.client.Client
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.io.IOException
import android.content.SharedPreferences
import java.util.concurrent.TimeUnit
import javax.inject.Named
import kotlin.math.max

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val webClient: IWebClient,
    private val notificationPreferencesHolder: NotificationPreferencesHolder,
    @Named("data_storage") private val dataStoragePreferences: SharedPreferences
) : Worker(appContext, params) {

    override fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: run {
            Timber.w("DownloadWorker: missing KEY_URL")
            return Result.failure()
        }
        val fileName = inputData.getString(KEY_FILE_NAME) ?: run {
            Timber.w("DownloadWorker: missing KEY_FILE_NAME")
            return Result.failure()
        }
        val mime = inputData.getString(KEY_MIME)
        val downloadFolderUri = inputData.getString(KEY_DOWNLOAD_FOLDER_URI)

        if (BuildConfig.DEBUG) Timber.d("DownloadWorker start: mime=$mime attempt=$runAttemptCount")

        val downloadsEnabled = notificationPreferencesHolder.getDownloadsEnabled()
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: downloadsEnabled=$downloadsEnabled")
        if (!downloadsEnabled) {
            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: notifications disabled, running without notifications")
        }

        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 10000 + (System.currentTimeMillis() % 50000).toInt())
        if (downloadsEnabled) {
            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: setting foreground notification")
            setForegroundSafe(foregroundInfo(notificationId, fileName, 0, indeterminate = true))
        }

        val okHttp = (webClient as? Client)?.getHttpClient() ?: run {
            Timber.e("DownloadWorker: webClient is not Client instance (class=${webClient.javaClass.name})")
            return Result.failure()
        }
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: got OkHttpClient instance")

        // Создаём отдельный клиент с увеличенными таймаутами для загрузки больших файлов
        val downloadClient = okHttp.newBuilder()
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: created download client with extended timeouts")

        // Проверяем наличие частично загруженного файла для докачки
        val destResult = createDestination(fileName, mime, downloadFolderUri) ?: run {
            Timber.e("DownloadWorker: createDestination returned null")
            return Result.failure()
        }
        try {
            val existingSize = getExistingFileSize(destResult)
            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: existing file size=$existingSize bytes")

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://4pda.to/forum/")
                .apply {
                    // Если есть частично загруженный файл, добавляем Range header для докачки
                    if (existingSize > 0) {
                        header("Range", "bytes=$existingSize-")
                        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: added Range header for resume from $existingSize bytes")
                    }
                }
                .header("Accept-Encoding", "identity") // Отключаем сжатие для корректной докачки
                .get()
                .build()
            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: built request")

            // Ограничиваем число попыток — без этого при «плохом» URL ретраи шли бесконечно.
            val maxAttempts = 3
            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: executing request (maxAttempts=$maxAttempts)")
            val response: Response = try {
                downloadClient.newCall(req).execute()
            } catch (e: IOException) {
                Timber.e(e, "DownloadWorker: network error")
                destResult.destination.abort()
                return if (runAttemptCount < maxAttempts) Result.retry() else Result.failure()
            } catch (e: Exception) {
                Timber.e(e, "DownloadWorker: unexpected error on newCall")
                destResult.destination.abort()
                return Result.failure()
            }
            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: got response code=${response.code}")

            response.use { resp ->
                val isResume = resp.code == 206 // Partial Content
                if (!resp.isSuccessful && resp.code != 206) {
                    Timber.w("DownloadWorker: HTTP ${resp.code} ${resp.message}")
                    // Улучшенная логика ретраев:
                    // 429 (Too Many Requests) - ретраить с экспоненциальной задержкой
                    // 401/403/5xx - ретраить
                    // 404/400 - не ретраить
                    val retryable = when (resp.code) {
                        429 -> true // Too Many Requests
                        in 500..599 -> true // Server errors
                        401, 403 -> true // Auth errors
                        404, 400 -> false // Client errors - don't retry
                        else -> false
                    }
                    destResult.destination.abort()
                    return if (retryable && runAttemptCount < maxAttempts) Result.retry() else Result.failure()
                }
                val body = resp.body ?: run {
                    Timber.w("DownloadWorker: empty body")
                    destResult.destination.abort()
                    return Result.failure()
                }

                // Определяем полный размер файла
                val contentRange = resp.header("Content-Range")
                val len = if (isResume && contentRange != null) {
                    // Content-Range: bytes start-end/total
                    val total = contentRange.substringAfterLast('/').toLongOrNull() ?: -1L
                    if (BuildConfig.DEBUG) Timber.d("DownloadWorker: resume mode, total size from Content-Range: $total")
                    total
                } else {
                    max(body.contentLength(), -1L)
                }

                // Проверка свободного места на диске
                if (len > 0) {
                    val availableSpace = getAvailableStorageSpace()
                    if (availableSpace < len - existingSize) {
                        Timber.e("DownloadWorker: insufficient storage space")
                        destResult.destination.abort()
                        return Result.failure()
                    }
                }

                try {
                    writeToDestination(notificationId, fileName, body, len, destResult.destination, existingSize)
                } catch (e: IOException) {
                    Timber.e(e, "DownloadWorker: IO error while writing")
                    destResult.destination.abort()
                    return if (runAttemptCount < maxAttempts) Result.retry() else Result.failure()
                } catch (e: Exception) {
                    Timber.e(e, "DownloadWorker: error while writing")
                    destResult.destination.abort()
                    return Result.failure()
                }

                destResult.destination.commit()
                try {
                    setProgressAsync(workDataOf(KEY_PROGRESS to 100)).get()
                } catch (_: Exception) {
                }

                // финальное уведомление с кнопкой Открыть (только если включено)
                if (downloadsEnabled) {
                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(destResult.openUri, mime ?: "*/*")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val openPendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        notificationId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // Используем отдельный канал для завершённых загрузок
                    val done = DownloadNotifications.completedBuilder(applicationContext)
                        .setContentTitle(applicationContext.getString(R.string.downloaded))
                        .setContentText(fileName)
                        .setContentIntent(openPendingIntent)
                        .addAction(0, applicationContext.getString(R.string.open), openPendingIntent)
                        .build()
                    // Используем другой notificationId для финального уведомления чтобы WorkManager не удалял его
                    val finalNotificationId = notificationId + 1000000
                    notifyIfAllowed(finalNotificationId, done)
                }

                val resolvedPath = destResult.absolutePath
                    ?: resolveMediaFilePath(applicationContext, destResult.openUri, fileName)
                val completedAt = System.currentTimeMillis()
                DownloadStore(dataStoragePreferences).markCompleted(id, completedAt)
                val out = Data.Builder()
                    .putString(KEY_OUTPUT_URI, destResult.openUri.toString())
                    .putLong(KEY_COMPLETED_AT, completedAt)
                    .apply {
                        if (resolvedPath != null) putString(KEY_OUTPUT_PATH, resolvedPath)
                    }
                    .build()
                return Result.success(out)
            }
        } catch (e: Exception) {
            Timber.e(e, "DownloadWorker: failed after destination creation")
            destResult.destination.abort()
            return Result.failure()
        }
    }

    private fun writeToDestination(
        notificationId: Int,
        fileName: String,
        body: okhttp3.ResponseBody,
        len: Long,
        dest: Destination,
        existingSize: Long
    ) {
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: writeToDestination start, len=$len, existingSize=$existingSize")
        val inStream = body.byteStream()
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: got input stream")
        val out = dest.openOutputStream(existingSize > 0).sink().buffer()
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: got output stream (append=${existingSize > 0})")

        // Адаптивный размер буфера: 64KB для больших файлов (>10MB), 32KB для остальных
        val bufferSize = if (len > 10 * 1024 * 1024) 64 * 1024 else DEFAULT_BUFFER
        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: using buffer size=$bufferSize bytes")

        inStream.use { input ->
            out.use { output ->
                val buf = ByteArray(bufferSize)
                var read: Int
                var total = existingSize // Начинаем с существующего размера
                var lastPercent = -1
                var lastLogTime = System.currentTimeMillis()
                while (true) {
                    if (isStopped) throw IOException("stopped")
                    read = input.read(buf)
                    if (read <= 0) break
                    output.write(buf, 0, read)
                    total += read.toLong()

                    // Логируем прогресс каждые 5 секунд
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 5000) {
                        if (BuildConfig.DEBUG) Timber.d("DownloadWorker: progress total=$total bytes, len=$len")
                        lastLogTime = now
                    }

                    if (len > 0) {
                        val percent = ((total * 100) / len).toInt().coerceIn(0, 100)
                        if (percent != lastPercent && (percent - lastPercent >= 2 || percent == 100)) {
                            lastPercent = percent
                            if (BuildConfig.DEBUG) Timber.d("DownloadWorker: progress $percent% (total=$total, len=$len)")
                            setProgressAsync(workDataOf(KEY_PROGRESS to percent))
                            val notif = foregroundInfo(notificationId, fileName, percent, indeterminate = false).notification
                            notifyIfAllowed(notificationId, notif)
                        }
                    }
                }
                output.flush()
                if (BuildConfig.DEBUG) Timber.d("DownloadWorker: writeToDestination finished, total=$total bytes")
            }
        }
    }

    private fun foregroundInfo(id: Int, fileName: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val progressText = if (indeterminate) {
            fileName
        } else {
            "$fileName (${progress}%)"
        }

        // PendingIntent для открытия экрана загрузок при клике на уведомление
        val downloadsIntent = Intent(applicationContext, forpdateam.ru.forpda.ui.activities.MainActivity::class.java).apply {
            putExtra("open_downloads", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            downloadsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = DownloadNotifications.baseBuilder(applicationContext)
            .setContentTitle(applicationContext.getString(R.string.loading_1))
            .setContentText(progressText)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(pendingIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notif)
        }
    }

    private fun setForegroundSafe(info: ForegroundInfo) {
        try {
            setForegroundAsync(info).get()
        } catch (_: Exception) {
        }
    }

    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        }.onFailure { e ->
            Timber.w(e, "DownloadWorker: notification skipped")
        }
    }

    private data class DestinationResult(
        val destination: Destination,
        val openUri: Uri,
        val absolutePath: String? = null
    )

    private fun createDestination(fileName: String, mime: String?, downloadFolderUri: String?): DestinationResult? {
        if (!downloadFolderUri.isNullOrBlank()) {
            createDocumentTreeDestination(fileName, mime, downloadFolderUri)?.let { return it }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            DestinationResult(
                destination = MediaStoreDestination(applicationContext, uri, values),
                openUri = uri
            )
        } else {
            val dir = legacyDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            val file = uniqueFile(dir, fileName)
            val contentUri = FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            DestinationResult(
                destination = FileDestination(file),
                openUri = contentUri
                ,
                absolutePath = file.absolutePath
            )
        }
    }

    private fun createDocumentTreeDestination(fileName: String, mime: String?, downloadFolderUri: String): DestinationResult? {
        val treeUri = Uri.parse(downloadFolderUri)
        val hasPersistedPermission = applicationContext.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }
        if (!hasPersistedPermission) {
            Timber.w("DownloadWorker: missing persisted permission for selected folder")
            return null
        }
        val folder = DocumentFile.fromTreeUri(applicationContext, treeUri)
        if (folder == null || !folder.exists() || !folder.canWrite()) {
            Timber.w("DownloadWorker: selected folder is unavailable")
            return null
        }
        val targetFile = uniqueDocument(folder, fileName, mime)
        return DestinationResult(
            destination = DocumentTreeDestination(applicationContext, targetFile.uri),
            openUri = targetFile.uri
        )
    }

    private fun uniqueDocument(folder: DocumentFile, fileName: String, mime: String?): DocumentFile {
        val base = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        var candidateName = fileName
        var index = 1
        while (folder.findFile(candidateName) != null) {
            candidateName = if (ext.isNotEmpty()) "$base ($index).$ext" else "$base ($index)"
            index++
        }
        return folder.createFile(mime ?: "application/octet-stream", candidateName)
            ?: throw IOException("Failed to create SAF document")
    }

    /**
     * Реальный путь в public Download (для удаления через File, если MediaStore.delete даёт 0).
     */
    private fun resolveMediaFilePath(context: Context, uri: Uri, requestedName: String): String? {
        val resolver = context.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: requestedName
        val dir = legacyDownloadDir()
        val candidate = File(dir, displayName)
        return if (candidate.exists()) candidate.absolutePath else null
    }

    private fun legacyDownloadDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            LEGACY_DOWNLOAD_SUBDIR
        )
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "")
        var f = File(dir, fileName)
        var i = 1
        while (f.exists()) {
            val name = if (ext.isNotEmpty()) "$base ($i).$ext" else "$base ($i)"
            f = File(dir, name)
            i++
        }
        return f
    }

    private fun getAvailableStorageSpace(): Long {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir?.freeSpace ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Timber.e(e, "DownloadWorker: failed to get available storage space")
            Long.MAX_VALUE
        }
    }

    private fun getExistingFileSize(destResult: DestinationResult): Long {
        return try {
            when (val dest = destResult.destination) {
                is FileDestination -> {
                    if (dest.file.exists()) dest.file.length() else 0L
                }
                is MediaStoreDestination -> {
                    val cursor = applicationContext.contentResolver.query(
                        dest.uri,
                        arrayOf(MediaStore.MediaColumns.SIZE),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) it.getLong(0) else 0L
                    } ?: 0L
                }
                is DocumentTreeDestination -> {
                    val cursor = applicationContext.contentResolver.query(
                        dest.uri,
                        arrayOf(android.provider.OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) it.getLong(0) else 0L
                    } ?: 0L
                }
                else -> 0L
            }
        } catch (e: Exception) {
            Timber.e(e, "DownloadWorker: failed to get existing file size")
            0L
        }
    }

    private interface Destination {
        fun openOutputStream(append: Boolean = false): java.io.OutputStream
        fun commit()
        fun abort()
    }

    private class FileDestination(val file: File) : Destination {
        override fun openOutputStream(append: Boolean): java.io.OutputStream {
            file.parentFile?.mkdirs()
            return if (append) {
                java.io.FileOutputStream(file, true)
            } else {
                file.outputStream()
            }
        }

        override fun commit() = Unit
        override fun abort() {
            runCatching { file.delete() }
        }
    }

    private class MediaStoreDestination(
        private val context: Context,
        val uri: Uri,
        private val values: ContentValues
    ) : Destination {
        override fun openOutputStream(append: Boolean): java.io.OutputStream {
            val mode = if (append) "wa" else "w"
            return context.contentResolver.openOutputStream(uri, mode) ?: throw IOException("openOutputStream null")
        }

        override fun commit() {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }

        override fun abort() {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private class DocumentTreeDestination(
        private val context: Context,
        val uri: Uri
    ) : Destination {
        override fun openOutputStream(append: Boolean): java.io.OutputStream {
            val mode = if (append) "wa" else "w"
            return context.contentResolver.openOutputStream(uri, mode) ?: throw IOException("openOutputStream null")
        }

        override fun commit() = Unit

        override fun abort() {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    companion object {
        private const val LEGACY_DOWNLOAD_SUBDIR = "ForPDA"
        const val WORK_TAG = "download"

        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_MIME = "mime"
        const val KEY_DOWNLOAD_FOLDER_URI = "downloadFolderUri"
        const val KEY_NOTIFICATION_ID = "notificationId"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_URI = "outputUri"
        const val KEY_OUTPUT_PATH = "outputPath"
        const val KEY_COMPLETED_AT = "completedAt"

        private const val DEFAULT_BUFFER = 32 * 1024
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

