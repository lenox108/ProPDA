package forpdateam.ru.forpda.ui.fragments.sitecontent

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.databinding.SiteCommentItemBinding
import forpdateam.ru.forpda.entity.remote.sitecontent.SiteComment
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseViewHolder

class SiteCommentsAdapter : BaseAdapter<SiteComment, SiteCommentsAdapter.Holder>() {

    private var itemClickListener: OnItemClickListener<SiteComment>? = null

    fun setOnItemClickListener(listener: OnItemClickListener<SiteComment>) {
        this.itemClickListener = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = SiteCommentItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class Holder(
            private val binding: SiteCommentItemBinding
    ) : BaseViewHolder<SiteComment>(binding.root), View.OnClickListener {

        init {
            binding.root.setOnClickListener(this)
        }

        override fun bind(item: SiteComment, position: Int) {
            binding.siteCommentTitle.text = item.articleTitle
            if (item.snippet.isBlank()) {
                binding.siteCommentSnippet.visibility = View.GONE
            } else {
                binding.siteCommentSnippet.visibility = View.VISIBLE
                binding.siteCommentSnippet.text = item.snippet
            }
            if (item.date.isNullOrBlank()) {
                binding.siteCommentDate.visibility = View.GONE
            } else {
                binding.siteCommentDate.visibility = View.VISIBLE
                binding.siteCommentDate.text = item.date
            }
        }

        override fun onClick(view: View) {
            val position = layoutPosition
            if (position < 0 || position >= getItemCount()) return
            itemClickListener?.onItemClick(getItem(position))
        }
    }
}
