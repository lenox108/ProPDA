package forpdateam.ru.forpda.entity.remote.reputation

/**
 * Parsed server-side report/appeal form for a reputation history entry.
 */
data class ReputationReportForm(
        val actionUrl: String,
        val method: String = METHOD_POST,
        val fields: LinkedHashMap<String, String> = LinkedHashMap(),
        val messageFieldName: String = "message",
        val token: String? = null,
        val reputationId: Int = 0,
        val sourceReportUrl: String? = null,
) {
    companion object {
        const val METHOD_POST = "POST"
        const val METHOD_GET = "GET"
    }
}
