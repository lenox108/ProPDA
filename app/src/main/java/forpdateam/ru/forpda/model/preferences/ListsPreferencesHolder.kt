package forpdateam.ru.forpda.model.preferences

import android.content.Context
import forpdateam.ru.forpda.model.datastore.ListsDataStore
import kotlinx.coroutines.flow.Flow

class ListsPreferencesHolder(
        private val context: Context
) {
    private val dataStore = ListsDataStore(context)

    fun observeUnreadTopFlow(): Flow<Boolean> = dataStore.observeUnreadTopFlow()

    fun observeShowDotFlow(): Flow<Boolean> = dataStore.observeShowDotFlow()

    fun observeFavLoadAllFlow(): Flow<Boolean> = dataStore.observeFavLoadAllFlow()

    fun observeFavShowUnreadBadgeFlow(): Flow<Boolean> = dataStore.observeFavShowUnreadBadgeFlow()

    suspend fun setSortingKey(key: String) = dataStore.setSortingKey(key)

    suspend fun setSortingOrder(order: String) = dataStore.setSortingOrder(order)

    fun getUnreadTop(): Boolean = dataStore.getUnreadTopImmediate()

    fun getShowDot(): Boolean = dataStore.getShowDotImmediate()

    suspend fun setFavLoadAll(value: Boolean) = dataStore.setFavLoadAll(value)

    fun getFavLoadAll(): Boolean = dataStore.getFavLoadAllImmediate()

    fun getFavShowUnreadBadge(): Boolean = dataStore.getFavShowUnreadBadgeImmediate()

    suspend fun setUnreadTop(value: Boolean) = dataStore.setUnreadTop(value)

    suspend fun setShowDot(value: Boolean) = dataStore.setShowDot(value)

    suspend fun setFavShowUnreadBadge(value: Boolean) = dataStore.setFavShowUnreadBadge(value)

    fun getSortingKey(): String = dataStore.getSortingKeyImmediate()

    fun getSortingOrder(): String = dataStore.getSortingOrderImmediate()
}
