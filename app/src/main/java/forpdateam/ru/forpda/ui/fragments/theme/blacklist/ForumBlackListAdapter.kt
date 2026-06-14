package forpdateam.ru.forpda.ui.fragments.theme.blacklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ItemForumBlacklistUserBinding
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.applyListRowPlate
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class ForumBlackListAdapter : BaseAdapter<ForumBlacklistedUser, ForumBlackListAdapter.UserHolder>() {

    private var itemClickListener: BaseAdapter.OnItemClickListener<ForumBlacklistedUser>? = null

    fun setItemClickListener(listener: BaseAdapter.OnItemClickListener<ForumBlacklistedUser>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserHolder {
        val binding = ItemForumBlacklistUserBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return UserHolder(binding)
    }

    override fun onBindViewHolder(holder: UserHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class UserHolder(
            private val binding: ItemForumBlacklistUserBinding
    ) : BaseViewHolder<ForumBlacklistedUser>(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return@setOnClickListener
                itemClickListener?.onItemClick(getItem(position))
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return@setOnLongClickListener false
                itemClickListener?.let {
                    it.onItemLongClick(getItem(position))
                    true
                } ?: false
            }
        }

        override fun bind(item: ForumBlacklistedUser, section: Int) {
            val res = binding.root.resources
            val inset = res.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset)
            val gap = res.getDimensionPixelSize(R.dimen.list_plate_group_gap_vertical)
            binding.root.applyListRowPlate(
                    ListPlateSegment.SINGLE,
                    inset,
                    gapBeforeGroupPx = if (section == 0) gap else 0,
                    gapAfterGroupPx = gap,
                    ensureSelectableForeground = true,
            )
            val nick = item.nick.trim()
            binding.itemNick.text = nick.ifBlank { res.getString(R.string.forum_blacklist_unknown_nick) }
            if (item.userId > 0) {
                binding.itemUserId.text = res.getString(R.string.forum_blacklist_user_id, item.userId)
                binding.itemUserId.visibility = View.VISIBLE
            } else {
                binding.itemUserId.visibility = View.GONE
            }
        }
    }
}
