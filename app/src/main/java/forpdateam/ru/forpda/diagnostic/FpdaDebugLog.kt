package forpdateam.ru.forpda.diagnostic

import android.net.Uri
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

/**
 * DEBUG-only structured single-line logs with stable logcat tags.
 *
 * Privacy: never log cookies, tokens, passwords, full HTML, or private message bodies.
 */
object FpdaDebugLog {

    const val TAG_THEME_OPEN = "FPDA_THEME_OPEN"
    /** Backward-compatible alias for [TAG_THEME_OPEN] in logcat filters. */
    const val TAG_TOPIC_OPEN = TAG_THEME_OPEN
    const val TAG_THEME_LOAD = "FPDA_THEME_LOAD"
    const val TAG_THEME_RENDER = "FPDA_THEME_RENDER"
    /** Backward-compatible alias for [TAG_THEME_RENDER] in logcat filters. */
    const val TAG_WEBVIEW_RENDER = TAG_THEME_RENDER
    const val TAG_TOPIC_SCROLL = "FPDA_TOPIC_SCROLL"
    const val TAG_TOPIC_READSTATE = "FPDA_TOPIC_READSTATE"
    const val TAG_ARTICLE_OPEN = "FPDA_ARTICLE_OPEN"
    const val TAG_ARTICLE_PARSE = "FPDA_ARTICLE_PARSE"
    const val TAG_ARTICLE_CACHE = "FPDA_ARTICLE_CACHE"
    const val TAG_COMMENTS_SECTION = "FPDA_COMMENTS_SECTION"
    const val TAG_STATE_RACE = "FPDA_STATE_RACE"
    const val TAG_NAV_BACKSTACK = "FPDA_NAV_BACKSTACK"
    const val TAG_TOPIC_SWITCH = "FPDA_TOPIC_SWITCH"
    const val TAG_QMS_WEBVIEW = "FPDA_QMS_WEBVIEW"
    const val TAG_QMS_CHAT = "FPDA_QMS_CHAT"
    const val TAG_QMS_OPEN = "FPDA_QMS_OPEN"
    const val TAG_QMS_NETWORK = "FPDA_QMS_NETWORK"
    const val TAG_QMS_PARSE = "FPDA_QMS_PARSE"
    const val TAG_QMS_STATE = "FPDA_QMS_STATE"
    const val TAG_QMS_CACHE = "FPDA_QMS_CACHE"
    const val TAG_COMMENT_ACTION = "FPDA_COMMENT_ACTION"
    const val TAG_ARTICLE_DEFERRED = "FPDA_ARTICLE_DEFERRED"
    const val TAG_ARTICLE_POLL = "FPDA_ARTICLE_POLL"
    const val TAG_ARTICLE_RENDER = "FPDA_ARTICLE_RENDER"
    /** Backward-compatible alias for article WebView load phases. */
    const val TAG_ARTICLE_WEBVIEW = TAG_ARTICLE_RENDER
    const val TAG_SMART_BUTTON = "FPDA_SMART_BUTTON"
    const val TAG_WEBVIEW_BLANK = "FPDA_WEBVIEW_BLANK"
    /** Shared cross-pipeline WebView render lifecycle (Theme/Search/QMS/News) diagnostics. */
    const val TAG_WEBVIEW_SESSION = "FPDA_WEBVIEW_SESSION"
    const val TAG_FAVORITES_UNREAD = "FPDA_FAVORITES_UNREAD"
    const val TAG_THEME_POST_READ_STATE = "FPDA_THEME_POST_READ_STATE"
    const val TAG_TOPIC_HIGHLIGHT = "PPDA_TOPIC_HIGHLIGHT"

    private val sensitiveQueryKeys = setOf(
            "auth_key",
            "session_id",
            "sid",
            "pass",
            "password",
            "token",
            "key",
            "cookie",
            "member_id"
    )

    fun newTraceId(): String = UUID.randomUUID().toString().replace("-", "").take(8)

    fun log(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        if (!BuildConfig.DEBUG) return
        val parts = buildList {
            add("event=$event")
            fields.forEach { (key, value) ->
                if (value != null) add("$key=$value")
            }
        }
        Timber.tag(tag).i(parts.joinToString(separator = " "))
    }

    fun warn(tag: String, event: String, fields: Map<String, Any?> = emptyMap()) {
        if (!BuildConfig.DEBUG) return
        val parts = buildList {
            add("event=$event")
            fields.forEach { (key, value) ->
                if (value != null) add("$key=$value")
            }
        }
        Timber.tag(tag).w(parts.joinToString(separator = " "))
    }

    fun sanitizeUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        return runCatching {
            val uri = Uri.parse(url.trim())
            val builder = uri.buildUpon().clearQuery()
            uri.queryParameterNames
                    .filter { name -> sensitiveQueryKeys.none { name.equals(it, ignoreCase = true) } }
                    .sorted()
                    .forEach { name ->
                        uri.getQueryParameters(name).forEach { value ->
                            builder.appendQueryParameter(name, value)
                        }
                    }
            val fragment = uri.encodedFragment?.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()
            (builder.build().toString() + fragment).take(512)
        }.getOrElse {
            url.trim().take(512)
        }
    }

    fun errorClass(t: Throwable?): String? =
            t?.let { it::class.java.simpleName.ifEmpty { it::class.java.name } }

    /**
     * Safe HTML diagnostics: length + short hash + coarse markers — never log raw markup/snippets.
     */
    fun classifyHtml(html: String?): Map<String, Any?> {
        val body = html.orEmpty()
        if (body.isEmpty()) {
            return mapOf("htmlLen" to 0, "htmlHash" to "empty")
        }
        val sample = body.take(8192)
        return mapOf(
                "htmlLen" to body.length,
                "htmlHash" to sha256Hex(sample),
                "hasForm" to body.contains("<form", ignoreCase = true),
                "hasArticle" to body.contains("<article", ignoreCase = true),
                "hasCommentList" to (
                        body.contains("comment-list", ignoreCase = true) ||
                                body.contains("comments-list", ignoreCase = true)
                        ),
                "hasMessList" to body.contains("mess_list", ignoreCase = true)
        )
    }

    private fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }.take(16)
    }

    enum class QmsArea { OPEN, NETWORK, PARSE, STATE, CACHE, WEBVIEW, CHAT }

    enum class ThemeArea { OPEN, LOAD, RENDER, SCROLL, SMART_BUTTON }

    enum class ArticleArea { OPEN, PARSE, POLL, RENDER, CACHE, WEBVIEW }

    fun logQms(
            area: QmsArea,
            event: String,
            fields: Map<String, Any?> = emptyMap(),
            warn: Boolean = false
    ) {
        val tag = when (area) {
            QmsArea.OPEN -> TAG_QMS_OPEN
            QmsArea.NETWORK -> TAG_QMS_NETWORK
            QmsArea.PARSE -> TAG_QMS_PARSE
            QmsArea.STATE -> TAG_QMS_STATE
            QmsArea.CACHE -> TAG_QMS_CACHE
            QmsArea.WEBVIEW -> TAG_QMS_WEBVIEW
            QmsArea.CHAT -> TAG_QMS_CHAT
        }
        if (warn) warn(tag, event, fields) else log(tag, event, fields)
    }

    fun logTheme(
            area: ThemeArea,
            event: String,
            fields: Map<String, Any?> = emptyMap(),
            warn: Boolean = false
    ) {
        val tag = when (area) {
            ThemeArea.OPEN -> TAG_THEME_OPEN
            ThemeArea.LOAD -> TAG_THEME_LOAD
            ThemeArea.RENDER -> TAG_THEME_RENDER
            ThemeArea.SCROLL -> TAG_TOPIC_SCROLL
            ThemeArea.SMART_BUTTON -> TAG_SMART_BUTTON
        }
        if (warn) warn(tag, event, fields) else log(tag, event, fields)
    }

    fun logSmartButton(
            event: String,
            fields: Map<String, Any?> = emptyMap(),
            warn: Boolean = false
    ) {
        logTheme(ThemeArea.SMART_BUTTON, event, fields, warn)
    }

    fun logArticle(
            area: ArticleArea,
            event: String,
            fields: Map<String, Any?> = emptyMap(),
            warn: Boolean = false
    ) {
        val tag = when (area) {
            ArticleArea.OPEN -> TAG_ARTICLE_OPEN
            ArticleArea.PARSE -> TAG_ARTICLE_PARSE
            ArticleArea.POLL -> TAG_ARTICLE_POLL
            ArticleArea.RENDER, ArticleArea.WEBVIEW -> TAG_ARTICLE_RENDER
            ArticleArea.CACHE -> TAG_ARTICLE_CACHE
        }
        if (warn) warn(tag, event, fields) else log(tag, event, fields)
    }

    fun fieldsWithTrace(traceId: String?, fields: Map<String, Any?> = emptyMap()): Map<String, Any?> =
            if (traceId.isNullOrBlank()) fields else fields + ("traceId" to traceId)

    fun fieldsWithGeneration(
            generationId: Int?,
            fields: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> =
            if (generationId == null) fields else fields + ("generationId" to generationId)
}
