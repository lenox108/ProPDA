package forpdateam.ru.forpda.blocklist

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory источник правды «забанен ли текущий аккаунт». Синглтон, чтобы стартовая
 * проверка в [forpdateam.ru.forpda.ui.activities.MainActivity] и сетевой
 * [forpdateam.ru.forpda.client.interceptors.BlocklistInterceptor] видели один и тот
 * же набор без обращения к диску на каждый запрос.
 *
 * Набор сидируется из кэша ([BlocklistPreferences]) при создании синглтона — значит
 * блок действует ещё до первого сетевого обновления и даже когда GitHub недоступен.
 * [BlocklistRepository.refresh] обновляет набор из сети.
 */
@Singleton
class BlocklistGuard @Inject constructor(
    private val preferences: BlocklistPreferences
) {

    @Volatile
    private var blocklist: Blocklist = preferences.getCached()

    /** Забанен ли аккаунт по id или нику. Аноним (NO_ID) без ника не банится. */
    fun isBanned(userId: Int, nick: String?): Boolean = blocklist.matches(userId, nick)

    /** Обновляет набор в памяти и в кэше (после успешной загрузки из сети). */
    fun update(value: Blocklist) {
        blocklist = value
        preferences.setCached(value)
    }

    fun snapshot(): Blocklist = blocklist
}
