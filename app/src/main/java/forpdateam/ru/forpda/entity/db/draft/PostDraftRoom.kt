package forpdateam.ru.forpda.entity.db.draft

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Персистентный черновик ответа в теме (полноэкранный редактор, TYPE_NEW_POST).
 *
 * [key] — стабильный ключ вида "topic:<topicId>". Черновик переживает не только смерть процесса
 * (её закрывает instance state фрагмента), но и полное удаление задачи из recents, где instance
 * state теряется. Хранит последнюю редакцию текста; каретка не персистится (при восстановлении
 * ставится в конец).
 */
@Entity(tableName = "post_draft")
data class PostDraftRoom(
    @PrimaryKey
    val key: String,
    val message: String = "",
    val updatedAt: Long = 0L,
)
