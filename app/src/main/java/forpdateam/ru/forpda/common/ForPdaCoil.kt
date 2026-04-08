package forpdateam.ru.forpda.common

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.client.Client
import kotlinx.coroutines.runBlocking

/**
 * Загрузка изображений с учётом cookies для 4pda (аватары, капча и т.д.) через общий OkHttp [Client].
 */
object ForPdaCoil {

    lateinit var imageLoader: ImageLoader
        private set

    fun init(application: Application) {
        val okHttp = (App.get().Di().webClient as Client).getHttpClient()
        imageLoader = ImageLoader.Builder(application)
                .okHttpClient(okHttp)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .respectCacheHeaders(true)
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
}
