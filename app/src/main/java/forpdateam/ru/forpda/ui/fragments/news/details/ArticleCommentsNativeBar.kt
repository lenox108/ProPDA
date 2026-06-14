package forpdateam.ru.forpda.ui.fragments.news.details

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsState

/**
 * Native footer for news inline comments — expand/collapse and status never depend on WebView JS taps.
 */
class ArticleCommentsNativeBar(root: View) {
    // <include android:id="article_comments_native_bar"> overrides the layout root id
    // (article_comments_native_card), so the passed view is the card itself.
    private val card: MaterialCardView = root as MaterialCardView
    private val titleView: TextView = root.requireViewById(R.id.article_comments_native_title)
    private val actionButton: MaterialButton = root.requireViewById(R.id.article_comments_native_action)
    private val statusView: TextView = root.requireViewById(R.id.article_comments_native_status)
    private val retryButton: MaterialButton = root.requireViewById(R.id.article_comments_native_retry)

    var onActionClick: (() -> Unit)? = null
    var onRetryClick: (() -> Unit)? = null

    private val actionTextColors: ColorStateList by lazy {
        val accent = actionButton.context.getColorFromAttr(R.attr.colorAccent)
        ColorStateList.valueOf(accent)
    }

    init {
        actionButton.setTextColor(actionTextColors)
        retryButton.setTextColor(actionTextColors)
        actionButton.setOnClickListener { onActionClick?.invoke() }
        retryButton.setOnClickListener { onRetryClick?.invoke() }
    }

    fun setVisible(visible: Boolean) {
        card.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun bind(
            title: String,
            actionLabel: String,
            collapsed: Boolean,
            commentsState: ArticleCommentsState,
            showLabel: String,
            hideLabel: String,
    ) {
        titleView.text = title
        actionButton.text = if (collapsed) showLabel else hideLabel
        actionButton.contentDescription = actionButton.text
        when (commentsState) {
            ArticleCommentsState.NotLoaded -> {
                statusView.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            is ArticleCommentsState.Loading -> {
                statusView.visibility = if (!collapsed) View.VISIBLE else View.GONE
                statusView.text = statusView.context.getString(R.string.news_inline_comments_loading)
                retryButton.visibility = View.GONE
            }
            is ArticleCommentsState.Loaded,
            ArticleCommentsState.Empty -> {
                statusView.visibility = View.GONE
                retryButton.visibility = View.GONE
            }
            is ArticleCommentsState.Error -> {
                if (!collapsed) {
                    statusView.visibility = View.VISIBLE
                    statusView.text = commentsState.throwable.message
                            ?: statusView.context.getString(R.string.error_occurred)
                    retryButton.visibility = if (commentsState.canRetry) View.VISIBLE else View.GONE
                } else {
                    statusView.visibility = View.GONE
                    retryButton.visibility = View.GONE
                }
            }
        }
    }
}
