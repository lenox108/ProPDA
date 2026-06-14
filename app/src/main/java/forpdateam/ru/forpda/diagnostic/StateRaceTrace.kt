package forpdateam.ru.forpda.diagnostic

object StateRaceTrace {

    fun log(
            domain: String,
            event: String,
            requestId: Int? = null,
            generation: Int? = null,
            currentGeneration: Int? = null,
            articleId: Int? = null,
            topicId: Int? = null,
            traceId: String? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "domain" to domain,
                "requestId" to requestId,
                "generation" to generation,
                "currentGeneration" to currentGeneration,
                "articleId" to articleId,
                "topicId" to topicId,
                "traceId" to traceId,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_STATE_RACE, event, fields)
    }
}
