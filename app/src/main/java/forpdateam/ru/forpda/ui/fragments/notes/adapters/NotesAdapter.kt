package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.ui.fragments.other.CloseableInfoDelegate
import forpdateam.ru.forpda.ui.fragments.other.DividerShadowItemDelegate
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.drawers.adapters.*
import java.util.*

class NotesAdapter(
        private val noteClickListener: BaseAdapter.OnItemClickListener<NoteItem>,
        private val folderListener: NoteFolderAdapterDelegate.Listener,
        private val infoClickListener: (CloseableInfo) -> Unit,
        private val manualModeProvider: () -> Boolean = { false },
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
        private val onMoreClick: (NoteItem, View) -> Unit = { _, _ -> }
) : ListDelegationAdapter<MutableList<ListItem>>() {

    companion object {
        /** Перерисовать только фон-плашку строки (форма зависит от соседей), не пересобирая её. */
        const val PAYLOAD_PLATE = "plate"
    }

    /**
     * The base [ListDelegationAdapter.items] is declared nullable, but we always
     * initialise it in [init] and never null it out, so this accessor documents the
     * invariant and avoids scattering `!!` across the adapter.
     */
    private val itemList: MutableList<ListItem>
        get() = requireNotNull(items) { "NotesAdapter items not initialised" }

    init {
        items = mutableListOf()
        delegatesManager.apply {
            addDelegate(DividerShadowItemDelegate())
            addDelegate(NoteSectionHeaderDelegate())
            addDelegate(NoteFolderAdapterDelegate(folderListener))
            addDelegate(NoteAdapterDelegate(noteClickListener, manualModeProvider, onStartDrag, onMoreClick))
            addDelegate(CloseableInfoDelegate(infoClickListener))
        }
    }

    fun bindItems(
            notes: List<NoteItem>,
            folders: List<NoteFolder>,
            expandedFolderIds: Set<Long>,
            includeAllFolders: Boolean,
            selectedFolderId: Long?,
            selectionMode: Boolean,
            selectedNoteIds: Set<Long>,
            infoList: List<CloseableInfo>,
            foldersTitle: String,
            withoutFolderTitle: String
    ) {
        val oldList = ArrayList(itemList)
        val newItems = itemList
        newItems.clear()
        newItems.addAll(infoList.map { CloseableInfoListItem(it) })
        newItems.addAll(
                buildNoteTreeItems(
                        notes = notes,
                        folders = folders,
                        expandedFolderIds = expandedFolderIds,
                        includeAllFolders = includeAllFolders,
                        selectedFolderId = selectedFolderId,
                        selectionMode = selectionMode,
                        selectedNoteIds = selectedNoteIds,
                        foldersTitle = foldersTitle,
                        withoutFolderTitle = withoutFolderTitle
                )
        )
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newItems.size
            // Identity по СТАБИЛЬНОМУ ключу (id заметки/папки/заголовку). Раньше здесь было
            // полное равенство data class, из-за чего смена isSelected делала элемент «другим»
            // → DiffUtil играл remove+insert → карточки мигали при выделении. Теперь смена
            // выделения — это change (areContentsTheSame=false) без remove/insert.
            override fun areItemsTheSame(o: Int, n: Int) = sameIdentity(oldList[o], newItems[n])
            override fun areContentsTheSame(o: Int, n: Int) = oldList[o] == newItems[n]
        }).dispatchUpdatesTo(this)
    }

    private fun sameIdentity(a: ListItem, b: ListItem): Boolean = when {
        a is NoteListItem && b is NoteListItem -> a.item.id == b.item.id
        a is NoteFolderListItem && b is NoteFolderListItem -> a.folder.id == b.folder.id
        a is NoteSectionHeaderListItem && b is NoteSectionHeaderListItem -> a.title == b.title
        a is CloseableInfoListItem && b is CloseableInfoListItem -> a.item.id == b.item.id
        else -> a::class == b::class
    }

    /** Визуальная перестановка при drag (без записи в БД — она на drop). */
    fun moveItem(from: Int, to: Int) {
        if (from == to) return
        val list = itemList
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        notifyItemMoved(from, to)
        // Углы плашки у первой/последней строки группы зависят от соседей, а notifyItemMoved
        // соседей не перебиндит — освежаем их фон payload'ом (содержимое не трогаем, чтобы
        // не сбить активный drag).
        val start = minOf(from, to)
        val count = maxOf(from, to) - start + 1
        notifyItemRangeChanged(start, count, PAYLOAD_PLATE)
    }

    fun itemAt(position: Int): ListItem? = itemList.getOrNull(position)

    /**
     * id заметок, делящих папку с заметкой на позиции [position], в текущем визуальном
     * порядке. Используется на drop для сохранения ручного порядка одной папки.
     */
    fun noteIdsInSameFolder(position: Int): List<Long> {
        val target = itemList.getOrNull(position) as? NoteListItem ?: return emptyList()
        val folderId = target.item.folderId
        return itemList.filterIsInstance<NoteListItem>()
                .filter { it.item.folderId == folderId }
                .map { it.item.id }
    }

    private fun buildNoteTreeItems(
            notes: List<NoteItem>,
            folders: List<NoteFolder>,
            expandedFolderIds: Set<Long>,
            includeAllFolders: Boolean,
            selectedFolderId: Long?,
            selectionMode: Boolean,
            selectedNoteIds: Set<Long>,
            foldersTitle: String,
            withoutFolderTitle: String
    ): List<ListItem> {
        val result = mutableListOf<ListItem>()
        val notesByFolder = notes.groupBy { it.folderId }

        if (includeAllFolders) {
            if (folders.isNotEmpty()) {
                result += NoteSectionHeaderListItem(foldersTitle)
                folders.forEach { folder ->
                    val folderNotes = notesByFolder[folder.id].orEmpty()
                    val isExpanded = expandedFolderIds.contains(folder.id)
                    result += NoteFolderListItem(folder, folderNotes.size, isExpanded)
                    if (isExpanded) {
                        result += folderNotes.map {
                            NoteListItem(
                                    item = it,
                                    isNested = true,
                                    selectionMode = selectionMode,
                                    isSelected = selectedNoteIds.contains(it.id)
                            )
                        }
                    }
                }
            }
            val notesWithoutFolder = notesByFolder[null].orEmpty()
            if (notesWithoutFolder.isNotEmpty()) {
                result += NoteSectionHeaderListItem(withoutFolderTitle)
                result += notesWithoutFolder.map {
                    NoteListItem(
                            item = it,
                            selectionMode = selectionMode,
                            isSelected = selectedNoteIds.contains(it.id)
                    )
                }
            }
            return result
        }

        if (selectedFolderId == null) {
            if (notes.isNotEmpty()) {
                result += NoteSectionHeaderListItem(withoutFolderTitle)
                result += notes.map {
                    NoteListItem(
                            item = it,
                            selectionMode = selectionMode,
                            isSelected = selectedNoteIds.contains(it.id)
                    )
                }
            }
            return result
        }

        val selectedFolder = folders.firstOrNull { it.id == selectedFolderId }
        if (selectedFolder != null) {
            val isExpanded = expandedFolderIds.contains(selectedFolder.id)
            result += NoteSectionHeaderListItem(foldersTitle)
            result += NoteFolderListItem(selectedFolder, notes.size, isExpanded = isExpanded)
            if (isExpanded) {
                result += notes.map {
                    NoteListItem(
                            item = it,
                            isNested = true,
                            selectionMode = selectionMode,
                            isSelected = selectedNoteIds.contains(it.id)
                    )
                }
            }
        } else {
            result += notes.map {
                NoteListItem(
                        item = it,
                        selectionMode = selectionMode,
                        isSelected = selectedNoteIds.contains(it.id)
                )
            }
        }
        return result
    }
}