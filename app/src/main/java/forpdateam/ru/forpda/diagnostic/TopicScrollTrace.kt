package forpdateam.ru.forpda.diagnostic

object TopicScrollTrace {

    fun log(
            event: String,
            traceId: String? = null,
            topicId: Int? = null,
            renderGenerationId: Int? = null,
            viewRuntimeGeneration: Int? = null,
            loadAction: String? = null,
            scrollY: Int? = null,
            anchorPostId: String? = null,
            restoreId: String? = null,
            restoreMode: String? = null,
            command: String? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "traceId" to traceId,
                "topicId" to topicId,
                "renderGenerationId" to renderGenerationId,
                "viewRuntimeGeneration" to viewRuntimeGeneration,
                "loadAction" to loadAction,
                "scrollY" to scrollY,
                "anchorPostId" to anchorPostId,
                "restoreId" to restoreId,
                "restoreMode" to restoreMode,
                "command" to command,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_TOPIC_SCROLL, event, fields)
    }

    fun render(
            event: String,
            traceId: String? = null,
            topicId: Int? = null,
            renderGenerationId: Int? = null,
            viewRuntimeGeneration: Int? = null,
            domReady: Boolean? = null,
            htmlLen: Int? = null,
            contentHeight: Int? = null,
            webViewHeight: Int? = null,
            lifecycle: String? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "traceId" to traceId,
                "topicId" to topicId,
                "renderGenerationId" to renderGenerationId,
                "viewRuntimeGeneration" to viewRuntimeGeneration,
                "domReady" to domReady,
                "htmlLen" to htmlLen,
                "contentHeight" to contentHeight,
                "webViewHeight" to webViewHeight,
                "lifecycle" to lifecycle,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_THEME_RENDER, event, fields)
    }
}
