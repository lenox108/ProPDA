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
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val oldItem = oldList[oldPos]
                val newItem = this@BaseAdapter.items[newPos]
                // For TabFragment, compare by tag which is unique identifier
                return if (oldItem is forpdateam.ru.forpda.ui.fragments.TabFragment && 
                          newItem is forpdateam.ru.forpda.ui.fragments.TabFragment) {
                    oldItem.tag == newItem.tag
                } else {
                    oldItem == newItem
                }
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                // For TabFragment, content changes when title or state changes
                val oldItem = oldList[oldPos]
                val newItem = this@BaseAdapter.items[newPos]
                return if (oldItem is forpdateam.ru.forpda.ui.fragments.TabFragment && 
                          newItem is forpdateam.ru.forpda.ui.fragments.TabFragment) {
                    oldItem.getTabTitle() == newItem.getTabTitle() && oldItem.tag == newItem.tag
                } else {
                    oldItem == newItem
                }
            }
        })
        try {
            diff.dispatchUpdatesTo(this)
        } catch (e: Exception) {
            // Fallback to notifyDataSetChanged if DiffUtil fails
            notifyDataSetChanged()
        }
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
