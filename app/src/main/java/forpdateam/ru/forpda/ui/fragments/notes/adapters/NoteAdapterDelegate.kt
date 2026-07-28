package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        // Кружок под значком типа — тот же приём, но слабее: подложка должна читаться как
        // мягкий акцентный контейнер, а не как второй элемент управления. Роль
        // colorSecondaryContainer для этого не годится — в части палитр она приравнена к
        // цвету карточки, и круг пропал бы.
        private val typeIconBackColor = ColorUtils.blendARGB(
                binding.root.context.getColorFromAttr(R.attr.content_card_surface),
                binding.root.context.getColorFromAttr(androidx.appcompat.R.attr.colorAccent),
                0.16f
        )

        private val typeIconTint = binding.root.context
                .getColorFromAttr(androidx.appcompat.R.attr.colorAccent)

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
            // INVISIBLE, а не GONE: иначе при входе в выделение текст строки прыгает вправо
            // на ширину кнопки.
            binding.itemMore.visibility = if (selectionMode) View.INVISIBLE else View.VISIBLE

            val density = binding.root.context.currentUiDensityValues()
            val res = binding.root.resources
            // Боковые отступы — общие для списков (плотность интерфейса), вертикальные — свои,
            // более плотные: в строке закладки три уровня (заголовок, превью, дата), и
            // «comfortable»-паддинг раздувал блок. Вложенность рисуем отступом ВНУТРИ плашки:
            // сдвинуть строку марджином нельзя — она перестала бы стыковаться с папкой в один блок.
            val verticalPadding = res.getDimensionPixelSize(R.dimen.note_row_padding_vertical)
            // dp20 + значок (dp36) + зазор (dp12) = 68dp, ровно та же левая граница текста,
            // что у названия папки (chevron 24 + 8 + иконка 24 + 12): вложенные закладки
            // выстраиваются в одну колонку со своей папкой.
            val nestedIndent = if (isNested) res.getDimensionPixelSize(R.dimen.dp20) else 0
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

            val type = NoteLinkType.of(item.link)
            binding.itemTypeIcon.setImageResource(type.iconRes)
            binding.itemTypeIcon.imageTintList = ColorStateList.valueOf(typeIconTint)
            // mutate() по той же причине, что и у фона строки: общий ConstantState иначе
            // разнёс бы тинт по всем кружкам списка.
            binding.itemTypeIcon.background?.mutate()
            ViewCompat.setBackgroundTintList(
                    binding.itemTypeIcon,
                    ColorStateList.valueOf(typeIconBackColor)
            )

            binding.itemTitle.text = NoteLinkType.displayTitle(item.title, type)
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
        }
    }
}
