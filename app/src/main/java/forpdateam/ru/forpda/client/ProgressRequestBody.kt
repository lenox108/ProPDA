package forpdateam.ru.forpda.client

import forpdateam.ru.forpda.model.data.remote.IWebClient
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.IOException
import okio.Sink
import okio.buffer
import timber.log.Timber

/**
 * RequestBody с отслеживанием прогресса загрузки.
 * 
 * Улучшения в Kotlin-версии:
 * - Первичный конструктор с val свойствами
 * - Упрощенная работа с Okio (методы-расширения)
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val listener: IWebClient.ProgressListener
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long {
        return try {
            delegate.contentLength()
        } catch (e: IOException) {
            Timber.e(e, "ProgressRequestBody contentLength error")
            -1
        }
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = countingSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    /**
     * Sink для подсчета записанных байт.
     */
    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten: Long = 0

        @Throws(IOException::class)
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            val contentLength = contentLength()
            if (contentLength > 0) {
                listener.onProgress((100L * bytesWritten / contentLength).toInt())
            }
        }
    }
}
