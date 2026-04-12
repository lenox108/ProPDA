package forpdateam.ru.forpda.model.repository.profile

import forpdateam.ru.forpda.entity.EntityWrapper
import forpdateam.ru.forpda.entity.app.profile.IUserHolder
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.cache.forumuser.ForumUsersCache
import forpdateam.ru.forpda.model.data.remote.api.profile.ProfileApi
import forpdateam.ru.forpda.model.repository.BaseRepository
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Created by radiationx on 02.01.18.
 */
class ProfileRepository(
        private val schedulers: SchedulersProvider,
        private val profileApi: ProfileApi,
        private val userHolder: IUserHolder,
        private val authHolder: AuthHolder,
        private val forumUsersCache: ForumUsersCache
) : BaseRepository(schedulers) {

    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()

    fun observeCurrentUser(): Flow<EntityWrapper<ProfileModel?>> = userHolder.observeCurrentUser()

    suspend fun loadSelf(): ProfileModel =
            loadProfile("https://4pda.to/forum/index.php?showuser=" + authHolder.get().userId)

    suspend fun loadProfile(url: String): ProfileModel = withContext(ioDispatcher) {
        Single.fromCallable { profileApi.getProfile(url) }
                .withNetworkTimeout(30)
                .withNetworkRetry()
                .await()
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
            withContext(ioDispatcher) { profileApi.saveNote(note) }
}
