package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemNoteFolderBinding
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteFolderListItem

class NoteFolderAdapterDelegate(
        private val listener: Listener
) : AdapterDelegate<MutableList<ListItem>>() {

    interface Listener {
        fun onFolderClick(folder: NoteFolder)
        fun onFolderRename(folder: NoteFolder)
        fun onFolderDelete(folder: NoteFolder)
    }

    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean =
            items[position] is NoteFolderListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemNoteFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, listener)
    }

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as NoteFolderListItem
        (holder as ViewHolder).bind(item)
    }

    private class ViewHolder(
            private val binding: ItemNoteFolderBinding,
            private val listener: Listener
    ) : RecyclerView.ViewHolder(binding.root) {

        private lateinit var currentItem: NoteFolderListItem

        init {
            binding.root.setOnClickListener {
                listener.onFolderClick(currentItem.folder)
            }
            binding.itemMore.setOnClickListener {
                showMenu(it)
            }
        }

        fun bind(item: NoteFolderListItem) {
            currentItem = item
            binding.itemTitle.text = item.folder.name
            binding.itemCount.text = binding.root.resources.getQuantityString(
                    R.plurals.note_bookmarks_count,
                    item.notesCount,
                    item.notesCount
            )
            binding.itemChevron.rotation = if (item.isExpanded) 90f else 0f
        }

        private fun showMenu(anchor: View) {
            PopupMenu(anchor.context, anchor).apply {
                menu.add(R.string.note_rename_folder).setOnMenuItemClickListener {
                    listener.onFolderRename(currentItem.folder)
                    true
                }
                menu.add(R.string.note_delete_folder).setOnMenuItemClickListener {
                    listener.onFolderDelete(currentItem.folder)
                    true
                }
            }.show()
        }
    }
}
