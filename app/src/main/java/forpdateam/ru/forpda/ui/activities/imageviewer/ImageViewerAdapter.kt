package forpdateam.ru.forpda.ui.activities.imageviewer

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.chrisbanes.photoview.OnPhotoTapListener
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.client.interceptors.ImageDownloadProgress
import forpdateam.ru.forpda.client.interceptors.ImageLoadingInterceptor
import coil.size.Precision
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.makeSnackbarAboveSystemBars
import forpdateam.ru.forpda.databinding.ImgViewPageBinding
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.internal.http2.StreamResetException
import timber.log.Timber
import java.util.Locale

class ImageViewerAdapter : PagerAdapter() {

    private var tapListener: OnPhotoTapListener? = null

    /** Лонг-тап по фото → меню действий (сохранить / открыть в браузере / скопировать ссылку). */
    private var longClickListener: ((position: Int) -> Unit)? = null
    private val items = mutableListOf<String>()

    /** Счётчик авто-ретраев на позицию: сбрасывается при успехе или ручном повторе. */
    private val autoRetryAttempts = mutableMapOf<Int, Int>()

    fun setTapListener(tapListener: OnPhotoTapListener) {
        this.tapListener = tapListener
    }

    fun setLongClickListener(listener: (position: Int) -> Unit) {
        longClickListener = listener
    }

    fun bindItem(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val binding = ImgViewPageBinding.inflate(LayoutInflater.from(container.context), container, false)
        container.addView(binding.root, 0)
        loadImage(container, binding, position)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    private fun loadImage(container: ViewGroup, binding: ImgViewPageBinding, position: Int) {
        val raw = items[position]
        val data = ForPdaCoil.normalizeData(FourPdaImageUrls.resolveViewerUrl(raw))
        logLoadStart(data)
        showConnecting(binding)

        // Подписка на побайтовый прогресс живёт ровно столько же, сколько сам запрос: снимается
        // в любом терминальном колбэке, иначе свайп на соседнюю страницу оставил бы висящий колбэк.
        val progressListener = ImageDownloadProgress.Listener { bytesRead, contentLength ->
            showDownloadProgress(binding, bytesRead, contentLength)
        }
        ImageDownloadProgress.register(data, progressListener)

        val request = ImageRequest.Builder(container.context)
                .data(data)
                .precision(Precision.EXACT)
                .allowHardware(false)
                .target(binding.photoView)
                .listener(object : ImageRequest.Listener {
                    override fun onStart(request: ImageRequest) {
                        showConnecting(binding)
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        ImageDownloadProgress.unregister(data, progressListener)
                        hideProgress(binding)
                        autoRetryAttempts.remove(position)
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        ImageDownloadProgress.unregister(data, progressListener)
                        // Транзиентные сбои (StreamResetException от CDN 4pda, таймауты)
                        // приходят при чтении тела — уже после интерцептора, поэтому его
                        // ретрай 503/504 их не ловит. Сами повторяем загрузку несколько
                        // раз, прежде чем показывать ручной «Повторить».
                        if (shouldAutoRetry(result.throwable)) {
                            val attempt = autoRetryAttempts.getOrElse(position) { 0 } + 1
                            if (attempt <= MAX_AUTO_RETRIES) {
                                autoRetryAttempts[position] = attempt
                                logAutoRetry(data, result.throwable, attempt)
                                showConnecting(binding)
                                binding.photoView.postDelayed(
                                        { loadImage(container, binding, position) },
                                        AUTO_RETRY_DELAY_MS * attempt
                                )
                                return
                            }
                        }
                        hideProgress(binding)
                        autoRetryAttempts.remove(position)
                        logLoadError(data, result.throwable)
                        container.makeSnackbarAboveSystemBars(errorMessageRes(result.throwable), Snackbar.LENGTH_LONG)
                                .setAction(R.string.retry) {
                                    autoRetryAttempts.remove(position)
                                    loadImage(container, binding, position)
                                }
                                .show()
                    }

                    override fun onCancel(request: ImageRequest) {
                        ImageDownloadProgress.unregister(data, progressListener)
                        hideProgress(binding)
                    }
                })
                .build()
        ForPdaCoil.imageLoader.enqueue(request)

        binding.photoView.setOnPhotoTapListener(tapListener)
        binding.photoView.setOnLongClickListener {
            val listener = longClickListener ?: return@setOnLongClickListener false
            listener(position)
            true
        }
    }

    /**
     * Этап «соединяемся»: байты ещё не пошли (DNS/TLS/ожидание ответа CDN) — показываем
     * бесконечную крутилку без цифр. Material запрещает переключаться в indeterminate,
     * пока индикатор на экране, поэтому сначала прячем его.
     */
    private fun showConnecting(binding: ImgViewPageBinding) {
        val bar = binding.progressBar
        if (!bar.isIndeterminate) {
            bar.visibility = View.GONE
            bar.isIndeterminate = true
        }
        bar.visibility = View.VISIBLE
        binding.progressText.text = ""
        binding.progressText.visibility = View.GONE
    }

    /**
     * Этап «качаем»: при известном Content-Length кольцо заполняется и в центре идёт процент,
     * без него — крутилка и накопленный объём, чтобы было видно, что загрузка живая, а не висит.
     */
    private fun showDownloadProgress(binding: ImgViewPageBinding, bytesRead: Long, contentLength: Long) {
        val bar = binding.progressBar
        if (bar.visibility != View.VISIBLE) return
        if (contentLength > 0) {
            val percent = (bytesRead * 100 / contentLength).toInt().coerceIn(0, 100)
            if (bar.isIndeterminate) {
                bar.isIndeterminate = false
            }
            bar.setProgressCompat(percent, true)
            binding.progressText.text = String.format(Locale.getDefault(), "%d%%", percent)
        } else {
            binding.progressText.text = Formatter.formatShortFileSize(binding.root.context, bytesRead)
        }
        binding.progressText.visibility = View.VISIBLE
    }

    private fun hideProgress(binding: ImgViewPageBinding) {
        binding.progressBar.visibility = View.GONE
        binding.progressText.visibility = View.GONE
    }

    /**
     * Стоит ли молча повторить загрузку. Клиентские HTTP-ошибки (401/403 — нужна авторизация,
     * 404 — нет файла) ретраем не лечатся, поэтому для них сразу показываем снэкбар. Всё
     * остальное (StreamResetException, обрывы соединения, таймауты) обычно проходит со 2-3 раза.
     */
    private fun shouldAutoRetry(throwable: Throwable): Boolean {
        val code = findResponseException(throwable)?.code
        return code == null || code !in 400..499
    }

    private fun logAutoRetry(data: String, throwable: Throwable, attempt: Int) {
        if (!BuildConfig.DEBUG) return
        val url = data.toHttpUrlOrNull() ?: return
        if (!ImageLoadingInterceptor.isFourPdaImageRequest(url)) return
        Timber.tag("ImageViewer").d(
            "imageAutoRetry host=%s path=%s type=%s attempt=%d",
            url.host,
            url.encodedPath,
            throwable::class.java.simpleName,
            attempt
        )
    }

    private fun errorMessageRes(throwable: Throwable): Int {
        val responseException = findResponseException(throwable)
        return when (responseException?.code) {
            in 500..599 -> R.string.image_viewer_server_unavailable
            401, 403 -> R.string.image_viewer_auth_required
            else -> R.string.error_occurred
        }
    }

    private fun findResponseException(throwable: Throwable?): OkHttpResponseException? {
        var current = throwable
        while (current != null) {
            if (current is OkHttpResponseException) return current
            current = current.cause
        }
        return null
    }

    private fun logLoadStart(data: String) {
        if (!BuildConfig.DEBUG) return
        val url = data.toHttpUrlOrNull() ?: return
        if (!ImageLoadingInterceptor.isFourPdaImageRequest(url)) return
        Timber.tag("ImageViewer").d(
            "imageRequestStart host=%s path=%s queryPresent=%s",
            url.host,
            url.encodedPath,
            url.encodedQuery != null
        )
    }

    private fun logLoadError(data: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) return
        val url = data.toHttpUrlOrNull() ?: return
        if (!ImageLoadingInterceptor.isFourPdaImageRequest(url)) return
        Timber.tag("ImageViewer").w(
            "imageRequestError host=%s path=%s type=%s code=%s detail=%s",
            url.host,
            url.encodedPath,
            throwable::class.java.simpleName,
            findResponseException(throwable)?.code?.toString().orEmpty(),
            describeFailure(throwable)
        )
    }

    /**
     * Разворачивает цепочку cause в строку с типом+сообщением каждого звена и, для
     * HTTP/2 StreamResetException, его errorCode (REFUSED_STREAM/ENHANCE_YOUR_CALM =
     * лимит/throttle CDN; INTERNAL_ERROR/PROTOCOL_ERROR/CANCEL = сбой origin/прокси).
     * Это и есть недостающий признак, почему именно CDN рвёт отдачу картинки.
     */
    private fun describeFailure(throwable: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 6) {
            if (depth > 0) sb.append(" <- ")
            sb.append(current::class.java.simpleName)
            current.message?.takeIf { it.isNotBlank() }?.let { sb.append('(').append(it.take(140)).append(')') }
            streamResetErrorCode(current)?.let { sb.append("[errorCode=").append(it).append(']') }
            current = current.cause
            depth++
        }
        return sb.toString()
    }

    private fun streamResetErrorCode(throwable: Throwable): String? =
        (throwable as? StreamResetException)?.errorCode?.name

    companion object {
        private const val MAX_AUTO_RETRIES = 2
        private const val AUTO_RETRY_DELAY_MS = 350L
    }
}
