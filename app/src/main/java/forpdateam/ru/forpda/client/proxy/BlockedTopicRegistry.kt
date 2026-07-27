package forpdateam.ru.forpda.client.proxy

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * Список тем, которые напрямую отдают заглушку, а через прокси открываются.
 *
 * Заполняется автоматически: [forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi] при
 * заглушке повторяет запрос через прокси, и если пришли посты — темa попадает сюда. Дальше все
 * запросы этой темы сразу идут через прокси, без лишнего прямого круга.
 *
 * Запись «протухает» через [REVALIDATE_AFTER_MS]: тему могли вернуть, и вечно гонять её через
 * прокси незачем. Протухшая запись НЕ удаляется — она просто перестаёт маршрутизировать, поэтому
 * следующий заход идёт напрямую; если снова придёт заглушка, автоповтор освежит отметку, а если
 * тема открылась — запись убирается насовсем ([forget]).
 *
 * Хранилище своё (не default prefs), чтобы «Сбросить список» не задевал остальные настройки.
 */
class BlockedTopicRegistry(context: Context) {

    private val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE)

    /** Идёт ли эта тема через прокси прямо сейчас (с учётом протухания). */
    fun isBlocked(topicId: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (topicId <= 0) return false
        val confirmedAt = prefs.getLong(topicId.toString(), 0L)
        if (confirmedAt <= 0L) return false
        // Часы могли уехать назад (смена таймзоны/ручная правка) — считаем такую отметку свежей,
        // иначе тема разово провалилась бы в прямой запрос и получила бы заглушку.
        val age = nowMs - confirmedAt
        return age < REVALIDATE_AFTER_MS || age < 0
    }

    /** Запомнить/освежить тему, которая открылась только через прокси. */
    fun remember(topicId: Int, nowMs: Long = System.currentTimeMillis()) {
        if (topicId <= 0) return
        prefs.edit().putLong(topicId.toString(), nowMs).apply()
        Timber.tag(LOG_TAG).i("topic %d routed via proxy", topicId)
    }

    /** Тема снова открывается напрямую — маршрут больше не нужен. */
    fun forget(topicId: Int) {
        if (topicId <= 0) return
        if (!prefs.contains(topicId.toString())) return
        prefs.edit().remove(topicId.toString()).apply()
        Timber.tag(LOG_TAG).i("topic %d back to direct route", topicId)
    }

    /** Сколько тем сейчас в списке (включая протухшие — они видны пользователю как «в списке»). */
    fun size(): Int = prefs.all.size

    fun topicIds(): List<Int> = prefs.all.keys.mapNotNull { it.toIntOrNull() }.sorted()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val STORE_NAME = "proxy_blocked_topics"
        private const val LOG_TAG = "ProxyRoute"

        /** Раз в 30 дней пробуем тему напрямую — вдруг ограничение сняли. */
        const val REVALIDATE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
