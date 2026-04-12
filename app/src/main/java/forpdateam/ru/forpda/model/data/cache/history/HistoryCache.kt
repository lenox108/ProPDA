package forpdateam.ru.forpda.model.data.cache.history

import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.db.history.HistoryItemBd
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryCache {

    private val dateFormat = SimpleDateFormat("dd.MM.yy, HH:mm", Locale.getDefault())

    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    fun observeItems(): StateFlow<List<HistoryItem>> = _items.asStateFlow()

    private fun loadFromRealm(): List<HistoryItem> = Realm.getDefaultInstance().use { realm ->
        realm.where(HistoryItemBd::class.java).findAll().sort("unixTime", Sort.DESCENDING).map { HistoryItem(it) }
    }

    fun getHistory(): List<HistoryItem> = loadFromRealm().also { _items.value = it }

    fun add(id: Int, url: String?, title: String?) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            val item = realmTr.where(HistoryItemBd::class.java).equalTo("id", id).findFirst()
            if (item == null) {
                realmTr.insert(HistoryItemBd().apply {
                    this.title = title
                    this.id = id
                    this.url = url
                    unixTime = System.currentTimeMillis()
                    date = dateFormat.format(Date(unixTime))
                })
            } else {
                item.url = url
                item.unixTime = System.currentTimeMillis()
                item.date = dateFormat.format(Date(item.getUnixTime()))
                realmTr.insertOrUpdate(item)
            }
        }
        _items.value = loadFromRealm()
    }

    fun remove(id: Int) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.where(HistoryItemBd::class.java).equalTo("id", id).findAll().deleteAllFromRealm()
        }
        _items.value = loadFromRealm()
    }

    fun clear() = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.delete(HistoryItemBd::class.java)
        }
        _items.value = emptyList()
    }
}
