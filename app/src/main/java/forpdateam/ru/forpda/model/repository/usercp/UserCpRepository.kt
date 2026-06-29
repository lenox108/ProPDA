package forpdateam.ru.forpda.model.repository.usercp

import forpdateam.ru.forpda.entity.remote.usercp.ForumSettings
import forpdateam.ru.forpda.model.data.remote.api.usercp.UserCpApi
import forpdateam.ru.forpda.model.preferences.ForumPageSizeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Репозиторий раздела «Настройки форума». Сетевые вызовы уводятся на IO с таймаутом,
 * как и в остальных репозиториях (см. ProfileRepository).
 *
 * Помимо чтения/записи настроек, кэширует `postpage` в [ForumPageSizeHolder], чтобы
 * счётчики «N стр.» в списках (избранное, темы форума) считались по реальному размеру
 * страницы пользователя.
 */
class UserCpRepository(
        private val userCpApi: UserCpApi,
        private val pageSizeHolder: ForumPageSizeHolder
) {

    suspend fun load(): ForumSettings = withContext(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) { userCpApi.loadSettings() }.also(::cachePostsPerPage)
    }

    suspend fun save(settings: ForumSettings): ForumSettings = withContext(Dispatchers.IO) {
        withTimeout(TIMEOUT_MS) { userCpApi.saveSettings(settings) }.also(::cachePostsPerPage)
    }

    private fun cachePostsPerPage(settings: ForumSettings) {
        settings.postPage.toIntOrNull()?.let { pageSizeHolder.setPostsPerPage(it) }
    }

    companion object {
        private const val TIMEOUT_MS = 30_000L
    }
}
