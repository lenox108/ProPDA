package forpdateam.ru.forpda.ui.fragments.news.details

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.TooltipCompat
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.databinding.ArticleCommentItemBinding
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder

class ArticleCommentsAdapter(
    private val authHolder: AuthHolder
) : RecyclerView.Adapter<ArticleCommentsAdapter.ViewHolder>() {

    private val list = ArrayList<Comment>()
    private var likedColorFilter: ColorFilter? = null
    private var dislikedColorFilter: ColorFilter? = null
    var clickListener: ClickListener? = null

    fun addAll(comments: List<Comment>) {
        addAll(comments, true)
    }

    fun addAll(comments: List<Comment>, clearList: Boolean) {
        val oldList = ArrayList(list)
        if (clearList) {
            clear()
        }
        list.addAll(comments)
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = list.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition].id == list[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == list[newItemPosition]
            }
        }).dispatchUpdatesTo(this)
    }

    fun clear() {
        list.clear()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        likedColorFilter = PorterDuffColorFilter(
            recyclerView.context.getColorFromAttr(R.attr.colorAccent),
            PorterDuff.Mode.SRC_ATOP
        )
        dislikedColorFilter = PorterDuffColorFilter(
            ContextCompat.getColor(recyclerView.context, R.color.dislike_color),
            PorterDuff.Mode.SRC_ATOP
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ArticleCommentItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.bindContent(item)
        val authData = authHolder.get()
        if (item.isDeleted) {
            holder.itemView.isClickable = false
            holder.binding.commentLikeImage.visibility = View.GONE
            holder.binding.commentLikeCount.visibility = View.GONE
            holder.binding.commentMore.visibility = View.GONE
            holder.binding.commentNick.visibility = View.GONE
            holder.binding.commentDate.visibility = View.GONE
            holder.binding.commentEditedMarker.visibility = View.GONE
        } else {
            holder.binding.commentLikeImage.visibility = View.VISIBLE
            holder.binding.commentLikeCount.visibility = View.VISIBLE
            holder.binding.commentNick.visibility = View.VISIBLE
            holder.binding.commentDate.visibility = View.VISIBLE

            holder.binding.commentNick.text = item.userNick
            holder.binding.commentDate.text = item.date
            holder.bindContent(item)
            holder.binding.commentMore.visibility = View.VISIBLE

            if (item.likeCount == 0) {
                holder.binding.commentLikeCount.visibility = View.GONE
            } else {
                holder.binding.commentLikeCount.visibility = View.VISIBLE
                holder.binding.commentLikeCount.text = item.likeCount.toString()
            }

            holder.binding.commentLikeImage.isEnabled = !item.isLikePending
            holder.binding.commentLikeImage.alpha = if (item.isLikePending) 0.5f else 1.0f
            when {
                item.likedByMe -> {
                    holder.binding.commentLikeImage.setImageDrawable(holder.heart)
                    holder.binding.commentLikeImage.colorFilter = likedColorFilter
                    holder.binding.commentLikeImage.isClickable = authData.userId != item.userId && !item.isLikePending
                }
                item.karma?.status == Comment.Karma.DISLIKED -> {
                    holder.binding.commentLikeImage.setImageDrawable(holder.heartOutline)
                    holder.binding.commentLikeImage.colorFilter = dislikedColorFilter
                    holder.binding.commentLikeImage.isClickable = false
                }
                else -> {
                    holder.binding.commentLikeImage.setImageDrawable(holder.heartOutline)
                    holder.binding.commentLikeImage.clearColorFilter()
                    holder.binding.commentLikeImage.isClickable = authData.userId != item.userId && !item.isLikePending
                }
            }
        }

        holder.itemView.setPadding(
            holder.itemView.context.resources.getDimensionPixelSize(R.dimen.dp12) * item.level,
            0, 0, 0
        )
    }

    override fun getItemCount(): Int = list.size

    fun getItem(position: Int): Comment = list[position]

    inner class ViewHolder(val binding: ArticleCommentItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val heart: Drawable = binding.root.context.getVecDrawable(R.drawable.ic_heart)
        val heartOutline: Drawable = binding.root.context.getVecDrawable(R.drawable.ic_heart_outline)

        fun bindContent(comment: Comment) {
            val content = ApiUtils.spannedFromHtml(comment.content)
            val editedHint = if (comment.isEdited) {
                binding.root.context.getString(R.string.comment_edited_hint)
            } else {
                null
            }
            val text = formatCommentContent(content, comment.isEdited)
            binding.commentContent.text = text
            binding.commentContent.contentDescription = text?.toString()?.takeIf { it.isNotBlank() }
            binding.commentEditedMarker.visibility = editedMarkerVisibility(comment.isEdited)
            binding.commentEditedMarker.contentDescription = editedHint
            TooltipCompat.setTooltipText(binding.commentEditedMarker, editedHint)
        }

        init {
            binding.commentNick.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener?.onNickClick(getItem(position), position)
                }
            }
            binding.commentLikeImage.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener?.onLikeClick(getItem(position), position)
                }
            }
            binding.commentContent.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener?.onReplyClick(getItem(position), position)
                }
            }
            binding.commentMore.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    clickListener?.onMoreClick(getItem(position), position)
                }
            }
        }
    }

    interface ClickListener {
        fun onNickClick(comment: Comment, position: Int)
        fun onLikeClick(comment: Comment, position: Int)
        fun onReplyClick(comment: Comment, position: Int)
        fun onMoreClick(comment: Comment, position: Int)
    }

    companion object {
        fun formatCommentContent(content: CharSequence?, isEdited: Boolean): CharSequence? = content

        fun editedMarkerVisibility(isEdited: Boolean): Int =
                if (isEdited) View.VISIBLE else View.GONE
    }
}
