package forpdateam.ru.forpda.client

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.IOException
import okio.source
import java.io.InputStream

/**
 * Утилита для создания RequestBody из InputStream.
 * 
 * Улучшения в Kotlin-версии:
 * - Функция-расширение вместо статического метода
 * - use() для автоматического закрытия ресурсов
 */
object RequestBodyUtil {
    
    @JvmStatic
    fun create(mediaType: MediaType?, inputStream: InputStream, contentLength: Long? = null): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? = mediaType

            override fun contentLength(): Long {
                return contentLength ?: -1L
            }

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                inputStream.source().use { source ->
                    sink.writeAll(source)
                }
            }
        }
    }
}

/**
 * Функция-расширение для удобного создания RequestBody.
 */
fun InputStream.toRequestBody(mediaType: MediaType? = null, contentLength: Long? = null): RequestBody {
    return RequestBodyUtil.create(mediaType, this, contentLength)
}
