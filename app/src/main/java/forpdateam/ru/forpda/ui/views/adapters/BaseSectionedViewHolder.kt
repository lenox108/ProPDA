package forpdateam.ru.forpda.ui.views.adapters

import android.view.View
import com.afollestad.sectionedrecyclerview.SectionedViewHolder

open class BaseSectionedViewHolder<T>(itemView: View) : SectionedViewHolder(itemView) {

    open fun bind(item: T, section: Int, relativePosition: Int, absolutePosition: Int) {
        // Override in subclass
    }

    open fun bind(item: T, section: Int) {
        // Override in subclass
    }

    open fun bind(item: T) {
        // Override in subclass
    }

    open fun bind(section: Int) {
        // Override in subclass
    }

    open fun bind() {
        // Override in subclass
    }
}
