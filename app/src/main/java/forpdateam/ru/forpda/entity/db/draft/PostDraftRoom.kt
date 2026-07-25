package forpdateam.ru.forpda.entity.db.draft

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Персистентный черновик ответа в теме (полноэкранный редактор, TYPE_NEW_POST).
 *
 * [key] — стабильный ключ вида "topic:<topicId>". Черновик переживает не только смерть процесса
 * (её закрывает instance state фрагмента), но и полное удаление задачи из recents, где instance
 * state теряется. Хранит точный текст, выделение, режим редактора и метаданные вложений.
 */
@Entity(tableName = "post_draft")
data class PostDraftRoom(
    @PrimaryKey
    val key: String,
    val message: String = "",
    val updatedAt: Long = 0L,
    val selectionStart: Int = -1,
    val selectionEnd: Int = -1,
    val attachmentsJson: String = "[]",
    val editorMode: String = "",
    val attachmentsChanged: Boolean = false,
)
