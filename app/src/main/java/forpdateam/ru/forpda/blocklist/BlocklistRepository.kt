package forpdateam.ru.forpda.blocklist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Оркестрация обновления блоклиста: сеть → [BlocklistGuard] (+ кэш). При сетевой
 * ошибке кэш не трогается — остаётся последний известный набор (уже в guard).
 */
@Singleton
class BlocklistRepository @Inject constructor(
    private val source: BlocklistSource,
    private val preferences: BlocklistPreferences,
    private val guard: BlocklistGuard,
) {

    /** Тянет свежий список; при успехе обновляет guard/кэш. Возвращает актуальный блоклист. */
    suspend fun refresh(): Blocklist = withContext(Dispatchers.IO) {
        val fetched = source.fetch()
        if (fetched != null) {
            guard.update(fetched)
            preferences.setLastCheckTime(System.currentTimeMillis())
            fetched
        } else {
            // Не смогли обновить (сеть/сервер) — держим то, что уже есть (кэш).
            guard.snapshot()
        }
    }
}
