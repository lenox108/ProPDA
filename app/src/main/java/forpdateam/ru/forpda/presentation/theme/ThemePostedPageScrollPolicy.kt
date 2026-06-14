package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Scroll target after the user posts a reply: must not be cleared by unread-open
 * scroll-suppression ([TopicOpenScrollRestorePolicy]).
 */
internal object ThemePostedPageScrollPolicy {
    fun shouldApplyPostedScroll(pendingPostedAnchor: String?): Boolean =
            !pendingPostedAnchor.isNullOrBlank()

  /**
   * @param exactAnchor true после редактирования поста — не подменять якорь «последним на странице».
   */
    fun resolveDomScrollAnchor(
            pendingPostedAnchor: String?,
            page: ThemePage?,
            exactAnchor: Boolean = false
    ): String? {
        if (pendingPostedAnchor.isNullOrBlank()) return null
        if (exactAnchor) return pendingPostedAnchor
        val topicPage = page ?: return pendingPostedAnchor
        val pendingId = pendingPostedAnchor.toIntOrNull() ?: return pendingPostedAnchor
        val parsedPosts = topicPage.posts.filter { it.id > 0 }
        if (parsedPosts.isEmpty()) return pendingPostedAnchor
        val lastId = parsedPosts.maxByOrNull { it.id }!!.id
        val firstId = parsedPosts.minByOrNull { it.id }!!.id
        // После ответа редирект часто даёт первый пост страницы, а новое сообщение — последнее.
        if (parsedPosts.size > 1 && pendingId == firstId && lastId != pendingId) {
            return lastId.toString()
        }
        return pendingPostedAnchor
    }
}
