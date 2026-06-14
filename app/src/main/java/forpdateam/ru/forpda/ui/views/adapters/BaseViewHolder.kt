package forpdateam.ru.forpda.ui.views.adapters

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Базовый ViewHolder для RecyclerView.
 * 
 * Улучшения в Kotlin-версии:
 * - open методы для переопределения
 * - itemView доступен как свойство
 */
open class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {

    /**
     * Привязка данных с указанием секции (для sectioned адаптеров).
     */
    open fun bind(item: T, section: Int) {}

    /**
     * Привязка данных элемента.
     */
    open fun bind(item: T) {}

    /**
     * Привязка по позиции.
     */
    open fun bind(position: Int) {}

    /**
     * Привязка без параметров.
     */
    open fun bind() {}
}
