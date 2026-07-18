package forpdateam.ru.forpda.entity.app.profile

import forpdateam.ru.forpda.entity.EntityWrapper
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import kotlinx.coroutines.flow.Flow

interface IUserHolder {
    var user: ProfileModel?

    fun observeCurrentUser(): Flow<EntityWrapper<ProfileModel?>>

    /**
     * Ник текущего пользователя без полного парса профиля (без разбора HTML
     * подписи/«о себе»). Дёшево вызывать часто — для проверки блоклиста на каждый
     * сетевой запрос. null, если профиль ещё не загружен.
     */
    fun currentNick(): String?
}
