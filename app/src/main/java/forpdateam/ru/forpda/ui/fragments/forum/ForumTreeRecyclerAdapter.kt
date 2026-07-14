package forpdateam.ru.forpda.ui.fragments.forum

import forpdateam.ru.forpda.common.getVecDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.databinding.ForumItemDefaultBinding
import forpdateam.ru.forpda.ui.currentUiDensityValues
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.setTextSizePx

/**
 * Плоский список разделов форума (DFS по дереву), без AndroidTreeView —
 * иначе вложенные layout'ы ATV накапливают отступ и строки «уезжают» вправо лесенкой.
 */
class ForumTreeRecyclerAdapter(
        private val onOpenForum: (ForumItemTree) -> Unit,
        private val onLongClick: (ForumItemTree) -> Boolean
) : RecyclerView.Adapter<ForumTreeRecyclerAdapter.VH>() {

    private var rootForum: ForumItemTree? = null
    private val expandedIds = mutableSetOf<Int>()
    private val rows = mutableListOf<ForumItemTree>()

    fun submit(root: ForumItemTree, state: State? = null) {
        rootForum = root
        if (state != null) {
            expandedIds.clear()
            expandedIds.addAll(state.expandedIds)
        }
        expandedIds.retainAll(root.collectForumIds())
        dispatchRebuild()
    }

    fun snapshotState(): State = State(expandedIds.toSet())

    fun restoreState(state: State) {
        expandedIds.clear()
        expandedIds.addAll(state.expandedIds)
        expandedIds.retainAll(rootForum.collectForumIds())
        dispatchRebuild()
    }

    private fun rebuildRows() {
        rows.clear()
        val root = rootForum ?: return
        fun walk(n: ForumItemTree) {
            n.forums?.forEach { child ->
                rows.add(child)
                if (!child.forums.isNullOrEmpty() && expandedIds.contains(child.id)) {
                    walk(child)
                }
            }
        }
        walk(root)
    }

    private fun toggleExpanded(id: Int) {
        if (expandedIds.contains(id)) {
            expandedIds.remove(id)
        } else {
            expandedIds.add(id)
        }
        dispatchRebuild()
    }

    /**
     * Раскрывает путь к разделу [id] и пересобирает список.
     * @return позиция строки или -1, если раздел не найден.
     */
    fun expandPathTo(id: Int): Int {
        val root = rootForum ?: return -1
        fun dfs(node: ForumItemTree, acc: List<Int>): Boolean {
            node.forums?.forEach { child ->
                if (child.id == id) {
                    acc.forEach { expandedIds.add(it) }
                    return true
                }
                if (!child.forums.isNullOrEmpty()) {
                    if (dfs(child, acc + child.id)) return true
                }
            }
            return false
        }
        if (!dfs(root, emptyList())) return -1
        dispatchRebuild()
        return rows.indexOfFirst { it.id == id }
    }

    private fun dispatchRebuild() {
        val oldRows = ArrayList(rows)
        rebuildRows()
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldRows.size
            override fun getNewListSize() = rows.size
            override fun areItemsTheSame(o: Int, n: Int) = oldRows[o].id == rows[n].id
            override fun areContentsTheSame(o: Int, n: Int) = oldRows[o].id == rows[n].id
        }).dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ForumItemDefaultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = rows[position]
        val ctx = holder.itemView.context
        if (holder.itemView.layoutParams == null) {
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val inset = ctx.resources.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
        val gap = ctx.resources.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
        val density = ctx.currentUiDensityValues()
        // Список разделов форума: каждая строка — отдельная M3-карточка со скруглением со всех сторон.
        // Склейка FIRST/MIDDLE/LAST по глубине давала «ленту» с прямыми боками у середины группы —
        // на скрине почти не видно радиуса (в отличие от списка тем, где группы короче).
        holder.binding.root.applyListRowPlate(
                ListPlateSegment.SINGLE,
                inset,
                if (position == 0) gap else 0,
                gap,
                ensureSelectableForeground = true
        )

        // Отступ заголовка одинаков на всех уровнях: подкатегории выравниваются с корневыми разделами.
        val titlePadH = ctx.resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
        holder.binding.forumItemTitle.setPaddingRelative(titlePadH, 0, 0, 0)
        holder.binding.forumItemTitle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = density.itemVerticalPaddingPx
            bottomMargin = density.itemVerticalPaddingPx
        }
        holder.binding.forumItemTitle.setTextSizePx(density.titleTextSizePx)
        holder.binding.forumItemIcon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = density.itemVerticalPaddingPx
            bottomMargin = density.itemVerticalPaddingPx
        }

        holder.binding.forumItemTitle.text = item.title

        val isBranch = !item.forums.isNullOrEmpty()
        val expanded = isBranch && expandedIds.contains(item.id)
        holder.binding.forumItemIcon.background = null
        if (!isBranch) {
            holder.binding.forumItemIcon.setImageDrawable(ctx.getVecDrawable(R.drawable.ic_forum_go_to_topics))
        } else {
            holder.binding.forumItemIcon.setImageDrawable(ctx.getVecDrawable(
                    if (expanded) R.drawable.ic_expand_less_black_24dp else R.drawable.ic_expand_more_black_24dp
            ))
        }

        holder.itemView.setOnClickListener {
            if (!item.forums.isNullOrEmpty()) {
                toggleExpanded(item.id)
            } else {
                onOpenForum(item)
            }
        }
        holder.itemView.setOnLongClickListener { onLongClick(item) }
    }

    override fun getItemCount(): Int = rows.size

    class VH(val binding: ForumItemDefaultBinding) : RecyclerView.ViewHolder(binding.root)

    data class State(val expandedIds: Set<Int>)

    private fun ForumItemTree?.collectForumIds(): Set<Int> {
        val root = this ?: return emptySet()
        val ids = mutableSetOf<Int>()
        fun walk(node: ForumItemTree) {
            node.forums?.forEach { child ->
                ids.add(child.id)
                walk(child)
            }
        }
        walk(root)
        return ids
    }
}
