package forpdateam.ru.forpda.ui.fragments.devdb.device.comments

import android.view.LayoutInflater
import android.view.ViewGroup
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getDrawableAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.DeviceCommentItemBinding
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.ui.fragments.devdb.DevDbHelper
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class CommentsAdapter(
    private val listener: CommentHolder.Listener
) : BaseAdapter<Device.Comment, CommentsAdapter.CommentHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentHolder {
        val binding = DeviceCommentItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CommentHolder(binding, listener)
    }

    override fun onBindViewHolder(holder: CommentHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class CommentHolder(
        private val binding: DeviceCommentItemBinding,
        listener: Listener
    ) : BaseViewHolder<Device.Comment>(binding.root) {

        private var currentItem: Device.Comment? = null

        init {
            binding.itemLikeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                binding.root.context.getVecDrawable(R.drawable.ic_thumb_up), null, null, null
            )
            binding.itemDislikeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(
                binding.root.context.getVecDrawable(R.drawable.ic_thumb_down), null, null, null
            )
            binding.itemTitle.setOnClickListener {
                currentItem?.let { item -> listener.onClick(item) }
            }
            binding.itemRating.background = binding.itemRating.context.getDrawableAttr(R.attr.count_background)
        }

        override fun bind(item: Device.Comment, position: Int) {
            currentItem = item
            binding.itemTitle.text = item.nick
            binding.itemDate.text = item.date
            binding.itemDesc.text = ApiUtils.spannedFromHtml(item.text)
            binding.itemRating.text = item.rating.toString()
            binding.itemLikeBtn.text = item.likes.toString()
            binding.itemDislikeBtn.text = item.dislikes.toString()
            binding.itemRating.background.colorFilter = DevDbHelper.getColorFilter(item.rating)
        }

        interface Listener {
            fun onClick(item: Device.Comment)
        }
    }
}
