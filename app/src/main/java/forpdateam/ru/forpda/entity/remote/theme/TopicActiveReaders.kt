package forpdateam.ru.forpda.entity.remote.theme

/**
 * «Сейчас эту тему читают» — снимок активных читателей темы, который сервер печатает внизу страницы
 * темы (полная версия сайта). Данные приходят вместе с обычной страницей, отдельного запроса нет,
 * поэтому значение — это снимок НА МОМЕНТ ЗАГРУЗКИ страницы, а не живой счётчик: он обновляется при
 * открытии темы, переходе по страницам и обновлении.
 *
 * Блок виден только авторизованным пользователям — у гостя его в HTML нет вовсе, и тогда
 * [forpdateam.ru.forpda.model.data.remote.api.theme.TopicActiveUsersParser] отдаёт null.
 */
data class TopicActiveReaders(
        /** Всего читающих: пользователи + гости + скрытые (число из самой фразы, если сервер его дал). */
        val total: Int,
        /** Ники видимых зарегистрированных читателей в порядке из разметки. */
        val members: List<Member> = emptyList(),
        val guests: Int = 0,
        val hidden: Int = 0,
) {
    data class Member(val userId: Int, val nick: String)
}
