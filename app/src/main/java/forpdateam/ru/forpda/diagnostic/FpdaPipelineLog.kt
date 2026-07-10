package forpdateam.ru.forpda.diagnostic

import org.json.JSONObject

/**
 * Structured pipeline logging for comments and QMS open/render flows.
 * Delegates to [FpdaDebugLog] (DEBUG-only). Keeps event names stable for logcat filters.
 */
object FpdaPipelineLog {

    fun comments(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_COMMENTS_SECTION, event, fields)
    }

    fun commentsWarn(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.warn(FpdaDebugLog.TAG_COMMENTS_SECTION, event, fields)
    }

    fun qmsOpen(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_QMS_OPEN, event, fields)
    }

    fun qmsNetwork(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_QMS_NETWORK, event, fields)
    }

    fun qmsParse(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_QMS_PARSE, event, fields)
    }

    fun qmsState(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_QMS_STATE, event, fields)
    }

    fun qmsCache(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_QMS_CACHE, event, fields)
    }

    fun stateRace(event: String, fields: Map<String, Any?> = emptyMap()) {
        FpdaDebugLog.log(FpdaDebugLog.TAG_STATE_RACE, event, fields)
    }

    /**
     * Normalizes JS bridge payload from [fpdaCommentsSectionLog] into a stable event + fields.
     */
    fun parseJsCommentsPayload(payload: String?): Pair<String, Map<String, Any?>> {
        if (payload.isNullOrBlank()) {
            return "bridge_called" to mapOf("source" to "js", "payloadEmpty" to true)
        }
        return runCatching {
            val json = JSONObject(payload)
            val rawEvent = json.optString("event").ifBlank { "bridge_called" }
            val event = normalizeJsCommentsEvent(rawEvent)
            val fields = linkedMapOf<String, Any?>("source" to "js", "jsEvent" to rawEvent)
            json.keys().forEach { key ->
                if (key != "event") {
                    fields[key] = json.opt(key)?.toString()?.take(200)
                }
            }
            event to fields
        }.getOrElse {
            "bridge_called" to mapOf("source" to "js", "payload" to payload.take(400), "parseError" to true)
        }
    }

    private fun normalizeJsCommentsEvent(raw: String): String = when (raw) {
        "expand_click" -> "expand_clicked"
        "kotlin_bridge_called" -> "bridge_called"
        "toggle_click" -> "expand_clicked"
        "inject_ok" -> "render_success"
        "inject_failed", "inject_skipped" -> "render_target_missing"
        "bindCommentsSection_called" -> "bindCommentsSection"
        "bind_attached", "bind_rebind", "bind_recheck", "bind_skipped" -> "bindCommentsSection"
        "collapsed_set" -> "state_changed"
        else -> raw
    }

    fun themeLoad(event: String, fields: Map<String, Any?> = emptyMap(), warn: Boolean = false) {
        FpdaDebugLog.logTheme(FpdaDebugLog.ThemeArea.LOAD, event, fields, warn)
    }

    fun themeRender(event: String, fields: Map<String, Any?> = emptyMap(), warn: Boolean = false) {
        FpdaDebugLog.logTheme(FpdaDebugLog.ThemeArea.RENDER, event, fields, warn)
    }

    fun themeOpen(event: String, fields: Map<String, Any?> = emptyMap(), warn: Boolean = false) {
        FpdaDebugLog.logTheme(FpdaDebugLog.ThemeArea.OPEN, event, fields, warn)
    }

    fun smartButton(event: String, fields: Map<String, Any?> = emptyMap(), warn: Boolean = false) {
        FpdaDebugLog.logSmartButton(event, fields, warn)
    }

    fun articleRender(event: String, fields: Map<String, Any?> = emptyMap(), warn: Boolean = false) {
        FpdaDebugLog.logArticle(FpdaDebugLog.ArticleArea.RENDER, event, fields, warn)
    }

    fun articleOpen(event: String, fields: Map<String, Any?> = emptyMap(), warn: Boolean = false) {
        FpdaDebugLog.logArticle(FpdaDebugLog.ArticleArea.OPEN, event, fields, warn)
    }
}
