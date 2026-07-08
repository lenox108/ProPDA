package forpdateam.ru.forpda.common
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import io.appmetrica.analytics.AppMetrica
import java.io.File
import java.io.FileInputStream
import forpdateam.ru.forpda.model.data.remote.api.RequestFile

/**
 * Created by radiationx on 13.01.17.
 */
object FilePickHelper {
    private const val LOG_TAG = "FilePickHelper"

    @JvmStatic
    fun pickFile(onlyImages: Boolean): Intent {
        // База — выбор любого документа (приложение «Файлы»/Документы).
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = if (onlyImages) "image/*" else "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooser = Intent.createChooser(getContent, "Выберите файл")

        // На Pixel галерея (Google Photos) объявляет обработчик GET_CONTENT только
        // для image/*-video/*, поэтому при type="*/*" система выкидывает её из чузера —
        // остаются только «Файлы». Явно добавляем системный Photo Picker и галерею,
        // чтобы выбор «из галереи» был доступен и для постов (onlyImages=false).
        val galleryIntents = buildGalleryIntents()
        if (galleryIntents.isNotEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, galleryIntents.toTypedArray())
        }
        return chooser
    }

    /**
     * Понятный выбор источника вложения вместо системного чузера с невнятными пунктами
     * («Инструмент выбора медиа», «Выполнить действие» на некоторых прошивках — жалоба
     * пользователя). Показывает диалог «Прикрепить» с тремя ясными вариантами и запускает
     * выбранный intent через [launch] (тот же launcher, что и раньше — результат
     * обрабатывается [onActivityResult] единообразно для всех трёх источников).
     */
    @JvmStatic
    fun showAttachChooser(context: Context, launch: (Intent) -> Unit) {
        // «Медиа (фото и видео)» уже включает фото, поэтому отдельный пункт «Фото» избыточен
        // (жалоба пользователя) — оставляем только «Медиа» и «Файлы».
        val items = arrayOf("Медиа (фото и видео)", "Файлы")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Прикрепить")
                .setItems(items) { _, which ->
                    launch(
                            when (which) {
                                0 -> mediaIntent()
                                else -> filesIntent()
                            }
                    )
                }
                .show()
    }

    /** Фото и видео — Photo Picker (по умолчанию показывает и то, и другое) или GET_CONTENT с медиа-фильтром. */
    @JvmStatic
    fun mediaIntent(): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
                }
            } else {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

    /** Любые файлы — системный «Файлы»/Документы напрямую (ACTION_OPEN_DOCUMENT). */
    @JvmStatic
    fun filesIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    }

    /**
     * Интенты для выбора медиа прямо из галереи. Результат (content://-Uri в
     * [Intent.getData] или [Intent.getClipData]) полностью совместим с [onActivityResult].
     */
    private fun buildGalleryIntents(): List<Intent> {
        val intents = mutableListOf<Intent>()
        // Системный Photo Picker (Android 13+): без разрешений, нативная галерея Pixel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intents.add(
                Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                    putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
                }
            )
        }
        // Классическая галерея как fallback для более старых версий / других прошивок.
        intents.add(
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
        return intents
    }

    @JvmStatic
    fun onActivityResult(context: Context, data: Intent): List<RequestFile> {
        val files = mutableListOf<RequestFile>()
        if (BuildConfig.DEBUG) Timber.d(LOG_TAG, "onActivityResult hasData=${data.data != null} clipCount=${data.clipData?.itemCount ?: 0}")
        if (data.data == null) {
            data.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    createFile(context, clipData.getItemAt(i).uri)?.let { files.add(it) }
                }
            }
        } else {
            createFile(context, data.data!!)?.let { files.add(it) }
        }
        return files
    }

    private fun createFile(context: Context, uri: Uri): RequestFile? {
        if (BuildConfig.DEBUG) Timber.d(LOG_TAG, "createFile scheme=${uri.scheme}")
        return try {
            val name = getFileName(context, uri)
            val extension = MimeTypeUtil.getExtension(name)
            var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mimeType == null) mimeType = context.contentResolver.getType(uri)
            if (mimeType == null) mimeType = MimeTypeUtil.getType(extension)
            val fileSize = getFileSize(context, uri)
            val streamProvider = {
                when (uri.scheme) {
                    "content" -> context.contentResolver.openInputStream(uri)
                    "file" -> FileInputStream(File(uri.path.orEmpty()))
                    else -> null
                } ?: error("Unable to open selected file: $uri")
            }
            RequestFile(name, mimeType ?: "", streamProvider(), fileSize, streamProvider)
        } catch (e: Exception) {
            AppMetrica.reportError(e.message.orEmpty(), e)
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        if (BuildConfig.DEBUG) Timber.d(LOG_TAG, "getFileName scheme=${uri.scheme} mime=${context.contentResolver.getType(uri)}")
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) result = c.getString(index)
                }
            }
        }
        if (result == null) {
            if (BuildConfig.DEBUG) Timber.d(LOG_TAG, "fallback file name from uri path")
            val path = uri.path ?: ""
            val cut = path.lastIndexOf('/')
            result = if (cut != -1) path.substring(cut + 1) else path
        }
        return result ?: ""
    }

    private fun getFileSize(context: Context, uri: Uri): Long? {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).length() }?.takeIf { it >= 0L }
        }
        if (uri.scheme != "content") {
            return null
        }
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        } ?: runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it >= 0L }
            }
        }.getOrNull()
    }
}
