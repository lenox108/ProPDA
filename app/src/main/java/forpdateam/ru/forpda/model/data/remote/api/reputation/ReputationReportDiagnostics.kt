package forpdateam.ru.forpda.model.data.remote.api.reputation

import timber.log.Timber

object ReputationReportDiagnostics {

    const val TAG = "PPDA_REPUTATION_REPORT"

    fun log(
            userId: Int,
            reputationId: Int,
            reportUrl: String?,
            formLoaded: Boolean?,
            tokenFound: Boolean?,
            submitStatus: String?,
            errorReason: String?,
    ) {
        Timber.tag(TAG).i(
                "userId=%d reputationId=%d reportUrl=%s formLoaded=%s tokenFound=%s submitStatus=%s errorReason=%s",
                userId,
                reputationId,
                reportUrl.orEmpty(),
                formLoaded?.toString().orEmpty(),
                tokenFound?.toString().orEmpty(),
                submitStatus.orEmpty(),
                errorReason.orEmpty(),
        )
    }
}
