package forpdateam.ru.forpda.diagnostic

object ReadStateTrace {

    fun log(
            event: String,
            topicId: Int? = null,
            pageSt: Int? = null,
            postId: String? = null,
            scrollY: Int? = null,
            anchorPostId: String? = null,
            allowedAsNavTarget: Boolean? = null,
            source: String? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "topicId" to topicId,
                "pageSt" to pageSt,
                "postId" to postId,
                "scrollY" to scrollY,
                "anchorPostId" to anchorPostId,
                "allowedAsNavTarget" to allowedAsNavTarget,
                "source" to source,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_TOPIC_READSTATE, event, fields)
    }
}
