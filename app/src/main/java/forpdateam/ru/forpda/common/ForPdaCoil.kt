package forpdateam.ru.forpda.common

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.client.Client
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException

/**
 * Загрузка изображений с учётом cookies для 4pda (аватары, капча и т.д.) через общий OkHttp [Client].
 */
object ForPdaCoil {

    lateinit var imageLoader: ImageLoader
        private set

    /** Ограничиваем параллельные декоды под нотификации (иначе можно “задушить” устройство). */
    private val notificationSemaphore = Semaphore(permits = 2)

    fun init(application: Application) {
        val okHttp = (App.get().Di().webClient as Client).getHttpClient()
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
                .crossfade(180)
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
     * Синхронная загрузка (вызывать с фонового потока, например в Rx).
     */
    @JvmStatic
    fun loadBitmapSync(context: Context, url: String): Bitmap? {
        // Защита от ANR: синхронную загрузку нельзя вызывать на main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return null
        }
        val data = normalizeData(url)
        return runBlocking {
            val req = ImageRequest.Builder(context.applicationContext)
                    .data(data)
                    .allowHardware(false)
                    .build()
            when (val r = imageLoader.execute(req)) {
                is SuccessResult -> (r.drawable as? BitmapDrawable)?.bitmap
                else -> null
            }
        }
    }

    /**
     * Асинхронная загрузка bitmap для нотификаций.
     * - НЕ блокирует main thread
     * - использует Coil cache (memory/disk)
     * - ограничивает параллельность
     */
    @JvmStatic
    fun loadBitmapForNotificationSingle(context: Context, url: String, width: Int, height: Int): Single<Bitmap> {
        val appCtx = context.applicationContext
        return rxSingle(Dispatchers.IO) {
            notificationSemaphore.acquire()
            try {
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
            } finally {
                notificationSemaphore.release()
            }
        }
    }
}
