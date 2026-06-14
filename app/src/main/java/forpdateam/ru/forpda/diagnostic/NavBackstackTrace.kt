package forpdateam.ru.forpda.diagnostic

object NavBackstackTrace {

    fun log(
            event: String,
            navigator: String? = null,
            command: String? = null,
            tabCount: Int? = null,
            screenKey: String? = null,
            topicId: Int? = null,
            historySize: Int? = null,
            reason: String? = null,
            extra: Map<String, Any?> = emptyMap()
    ) {
        val fields = linkedMapOf<String, Any?>(
                "navigator" to navigator,
                "command" to command,
                "tabCount" to tabCount,
                "screenKey" to screenKey,
                "topicId" to topicId,
                "historySize" to historySize,
                "reason" to reason
        )
        fields.putAll(extra)
        FpdaDebugLog.log(FpdaDebugLog.TAG_NAV_BACKSTACK, event, fields)
    }
}
