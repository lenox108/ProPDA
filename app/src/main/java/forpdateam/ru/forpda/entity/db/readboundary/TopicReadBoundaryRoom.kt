package forpdateam.ru.forpda.entity.db.readboundary

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Персистентная клиентская «граница прочитанного» на тему (модель Discourse, адаптированная
 * под 4PDA-бэкенд, который per-post read tracking не умеет — серверный `view=getnewpost` метит
 * страницу прочитанной по факту загрузки и уползает вниз, см. заметку про walk-down).
 *
 * [lastSeenPostId] — самый НОВЫЙ (наибольший id) пост, который РЕАЛЬНО побывал во вьюпорте у
 * пользователя. Монотонно растёт (никогда не уменьшается). Используется при открытии темы, чтобы
 * не садиться НИЖЕ реально прочитанного, когда серверный getnewpost уже увёл якорь дальше.
 */
@Entity(tableName = "topic_read_boundary")
data class TopicReadBoundaryRoom(
    @PrimaryKey
    val topicId: Int = 0,
    val lastSeenPostId: Int = 0,
    val lastSeenPage: Int = 0,
    val updatedAt: Long = 0L,
)
