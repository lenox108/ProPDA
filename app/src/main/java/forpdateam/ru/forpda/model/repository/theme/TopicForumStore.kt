package forpdateam.ru.forpda.model.repository.theme

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лёгкая персистентная карта topicId → forumId (в каком разделе живёт тема).
 *
 * Зачем: индикатору «новых сообщений» в Истории для не-избранных тем нужен read-only способ узнать
 * «появились ли новые ответы» — им служит флаг «+» в списке раздела ([showforum=N]). Чтобы дёрнуть
 * список нужного раздела, надо знать forumId темы, а сама История его не хранит. Ловим forumId при
 * заходе в тему ([ThemeUseCase.recordThemeVisit] — там доступен [ThemePage.forumId]).
 *
 * Реализация — SharedPreferences по одному ключу на тему (`tf_<topicId>` → forumId), чтобы НЕ трогать
 * общую Room-БД с её хрупкими downgrade-мостами миграций. Запись O(1), значения крошечные (int).
 * Работает «вперёд»: для тем, посещённых до появления фичи, forumId неизвестен → у них останется
 * только сшивка с Избранным (harvest их не подсветит, пока не зайдёшь в тему заново).
 */
@Singleton
class TopicForumStore @Inject constructor(
        @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun put(topicId: Int, forumId: Int) {
        if (topicId <= 0 || forumId <= 0) return
        val key = key(topicId)
        // Не переписываем, если не изменилось — экономим лишний commit на каждом заходе.
        if (prefs.getInt(key, 0) == forumId) return
        prefs.edit().putInt(key, forumId).apply()
    }

    /** forumId темы или 0, если ещё не знаем. */
    fun get(topicId: Int): Int = if (topicId > 0) prefs.getInt(key(topicId), 0) else 0

    private fun key(topicId: Int) = "tf_$topicId"

    private companion object {
        const val PREFS_NAME = "topic_forum_map"
    }
}
