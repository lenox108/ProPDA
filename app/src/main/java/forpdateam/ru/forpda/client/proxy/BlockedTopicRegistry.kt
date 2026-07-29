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

    /**
     * Запомнить/освежить тему, которая открылась только через прокси.
     *
     * [title] сохраняем рядом, чтобы на экране настроек список был читаемым: «в списке 2 темы»
     * ничего не говорит о том, что именно ходит мимо прямого маршрута.
     */
    fun remember(topicId: Int, title: String? = null, nowMs: Long = System.currentTimeMillis()) {
        if (topicId <= 0) return
        prefs.edit()
                .putLong(topicId.toString(), nowMs)
                .apply {
                    // Пустой заголовок не затирает прежний: имя темы полезнее пустой строки.
                    title?.trim()?.takeIf { it.isNotEmpty() }?.let { putString(titleKey(topicId), it) }
                }
                .apply()
        Timber.tag(LOG_TAG).i("topic %d routed via proxy", topicId)
    }

    /** Тема снова открывается напрямую — маршрут больше не нужен. */
    fun forget(topicId: Int) {
        if (topicId <= 0) return
        if (!prefs.contains(topicId.toString())) return
        prefs.edit().remove(topicId.toString()).remove(titleKey(topicId)).apply()
        Timber.tag(LOG_TAG).i("topic %d back to direct route", topicId)
    }

    /** Сколько тем сейчас в списке (включая протухшие — они видны пользователю как «в списке»). */
    fun size(): Int = topics().size

    /** Список для экрана настроек: свежие сверху. */
    fun topics(): List<BlockedTopic> = prefs.all.entries
            .mapNotNull { (key, value) ->
                val id = key.toIntOrNull() ?: return@mapNotNull null // отсеиваем ключи заголовков
                val confirmedAt = value as? Long ?: return@mapNotNull null
                BlockedTopic(id, prefs.getString(titleKey(id), null)?.takeIf { it.isNotBlank() }, confirmedAt)
            }
            .sortedByDescending { it.confirmedAt }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun titleKey(topicId: Int) = "$topicId$TITLE_SUFFIX"

    /** @property title null для тем, попавших в список до того, как мы стали сохранять имя. */
    data class BlockedTopic(val id: Int, val title: String?, val confirmedAt: Long)

    companion object {
        private const val STORE_NAME = "proxy_blocked_topics"
        private const val LOG_TAG = "ProxyRoute"
        private const val TITLE_SUFFIX = ".title"

        /** Раз в 30 дней пробуем тему напрямую — вдруг ограничение сняли. */
        const val REVALIDATE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
    }
}
