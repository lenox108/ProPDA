package forpdateam.ru.forpda.model.repository.reputation

import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.ReputationReportForm
import forpdateam.ru.forpda.model.data.remote.api.reputation.ReputationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 03.01.18.
 */

class ReputationRepository(
        private val reputationApi: ReputationApi
) {

    suspend fun loadReputation(userId: Int, mode: String, sort: String, st: Int): RepData = withContext(Dispatchers.IO) {
        reputationApi.getReputation(userId, mode, sort, st)
    }

    suspend fun changeReputation(postId: Int, userId: Int, type: Boolean, message: String): Boolean = withContext(Dispatchers.IO) {
        reputationApi.editReputation(postId, userId, type, message)
    }

    suspend fun loadReportForm(userId: Int, reputationId: Int, reportUrl: String): ReputationReportForm =
            withContext(Dispatchers.IO) {
                reputationApi.loadReportForm(userId, reputationId, reportUrl)
            }

    suspend fun submitReport(userId: Int, form: ReputationReportForm, message: String): Boolean =
            withContext(Dispatchers.IO) {
                reputationApi.submitReport(userId, form, message)
            }

}
