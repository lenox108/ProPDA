package forpdateam.ru.forpda.ui.views.messagepanel.advanced.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.MessagePanelAdvancedItemBinding
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.ButtonData
import java.util.Collections

class PanelItemAdapter(
    private val items: MutableList<ButtonData>,
    private val urlsToAssets: List<String>,
    private val type: Int
) : RecyclerView.Adapter<PanelItemAdapter.ViewHolder>(), ItemDragCallback.ItemTouchHelperAdapter {

    companion object {
        const val TYPE_ASSET = 0
        const val TYPE_DRAWABLE = 1
    }

    private var itemClickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onItemClick(item: ButtonData)
    }

    fun setOnItemClickListener(mItemClickListener: OnItemClickListener) {
        this.itemClickListener = mItemClickListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = MessagePanelAdvancedItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        if (type == TYPE_ASSET) {
            ForPdaCoil.loadInto(holder.binding.itemIcon, urlsToAssets[position])
        } else if (type == TYPE_DRAWABLE) {
            holder.binding.itemIcon.setImageDrawable(
                holder.itemView.context.getVecDrawable(item.iconRes)
            )
        }
        if (item.title == null) {
            holder.binding.itemTitle.visibility = View.GONE
            holder.itemView.contentDescription = item.text
        } else {
            holder.itemView.contentDescription = item.title
            holder.binding.itemTitle.text = item.title
            holder.binding.itemTitle.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val binding: MessagePanelAdvancedItemBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val item = items[layoutPosition]
            if (item.listener != null) {
                item.listener?.onClick(item)
            } else if (itemClickListener != null) {
                itemClickListener?.onItemClick(item)
            }
        }
    }

    override fun onItemDismiss(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
}
