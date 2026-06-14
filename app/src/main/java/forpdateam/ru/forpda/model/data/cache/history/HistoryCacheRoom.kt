package forpdateam.ru.forpda.model.data.cache.history

import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.db.history.HistoryItemDao
import forpdateam.ru.forpda.entity.db.history.HistoryItemRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryCacheRoom(private val historyItemDao: HistoryItemDao) {

    private val dateFormat = SimpleDateFormat("dd.MM.yy, HH:mm", Locale.getDefault())

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    fun observeItems(): StateFlow<List<HistoryItem>> = _items.asStateFlow()

    suspend fun getHistory(): List<HistoryItem> {
        val items = historyItemDao.getAllHistoryList()
        val historyItems = items.map { HistoryItem().apply {
            id = it.id
            url = it.url
            title = it.title
            date = it.date
            unixTime = it.unixTime
        } }
        _items.value = historyItems
        return historyItems
    }

    suspend fun add(id: Int, url: String?, title: String?) {
        val existingItem = historyItemDao.getHistoryById(id)
        val unixTime = System.currentTimeMillis()
        val date = dateFormat.format(Date(unixTime))

        if (existingItem == null) {
            val newItem = HistoryItemRoom(
                id = id,
                url = url ?: "",
                title = title ?: "",
                date = date,
                unixTime = unixTime
            )
            historyItemDao.insertHistory(newItem)
        } else {
            val updatedItem = existingItem.copy(
                url = url ?: existingItem.url,
                unixTime = unixTime,
                date = date
            )
            historyItemDao.updateHistory(updatedItem)
        }
        getHistory()
    }

    suspend fun remove(id: Int) {
        historyItemDao.deleteHistory(id)
        getHistory()
    }

    suspend fun clear() {
        historyItemDao.deleteAllHistory()
        _items.value = emptyList()
    }
}
