package forpdateam.ru.forpda.ui.fragments.attachments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.databinding.TopicAttachmentItemBinding
import forpdateam.ru.forpda.entity.remote.attachments.TopicAttachment
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class TopicAttachmentsAdapter : BaseAdapter<TopicAttachment, TopicAttachmentsAdapter.AttachmentHolder>() {

    private var itemClickListener: OnItemClickListener<TopicAttachment>? = null

    fun setOnItemClickListener(listener: OnItemClickListener<TopicAttachment>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentHolder {
        val binding = TopicAttachmentItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return AttachmentHolder(binding)
    }

    override fun onBindViewHolder(holder: AttachmentHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class AttachmentHolder(
            private val binding: TopicAttachmentItemBinding
    ) : BaseViewHolder<TopicAttachment>(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        override fun bind(item: TopicAttachment, position: Int) {
            binding.attachItemName.text = item.name
            if (item.sizeText.isNullOrBlank()) {
                binding.attachItemSize.visibility = View.GONE
            } else {
                binding.attachItemSize.visibility = View.VISIBLE
                binding.attachItemSize.text = item.sizeText
            }
            if (item.isImage) {
                ForPdaCoil.loadInto(binding.attachItemImage, item.url)
            } else {
                binding.attachItemImage.setImageResource(R.drawable.ic_attachment)
            }
        }

        override fun onClick(view: View) {
            val position = layoutPosition
            if (position < 0 || position >= getItemCount()) return
            itemClickListener?.onItemClick(getItem(position))
        }
    }
}
