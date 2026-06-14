package forpdateam.ru.forpda.diagnostic

object ArticleParseTrace {

    fun log(
            event: String,
            articleId: Int? = null,
            parserVersion: String? = null,
            selectorSuccess: Boolean? = null,
            bodyLen: Int? = null,
            titleLen: Int? = null,
            commentsCount: Int? = null,
            elapsedMs: Long? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "articleId" to articleId,
                "parserVersion" to parserVersion,
                "selectorSuccess" to selectorSuccess,
                "bodyLen" to bodyLen,
                "titleLen" to titleLen,
                "commentsCount" to commentsCount,
                "elapsedMs" to elapsedMs,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_ARTICLE_PARSE, event, fields)
    }
}
