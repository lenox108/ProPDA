package forpdateam.ru.forpda.model.interactors.other

import forpdateam.ru.forpda.entity.app.other.MenuShortcut
import forpdateam.ru.forpda.model.datastore.OtherDataStore
import forpdateam.ru.forpda.ui.views.drawers.adapters.OtherMenuSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Пользовательские плитки меню («Закреплённое»): хранятся JSON-массивом в DataStore.
 * Идентификаторы отрицательные и монотонно убывают — так они не сталкиваются со штатными
 * пунктами меню и переживают удаление соседей (переиспользовать id нельзя, иначе сохранённый
 * порядок плиток «оживит» удалённый ярлык на месте нового).
 */
class MenuShortcutsRepository(
        private val dataStore: OtherDataStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val shortcutsRelay = MutableStateFlow<List<MenuShortcut>>(emptyList())

    init {
        scope.launch {
            dataStore.otherMenuShortcuts.collect { raw ->
                shortcutsRelay.value = decode(raw)
            }
        }
    }

    fun observe(): StateFlow<List<MenuShortcut>> = shortcutsRelay.asStateFlow()

    fun get(): List<MenuShortcut> = shortcutsRelay.value

    /** Создаёт ярлык с новым id и сохраняет его. Возвращает созданный ярлык. */
    suspend fun add(
            type: MenuShortcut.Type,
            title: String,
            url: String,
            section: OtherMenuSection
    ): MenuShortcut {
        val current = shortcutsRelay.value
        val nextId = (current.minOfOrNull { it.id } ?: 0).coerceAtMost(0) - 1
        val shortcut = MenuShortcut(nextId, type, title, url, section)
        val updated = current + shortcut
        shortcutsRelay.value = updated
        dataStore.setOtherMenuShortcuts(encode(updated))
        return shortcut
    }

    suspend fun remove(id: Int) {
        val updated = shortcutsRelay.value.filterNot { it.id == id }
        shortcutsRelay.value = updated
        dataStore.setOtherMenuShortcuts(encode(updated))
    }

    suspend fun clear() {
        shortcutsRelay.value = emptyList()
        dataStore.setOtherMenuShortcuts("")
    }

    private fun encode(items: List<MenuShortcut>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type.name)
                put("title", item.title)
                put("url", item.url)
                put("section", item.section.name)
            })
        }
        return array.toString()
    }

    private fun decode(raw: String): List<MenuShortcut> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optInt("id", 0)
                val url = obj.optString("url")
                if (id >= 0 || url.isBlank()) return@mapNotNull null
                MenuShortcut(
                        id = id,
                        type = runCatching { MenuShortcut.Type.valueOf(obj.optString("type")) }
                                .getOrDefault(MenuShortcut.Type.LINK),
                        title = obj.optString("title").ifBlank { url },
                        url = url,
                        section = runCatching { OtherMenuSection.valueOf(obj.optString("section")) }
                                .getOrDefault(OtherMenuSection.QUICK)
                )
            }
        }.onFailure { Timber.e(it, "menu shortcuts decode failed") }.getOrDefault(emptyList())
    }
}
