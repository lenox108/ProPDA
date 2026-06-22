package forpdateam.ru.forpda.entity.db.offline

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 1 data layer for the offline-reading feature (§5.1 of
 * REFACTOR_PLAN.md).
 *
 * One row per saved article/theme. HTML payload and image assets
 * are intentionally NOT stored in Room — they live on the
 * filesystem under [forpdateam.ru.forpda.model.data.offline.OfflineStorage]
 * and the row only carries the path and a serialized model.
 */
@Entity(
        tableName = "offline_items",
        indices = [Index(value = ["savedAtMs"], orders = [Index.Order.DESC])]
)
data class OfflineItemRoom(
        @PrimaryKey
        val id: String,                 // "article:<id>" / "theme:<topicId>:<page>"
        val type: String,               // OfflineItemType.name
        val sourceUrl: String,
        val title: String,
        val savedAtMs: Long,
        val sizeBytes: Long,
        val status: String,             // OfflineItemStatus.name
        val htmlPath: String,           // relative path under OfflineStorage.ROOT_DIR
        val modelJson: String           // serialized model (kotlinx.serialization)
)

object OfflineItemType {
    const val ARTICLE = "ARTICLE"
    const val THEME = "THEME"
}

object OfflineItemStatus {
    /** Saved in PARTIAL state when record is created; image downloads still pending. */
    const val PARTIAL = "PARTIAL"

    /** HTML + model + all referenced images have been persisted to disk. */
    const val COMPLETE = "COMPLETE"

    /** Save attempt failed; record is kept so the user can retry. */
    const val FAILED = "FAILED"
}
