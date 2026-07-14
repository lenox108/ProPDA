package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.setTextSizePx
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder
import forpdateam.ru.forpda.ui.views.drawers.adapters.ListItem
import forpdateam.ru.forpda.ui.views.drawers.adapters.NoteListItem
import forpdateam.ru.forpda.databinding.ItemNoteBinding

class NoteAdapterDelegate(
        private val clickListener: BaseAdapter.OnItemClickListener<NoteItem>,
        private val manualModeProvider: () -> Boolean = { false },
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {},
        // Меню действий закладки (перенос в папку, правка, ссылка, удаление) строит фрагмент —
        // у него под рукой список папок и диалоги.
        private val onMoreClick: (NoteItem, View) -> Unit = { _, _ -> }
) : AdapterDelegate<MutableList<ListItem>>() {
    override fun isForViewType(items: MutableList<ListItem>, position: Int): Boolean = items[position] is NoteListItem

    override fun onCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteHolder(binding, clickListener, manualModeProvider, onStartDrag, onMoreClick)
    }

    override fun onBindViewHolder(items: MutableList<ListItem>, position: Int, holder: RecyclerView.ViewHolder, payloads: MutableList<Any>) {
        val item = items[position] as NoteListItem
        val segment = notePlateSegment(items, position)
        holder as NoteHolder
        // Перестановка при drag меняет у соседей только форму плашки (NotesAdapter.moveItem):
        // перерисовываем фон, не пересобирая строку — иначе ItemTouchHelper дёргает вид.
        if (payloads.contains(NotesAdapter.PAYLOAD_PLATE)) {
            holder.applyPlate(segment)
            return
        }
        holder.bind(item.item, item.isNested, item.selectionMode, item.isSelected, segment)
    }

    class NoteHolder(
            private val binding: ItemNoteBinding,
            private val clickListener: BaseAdapter.OnItemClickListener<NoteItem>,
            private val manualModeProvider: () -> Boolean,
            private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
            private val onMoreClick: (NoteItem, View) -> Unit
    ) : BaseViewHolder<NoteItem>(binding.root) {

        private lateinit var currentItem: NoteItem

        // Цвет выделенной строки = фон плашки, смешанный с акцентом темы. Тот же приём,
        // что в избранном (FavoritesAdapter.selectionRowColor): выводится из палитры,
        // поэтому заметен и гармоничен в любой теме, включая AMOLED.
        private val selectionRowColor = ColorUtils.blendARGB(
                binding.root.context.getColorFromAttr(R.attr.content_card_surface),
                binding.root.context.getColorFromAttr(androidx.appcompat.R.attr.colorAccent),
                0.30f
        )

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
            binding.itemMore.setOnClickListener { anchor ->
                onMoreClick(currentItem, anchor)
            }
        }

        fun applyPlate(segment: ListPlateSegment) {
            binding.root.applyNotePlate(segment)
        }

        override fun bind(item: NoteItem) {
            bind(item, isNested = false, selectionMode = false, isSelected = false, segment = ListPlateSegment.SINGLE)
        }

        fun bind(
                item: NoteItem,
                isNested: Boolean,
                selectionMode: Boolean,
                isSelected: Boolean,
                segment: ListPlateSegment
        ) {
            currentItem = item
            applyPlate(segment)
            // В режиме выбора ⋮ гасим (действуют пакетные операции из тулбара), но именно
            // INVISIBLE, а не GONE: заголовок и дата спозиционированы через toStartOf(item_more),
            // и на GONE-якоре RelativeLayout схлопывал заголовок в нулевую ширину — в выделении
            // от строки оставалась одна дата.
            binding.itemMore.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE

            val density = binding.root.context.currentUiDensityValues()
            val res = binding.root.resources
            // Боковые отступы — общие для списков (плотность интерфейса), вертикальные — свои,
            // более плотные: в строке закладки три уровня (заголовок, превью, дата), и
            // «comfortable»-паддинг раздувал блок. Вложенность рисуем отступом ВНУТРИ плашки:
            // сдвинуть строку марджином нельзя — она перестала бы стыковаться с папкой в один блок.
            val verticalPadding = res.getDimensionPixelSize(R.dimen.note_row_padding_vertical)
            val nestedIndent = if (isNested) res.getDimensionPixelSize(R.dimen.dp32) else 0
            binding.root.setPaddingRelative(
                    density.itemHorizontalPaddingPx + nestedIndent,
                    verticalPadding,
                    density.itemHorizontalPaddingPx,
                    verticalPadding
            )
            binding.itemTitle.setTextSizePx(density.titleTextSizePx)
            binding.itemContent.setTextSizePx(density.subtitleTextSizePx)
            binding.itemDate.setTextSizePx(density.metadataTextSizePx)

            binding.root.isSelected = isSelected
            // Тонируем фон плашки (текст поверх остаётся чётким), ripple-foreground сохраняется.
            // mutate() — чтобы тинт не «протёк» на другие строки через общий ConstantState.
            binding.root.background?.mutate()
            ViewCompat.setBackgroundTintList(
                    binding.root,
                    if (isSelected) ColorStateList.valueOf(selectionRowColor) else null
            )

            binding.itemTitle.text = item.title
            val content = item.content
            if (content.isNullOrEmpty()) {
                binding.itemContent.visibility = View.GONE
            } else {
                binding.itemContent.visibility = View.VISIBLE
                binding.itemContent.text = content
            }

            val createdAt = NoteDateFormatter.format(binding.root.context, item.createdAt)
            if (createdAt == null) {
                binding.itemDate.visibility = View.GONE
            } else {
                binding.itemDate.visibility = View.VISIBLE
                binding.itemDate.text = createdAt
            }
            layoutDate(hasContent = !content.isNullOrEmpty())
        }

        /**
         * Есть превью — дата уходит мета-строкой под него (как дата в строке форума).
         * Превью нет — дата встаёт на базовую линию заголовка справа: иначе она висит
         * отдельной строкой под коротким заголовком и блок читается пустым и рыхлым.
         */
        private fun layoutDate(hasContent: Boolean) {
            val dateParams = binding.itemDate.layoutParams as RelativeLayout.LayoutParams
            val titleParams = binding.itemTitle.layoutParams as RelativeLayout.LayoutParams
            if (hasContent) {
                dateParams.addRule(RelativeLayout.BELOW, R.id.item_content)
                dateParams.removeRule(RelativeLayout.ALIGN_BASELINE)
                // Правый край — по краю превью, то есть ровно перед кнопкой ⋮.
                dateParams.removeRule(RelativeLayout.START_OF)
                dateParams.addRule(RelativeLayout.ALIGN_END, R.id.item_content)
                dateParams.topMargin = binding.root.resources.getDimensionPixelSize(R.dimen.note_row_line_spacing)
                dateParams.marginStart = 0
                titleParams.removeRule(RelativeLayout.START_OF)
                titleParams.addRule(RelativeLayout.START_OF, R.id.item_more)
                titleParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                dateParams.removeRule(RelativeLayout.BELOW)
                dateParams.removeRule(RelativeLayout.ALIGN_END)
                dateParams.addRule(RelativeLayout.ALIGN_BASELINE, R.id.item_title)
                dateParams.addRule(RelativeLayout.START_OF, R.id.item_more)
                dateParams.topMargin = 0
                // Заголовок ужимается до даты — не даём им слипнуться.
                dateParams.marginStart = binding.root.resources.getDimensionPixelSize(R.dimen.dp8)
                titleParams.addRule(RelativeLayout.START_OF, R.id.item_date)
                titleParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            binding.itemDate.layoutParams = dateParams
            binding.itemTitle.layoutParams = titleParams
        }
    }
}
