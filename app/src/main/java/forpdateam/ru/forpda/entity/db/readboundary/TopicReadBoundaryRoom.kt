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
 *
 * [maxLoadedPostId] / [maxLoadedPage] — самый дальний пост/страница, которые ЭТО устройство хотя бы
 * ЗАГРУЖАЛО с сервера (включая предзагрузку гибридным скроллом, даже если юзер туда не долистал).
 * Тоже монотонны. Нужны, чтобы отличить серверный walk-down ОТ прогресса, сделанного на ДРУГОМ
 * устройстве/в браузере: 4PDA метит прочитанной всю загруженную страницу, поэтому серверный якорь
 * может уехать максимум на ОДНУ страницу дальше самого дальнего, что грузило это устройство. Если
 * серверный якорь ушёл дальше — прогресс внешний, локальная граница устарела (см.
 * [forpdateam.ru.forpda.presentation.theme.TopicReadBoundaryPolicy.isCrossDeviceReadProgress]).
 */
@Entity(tableName = "topic_read_boundary")
data class TopicReadBoundaryRoom(
    @PrimaryKey
    val topicId: Int = 0,
    val lastSeenPostId: Int = 0,
    val lastSeenPage: Int = 0,
    val updatedAt: Long = 0L,
    val maxLoadedPostId: Int = 0,
    val maxLoadedPage: Int = 0,
)
