package forpdateam.ru.forpda.client.interceptors

import android.os.Handler
import android.os.Looper
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

import java.util.concurrent.ConcurrentHashMap

/**
 * Побайтовый прогресс скачивания картинки. Coil сам о прогрессе ничего не сообщает — он знает только
 * «начал / получил / упал», поэтому индикатор в просмотрщике мог быть лишь бесконечной крутилкой.
 * Тело ответа читается один раз (в диск-кэш Coil), так что счётчик байт на нём и есть реальный прогресс.
 *
 * Подписчик регистрируется ПО URL (тому же, что уходит в [coil.request.ImageRequest.data]) до старта
 * загрузки и снимается в терминальном колбэке; если на URL никто не подписан, тело не оборачивается
 * вовсе — для аватаров/превью накладных расходов нет.
 */
object ImageDownloadProgress {

    fun interface Listener {
        /** [contentLength] < 0 — сервер не прислал Content-Length, процент посчитать нельзя. */
        fun onProgress(bytesRead: Long, contentLength: Long)
    }

    private val listeners = ConcurrentHashMap<String, Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun register(url: String, listener: Listener) {
        listeners[url] = listener
    }

    fun unregister(url: String, listener: Listener) {
        listeners.remove(url, listener)
    }

    internal fun isTracked(url: String): Boolean = listeners.containsKey(url)

    /** Вызывается с потока чтения тела; колбэк доставляем на main, там же живёт индикатор. */
    internal fun report(url: String, bytesRead: Long, contentLength: Long) {
        val listener = listeners[url] ?: return
        mainHandler.post {
            // Между post и его выполнением подписчик мог смениться (свайп на соседнюю картинку).
            if (listeners[url] === listener) {
                listener.onProgress(bytesRead, contentLength)
            }
        }
    }
}

/**
 * Оборачивает тело ответа счётчиком байт для тех URL, на которые кто-то подписан через
 * [ImageDownloadProgress]. Ставится только на OkHttp-клиент Coil (см. `ForPdaCoil.init`).
 */
class ImageProgressInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val key = request.url.toString()
        if (!ImageDownloadProgress.isTracked(key)) return response
        val body = response.body ?: return response
        return response.newBuilder()
            .body(ProgressResponseBody(key, body))
            .build()
    }
}

private class ProgressResponseBody(
    private val url: String,
    // Не `delegate`: у okio.ForwardingSource ниже есть своё свойство с таким именем, оно бы перекрыло это.
    private val original: ResponseBody,
) : ResponseBody() {

    private val countingSource: BufferedSource by lazy {
        object : ForwardingSource(original.source()) {
            private var totalBytesRead = 0L
            private var lastReported = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val read = super.read(sink, byteCount)
                if (read == -1L) {
                    report(endOfStream = true)
                } else {
                    totalBytesRead += read
                    if (totalBytesRead - lastReported >= REPORT_THRESHOLD_BYTES) {
                        report(endOfStream = false)
                    }
                }
                return read
            }

            private fun report(endOfStream: Boolean) {
                lastReported = totalBytesRead
                val length = original.contentLength()
                // Без Content-Length процент неизвестен, но в конце потока размер уже точно
                // равен прочитанному — это даёт честные 100% на последнем событии.
                val reportedLength = when {
                    length > 0 -> length
                    endOfStream -> totalBytesRead
                    else -> -1L
                }
                ImageDownloadProgress.report(url, totalBytesRead, reportedLength)
            }
        }.buffer()
    }

    override fun contentType(): MediaType? = original.contentType()

    override fun contentLength(): Long = original.contentLength()

    override fun source(): BufferedSource = countingSource

    private companion object {
        /** Не чаще ~раза на 8 КБ: иначе на быстрой сети получаем сотни post() в main-очередь. */
        const val REPORT_THRESHOLD_BYTES = 8 * 1024L
    }
}
