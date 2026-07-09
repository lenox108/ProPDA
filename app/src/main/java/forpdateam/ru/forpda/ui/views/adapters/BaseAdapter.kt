package forpdateam.ru.forpda.ui.views.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * Базовый адаптер для RecyclerView.
 *
 * Улучшения в Kotlin-версии:
 * - MutableList вместо ArrayList
 * - Функциональный тип для OnItemClickListener
 * - Упрощенные generic-типы
 */
abstract class BaseAdapter<E, VH : BaseViewHolder<E>> : RecyclerView.Adapter<VH>() {

    @JvmField
    protected val items: MutableList<E> = mutableListOf()

    fun setItems(items: List<E>) {
        clear()
        this.items.addAll(items)
    }

    fun addAll(items: Collection<E>) {
        addAll(items, true)
    }

    fun addAll(items: Collection<E>, clearList: Boolean) {
        // Note: This method is not suspend, so mutex cannot be used here directly
        // Adapter updates should be synchronized at the call site
        val oldList = ArrayList(this.items)
        if (clearList) {
            this.items.clear()
        }
        this.items.addAll(items)
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = this@BaseAdapter.items.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
                    areItemsSame(oldList[oldPos], this@BaseAdapter.items[newPos])

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
                    areContentsSame(oldList[oldPos], this@BaseAdapter.items[newPos])
        })
        try {
            diff.dispatchUpdatesTo(this)
        } catch (e: Exception) {
            // Fallback to notifyDataSetChanged if DiffUtil fails
            notifyDataSetChanged()
        }
    }

    /**
     * Stable-identity check for [DiffUtil] («тот же элемент»). Default keeps the historical behaviour:
     * [TabFragment]s match by tag, everything else by reference/`equals`. Subclasses whose items are
     * re-created on each load (e.g. search results) should override to compare by a stable id so DiffUtil
     * can reuse rows and animate instead of treating every reload as a full replace (O(n·m) diff for nothing).
     */
    protected open fun areItemsSame(oldItem: E, newItem: E): Boolean =
            if (oldItem is forpdateam.ru.forpda.ui.fragments.TabFragment &&
                    newItem is forpdateam.ru.forpda.ui.fragments.TabFragment) {
                oldItem.tag == newItem.tag
            } else {
                oldItem == newItem
            }

    /** Content-equality check for [DiffUtil] («содержимое не изменилось» → без ре-байнда). */
    protected open fun areContentsSame(oldItem: E, newItem: E): Boolean =
            if (oldItem is forpdateam.ru.forpda.ui.fragments.TabFragment &&
                    newItem is forpdateam.ru.forpda.ui.fragments.TabFragment) {
                oldItem.getTabTitle() == newItem.getTabTitle() && oldItem.tag == newItem.tag
            } else {
                oldItem == newItem
            }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    fun getItem(position: Int): E = items[position]

    protected fun inflateLayout(parent: ViewGroup, @LayoutRes id: Int): View {
        return LayoutInflater.from(parent.context).inflate(id, parent, false)
    }

    /**
     * Интерфейс для обработки кликов.
     * Содержит оба метода для обратной совместимости с Java кодом.
     */
    interface OnItemClickListener<T> {
        fun onItemClick(item: T)
        fun onItemLongClick(item: T): Boolean = false
    }

    /**
     * Функциональный интерфейс только для обработки кликов (без long click).
     */
    fun interface OnSimpleClickListener<T> {
        fun onItemClick(item: T)
    }

    /**
     * Функциональный интерфейс для обработки долгих кликов.
     */
    fun interface OnItemLongClickListener<T> {
        fun onItemLongClick(item: T): Boolean
    }
}
