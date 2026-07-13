package forpdateam.ru.forpda.ui.fragments.news.details
import forpdateam.ru.forpda.ui.applyM3RefreshStyle

import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.databinding.ArticleCommentsBinding
import forpdateam.ru.forpda.common.showSnackbar
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.widget.AppCompatImageButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RelativeLayout

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.model.data.remote.api.ApiUtils
import forpdateam.ru.forpda.ui.dp12
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.news.Comment
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentUiEvent
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentsState
import forpdateam.ru.forpda.presentation.articles.detail.comments.ArticleCommentViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerTopScroller
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.R as MaterialR
import forpdateam.ru.forpda.ui.tuneForListPerformance
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Created by radiationx on 03.09.17.
 */

@AndroidEntryPoint
class ArticleCommentsFragment : Fragment(), ArticleCommentsAdapter.ClickListener, TabTopScroller {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var router: TabRouter
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var errorHandler: IErrorHandler
    @Inject lateinit var clipboardHelper: ClipboardHelper

    private var _binding: ArticleCommentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var refreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var recyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var messageField: EditText
    private lateinit var buttonSend: AppCompatImageButton
    private lateinit var buttonClose: AppCompatImageButton
    private lateinit var progressBarSend: com.google.android.material.loadingindicator.LoadingIndicator
    private lateinit var writePanel: RelativeLayout
    private lateinit var fabWrite: FloatingActionButton
    private val adapter by lazy { ArticleCommentsAdapter(authHolder) }
    private var currentReplyComment: Comment? = null
    private lateinit var contentController: ContentController
    private lateinit var additionalContentFrame: ViewGroup
    private lateinit var topScroller: RecyclerTopScroller
    private val commentMenu = DynamicDialogMenu<ArticleCommentsFragment, Comment>()
    private val fabHideHandler = Handler(Looper.getMainLooper())
    private var fabShownByScroll = false
    private var bottomNavInsetPx = 0
    private var imeVisible = false
    private val hideWriteFabRunnable = Runnable {
        fabShownByScroll = false
        updateWriteFabState()
    }

    private val presenter: ArticleCommentViewModel by viewModels(
            ownerProducer = { requireParentFragment() },
    ) {
        ArticleCommentViewModel.Factory(hostFragment().provideChildInteractor(), router, linkHandler, authHolder, errorHandler)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ArticleCommentsBinding.inflate(inflater, container, false)
        binding.root.setBackgroundColor(requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        refreshLayout = binding.swipeRefreshList
        recyclerView = binding.baseList
        writePanel = binding.commentWritePanel
        messageField = binding.messageField
        buttonSend = binding.buttonSend
        buttonClose = binding.buttonClose
        progressBarSend = binding.sendProgress
        fabWrite = binding.fabWrite
        additionalContentFrame = binding.additionalContent
        contentController = ContentController(null, additionalContentFrame, refreshLayout)

        refreshLayout.applyM3RefreshStyle()
        refreshLayout.setOnRefreshListener { presenter.updateComments() }

        recyclerView.setBackgroundColor(requireContext().chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.tuneForListPerformance()
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(dp12, false))
        adapter.clickListener = this
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dx != 0 || dy != 0) {
                    showWriteFabTemporarily()
                }
            }
        })
        initCommentMenu()

        topScroller = RecyclerTopScroller(recyclerView, hostFragment().getAppBar())

        messageField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (s.isEmpty()) {
                    currentReplyComment = null
                }
                buttonSend.isClickable = s.isNotEmpty()
            }
        })
        messageField.setOnFocusChangeListener { _, _ -> updateWriteFabState() }

        buttonSend.setOnClickListener { sendComment() }
        buttonClose.setOnClickListener {
            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(messageField.windowToken, 0)
            messageField.text = null
            currentReplyComment = null
            messageField.clearFocus()
            setCommentEditorVisible(false)
        }
        fabWrite.setOnClickListener {
            setCommentEditorVisible(true)
            messageField.requestFocus()
            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(messageField, InputMethodManager.SHOW_IMPLICIT)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Поднимаем writePanel и FAB при открытии системных нижних inset'ов.
        // Без этого adjustResize не срабатывает внутри ViewPager → клавиатура закрывает поле ввода.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) && imeBottom > 0
            bottomNavInsetPx = navBottom
            val extraBottom = maxOf(imeBottom, navBottom)
            v.updatePadding(bottom = extraBottom)
            updateWriteFabBottomMargin()
            updateWriteFabState()
            insets
        }

        presenter.start()
        observeViewModel()
        loadComments()
    }

    override fun onDestroyView() {
        fabHideHandler.removeCallbacks(hideWriteFabRunnable)
        super.onDestroyView()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        setRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.sendRefreshing.collect { isRefreshing ->
                        setSendRefreshing(isRefreshing)
                    }
                }
                launch {
                    presenter.messageFieldVisible.collect { isVisible ->
                        setMessageFieldVisible(isVisible)
                    }
                }
                launch {
                    presenter.commentsState.collect { state ->
                        renderCommentsState(state)
                    }
                }
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: ArticleCommentUiEvent) {
        when (event) {
            is ArticleCommentUiEvent.ShowComments -> showComments(event.comments)
            is ArticleCommentUiEvent.ScrollToComment -> scrollToComment(event.index)
            is ArticleCommentUiEvent.ShowEditComment -> showEditCommentDialog(event.action, event.text)
            is ArticleCommentUiEvent.UpdateCommentLike -> {
                val loaded = presenter.commentsState.value as? ArticleCommentsState.Loaded ?: return
                showComments(loaded.comments)
            }
            is ArticleCommentUiEvent.OnReplyComment -> onReplyComment()
            is ArticleCommentUiEvent.PatchComment -> {
                val loaded = presenter.commentsState.value as? ArticleCommentsState.Loaded ?: return
                showComments(loaded.comments)
            }
            ArticleCommentUiEvent.RefreshLoadMoreUi -> Unit
        }
    }

    override fun toggleScrollTop() {
        topScroller.toggleScrollTop()
    }

    private fun createFunny(comments: List<Comment>) {
        val density = resources.displayMetrics.density
        if (comments.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
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

    private fun setMessageFieldVisible(isVisible: Boolean) {
        if (!isVisible || presenter.commentsState.value !is ArticleCommentsState.Loaded) {
            setCommentEditorVisible(false)
        } else {
            updateWriteFabState()
        }
    }

    private fun setCommentEditorVisible(isVisible: Boolean) {
        writePanel.visibility = if (isVisible) View.VISIBLE else View.GONE
        if (isVisible) {
            fabShownByScroll = false
            fabHideHandler.removeCallbacks(hideWriteFabRunnable)
        }
        updateWriteFabState()
    }

    private fun updateWriteFabState() {
        val canOpenEditor = presenter.commentsState.value is ArticleCommentsState.Loaded &&
                authHolder.get().isAuth() &&
                writePanel.visibility != View.VISIBLE &&
                !messageField.hasFocus() &&
                !imeVisible
        fabWrite.visibility = if (canOpenEditor && fabShownByScroll) View.VISIBLE else View.GONE
        fabWrite.isEnabled = canOpenEditor
        fabWrite.isClickable = canOpenEditor
    }

    private fun showWriteFabTemporarily() {
        if (presenter.commentsState.value !is ArticleCommentsState.Loaded) return
        fabShownByScroll = true
        updateWriteFabState()
        fabHideHandler.removeCallbacks(hideWriteFabRunnable)
        fabHideHandler.postDelayed(hideWriteFabRunnable, WRITE_FAB_VISIBLE_MS)
    }

    private fun updateWriteFabBottomMargin() {
        val margin = resources.getDimensionPixelSize(R.dimen.fab_margin)
        val bottomChromeHeight = binding.root.rootView
                .findViewById<View?>(R.id.bottomMenuRecycler)
                ?.height
                ?.takeIf { it > 0 }
                ?: (resources.getDimensionPixelSize(R.dimen.bottom_nav_tab_bar_height) + bottomNavInsetPx)
        fabWrite.updateLayoutParams<RelativeLayout.LayoutParams> {
            bottomMargin = bottomChromeHeight + margin
            marginEnd = margin
        }
    }

    override fun onNickClick(comment: Comment, position: Int) {
        presenter.openProfile(comment)
    }

    override fun onLikeClick(comment: Comment, position: Int) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        presenter.toggleLikeComment(comment)
    }

    override fun onReplyClick(comment: Comment, position: Int) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        if (!presenter.canReply(comment)) {
            showSnackbar(R.string.comment_action_unavailable)
            return
        }
        if (messageField.text.isEmpty()) {
            fillMessageField(comment)
        } else {
            MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.comment_reply_warning)
                    .setPositiveButton(getString(R.string.ok)) { _, _ ->
                        fillMessageField(comment)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .showWithStyledButtons()
        }

    }

    override fun onMoreClick(comment: Comment, position: Int) {
        showCommentMenu(comment)
    }

    private fun initCommentMenu() {
        commentMenu.addItem(getString(R.string.reply)) { fragment, comment -> fragment.onReplyClick(comment, -1) }
        commentMenu.addItem(getString(R.string.comment_open_profile)) { fragment, comment -> fragment.presenter.openProfile(comment) }
        commentMenu.addItem(getString(R.string.edit)) { fragment, comment -> fragment.showEditCommentDialog(comment) }
        commentMenu.addItem(getString(R.string.delete)) { fragment, comment -> fragment.showDeleteCommentDialog(comment) }
        commentMenu.addItem(getString(R.string.copy_link)) { fragment, comment -> fragment.copyCommentLink(comment) }
        commentMenu.addItem(getString(R.string.comment_plus_karma)) { fragment, comment -> fragment.runAuthAction(comment.actions.karmaPlus) }
        commentMenu.addItem(getString(R.string.comment_hide)) { fragment, comment -> fragment.runAuthAction(comment.actions.hide) }
        commentMenu.addItem(getString(R.string.comment_reputation_plus)) { fragment, comment -> fragment.showReasonActionDialog(comment.actions.reputationPlus, R.string.comment_reputation_reason) }
        commentMenu.addItem(getString(R.string.comment_reputation_minus)) { fragment, comment -> fragment.showReasonActionDialog(comment.actions.reputationMinus, R.string.comment_reputation_reason) }
        commentMenu.addItem(getString(R.string.report)) { fragment, comment -> fragment.showReasonActionDialog(comment.actions.report, R.string.comment_report_reason, "message") }
    }

    private fun showCommentMenu(comment: Comment) {
        commentMenu.disallowAll()
        val actions = comment.actions
        if (presenter.canReply(comment)) commentMenu.allow(0)
        if (actions.profile?.isValid() == true || comment.userId > 0) commentMenu.allow(1)
        val auth = authHolder.get().isAuth()
        val authUserId = authHolder.get().userId
        val isOwnComment = ArticleCommentActionVisibility.isOwnCommentForActions(authUserId, comment)
        if (auth) {
            if (ArticleCommentActionVisibility.canShowEdit(auth, authUserId, comment)) commentMenu.allow(2)
            if (ArticleCommentActionVisibility.canShowDelete(auth, authUserId, comment)) commentMenu.allow(3)
        }
        logOwnCommentMenuDecision(comment, auth, authUserId, isOwnComment)
        if (isOwnComment && comment.id > 0) commentMenu.allow(4)
        if (auth && !isOwnComment && actions.karmaPlus?.isValid() == true) commentMenu.allow(5)
        if (auth && actions.hide?.isValid() == true) commentMenu.allow(6)
        if (auth && !isOwnComment && actions.reputationPlus?.isValid() == true) commentMenu.allow(7)
        if (auth && !isOwnComment && actions.reputationMinus?.isValid() == true) commentMenu.allow(8)
        if (auth && !isOwnComment && actions.report?.isValid() == true) commentMenu.allow(9)
        commentMenu.show(requireContext(), this, comment, getString(R.string.comment_actions_title))
    }

    private fun logOwnCommentMenuDecision(comment: Comment, auth: Boolean, authUserId: Int, isOwnComment: Boolean) {
        if (!forpdateam.ru.forpda.BuildConfig.DEBUG) return
        if (!isOwnComment && comment.userId != authUserId) return
        val reason = ArticleCommentActionVisibility.editHiddenReason(auth, authUserId, comment)
        Timber.d(
                "NewsComments menu id=%d authorId=%d authUserId=%d isOwn=%s hasEdit=%s hasDelete=%s hasRep=%s source=%s editHidden=%s",
                comment.id,
                comment.userId,
                authUserId,
                isOwnComment,
                comment.actions.edit?.isValid() == true,
                comment.actions.delete?.isValid() == true,
                ArticleCommentActionVisibility.hasReputationAction(comment),
                if (comment.actions.edit?.isValid() == true || comment.actions.delete?.isValid() == true) "mobile/desktop merged" else "mobile",
                reason ?: "shown"
        )
    }

    private fun runAuthAction(action: Comment.Action?) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        if (action?.isValid() == true) {
            presenter.executeCommentAction(action)
        }
    }

    private fun showReasonActionDialog(action: Comment.Action?, titleRes: Int, fieldName: String? = null) {
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        if (action?.isValid() != true) return
        val input = EditText(requireContext())
        input.minLines = 3
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setView(input)
                .setPositiveButton(R.string.send) { _, _ ->
                    val reasonField = fieldName ?: action.reasonFieldName ?: "message"
                    presenter.executeCommentAction(action, mapOf(reasonField to input.text.toString()))
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun copyCommentLink(comment: Comment) {
        val articleId = hostFragment().currentArticleId().takeIf { it > 0 } ?: return
        clipboardHelper.copyToClipboard("https://4pda.to/index.php?p=$articleId#comment-${comment.id}")
        showSnackbar(R.string.link_copied)
    }

    private fun showEditCommentDialog(comment: Comment) {
        if (comment.actions.edit?.isValid() != true) return
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        presenter.loadEditCommentForm(comment)
    }

    private fun showEditCommentDialog(action: Comment.Action, text: String) {
        val input = EditText(requireContext())
        input.minLines = 3
        input.setText(ApiUtils.spannedFromHtml(text).toString())
        input.setSelection(input.text.length)
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit)
                .setView(input)
                .setPositiveButton(R.string.send) { _, _ ->
                    presenter.editComment(action, input.text.toString())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showDeleteCommentDialog(comment: Comment) {
        val action = comment.actions.delete?.takeIf { it.isValid() } ?: return
        if (!authHolder.get().isAuth()) {
            Utils.showNeedAuthDialog(requireContext(), router)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.comment_delete_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    presenter.deleteComment(action)
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    @SuppressLint("SetTextI18n")
    private fun fillMessageField(comment: Comment) {
        setCommentEditorVisible(true)
        currentReplyComment = comment
        messageField.setText("${currentReplyComment?.userNick},\n")
        messageField.setSelection(messageField.text.length)
        messageField.requestFocus()
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageField, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun sendComment() {
        val commentId = currentReplyComment?.id ?: 0
        presenter.replyComment(commentId, messageField.text.toString())
    }

    private fun setRefreshing(isRefreshing: Boolean) {
        refreshLayout.isRefreshing = isRefreshing
    }

    private fun setSendRefreshing(isRefreshing: Boolean) {
        progressBarSend.visibility = if (isRefreshing) View.VISIBLE else View.GONE
        buttonSend.visibility = if (isRefreshing) View.GONE else View.VISIBLE
    }

    private fun showComments(comments: List<Comment>) {
        adapter.addAll(comments)
        createFunny(comments)
    }

    private fun renderCommentsState(state: ArticleCommentsState) {
        when (state) {
            ArticleCommentsState.NotLoaded -> {
                adapter.clear()
                adapter.notifyDataSetChanged()
                contentController.hideContent(ContentController.TAG_NO_DATA)
                contentController.hideContent(ContentController.TAG_ERROR)
                additionalContentFrame.visibility = View.GONE
                setCommentEditorVisible(false)
            }
            is ArticleCommentsState.Loading -> {
                contentController.hideContent(ContentController.TAG_NO_DATA)
                contentController.hideContent(ContentController.TAG_ERROR)
                additionalContentFrame.visibility = View.GONE
                setCommentEditorVisible(false)
            }
            is ArticleCommentsState.Loaded -> {
                contentController.hideContent(ContentController.TAG_ERROR)
                setMessageFieldVisible(authHolder.get().isAuth())
            }
            ArticleCommentsState.Empty -> {
                adapter.clear()
                adapter.notifyDataSetChanged()
                contentController.hideContent(ContentController.TAG_ERROR)
                contentController.showContent(ContentController.TAG_NO_DATA)
                setCommentEditorVisible(false)
            }
            is ArticleCommentsState.Error -> {
                adapter.clear()
                adapter.notifyDataSetChanged()
                showErrorState()
                setCommentEditorVisible(false)
            }
        }
    }

    private fun showErrorState() {
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_comment)
                    .setTitle(R.string.error_occurred)
                    .addAction(R.string.retry) { presenter.updateComments() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.hideContent(ContentController.TAG_NO_DATA)
        contentController.showContent(ContentController.TAG_ERROR)
        additionalContentFrame.visibility = View.VISIBLE
        additionalContentFrame.bringToFront()
        writePanel.bringToFront()
    }

    private fun scrollToComment(position: Int) {
        recyclerView.scrollToPosition(position)
    }

    private fun onReplyComment() {
        messageField.text = null
        currentReplyComment = null
    }

    fun loadComments() {
        presenter.loadCommentsIfNeeded()
    }

    private fun hostFragment(): NewsDetailsFragment {
        return (parentFragment as? NewsDetailsFragment)
                ?: ((parentFragment as? ArticleContentFragment)?.hostFragment()
                        ?: throw IllegalStateException("ArticleCommentsFragment must be hosted by news details"))
    }

    companion object {
        private const val WRITE_FAB_VISIBLE_MS = 3000L
    }

}
