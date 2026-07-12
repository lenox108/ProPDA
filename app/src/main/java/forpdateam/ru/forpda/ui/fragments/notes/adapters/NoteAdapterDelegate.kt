package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.ui.dp16
import forpdateam.ru.forpda.ui.dp40
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteListItem
import forpdateam.ru.forpda.databinding.ItemNoteBinding

class NoteAdapterDelegate(
        private val clickListener: BaseAdapter.OnItemClickListener<NoteItem>,
        private val manualModeProvider: () -> Boolean = { false },
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : AdapterDelegate<MutableList<ListItem>>() {
    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is NoteListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteHolder(binding, clickListener, manualModeProvider, onStartDrag)
    }

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as NoteListItem
        (holder as NoteHolder).bind(item.item, item.isNested, item.selectionMode, item.isSelected)
    }

    class NoteHolder(
            private val binding: ItemNoteBinding,
            private val clickListener: BaseAdapter.OnItemClickListener<NoteItem>,
            private val manualModeProvider: () -> Boolean,
            private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
    ) : BaseViewHolder<NoteItem>(binding.root) {

        private lateinit var currentItem: NoteItem

        // Дефолтная (не-выделенная) обводка карточки, как её задал item_note.xml:
        // ?attr/list_plate_stroke_color шириной ?attr/list_plate_stroke_width.
        // В светлой/тёмной темах ширина = 0dp (обводки нет, карточка отделяется
        // тоном), в AMOLED = 1dp faint amoled_list_plate_stroke — иначе карточка
        // #000000 на #000000 сливалась с фоном и заметки шли без разделителей.
        // Захватываем до первого bind, чтобы восстанавливать после accent-стейта.
        private val defaultStrokeColors = binding.root.strokeColorStateList
        private val defaultStrokeWidth = binding.root.strokeWidth

        init {
            binding.root.setOnClickListener {
                clickListener.onItemClick(currentItem)
            }
            binding.root.setOnLongClickListener {
                // В ручном режиме долгий тап «берёт» заметку для перетаскивания
                // (drag-and-drop), в остальных — обычный вход в режим выделения.
                if (manualModeProvider()) {
                    onStartDrag(this)
                } else {
                    clickListener.onItemLongClick(currentItem)
                }
                true
            }
        }

        override fun bind(item: NoteItem) {
            bind(item, isNested = false, selectionMode = false, isSelected = false)
        }

        fun bind(item: NoteItem, isNested: Boolean, selectionMode: Boolean, isSelected: Boolean) {
            currentItem = item
            binding.root.updateLayoutParams<RecyclerView.LayoutParams> {
                marginStart = when {
                    selectionMode -> 0
                    isNested -> binding.root.dp40
                    else -> 0
                }
                marginEnd = if (isNested) binding.root.dp16 else 0
            }
            binding.root.isSelected = isSelected
            binding.root.isChecked = isSelected
            // Выделение = акцентная обводка 2dp; иначе — дефолтная faint-обводка
            // из item_note.xml (0dp в light/dark, 1dp в AMOLED). Раньше здесь
            // жёстко ставилось 0dp в невыделенном состоянии, отчего карточки
            // заметок в AMOLED оставались без разделителей.
            if (isSelected) {
                binding.root.setStrokeColor(binding.root.context.getColorFromAttr(R.attr.colorAccent))
                binding.root.strokeWidth = binding.root.resources.getDimensionPixelSize(R.dimen.dp2)
            } else {
                defaultStrokeColors?.let { binding.root.setStrokeColor(it) }
                binding.root.strokeWidth = defaultStrokeWidth
            }
            binding.itemTitle.text = item.title
            val content = item.content
            if (content.isNullOrEmpty()) {
                binding.itemContent.visibility = View.GONE
            } else {
                binding.itemContent.visibility = View.VISIBLE
                binding.itemContent.text = content
            }
            //binding.itemDate.setText(item.getDate());
        }
    }
}