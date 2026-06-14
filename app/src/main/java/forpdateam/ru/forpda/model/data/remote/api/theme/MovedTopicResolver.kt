package forpdateam.ru.forpda.model.data.remote.api.theme

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory кэш для соответствия «старый id темы → новый id темы».
 *
 * Заполняется, когда `ThemeApi` (или любой другой компонент) обнаружил,
 * что `?showtopic=OLD` 302-редиректит на `?showtopic=NEW`. Все последующие
 * открытия той же `OLD` ссылки идут сразу на `NEW`, экономя сетевой round-trip.
 *
 * Кэш «один процесс / синглтон»: ttl нет (для дешёвой стратегии), очищается при
 * перезапуске процесса. Этого достаточно — id перенесённых тем не меняются.
 */
object MovedTopicResolver {

    private val cache = ConcurrentHashMap<Int, Int>()

    /** @return new topic id для уже известного [oldTopicId], либо null. */
    fun resolve(oldTopicId: Int): Int? = cache[oldTopicId]?.takeIf { it != oldTopicId }

    fun remember(oldTopicId: Int, newTopicId: Int) {
        if (oldTopicId <= 0 || newTopicId <= 0) return
        if (oldTopicId == newTopicId) return
        cache[oldTopicId] = newTopicId
    }

    /** Только для тестов / редких сценариев очистки. */
    fun clearForTests() {
        cache.clear()
    }

    fun snapshotForTests(): Map<Int, Int> = cache.toMap()
}
