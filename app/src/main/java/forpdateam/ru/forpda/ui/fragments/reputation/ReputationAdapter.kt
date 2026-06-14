package forpdateam.ru.forpda.ui.fragments.reputation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.databinding.ReputationItemBinding
import forpdateam.ru.forpda.entity.remote.reputation.RepItem
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class ReputationAdapter : BaseAdapter<RepItem, ReputationAdapter.ReputationHolder>() {

    private var itemClickListener: BaseAdapter.OnItemClickListener<RepItem>? = null

    fun setOnItemClickListener(listener: BaseAdapter.OnItemClickListener<RepItem>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReputationHolder {
        val binding = ReputationItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ReputationHolder(binding)
    }

    override fun onBindViewHolder(holder: ReputationHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ReputationHolder(
        private val binding: ReputationItemBinding
    ) : BaseViewHolder<RepItem>(binding.root), View.OnClickListener, View.OnLongClickListener {

        init {
            binding.root.setOnClickListener(this)
            binding.root.setOnLongClickListener(this)
        }

        override fun bind(item: RepItem, position: Int) {
            binding.repItemTitle.text = item.title
            binding.repItemLastNick.text = item.userNick
            binding.repItemDate.text = Utils.formatForumDisplayDateTime(item.date, "reputation.item").orEmpty()
            
            if (item.sourceUrl == null) {
                binding.repItemDesc.visibility = View.GONE
            } else {
                binding.repItemDesc.visibility = View.VISIBLE
                binding.repItemDesc.text = item.sourceTitle
            }
            
            ForPdaCoil.loadInto(binding.repItemImage, item.image)
        }

        override fun onClick(view: View) {
            itemClickListener?.onItemClick(getItem(layoutPosition))
        }

        override fun onLongClick(view: View): Boolean {
            return itemClickListener?.let {
                it.onItemLongClick(getItem(layoutPosition))
                true
            } ?: false
        }
    }
}
