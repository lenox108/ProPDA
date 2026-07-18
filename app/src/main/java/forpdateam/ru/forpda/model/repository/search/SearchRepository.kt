package forpdateam.ru.forpda.model.repository.search

import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.search.SearchResult
import forpdateam.ru.forpda.entity.remote.search.SearchSettings
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.search.SearchApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 01.01.18.
 */

class SearchRepository(
        private val searchApi: SearchApi,
        private val forumUsersCache: ForumUsersCacheRoom,
        private val forumSectionTitleIndex: ForumSectionTitleIndex
) {

    suspend fun getSearch(settings: SearchSettings): SearchResult = withContext(Dispatchers.IO) {
        resolveUserId(settings)
        // Переключение source делаем не персистентным: source восстанавливается после запроса, чтобы
        // поиск по юзеру не менял «на будущее» выбранный пользователем режим (Заголовки) для последующих
        // поисков по ключевым словам. userId, наоборот, сохраняем — он нужен для пагинации.
        val originalSource = settings.source
        if (shouldForceContentSource(settings)) {
            settings.source = SearchSettings.SOURCE_CONTENT.first
        }
        try {
            searchApi.getSearch(settings)
                    .also { saveUsers(it) }
                    .also { appendForumSectionTitleSuggestions(it, settings) }
        } finally {
            settings.source = originalSource
        }
    }

    /**
     * Форумный поиск 4pda фильтрует по автору ТОЛЬКО по числовому `username-id`: текстовое `username`
     * сервер игнорирует (проверено — «Вы искали:» пустое, результатов ноль). Поэтому перед запросом по
     * нику, введённому вручную (userId ещё не известен — в отличие от открытия из поста/профиля, где id
     * уже есть из ссылки showuser=), резолвим ник в id через кэш форум-юзеров (+ QMS-autocomplete).
     */
    private suspend fun resolveUserId(settings: SearchSettings) {
        if (settings.resourceType != SearchSettings.RESOURCE_FORUM.first) return
        if (settings.userId > 0) return
        val nick = settings.nick?.trim().orEmpty()
        if (nick.isEmpty()) return
        findUserByNick(nick)?.let { settings.userId = it.id }
    }

    /**
     * Резолв ника форума в пользователя (id) через кэш форум-юзеров + QMS-autocomplete. Возвращает юзера
     * только при ТОЧНОМ совпадении ника: автокомплит отдаёт префиксные совпадения, а вернуть «не того»
     * пользователя хуже, чем ничего. Используется и для фильтра поиска, и для «Открыть профиль по нику».
     */
    suspend fun findUserByNick(nick: String): ForumUser? = withContext(Dispatchers.IO) {
        val trimmed = nick.trim()
        if (trimmed.isEmpty()) return@withContext null
        val resolved = runCatching { forumUsersCache.getUserByNick(trimmed) }.getOrNull()
        resolved?.takeIf { it.id > 0 && it.nick?.equals(trimmed, ignoreCase = true) == true }
    }

    /**
     * Автор-фильтр 4pda работает лишь при поиске по содержанию (`source=pst`): при `source=top`
     * (заголовки) ответ пуст даже с корректным `username-id` (проверено). Поэтому для поиска по юзеру
     * с выбранными «Заголовками» подменяем source на «Содержание» на время запроса.
     */
    private fun shouldForceContentSource(settings: SearchSettings): Boolean =
            settings.resourceType == SearchSettings.RESOURCE_FORUM.first &&
                    settings.userId > 0 &&
                    settings.source == SearchSettings.SOURCE_TITLES.first

    private suspend fun saveUsers(page: SearchResult) {
        val forumUsers = page.items.map { post ->
            ForumUser().apply {
                id = post.userId
                nick = post.nick
                avatar = post.avatar
            }
        }
        forumUsersCache.saveUsers(forumUsers)
    }

    private fun appendForumSectionTitleSuggestions(result: SearchResult, settings: SearchSettings) {
        val suggestions = forumSectionTitleIndex.suggestions(settings, result.items)
        if (suggestions.isEmpty()) return
        result.items.addAll(suggestions)
    }

}
