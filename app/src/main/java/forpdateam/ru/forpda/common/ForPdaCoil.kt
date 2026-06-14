package forpdateam.ru.forpda.common

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.webkit.MimeTypeMap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import forpdateam.ru.forpda.client.Client
import forpdateam.ru.forpda.model.data.remote.IWebClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.buffer
import java.io.IOException

/**
 * Загрузка изображений с учётом cookies для 4pda (аватары, капча и т.д.) через общий OkHttp [Client].
 */
object ForPdaCoil {

    data class CachedImageBytes(
        val bytes: ByteArray,
        val mimeType: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CachedImageBytes) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    lateinit var imageLoader: ImageLoader
        private set

    /** Ограничиваем параллельные декоды под нотификации (иначе можно “задушить” устройство). */
    private val notificationSemaphore = Semaphore(permits = 2)

    /**
     * Удаляет WordPress-суффикс размера из URL изображения.
     * Пример: https://4pda.to/wp-content/uploads/2024/01/image-150x84.jpg
     *       → https://4pda.to/wp-content/uploads/2024/01/image.jpg
     */
    @JvmStatic
    fun stripWordPressSizeSuffix(url: String): String = FourPdaImageUrls.stripWordPressSizeSuffix(url)

    fun init(application: Application, webClient: IWebClient) {
        val okHttp = (webClient as Client).getHttpClient()
        imageLoader = ImageLoader.Builder(application)
                .okHttpClient(okHttp)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .respectCacheHeaders(true)
                .memoryCache {
                    MemoryCache.Builder(application)
                        // 25% от доступной памяти процесса под картинки
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(application.cacheDir.resolve("image_cache"))
                        // 256MB: для аватаров/превью и быстрого скролла
                        .maxSizeBytes(256L * 1024L * 1024L)
                        .build()
                }
                .build()
    }

    @JvmStatic
    fun normalizeData(url: String): String {
        val t = url.trim()
        if (t.isEmpty()) return t
        if (t.startsWith("assets://")) {
            return "file:///android_asset/" + t.removePrefix("assets://")
        }
        if (t.startsWith("//")) {
            return "https:$t"
        }
        return t
    }

    @JvmStatic
    fun loadInto(imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) return
        val req = ImageRequest.Builder(imageView.context.applicationContext)
                .data(normalizeData(url))
                .precision(Precision.INEXACT)
                .crossfade(false)
                .target(imageView)
                .build()
        imageLoader.enqueue(req)
    }

    @JvmStatic
    fun loadIntoWithProgress(imageView: ImageView, url: String?, progress: ProgressBar?) {
        if (url.isNullOrBlank()) {
            progress?.visibility = View.GONE
            return
        }
        progress?.visibility = View.VISIBLE
        val req = ImageRequest.Builder(imageView.context.applicationContext)
                .data(normalizeData(url))
                .precision(Precision.INEXACT)
                .crossfade(false)
                .target(imageView)
                .listener(object : ImageRequest.Listener {
                    override fun onStart(request: ImageRequest) {
                        progress?.visibility = View.VISIBLE
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        progress?.visibility = View.GONE
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        progress?.visibility = View.GONE
                    }

                    override fun onCancel(request: ImageRequest) {
                        progress?.visibility = View.GONE
                    }
                })
                .build()
        imageLoader.enqueue(req)
    }

    /**
     * Загрузка изображения в полном качестве (без даунскейла Coil).
     * Удаляет WordPress-суффикс размера из URL для получения оригинала.
     */
    @JvmStatic
    fun loadIntoFullQuality(imageView: ImageView, url: String?, progress: ProgressBar? = null) {
        if (url.isNullOrBlank()) {
            progress?.visibility = View.GONE
            return
        }
        progress?.visibility = View.VISIBLE
        val fullUrl = stripWordPressSizeSuffix(url)
        val req = ImageRequest.Builder(imageView.context.applicationContext)
                .data(normalizeData(fullUrl))
                .precision(Precision.EXACT)
                .crossfade(false)
                .target(imageView)
                .listener(object : ImageRequest.Listener {
                    override fun onStart(request: ImageRequest) {
                        progress?.visibility = View.VISIBLE
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        progress?.visibility = View.GONE
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        progress?.visibility = View.GONE
                    }

                    override fun onCancel(request: ImageRequest) {
                        progress?.visibility = View.GONE
                    }
                })
                .build()
        imageLoader.enqueue(req)
    }

    /**
     * MIME type for WebView avatar responses when serving raw cached bytes.
     * Falls back to JPEG for extension-less 4pda avatar URLs.
     */
    @JvmStatic
    fun mimeTypeFromUrl(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".gif") -> "image/gif"
            path.endsWith(".bmp") -> "image/bmp"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                path.substringAfterLast('.', "")
            ) ?: "image/jpeg"
        }
    }

    /**
     * Reads encoded image bytes from Coil disk cache without decode/re-encode.
     * Call from a background thread only.
     */
    @JvmStatic
    fun loadCachedImageBytesSync(context: Context, url: String): CachedImageBytes? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        if (!::imageLoader.isInitialized) return null
        val cacheKey = normalizeData(url)
        val diskCache = imageLoader.diskCache ?: return null
        return runBlocking(Dispatchers.IO) {
            diskCache.openSnapshot(cacheKey)?.use { snapshot ->
                diskCache.fileSystem.source(snapshot.data).buffer().use { buffered ->
                    val bytes = buffered.readByteArray()
                    if (bytes.isEmpty()) return@runBlocking null
                    CachedImageBytes(bytes, mimeTypeFromUrl(cacheKey))
                }
            }
        }
    }

    internal fun bindImageLoaderForTest(loader: ImageLoader) {
        imageLoader = loader
    }

    /**
     * Синхронная загрузка (вызывать с фонового потока, не с main).
     */
    @JvmStatic
    fun loadBitmapSync(context: Context, url: String, allowNetwork: Boolean = true): Bitmap? {
        // Защита от ANR: синхронную загрузку нельзя вызывать на main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return null
        }
        val data = normalizeData(url)
        return runBlocking(Dispatchers.IO) {
            val req = ImageRequest.Builder(context.applicationContext)
                    .data(data)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(if (allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .build()
            when (val r = imageLoader.execute(req)) {
                is SuccessResult -> (r.drawable as? BitmapDrawable)?.bitmap
                else -> null
            }
        }
    }

    /**
     * Асинхронная загрузка bitmap для нотификаций.
     * - Не вызывать с main thread без корутины (внутри — [Dispatchers.IO])
     * - использует Coil cache (memory/disk)
     * - ограничивает параллельность
     */
    @JvmStatic
    suspend fun loadBitmapForNotification(context: Context, url: String, width: Int, height: Int): Bitmap {
        val appCtx = context.applicationContext
        return withContext(Dispatchers.IO) {
            notificationSemaphore.withPermit {
                suspend fun loadOne(dataUrl: String): Bitmap? {
                    val data = normalizeData(dataUrl)
                    val req = ImageRequest.Builder(appCtx)
                            .data(data)
                            .size(width, height)
                            .allowHardware(false)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    return when (val r = imageLoader.execute(req)) {
                        is SuccessResult -> (r.drawable as? BitmapDrawable)?.bitmap
                        else -> null
                    }
                }

                loadOne(url)
                        ?: loadOne("assets://av.png")
                        ?: throw IOException("Failed to load notification avatar bitmap")
            }
        }
    }
}
