package forpdateam.ru.forpda.model.repository.history

import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.history.HistoryCache
import forpdateam.ru.forpda.model.repository.BaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */
class HistoryRepository(
        private val schedulers: SchedulersProvider,
        private val historyCache: HistoryCache
) : BaseRepository(schedulers) {

    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()

    fun observeItems(): Flow<List<HistoryItem>> = historyCache.observeItems()

    suspend fun getHistory(): List<HistoryItem> = withContext(ioDispatcher) {
        historyCache.getHistory()
    }

    suspend fun remove(id: Int) = withContext(ioDispatcher) {
        historyCache.remove(id)
    }

    suspend fun clear() = withContext(ioDispatcher) {
        historyCache.clear()
    }
}
