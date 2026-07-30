package forpdateam.ru.forpda.entity.remote.theme

/**
 * Звено пути темы по разделам форума (см. [forpdateam.ru.forpda.model.data.remote.api.theme.TopicForumPathParser]).
 *
 * @param title название раздела, как его показывает форум.
 * @param forumId id раздела; 0 — корень форума (список разделов), а не конкретный раздел.
 */
data class TopicForumPathItem(
        val title: String,
        val forumId: Int,
) {
    val isForumRoot: Boolean get() = forumId <= 0
}
