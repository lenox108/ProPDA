package forpdateam.ru.forpda.model.repository.theme

import forpdateam.ru.forpda.common.di.AppScope
import forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryDao
import forpdateam.ru.forpda.entity.db.readboundary.TopicReadBoundaryRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Персистентная, МОНОТОННАЯ клиентская граница прочитанного на тему (модель Discourse).
 *
 * Зачем: 4PDA/IPB (как и XenForo) метит тему прочитанной по факту загрузки страницы — серверный
 * `view=getnewpost` неизбежно уезжает вниз (walk-down), из-за чего при переоткрытии якорь садится
 * НИЖЕ реально непрочитанных постов. Сервер точнее не умеет, поэтому точную границу ведём локально:
 * [lastSeenPostId] — наибольший id поста, который реально побывал во вьюпорте у пользователя.
 *
 * Свойства (важно для безопасности):
 *  - Монотонность: [recordSeen] двигает границу только ВВЕРХ (id постов 4PDA глобально возрастают),
 *    поэтому назад якорь не прыгает — максимум перечитывание уже виденного, но не пропуск.
 *  - Персистентность: пережить перезапуск (обычная причина «периодически перескакивает»).
 *  - Синхронный [get] из in-memory кэша (резолвер якоря на main-потоке), запись в Room — асинхронно.
 *  - Cold-miss (кэш не прогрет) → [get] вернёт null → фолбэк на текущее серверное поведение (безопасно).
 */
@Singleton
class TopicReadBoundaryStore @Inject constructor(
    private val dao: TopicReadBoundaryDao,
    @AppScope private val appScope: CoroutineScope,
) {

    private val cache: MutableMap<Int, TopicReadBoundaryRoom> = ConcurrentHashMap()

    init {
        hydrateAsync()
    }

    /**
     * Прогрев кэша из Room — с ПОМЕРЖИВАНИЕМ, а не слепой записью. На холодном старте открытие темы
     * может успеть записать в кэш свежую строку ДО завершения прогрева (recordSeen/recordLoaded создают
     * её «с нуля», без знаний о персистентных значениях): слепой `cache[it] = db` затирал этот свежий
     * прогресс стухшей строкой из БД, а свежая строка «с нуля», наоборот, теряла накопленные
     * maxLoaded-поля и lastSeen и REPLACE'ом уносила их и из Room. Мерж по-полевому монотонен (max), так что
     * оба направления гонки схлопываются в корректный результат; если память оказалась свежее БД —
     * доливаем мерж обратно в Room.
     */
    private fun hydrateAsync() {
        appScope.launch(Dispatchers.IO) {
            runCatching { dao.getAll() }.getOrNull()?.forEach { db ->
                val merged = cache.merge(db.topicId, db) { mem, dbRow -> mergeRows(mem, dbRow) }
                if (merged != null && merged != db) {
                    runCatching { dao.upsert(merged) }
                }
            }
        }
    }

    /** Бэкап восстановил таблицу под живым процессом — перечитать её в кэш (мерж тот же). */
    fun rehydrateAfterRestore() {
        cache.clear()
        hydrateAsync()
    }

    private fun mergeRows(a: TopicReadBoundaryRoom, b: TopicReadBoundaryRoom): TopicReadBoundaryRoom {
        val seenWinner = if (a.lastSeenPostId >= b.lastSeenPostId) a else b
        return TopicReadBoundaryRoom(
            topicId = a.topicId,
            lastSeenPostId = seenWinner.lastSeenPostId,
            lastSeenPage = seenWinner.lastSeenPage,
            updatedAt = maxOf(a.updatedAt, b.updatedAt),
            maxLoadedPostId = maxOf(a.maxLoadedPostId, b.maxLoadedPostId),
            maxLoadedPage = maxOf(a.maxLoadedPage, b.maxLoadedPage),
        )
    }

    /** Наибольший реально-виденный пост темы, либо null если границы ещё нет. */
    fun get(topicId: Int): TopicReadBoundaryRoom? {
        if (topicId <= 0) return null
        return cache[topicId]
    }

    fun lastSeenPostId(topicId: Int): Int = get(topicId)?.lastSeenPostId ?: 0

    /** Самая дальняя страница, которую ЭТО устройство хотя бы загружало (0 = неизвестно). */
    fun maxLoadedPage(topicId: Int): Int = get(topicId)?.maxLoadedPage ?: 0

    /** Самый дальний пост, который ЭТО устройство хотя бы загружало (0 = неизвестно). */
    fun maxLoadedPostId(topicId: Int): Int = get(topicId)?.maxLoadedPostId ?: 0

    /**
     * Двигает границу вверх, если [postId] новее (больше) сохранённого. No-op при откате/невалидном id.
     * Поля [TopicReadBoundaryRoom.maxLoadedPostId]/[TopicReadBoundaryRoom.maxLoadedPage] сохраняем
     * (copy) — это ортогональный монотонный трек, не относящийся к «границе виденного».
     */
    fun recordSeen(topicId: Int, postId: Int, page: Int) {
        if (topicId <= 0 || postId <= 0) return
        val current = cache[topicId]
        if (current != null && postId <= current.lastSeenPostId) return
        val updated = (current ?: TopicReadBoundaryRoom(topicId = topicId)).copy(
            lastSeenPostId = postId,
            lastSeenPage = if (page > 0) page else (current?.lastSeenPage ?: 0),
            updatedAt = System.currentTimeMillis(),
        )
        cache[topicId] = updated
        appScope.launch(Dispatchers.IO) { runCatching { dao.upsert(updated) } }
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_READ_BOUNDARY", "recordSeen topic=$topicId lastSeenPostId=$postId page=$page")
        }
    }

    /**
     * Двигает трек «самого дальнего загруженного» вверх. Пишется при КАЖДОЙ загрузке страницы темы с
     * сервера (в т.ч. предзагрузка гибридным скроллом), в отличие от [recordSeen] (только реально
     * виденное). Монотонен. Поля границы виденного сохраняем (copy). No-op, если ни пост, ни страница
     * не выросли.
     */
    fun recordLoaded(topicId: Int, postId: Int, page: Int) {
        if (topicId <= 0 || postId <= 0) return
        val current = cache[topicId]
        val curMaxPost = current?.maxLoadedPostId ?: 0
        val curMaxPage = current?.maxLoadedPage ?: 0
        val newMaxPost = maxOf(postId, curMaxPost)
        val newMaxPage = maxOf(if (page > 0) page else 0, curMaxPage)
        if (newMaxPost == curMaxPost && newMaxPage == curMaxPage) return
        val updated = (current ?: TopicReadBoundaryRoom(topicId = topicId)).copy(
            maxLoadedPostId = newMaxPost,
            maxLoadedPage = newMaxPage,
            updatedAt = System.currentTimeMillis(),
        )
        cache[topicId] = updated
        appScope.launch(Dispatchers.IO) { runCatching { dao.upsert(updated) } }
        if (forpdateam.ru.forpda.BuildConfig.DEBUG) {
            android.util.Log.i("FPDA_READ_BOUNDARY", "recordLoaded topic=$topicId maxLoadedPostId=$newMaxPost page=$newMaxPage")
        }
    }

    /** Тема реально дочитана до конца — граница больше не нужна. */
    fun clear(topicId: Int) {
        if (topicId <= 0) return
        cache.remove(topicId)
        appScope.launch(Dispatchers.IO) { runCatching { dao.delete(topicId) } }
    }
}
