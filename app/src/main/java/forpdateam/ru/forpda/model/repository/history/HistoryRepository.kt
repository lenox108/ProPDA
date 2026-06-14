package forpdateam.ru.forpda.model.repository.history

import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.model.data.cache.history.HistoryCacheRoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */
class HistoryRepository(
        private val historyCache: HistoryCacheRoom
) {

    fun observeItems(): Flow<List<HistoryItem>> = historyCache.observeItems()

    suspend fun getHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        historyCache.getHistory()
    }

    suspend fun remove(id: Int) = withContext(Dispatchers.IO) {
        historyCache.remove(id)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        historyCache.clear()
    }
}
