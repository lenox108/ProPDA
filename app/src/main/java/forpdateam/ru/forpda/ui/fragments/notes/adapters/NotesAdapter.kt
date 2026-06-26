package forpdateam.ru.forpda.ui.fragments.notes.adapters

import androidx.recyclerview.widget.DiffUtil
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
        private val infoClickListener: (CloseableInfo) -> Unit
) : ListDelegationAdapter<MutableList<ListItem>>() {


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
            addDelegate(NoteAdapterDelegate(noteClickListener))
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
            override fun areItemsTheSame(o: Int, n: Int) = oldList[o] == newItems[n]
            override fun areContentsTheSame(o: Int, n: Int) = oldList[o] == newItems[n]
        }).dispatchUpdatesTo(this)
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