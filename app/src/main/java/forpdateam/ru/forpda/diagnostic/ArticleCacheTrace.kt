package forpdateam.ru.forpda.diagnostic

object ArticleCacheTrace {

    fun log(
            event: String,
            articleId: Int? = null,
            cacheLayer: String? = null,
            hit: Boolean? = null,
            valid: Boolean? = null,
            htmlLen: Int? = null,
            mappedHtmlLen: Int? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "articleId" to articleId,
                "cacheLayer" to cacheLayer,
                "hit" to hit,
                "valid" to valid,
                "htmlLen" to htmlLen,
                "mappedHtmlLen" to mappedHtmlLen,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_ARTICLE_CACHE, event, fields)
    }
}
