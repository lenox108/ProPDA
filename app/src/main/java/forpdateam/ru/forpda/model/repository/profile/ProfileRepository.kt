package forpdateam.ru.forpda.model.repository.profile

import forpdateam.ru.forpda.entity.EntityWrapper
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCacheRoom
import forpdateam.ru.forpda.model.data.remote.api.profile.ProfileApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Created by radiationx on 02.01.18.
 */
class ProfileRepository(
        private val profileApi: ProfileApi,
        private val userHolder: IUserHolder,
        private val authHolder: AuthHolder,
        private val forumUsersCache: ForumUsersCacheRoom
) {

    fun observeCurrentUser(): Flow<EntityWrapper<ProfileModel?>> = userHolder.observeCurrentUser()

    suspend fun loadSelf(): ProfileModel =
            loadProfile("https://4pda.to/forum/index.php?showuser=" + authHolder.get().userId)

    suspend fun loadProfile(url: String): ProfileModel = withContext(Dispatchers.IO) {
        withTimeout(30_000L) {
            profileApi.getProfile(url)
        }
    }.also { profile ->
        if (profile.id == authHolder.get().userId) {
            userHolder.user = profile
        }
        forumUsersCache.saveUser(ForumUser().apply {
            id = profile.id
            nick = profile.nick
            avatar = profile.avatar
        })
    }

    suspend fun saveNote(note: String): Boolean =
            withContext(Dispatchers.IO) { profileApi.saveNote(note) }
}
