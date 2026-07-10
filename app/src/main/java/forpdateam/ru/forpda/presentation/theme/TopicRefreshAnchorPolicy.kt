package forpdateam.ru.forpda.presentation.theme

/**
 * Куда сажать якорь после обновления темы жестом «снизу вверх».
 *
 * Жест взводится только у истинного низа списка, поэтому на момент обновления пользователь видел все
 * загруженные посты. Если перезагруженная страница принесла посты новее последнего виденного —
 * пользователь их ещё не читал, и садиться надо на ПЕРВЫЙ из них, а не на низ (иначе новые посты
 * уезжают вверх непрочитанными, а [NativeTopicFragment.markVisiblePostsRead] ещё и пометит их
 * прочитанными). Новых постов нет → обычное «вернуться на низ».
 *
 * id постов на 4PDA глобально возрастают (больше id = позже опубликован).
 */
object TopicRefreshAnchorPolicy {

    /**
     * @param reloadedPostIds id постов свежезагруженной страницы, в порядке отображения.
     * @param seenUpToPostId наибольший id поста, который пользователь видел до обновления (0 = неизвестно).
     * @return id первого не-виденного поста, либо null — если новых постов нет (садиться на низ).
     */
    fun firstUnseenPostId(reloadedPostIds: List<Int>, seenUpToPostId: Int): Int? {
        if (seenUpToPostId <= 0) return null
        return reloadedPostIds.firstOrNull { it > seenUpToPostId }
    }
}
