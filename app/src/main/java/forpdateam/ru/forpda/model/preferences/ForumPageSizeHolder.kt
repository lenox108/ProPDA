package forpdateam.ru.forpda.model.preferences

import android.content.SharedPreferences

/**
 * Хранит число сообщений на странице темы (серверная настройка `postpage`), чтобы
 * счётчики «N стр.» в СПИСКАХ (избранное, темы форума) считались по реальному размеру
 * страницы, а не по захардкоженным 20.
 *
 * Значение узнаётся из двух источников и кэшируется в SharedPreferences (переживает рестарт):
 *  - явно: при загрузке/сохранении настроек форума (UserCpRepository → [setPostsPerPage]);
 *  - попутно: при открытии любой многостраничной темы парсер пагинации сообщает реальный
 *    perPage (см. ThemeParser), что покрывает случай смены настройки на самом сайте.
 *
 * Синхронный доступ — парсеры читают значение прямо во время разбора HTML на IO.
 */
class ForumPageSizeHolder(
        private val preferences: SharedPreferences
) {
    fun getPostsPerPage(): Int =
            preferences.getInt(KEY_POSTS_PER_PAGE, DEFAULT_POSTS_PER_PAGE).coerceAtLeast(1)

    fun setPostsPerPage(value: Int) {
        if (value <= 0) return
        if (value == getPostsPerPage()) return
        preferences.edit().putInt(KEY_POSTS_PER_PAGE, value).apply()
    }

    companion object {
        const val DEFAULT_POSTS_PER_PAGE = 20
        private const val KEY_POSTS_PER_PAGE = "forum.posts_per_page"
    }
}
