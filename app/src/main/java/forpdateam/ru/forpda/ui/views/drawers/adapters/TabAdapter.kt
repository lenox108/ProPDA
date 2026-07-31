package forpdateam.ru.forpda.ui.views.drawers.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.DrawerTabItemBinding
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.listPlateSegment

/**
 * Строка списка открытых вкладок. Неизменяемый снимок: фрагмент как источник данных не годится
 * для [DiffUtil] — в списке лежат ТЕ ЖЕ экземпляры фрагментов, поэтому сравнение «старого» и
 * «нового» элемента всегда давало равенство и заголовок не обновлялся бы без notifyDataSetChanged.
 */
data class TabRowItem(
        val tag: String,
        val title: String,
        val subtitle: String?,
        @DrawableRes val iconRes: Int,
        val isActive: Boolean,
        val isPinned: Boolean = false,
        /** Уровень вложенности в дереве переходов; учитывается только при [showTree]. */
        val depth: Int = 0,
        val showTree: Boolean = false,
)

/**
 * Адаптер для вкладок в drawer.
 *
 * Обычный режим: тап — перейти на вкладку, свайп вбок — закрыть, крестик — закрыть,
 * долгое нажатие — меню вкладки.
 *
 * Режим сортировки ([reorderMode], пункт меню «Переместить»): вместо крестика — ручка, строка
 * тащится с касания по ручке. Именно с касания, а не по долгому нажатию: список лежит внутри
 * нижнего листа (BottomSheetBehavior), и на удержании лист успевает увести вертикальный жест
 * себе — вместо строки уезжала вся шторка. Старт по ACTION_DOWN гонку убирает: [ItemTouchHelper]
 * забирает жест сразу и сам запрещает перехват выше по иерархии.
 */
class TabAdapter(
        private val listener: Listener
) : RecyclerView.Adapter<TabAdapter.TabHolder>() {

    interface Listener {
        fun onTabClick(tag: String)
        fun onTabClose(tag: String)
        fun onTabMenu(tag: String, anchor: View)
        fun onTabDragStart(holder: RecyclerView.ViewHolder)
    }

    private val items = mutableListOf<TabRowItem>()

    var reorderMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, items.size)
        }

    fun submitRows(rows: List<TabRowItem>) {
        val old = ArrayList(items)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = rows.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].tag == rows[newPos].tag
            override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == rows[newPos]
        })
        val orderChanged = old.map { it.tag } != rows.map { it.tag }
        items.clear()
        items.addAll(rows)
        diff.dispatchUpdatesTo(this)
        // Строки могли переехать (закрепление, закрытие) — DiffUtil двигает их без ре-байнда,
        // а форма плашки зависит от позиции.
        if (orderChanged) refreshRowPlates()
    }

    fun getItem(position: Int): TabRowItem? = items.getOrNull(position)

    fun currentRows(): List<TabRowItem> = items.toList()

    /** Перестановка строк во время перетаскивания; порядок в модели фиксируется по отпусканию. */
    fun moveRow(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    /**
     * Пересчёт скруглений плашки (первая/средняя/последняя строка) после перестановки:
     * [notifyItemMoved] строки не перепривязывает, а форма зависит от позиции — иначе список
     * распадается на отдельные скруглённые карточки.
     */
    fun refreshRowPlates() {
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabHolder {
        val binding = DrawerTabItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabHolder(binding)
    }

    override fun onBindViewHolder(holder: TabHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class TabHolder(private val binding: DrawerTabItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var currentItem: TabRowItem? = null

        @SuppressLint("ClickableViewAccessibility")
        private fun bindGestures() {
            binding.root.setOnClickListener {
                if (reorderMode) return@setOnClickListener
                currentItem?.also { listener.onTabClick(it.tag) }
            }
            binding.root.setOnLongClickListener { view ->
                if (reorderMode) return@setOnLongClickListener false
                val item = currentItem ?: return@setOnLongClickListener false
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                listener.onTabMenu(item.tag, view)
                true
            }
            binding.drawerItemClose.setOnClickListener {
                currentItem?.also { listener.onTabClose(it.tag) }
            }
            binding.drawerItemDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    listener.onTabDragStart(this@TabHolder)
                }
                false
            }
        }

        init {
            bindGestures()
        }

        fun bind(item: TabRowItem, position: Int) {
            currentItem = item
            // Строка могла приехать из переработки после свайпа/перетаскивания: сбрасываем следы жеста,
            // иначе переиспользованная вью останется полупрозрачной и сдвинутой.
            binding.root.alpha = 1f
            binding.root.translationX = 0f

            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val segment = listPlateSegment(position > 0, position < itemCount - 1)
            binding.root.applyListRowPlate(
                    segment,
                    inset,
                    gapBeforeGroupPx = 0,
                    gapAfterGroupPx = 0,
                    ensureSelectableForeground = true,
            )

            val ctx = binding.root.context
            val onSurface = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
            val onSurfaceVariant = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
            // Активная вкладка выделяется акцент-тонированной плашкой + акцентным текстом — одного цветного
            // текста было мало, чтобы заметить «где я сейчас». Акцент с alpha читается в любой теме (в т.ч.
            // AMOLED, где secondaryContainer почти чёрный). Неактивные — обычный ряд.
            if (item.isActive) {
                val accent = ctx.getColorFromAttr(com.google.android.material.R.attr.colorSecondary)
                binding.root.backgroundTintList = ColorStateList.valueOf(
                        ColorUtils.setAlphaComponent(accent, 0x33)) // ~20%
                binding.drawerItemTitle.setTextColor(accent)
                binding.drawerItemTitle.typeface = Typeface.DEFAULT_BOLD
                binding.drawerItemIcon.imageTintList = ColorStateList.valueOf(accent)
            } else {
                binding.root.backgroundTintList = null
                binding.drawerItemTitle.setTextColor(onSurface)
                binding.drawerItemTitle.typeface = Typeface.DEFAULT
                binding.drawerItemIcon.imageTintList = ColorStateList.valueOf(onSurfaceVariant)
            }

            binding.drawerItemTitle.text = item.title
            binding.drawerItemSubtitle.apply {
                text = item.subtitle.orEmpty()
                visibility = if (item.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            binding.drawerItemIcon.setImageResource(item.iconRes)
            binding.drawerItemPin.visibility = if (item.isPinned) View.VISIBLE else View.GONE

            // В режиме сортировки крестик уступает место ручке: закрывать вкладки на ходу
            // всё равно нельзя (порядок строк меняется под пальцем).
            binding.drawerItemClose.visibility = if (reorderMode) View.GONE else View.VISIBLE
            binding.drawerItemDrag.visibility = if (reorderMode) View.VISIBLE else View.GONE

            val indentStep = res.getDimensionPixelSize(R.dimen.dp16)
            val indent = if (item.showTree) indentStep * item.depth.coerceAtMost(MAX_TREE_DEPTH) else 0
            binding.drawerItemIndent.updateLayoutParams { width = indent }
            binding.drawerItemTreeLine.visibility =
                    if (item.showTree && item.depth > 0) View.VISIBLE else View.GONE
        }
    }

    private companion object {
        /** Глубже отступ не растёт — иначе на длинной цепочке заголовку не остаётся ширины. */
        const val MAX_TREE_DEPTH = 4
    }
}
