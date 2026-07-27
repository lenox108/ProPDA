package forpdateam.ru.forpda.model.data.cache.forumuser

import forpdateam.ru.forpda.entity.remote.others.user.ForumUser

interface UserSource {
    /**
     * @param background true для спекулятивных запросов (догрузка аватарок ленты): такие уступают
     * дорогу пользовательским и молчат, пока действует ограничение 4pda после 429.
     */
    fun getUsers(nick: String, background: Boolean = false): List<ForumUser>
}