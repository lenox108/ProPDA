package forpdateam.ru.forpda.common.animation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import forpdateam.ru.forpda.model.data.remote.api.attachments.AttachmentFileConverter
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Приводит вложение к формату, который форум действительно принимает.
 *
 * Что и почему (проверено живьём на QMS):
 * - **animated WebP** форум отклоняет на аплоаде (ответ в 1 байт) → перекодируем в gif;
 * - **APNG** форум принимает, но пережимает в один кадр (2261 B → 217 B) → тоже в gif;
 * - **статический WebP** форума в белом списке расширений нет → отдаём png (с альфой)
 *   или jpeg (без альфы).
 *
 * Всё остальное (gif, png, jpeg, не-картинки) не трогаем.
 */
class AnimatedAttachmentConverter(
    private val context: Context,
) : AttachmentFileConverter {

    override fun convert(file: RequestFile): RequestFile? {
        // Читаем через отдельный поток, чтобы не тронуть основной поток загрузки.
        if (!file.canOpenStreamAgain()) {
            Timber.d("attach convert: %s — поток не переоткрывается, пропускаем", file.fileName)
            return null
        }
        val size = file.fileSize
        if (size != null && size > MAX_INPUT_BYTES) return null

        val bytes = runCatching {
            file.reopenStream()?.use { stream -> stream.readBytesCapped(MAX_INPUT_BYTES) }
        }.onFailure { Timber.w(it, "attach convert: не прочитали %s", file.fileName) }
            .getOrNull() ?: return null

        val format = AnimatedImageProbe.detect(bytes)
        Timber.d("attach convert: %s → формат %s (%d байт)", file.fileName, format, bytes.size)
        return when (format) {
            AnimatedImageProbe.Format.ANIMATED_WEBP -> convertAnimated(file, bytes) { out ->
                WebPAnimParser.parse(bytes)?.let { encodeWebP(it, out) } ?: 0
            }

            AnimatedImageProbe.Format.ANIMATED_PNG -> convertAnimated(file, bytes) { out ->
                ApngParser.parse(bytes)?.let { encodeApng(it, out) } ?: 0
            }

            AnimatedImageProbe.Format.STATIC_WEBP -> convertStaticWebP(file, bytes)

            AnimatedImageProbe.Format.OTHER -> null
        }
    }

    // region анимация → gif

    private fun convertAnimated(
        source: RequestFile,
        bytes: ByteArray,
        encode: (OutputStream) -> Int,
    ): RequestFile? {
        val target = newCacheFile(source.fileName, "gif") ?: return null
        val frames = runCatching {
            BufferedOutputStream(FileOutputStream(target)).use { out -> encode(out) }
        }.onFailure { Timber.w(it, "attach convert to gif failed") }.getOrDefault(0)

        if (frames <= 0 || !target.isFile || target.length() <= 0L || target.length() > MAX_OUTPUT_BYTES) {
            if (frames > 0 && target.length() > MAX_OUTPUT_BYTES) {
                Timber.w("attach convert: gif too big (%d bytes), keeping original", target.length())
            }
            target.delete()
            return null
        }
        Timber.d("attach convert: %d bytes → gif %d bytes, %d frames", bytes.size, target.length(), frames)
        return requestFileFor(source, target, "image/gif")
    }

    private fun encodeWebP(animation: WebPAnimParser.Animation, out: OutputStream): Int {
        val surface = Surface(animation.width, animation.height) ?: return 0
        try {
            val frames = animation.frames.asSequence().mapNotNull { frame ->
                val bitmap = decode(frame.image) ?: return@mapNotNull null
                if (!frame.blend) surface.clear(frame.x, frame.y, frame.width, frame.height)
                surface.draw(bitmap, frame.x, frame.y)
                bitmap.recycle()
                val encoded = GifEncoder.Frame(surface.snapshot(), frame.durationMs)
                if (frame.disposeToBackground) surface.clear(frame.x, frame.y, frame.width, frame.height)
                encoded
            }
            return GifEncoder.encode(out, surface.outWidth, surface.outHeight, frames)
        } finally {
            surface.release()
        }
    }

    private fun encodeApng(animation: ApngParser.Animation, out: OutputStream): Int {
        val surface = Surface(animation.width, animation.height) ?: return 0
        try {
            val frames = animation.frames.asSequence().mapNotNull { frame ->
                val bitmap = decode(frame.image) ?: return@mapNotNull null
                if (frame.disposeOp == ApngParser.DISPOSE_PREVIOUS) surface.backup()
                if (frame.blendOp == ApngParser.BLEND_SOURCE) {
                    surface.clear(frame.x, frame.y, frame.width, frame.height)
                }
                surface.draw(bitmap, frame.x, frame.y)
                bitmap.recycle()
                val encoded = GifEncoder.Frame(surface.snapshot(), frame.delayMs)
                when (frame.disposeOp) {
                    ApngParser.DISPOSE_BACKGROUND -> surface.clear(frame.x, frame.y, frame.width, frame.height)
                    ApngParser.DISPOSE_PREVIOUS -> surface.restoreBackup()
                }
                encoded
            }
            return GifEncoder.encode(out, surface.outWidth, surface.outHeight, frames)
        } finally {
            surface.release()
        }
    }

    private fun decode(image: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(image, 0, image.size) }.getOrNull()

    /**
     * Холст для склейки кадров + (при необходимости) уменьшенная копия для кодирования:
     * gif из анимации 2000×2000 весил бы десятки мегабайт, форум такое не примет.
     */
    private class Surface private constructor(
        private val canvasBitmap: Bitmap,
        private val scaledBitmap: Bitmap?,
        val outWidth: Int,
        val outHeight: Int,
    ) {
        private val canvas = Canvas(canvasBitmap)
        private val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        private val scalePaint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val pixels = IntArray(outWidth * outHeight)
        private var backup: Bitmap? = null
        private var scaledCanvas: Canvas? = null

        companion object {
            operator fun invoke(width: Int, height: Int): Surface? {
                if (width <= 0 || height <= 0) return null
                val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(width, height))
                val outWidth = maxOf(1, (width * scale).toInt())
                val outHeight = maxOf(1, (height * scale).toInt())
                val canvasBitmap = runCatching {
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                }.getOrNull() ?: return null
                val scaledBitmap = if (scale < 1f) {
                    runCatching {
                        Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                    }.getOrNull() ?: run {
                        canvasBitmap.recycle()
                        return null
                    }
                } else {
                    null
                }
                return Surface(canvasBitmap, scaledBitmap, outWidth, outHeight)
            }
        }

        fun clear(x: Int, y: Int, width: Int, height: Int) {
            canvas.drawRect(
                x.toFloat(),
                y.toFloat(),
                (x + width).toFloat(),
                (y + height).toFloat(),
                clearPaint,
            )
        }

        fun draw(bitmap: Bitmap, x: Int, y: Int) {
            canvas.drawBitmap(bitmap, x.toFloat(), y.toFloat(), null)
        }

        fun backup() {
            backup?.recycle()
            backup = runCatching { canvasBitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        }

        fun restoreBackup() {
            val saved = backup ?: return
            canvas.drawRect(0f, 0f, canvasBitmap.width.toFloat(), canvasBitmap.height.toFloat(), clearPaint)
            canvas.drawBitmap(saved, 0f, 0f, null)
        }

        /** Пиксели текущего холста; массив переиспользуется — кодировщик забирает кадр сразу. */
        fun snapshot(): IntArray {
            val source = scaledBitmap?.also { scaled ->
                // Без очистки прозрачные места нового кадра показали бы предыдущий.
                scaled.eraseColor(0)
                (scaledCanvas ?: Canvas(scaled).also { scaledCanvas = it }).drawBitmap(
                    canvasBitmap,
                    Rect(0, 0, canvasBitmap.width, canvasBitmap.height),
                    Rect(0, 0, outWidth, outHeight),
                    scalePaint,
                )
            } ?: canvasBitmap
            source.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
            return pixels
        }

        fun release() {
            backup?.recycle()
            scaledBitmap?.recycle()
            canvasBitmap.recycle()
        }
    }

    // endregion

    // region статический webp → png/jpeg

    private fun convertStaticWebP(source: RequestFile, bytes: ByteArray): RequestFile? {
        val bitmap = decode(bytes) ?: return null
        val lossless = bitmap.hasAlpha()
        val extension = if (lossless) "png" else "jpg"
        val target = newCacheFile(source.fileName, extension)
        if (target == null) {
            bitmap.recycle()
            return null
        }
        val ok = runCatching {
            BufferedOutputStream(FileOutputStream(target)).use { out ->
                if (lossless) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }
        }.onFailure { Timber.w(it, "attach convert webp→$extension failed") }.getOrDefault(false)
        bitmap.recycle()

        if (!ok || target.length() <= 0L || target.length() > MAX_OUTPUT_BYTES) {
            target.delete()
            return null
        }
        return requestFileFor(source, target, if (lossless) "image/png" else "image/jpeg")
    }

    // endregion

    private fun requestFileFor(source: RequestFile, target: File, mimeType: String): RequestFile =
        RequestFile(
            fileName = target.name,
            mimeType = mimeType,
            fileStream = FileInputStream(target),
            fileSize = target.length(),
            streamProvider = { FileInputStream(target) },
            requestName = source.requestName,
            sourceUri = source.sourceUri,
        )

    private fun newCacheFile(sourceName: String, extension: String): File? {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        pruneOldFiles(dir)
        val base = sourceName.substringBeforeLast('.', sourceName)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
            .ifBlank { "attachment" }
        return runCatching { File.createTempFile("${base}_", ".$extension", dir) }.getOrNull()
    }

    private fun pruneOldFiles(dir: File) {
        val deadline = System.currentTimeMillis() - CACHE_TTL_MS
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < deadline) file.delete()
        }
    }

    private fun java.io.InputStream.readBytesCapped(limit: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > limit) return null
        }
        return buffer.toByteArray()
    }

    companion object {
        /** Больше этого в память не читаем — конвертация таких вложений всё равно бессмысленна. */
        private const val MAX_INPUT_BYTES = 24L * 1024 * 1024

        /** Gif раздувается относительно webp; если вышло больше — смысла отправлять нет. */
        private const val MAX_OUTPUT_BYTES = 32L * 1024 * 1024

        /** Длинная сторона результата. */
        private const val MAX_SIDE = 1200

        private const val CACHE_DIR = "attach_converted"
        private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000
    }
}
