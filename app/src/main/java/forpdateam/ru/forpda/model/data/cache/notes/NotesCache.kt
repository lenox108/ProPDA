package forpdateam.ru.forpda.model.data.cache.notes

import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.db.notes.NoteItemBd
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotesCache {

    private val _items = MutableStateFlow<List<NoteItem>>(emptyList())
    fun observeItems(): StateFlow<List<NoteItem>> = _items.asStateFlow()

    private fun loadFromRealm(): List<NoteItem> = Realm.getDefaultInstance().use { realm ->
        realm.where(NoteItemBd::class.java).findAll().sort("id", Sort.DESCENDING).map { NoteItem(it) }
    }

    fun getItems(): List<NoteItem> = loadFromRealm().also { _items.value = it }

    fun update(item: NoteItem) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            val itemBd = getItemById(item.id, realmTr)?.apply {
                title = item.title
                link = item.link
                content = item.content
            } ?: NoteItemBd(item)
            realmTr.insertOrUpdate(itemBd)
        }
        _items.value = loadFromRealm()
    }

    fun delete(id: Long) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.where(NoteItemBd::class.java).equalTo("id", id).findAll().deleteAllFromRealm()
        }
        _items.value = loadFromRealm()
    }

    fun add(item: NoteItem) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.insertOrUpdate(NoteItemBd(item))
        }
        _items.value = loadFromRealm()
    }

    fun add(items: List<NoteItem>) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.insertOrUpdate(items.map { NoteItemBd(it) })
        }
        _items.value = loadFromRealm()
    }

    private fun getItemById(id: Long, realm: Realm) = realm.where(NoteItemBd::class.java)
            .equalTo("id", id)
            .findFirst()
}
