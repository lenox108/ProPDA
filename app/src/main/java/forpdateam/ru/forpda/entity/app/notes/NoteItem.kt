package forpdateam.ru.forpda.entity.app.notes

/**
 * Created by radiationx on 06.09.17.
 */
data class NoteItem(
    override var id: Long = 0,
    override var title: String? = null,
    override var link: String? = null,
    override var content: String? = null,
    override var folderId: Long? = null,
    override var createdAt: Long = 0,
    override var updatedAt: Long = 0,
    override var sortOrder: Long = 0
) : INoteItem {
    constructor(item: INoteItem) : this(
        id = item.id,
        title = item.title,
        link = item.link,
        content = item.content,
        folderId = item.folderId,
        createdAt = item.createdAt,
        updatedAt = item.updatedAt,
        sortOrder = item.sortOrder
    )
}
