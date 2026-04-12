package forpdateam.ru.forpda.model.data.cache.favorites

import forpdateam.ru.forpda.entity.db.favorites.FavItemBd
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import io.realm.Realm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesCache {

    private val _items = MutableStateFlow<List<FavItem>>(emptyList())
    fun observeItems(): StateFlow<List<FavItem>> = _items.asStateFlow()

    private fun loadFromRealm(): List<FavItem> = Realm.getDefaultInstance().use { realm ->
        realm.where(FavItemBd::class.java).findAll().map { FavItem(it) }
    }

    fun getItems(): List<FavItem> = loadFromRealm().also { _items.value = it }

    fun saveFavorites(items: List<FavItem>) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.delete(FavItemBd::class.java)
            realmTr.copyToRealmOrUpdate(items.map { FavItemBd(it) })
        }
        _items.value = loadFromRealm()
    }

    fun getItemByFavId(favId: Int): FavItem? = Realm.getDefaultInstance().use { realm ->
        realm.where(FavItemBd::class.java).equalTo("favId", favId).findFirst()?.let {
            FavItem(it)
        }
    }

    fun getItemByTopicId(topicId: Int): FavItem? = Realm.getDefaultInstance().use { realm ->
        realm.where(FavItemBd::class.java).equalTo("topicId", topicId).findFirst()?.let {
            FavItem(it)
        }
    }

    fun updateItem(item: FavItem) = Realm.getDefaultInstance().use { realm ->
        realm.executeTransaction { realmTr ->
            realmTr.where(FavItemBd::class.java).equalTo("favId", item.favId).findFirst()?.let {
                realmTr.copyToRealmOrUpdate(FavItemBd(item))
            }
        }
        _items.value = loadFromRealm()
    }
}
