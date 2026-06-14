package forpdateam.ru.forpda.entity.app.notes

/**
 * Created by radiationx on 06.09.17.
 * Converted to Kotlin.
 */
interface INoteItem {
    var id: Long
    var title: String?
    var link: String?
    var content: String?
    var folderId: Long?
    var createdAt: Long
    var updatedAt: Long
    var sortOrder: Long
}
