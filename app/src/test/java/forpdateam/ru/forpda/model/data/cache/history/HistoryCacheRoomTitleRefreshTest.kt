package forpdateam.ru.forpda.model.data.cache.history

import forpdateam.ru.forpda.entity.db.history.HistoryItemDao
import forpdateam.ru.forpda.entity.db.history.HistoryItemRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Повторный заход в тему обязан обновлять её НАЗВАНИЕ в «Истории»: первый заход мог сохранить
 * заголовок страницы-заглушки (закрытая без VPN тема отдаёт «Ошибка 404 / такой ссылки не
 * существует»), и до фикса это имя оставалось в «Истории» навсегда — апдейт нёс только url и дату.
 */
class HistoryCacheRoomTitleRefreshTest {

    private class FakeDao : HistoryItemDao {
        val rows = linkedMapOf<Int, HistoryItemRoom>()
        override fun getAllHistory(): Flow<List<HistoryItemRoom>> = flowOf(rows.values.toList())
        override suspend fun getAllHistoryList(): List<HistoryItemRoom> = rows.values.toList()
        override suspend fun getHistoryById(id: Int): HistoryItemRoom? = rows[id]
        override suspend fun insertHistory(history: HistoryItemRoom) { rows[history.id] = history }
        override suspend fun insertHistoryList(historyList: List<HistoryItemRoom>) {
            historyList.forEach { rows[it.id] = it }
        }
        override suspend fun updateHistory(history: HistoryItemRoom) { rows[history.id] = history }
        override suspend fun deleteHistory(id: Int) { rows.remove(id) }
        override suspend fun deleteAllHistory() { rows.clear() }
    }

    @Test
    fun `repeat visit overwrites stale title`() = runBlocking {
        val dao = FakeDao()
        val cache = HistoryCacheRoom(dao)

        cache.add(1, "https://4pda.to/forum/index.php?showtopic=1", "Ой! Ошибка 404")
        cache.add(1, "https://4pda.to/forum/index.php?showtopic=1", "Настоящее название темы")

        assertEquals("Настоящее название темы", dao.rows.getValue(1).title)
    }

    @Test
    fun `blank title keeps the stored one`() = runBlocking {
        val dao = FakeDao()
        val cache = HistoryCacheRoom(dao)

        cache.add(1, "https://4pda.to/forum/index.php?showtopic=1", "Настоящее название темы")
        cache.add(1, "https://4pda.to/forum/index.php?showtopic=1&st=1000", "   ")

        assertEquals("Настоящее название темы", dao.rows.getValue(1).title)
    }
}
