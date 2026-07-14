package forpdateam.ru.forpda.model

import android.content.SharedPreferences
import forpdateam.ru.forpda.entity.app.CloseableInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CloseableInfoHolder(
        private val preferences: SharedPreferences
) {

    companion object {

        const val item_other_menu_drag = 10
        const val item_notes_sync = 11

        /**
         * Подсказка про настройку меню. Отдельный id, а не новый текст у [item_other_menu_drag]:
         * тот у большинства давно закрыт (id лежит в closeable_info_closed_ids), и правка строки
         * никому бы не показалась — про плитки-ярлыки и разделы так и не узнали бы.
         */
        const val item_other_menu_customize = 12

        val ALL_ITEMS = arrayOf(
                item_other_menu_drag,
                item_notes_sync,
                item_other_menu_customize
        )
    }

    private val _items = MutableStateFlow(loadInitialList())

    private fun loadInitialList(): List<CloseableInfo> {
        val closedIds: List<Int> = preferences.getString("closeable_info_closed_ids", null)?.let { savedIds ->
            savedIds.split(',').mapNotNull { it.toIntOrNull() }
        } ?: emptyList()

        return ALL_ITEMS.map { CloseableInfo(it, closedIds.contains(it)) }
    }

    fun observe(): Flow<List<CloseableInfo>> = _items.asStateFlow()

    fun get(): List<CloseableInfo> = _items.value.map { CloseableInfo(it.id, it.isClosed) }

    fun close(item: CloseableInfo) {
        val current = _items.value.map { info ->
            if (info.id == item.id) info.copy(isClosed = true) else info
        }
        val closedItems = current.filter { it.isClosed }
        preferences.edit()
                .putString("closeable_info_closed_ids", closedItems.joinToString(",") { it.id.toString() })
                .apply()
        _items.value = current
    }
}
