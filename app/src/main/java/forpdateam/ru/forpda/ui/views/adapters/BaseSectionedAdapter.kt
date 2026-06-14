package forpdateam.ru.forpda.ui.views.adapters

import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.sectionedrecyclerview.SectionedRecyclerViewAdapter

abstract class BaseSectionedAdapter<E, VH : BaseSectionedViewHolder<E>> : SectionedRecyclerViewAdapter<VH>() {

    companion object {
        private const val STABLE_ID_STRUCTURAL_BASE = Long.MIN_VALUE
        private const val STABLE_ID_KIND_SHIFT = 56
        private const val STABLE_ID_SECTION_SHIFT = 24
        private const val STABLE_ID_KIND_HEADER = 0L
        private const val STABLE_ID_KIND_FOOTER = 1L
        private const val STABLE_ID_KIND_ITEM_FALLBACK = 2L
    }

    val sections: MutableList<Pair<String, List<E>>> = mutableListOf()
    var itemClickListener: OnItemClickListener<E>? = null

    fun addSection(title: String, items: List<E>) {
        sections.add(Pair(title, items.toList()))
    }

    fun addSection(item: Pair<String, List<E>>) {
        sections.add(Pair(item.first, item.second.toList()))
    }

    fun clear() {
        sections.clear()
        notifyDataSetChanged()
    }

    /**
     * Submit a new list of sections. Performs structural diff to avoid full
     * [notifyDataSetChanged] when only content inside unchanged sections changes.
     *
     * Callers should build their sections list and pass it here instead of
     * manually calling [clear] / [addSection] / [notifyDataSetChanged].
     */
    fun submitSections(newSections: List<Pair<String, List<E>>>) {
        val oldSections = sections.map { Pair(it.first, it.second.toList()) }

        // Fast-path: identical structure + identical content → no-op
        if (oldSections.size == newSections.size &&
            oldSections.zip(newSections).all { (old, new) ->
                old.first == new.first &&
                        old.second.size == new.second.size &&
                        areSectionListsContentsEqual(old.second, new.second)
            }
        ) {
            return
        }

        sections.clear()
        sections.addAll(newSections.map { Pair(it.first, it.second.toList()) })

        // Structural change (different count or titles) → full refresh
        if (oldSections.size != newSections.size ||
            oldSections.zip(newSections).any { (old, new) -> old.first != new.first }
        ) {
            notifyDataSetChanged()
            return
        }

        // Same structure — check if any section changed size
        var anySizeChanged = false
        var anyContentChanged = false
        for (i in newSections.indices) {
            if (oldSections[i].second.size != newSections[i].second.size) {
                anySizeChanged = true
            } else if (!areSectionListsContentsEqual(oldSections[i].second, newSections[i].second)) {
                anyContentChanged = true
            }
        }

        if (anySizeChanged) {
            notifyDataSetChanged()
            return
        }

        if (!anyContentChanged) return // nothing changed

        // Same sizes, only content changed — invalidate position manager then notify per section
        getItemCount() // force PositionManager invalidation before range notifications
        for (i in newSections.indices) {
            if (!areSectionListsContentsEqual(oldSections[i].second, newSections[i].second)) {
                notifySectionChanged(i)
            }
        }
    }

    /**
     * Override to provide identity comparison for items within a section.
     * Default uses [equals].
     */
    protected open fun areSectionItemsTheSame(oldItem: E, newItem: E): Boolean = oldItem == newItem

    /**
     * Override to provide content comparison for items within a section.
     * Default uses [equals].
     */
    protected open fun areSectionContentsTheSame(oldItem: E, newItem: E): Boolean = oldItem == newItem

    private fun areSectionListsContentsEqual(oldList: List<E>, newList: List<E>): Boolean {
        if (oldList.size != newList.size) return false
        return oldList.zip(newList).all { (old, new) -> areSectionContentsTheSame(old, new) }
    }

    fun getItemPosition(layPos: Int): IntArray {
        val result = intArrayOf(-1, -1)
        var position = layPos
        for (i in 0 until sectionCount) {
            position--
            if (position < 0) break

            val itemCount = getItemCount(i)
            if (position < itemCount) {
                result[0] = i
                result[1] = position
                break
            }

            position -= itemCount
            if (showFooters()) {
                position--
            }
        }
        return result
    }

    fun getItem(layPos: Int): E? {
        val position = getItemPosition(layPos)
        if (position[0] == -1) {
            return null
        }
        return sections[position[0]].second[position[1]]
    }

    fun getItem(section: Int, relativePosition: Int): E {
        return sections[section].second[relativePosition]
    }

    final override fun getItemId(position: Int): Long {
        var relative = position
        for (section in 0 until sectionCount) {
            if (relative == 0) {
                return stableStructuralId(STABLE_ID_KIND_HEADER, section)
            }
            relative--

            val itemCount = getItemCount(section)
            if (relative < itemCount) {
                return getItemId(section, relative).takeUnless { it == RecyclerView.NO_ID }
                        ?: stableStructuralId(STABLE_ID_KIND_ITEM_FALLBACK, section, relative)
            }
            relative -= itemCount

            if (showFooters()) {
                if (relative == 0) {
                    return stableStructuralId(STABLE_ID_KIND_FOOTER, section)
                }
                relative--
            }
        }

        return RecyclerView.NO_ID
    }

    private fun stableStructuralId(kind: Long, section: Int, relativePosition: Int = 0): Long {
        val sectionKey = section.toLong() and 0xffffffffL
        val relativeKey = relativePosition.toLong() and 0xffffffL
        return STABLE_ID_STRUCTURAL_BASE or
                (kind shl STABLE_ID_KIND_SHIFT) or
                (sectionKey shl STABLE_ID_SECTION_SHIFT) or
                relativeKey
    }

    override fun getSectionCount(): Int = sections.size

    override fun getItemCount(section: Int): Int = sections[section].second.size

    override fun onBindHeaderViewHolder(vh: VH, i: Int, b: Boolean) {
        // Override in subclass
    }

    override fun onBindFooterViewHolder(vh: VH, i: Int) {
        // Override in subclass
    }

    override fun onBindViewHolder(vh: VH, i: Int, i1: Int, i2: Int) {
        // Override in subclass
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        throw IllegalStateException("Must be overridden in subclass")
    }

    protected fun inflateLayout(parent: ViewGroup, @LayoutRes id: Int): View {
        return LayoutInflater.from(parent.context).inflate(id, parent, false)
    }

    interface OnItemClickListener<T> {
        fun onItemClick(item: T)
        fun onItemLongClick(item: T): Boolean = false
    }
}
