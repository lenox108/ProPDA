package forpdateam.ru.forpda.ui.views.messagepanel.attachments

import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.model.data.remote.api.RequestFile
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for attachment content and selection.
 *
 * EditPost has compact and fullscreen presentations of the same editor. Both popups subscribe to
 * this store instead of copying two mutable lists back and forth.
 */
class AttachmentStateStore {
    data class Change(
        val item: AttachmentItem? = null,
        val contentChanged: Boolean,
    )

    private val items = mutableListOf<AttachmentItem>()
    private val listeners = linkedSetOf<(Change) -> Unit>()
    private val sourceFiles = IdentityHashMap<AttachmentItem, RequestFile>()
    private val localIds = IdentityHashMap<AttachmentItem, Long>()
    private val nextLocalId = AtomicLong(1L)

    fun items(): List<AttachmentItem> = items.toList()

    fun selectedItems(): List<AttachmentItem> = items.filter(AttachmentItem::selected)

    fun rowId(item: AttachmentItem): Long =
        if (item.id > 0) {
            item.id.toLong()
        } else {
            -localIds.getOrPut(item) { nextLocalId.getAndIncrement() }
        }

    fun add(item: AttachmentItem, source: RequestFile? = null) {
        if (item in items) return
        item.selected = false
        items += item
        source?.let {
            sourceFiles[item] = it
            copySourceMetadata(item, it)
        }
        notify(Change(contentChanged = true))
    }

    fun addAll(newItems: List<AttachmentItem>) {
        if (newItems.isEmpty()) return
        newItems.forEach { item ->
            if (item !in items) {
                item.selected = false
                items += item
            }
        }
        notify(Change(contentChanged = true))
    }

    fun replace(newItems: List<AttachmentItem>) {
        val snapshot = ArrayList(newItems)
        val retainedSources = IdentityHashMap<AttachmentItem, RequestFile>()
        snapshot.forEach { item -> sourceFiles[item]?.let { retainedSources[item] = it } }
        items.forEach { it.selected = false }
        items.clear()
        sourceFiles.clear()
        localIds.clear()
        snapshot.forEach { item ->
            item.selected = false
            items += item
            retainedSources[item]?.let { sourceFiles[item] = it }
        }
        notify(Change(contentChanged = true))
    }

    fun clear() {
        if (items.isEmpty() && sourceFiles.isEmpty()) return
        items.forEach {
            it.selected = false
            it.progressListener = null
        }
        items.clear()
        sourceFiles.clear()
        localIds.clear()
        notify(Change(contentChanged = true))
    }

    fun remove(item: AttachmentItem): Boolean {
        if (!items.remove(item)) return false
        item.selected = false
        item.progressListener = null
        sourceFiles.remove(item)
        localIds.remove(item)
        notify(Change(contentChanged = true))
        return true
    }

    fun removeAll(removed: Collection<AttachmentItem>): Boolean {
        if (removed.isEmpty()) return false
        val identities = removed.toSet()
        val matched = items.filter { it in identities }
        if (matched.isEmpty()) return false
        matched.forEach { item ->
            item.selected = false
            item.progressListener = null
            sourceFiles.remove(item)
            localIds.remove(item)
        }
        items.removeAll(identities)
        notify(Change(contentChanged = true))
        return true
    }

    fun toggleSelection(item: AttachmentItem) {
        if (item !in items) return
        item.toggle()
        notify(Change(item = item, contentChanged = false))
    }

    fun selectOnly(item: AttachmentItem) {
        if (item !in items) return
        items.forEach { it.selected = it === item }
        notify(Change(contentChanged = false))
    }

    fun clearSelection() {
        if (items.none(AttachmentItem::selected)) return
        items.forEach { it.selected = false }
        notify(Change(contentChanged = false))
    }

    fun itemChanged(item: AttachmentItem, contentChanged: Boolean = true) {
        if (item !in items) return
        notify(Change(item = item, contentChanged = contentChanged))
    }

    fun setSource(item: AttachmentItem, source: RequestFile) {
        if (item !in items) return
        sourceFiles[item] = source
        copySourceMetadata(item, source)
    }

    fun sourceFor(item: AttachmentItem): RequestFile? = sourceFiles[item]

    fun removeSource(item: AttachmentItem) {
        sourceFiles.remove(item)
    }

    fun addListener(listener: (Change) -> Unit) {
        listeners += listener
        listener(Change(contentChanged = false))
    }

    fun removeListener(listener: (Change) -> Unit) {
        listeners -= listener
    }

    private fun notify(change: Change) {
        listeners.toList().forEach { it(change) }
    }

    private fun copySourceMetadata(item: AttachmentItem, file: RequestFile) {
        item.sourceUri = file.sourceUri
        item.sourceMimeType = file.mimeType
        item.sourceFileSize = file.fileSize
    }
}
