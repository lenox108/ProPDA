package forpdateam.ru.forpda.model.data.remote.api

import java.util.LinkedHashMap

/**
 * Created by radiationx on 02.05.17.
 * Converted to Kotlin.
 */
class NetworkRequest private constructor(builder: Builder) {
    val url: String = builder.url
    val headers: LinkedHashMap<String, String>? = builder.headers
    val formHeaders: LinkedHashMap<String, String>? = builder.formHeaders
    val rawBody: String? = builder.rawBody
    val rawBodyContentType: String = builder.rawBodyContentType
    val encodedFormHeaders: Set<String>? = builder.encodedFormHeaders
    val isMultipartForm: Boolean = builder.isMultipartForm
    val file: RequestFile? = builder.file
    val method: Boolean = builder.method
    val isWithoutBody: Boolean = builder.withoutBody
    val skipCounterUpdate: Boolean = builder.skipCounterUpdate

    class Builder {
        internal var url: String = ""
        internal var headers: LinkedHashMap<String, String>? = null
        internal var formHeaders: LinkedHashMap<String, String>? = null
        internal var rawBody: String? = null
        internal var rawBodyContentType: String = "application/x-www-form-urlencoded"
        internal var encodedFormHeaders: MutableSet<String>? = null
        internal var isMultipartForm: Boolean = false
        internal var file: RequestFile? = null
        internal var method: Boolean = true
        internal var withoutBody: Boolean = false
        internal var skipCounterUpdate: Boolean = false

        fun url(url: String) = apply { this.url = url }

        fun addHeaders(headers: LinkedHashMap<String, String>) = apply {
            if (this.headers == null) {
                this.headers = LinkedHashMap()
            }
            this.headers?.putAll(headers)
        }

        fun addHeader(name: String, value: String) = apply {
            if (headers == null) {
                headers = LinkedHashMap()
            }
            headers?.put(name, value)
        }

        fun copyFrom(request: NetworkRequest) = apply {
            url = request.url
            headers = request.headers?.let { LinkedHashMap(it) }
            formHeaders = request.formHeaders?.let { LinkedHashMap(it) }
            rawBody = request.rawBody
            rawBodyContentType = request.rawBodyContentType
            encodedFormHeaders = request.encodedFormHeaders?.toMutableSet()
            isMultipartForm = request.isMultipartForm
            file = request.file
            method = request.method
            withoutBody = request.isWithoutBody
            skipCounterUpdate = request.skipCounterUpdate
        }

        fun xhrHeader() = apply {
            addHeader("X-Requested-With", "XMLHttpRequest")
        }

        @JvmOverloads
        fun formHeaders(formHeaders: Map<String, String>, encoded: Boolean = false) = apply {
            if (this.formHeaders == null) {
                this.formHeaders = LinkedHashMap()
            }
            this.formHeaders?.putAll(formHeaders)
            if (encoded) {
                if (this.encodedFormHeaders == null) {
                    encodedFormHeaders = HashSet()
                }
                this.formHeaders?.keys?.let { encodedFormHeaders?.addAll(it) }
            }
            method = false
        }

        @JvmOverloads
        fun formHeader(name: String, value: String, encoded: Boolean = false) = apply {
            if (formHeaders == null) {
                formHeaders = LinkedHashMap()
            }
            formHeaders?.put(name, value)
            if (encoded) {
                if (encodedFormHeaders == null) {
                    encodedFormHeaders = HashSet()
                }
                encodedFormHeaders?.add(name)
            }
            method = false
        }

        fun rawBody(body: String, contentType: String = "application/x-www-form-urlencoded") = apply {
            rawBody = body
            rawBodyContentType = contentType
            method = false
        }

        fun multipart() = apply { isMultipartForm = true }

        fun withoutBody() = apply { withoutBody = true }

        fun skipCounterUpdate() = apply { skipCounterUpdate = true }

        fun file(file: RequestFile) = apply {
            this.file = file
            isMultipartForm = true
            method = false
        }

        fun build(): NetworkRequest = NetworkRequest(this)
    }
}
