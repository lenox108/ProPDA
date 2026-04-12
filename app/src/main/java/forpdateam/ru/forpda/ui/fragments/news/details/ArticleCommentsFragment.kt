package forpdateam.ru.forpda.ui.fragments.news.details

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout

import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentView
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerTopScroller
import com.google.android.material.R as MaterialR
import forpdateam.ru.forpda.ui.tuneForListPerformance
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent

/**
 * Created by radiationx on 03.09.17.
 */

class ArticleCommentsFragment : Fragment(), ArticleCommentView, ArticleCommentsAdapter.ClickListener, TabTopScroller {
    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var messageField: EditText
    private lateinit var buttonSend: AppCompatImageButton
    private lateinit var progressBarSend: ProgressBar
    private lateinit var writePanel: RelativeLayout
    private val authHolder = App.get().Di().authHolder
    private val adapter = ArticleCommentsAdapter(authHolder)
    private var currentReplyComment: Comment? = null
    private lateinit var contentController: ContentController
    private lateinit var additionalContentFrame: ViewGroup
    private lateinit var topScroller: RecyclerTopScroller

    private val presenter: ArticleCommentViewModel by viewModels {
        val parent = parentFragment as NewsDetailsFragment
        ArticleCommentViewModel.Factory(
                parent.provideChildInteractor(),
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().authHolder,
                App.get().Di().errorHandler
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.article_comments, container, false)
        refreshLayout = view.findViewById<View>(R.id.swipe_refresh_list) as androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        recyclerView = view.findViewById<View>(R.id.base_list) as androidx.recyclerview.widget.RecyclerView
        writePanel = view.findViewById<View>(R.id.comment_write_panel) as RelativeLayout
        messageField = view.findViewById<View>(R.id.message_field) as EditText
        //val sendContainer = view.findViewById<View>(R.id.send_container) as FrameLayout
        buttonSend = view.findViewById<View>(R.id.button_send) as AppCompatImageButton
        progressBarSend = view.findViewById<View>(R.id.send_progress) as ProgressBar
        additionalContentFrame = view.findViewById(R.id.additional_content)
        contentController = ContentController(null, additionalContentFrame, refreshLayout)

        refreshLayout.setProgressBackgroundColorSchemeColor(App.getColorFromAttr(context, R.attr.colorPrimary))
        refreshLayout.setColorSchemeColors(
                App.getColorFromAttr(context, R.attr.colorPrimary),
                App.getColorFromAttr(context, MaterialR.attr.colorSecondary),
                App.getColorFromAttr(context, R.attr.colorAccent)
        )
        refreshLayout.setOnRefreshListener { presenter.updateComments() }

        recyclerView.setBackgroundColor(App.getColorFromAttr(context, R.attr.background_for_lists))
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.tuneForListPerformance()
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(App.px12, false))
        adapter.clickListener = this
        recyclerView.adapter = adapter

        topScroller = RecyclerTopScroller(recyclerView, (parentFragment as NewsDetailsFragment).getAppBar())

        messageField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (s.isEmpty()) {
                    currentReplyComment = null
                }
                buttonSend.isClickable = s.isNotEmpty()
            }
        })

        buttonSend.setOnClickListener { sendComment() }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attachView(this)
        presenter.start()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
    }

    override fun toggleScrollTop() {
        topScroller.toggleScrollTop()
    }

    private fun createFunny(comments: List<Comment>) {
        val density = resources.displayMetrics.density
        if (comments.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(context)
                        .setImage(R.drawable.ic_comment)
                        .setTitle(R.string.funny_article_comments_nodata_title)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
            additionalContentFrame.visibility = View.VISIBLE
            ViewCompat.setElevation(additionalContentFrame, 12f * density)
            ViewCompat.setElevation(refreshLayout, 0f)
            additionalContentFrame.bringToFront()
            writePanel.bringToFront()
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
            additionalContentFrame.visibility = View.GONE
            ViewCompat.setElevation(additionalContentFrame, 0f)
            ViewCompat.setElevation(refreshLayout, 0f)
            refreshLayout.bringToFront()
            writePanel.bringToFront()
        }
    }

    override fun setMessageFieldVisible(isVisible: Boolean) {
        if (isVisible) {
            writePanel.visibility = View.VISIBLE
        } else {
            writePanel.visibility = View.GONE
        }
        adapter.notifyDataSetChanged()
    }

    override fun onNickClick(comment: Comment, position: Int) {
        presenter.openProfile(comment)
    }

    override fun onLikeClick(comment: Comment, position: Int) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext())
            return
        }
        comment.karma?.apply {
            status = Comment.Karma.LIKED
            count++
        }

        adapter.notifyItemChanged(position)
        presenter.likeComment(comment.id)
    }

    override fun onReplyClick(comment: Comment, position: Int) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext())
            return
        }
        if (messageField.text.isEmpty()) {
            fillMessageField(comment)
        } else {
            AlertDialog.Builder(context!!)
                    .setMessage(R.string.comment_reply_warning)
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        fillMessageField(comment)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
        }

    }

    @SuppressLint("SetTextI18n")
    private fun fillMessageField(comment: Comment) {
        currentReplyComment = comment
        messageField.setText("${currentReplyComment?.userNick},\n")
        messageField.setSelection(messageField.text.length)
        messageField.requestFocus()
        val imm = activity!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun sendComment() {
        val commentId = currentReplyComment?.id ?: 0
        presenter.replyComment(commentId, messageField.text.toString())
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        refreshLayout.isRefreshing = isRefreshing
    }

    override fun setSendRefreshing(isRefreshing: Boolean) {
        progressBarSend.visibility = if (isRefreshing) View.VISIBLE else View.GONE
        buttonSend.visibility = if (isRefreshing) View.GONE else View.VISIBLE
    }

    override fun showComments(comments: List<Comment>) {
        adapter.addAll(comments)
        createFunny(comments)
    }

    override fun scrollToComment(position: Int) {
        recyclerView.scrollToPosition(position)
    }

    override fun onReplyComment() {
        messageField.text = null
        currentReplyComment = null
    }

}
