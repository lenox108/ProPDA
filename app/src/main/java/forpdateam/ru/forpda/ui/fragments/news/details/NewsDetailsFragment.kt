package forpdateam.ru.forpda.ui.fragments.news.details

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.appbar.CollapsingToolbarLayout
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.databinding.FragmentArticleBinding

import java.util.ArrayList

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.interactors.CrossScreenInteractor
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.model.interactors.news.ArticleDiskCache
import forpdateam.ru.forpda.model.interactors.news.ArticleMemoryCache
import forpdateam.ru.forpda.model.interactors.news.ArticlePrefetchService
import forpdateam.ru.forpda.presentation.articles.detail.ArticleDetailUiEvent
import forpdateam.ru.forpda.presentation.articles.detail.ArticleDetailViewModel
import forpdateam.ru.forpda.presentation.articles.detail.ArticleUiState
import forpdateam.ru.forpda.ui.SystemBarAppearance
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.repository.news.NewsRepository
import forpdateam.ru.forpda.presentation.articles.detail.ArticleTemplate
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import androidx.fragment.app.commitNow

/**
 * Created by isanechek on 8/19/17.
 */

@AndroidEntryPoint
class NewsDetailsFragment : TabFragment(), TabTopScroller {
    @Inject lateinit var articleTemplate: ArticleTemplate
    @Inject lateinit var newsRepository: NewsRepository
    @Inject lateinit var router: TabRouter
    @Inject lateinit var linkHandler: ILinkHandler
    @Inject lateinit var errorHandler: IErrorHandler
    @Inject lateinit var clipboardHelper: ClipboardHelper
    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var articleDiskCache: ArticleDiskCache
    @Inject lateinit var articleMemoryCache: ArticleMemoryCache
    @Inject lateinit var articlePrefetchService: ArticlePrefetchService
    @Inject lateinit var crossScreenInteractor: CrossScreenInteractor

    private var _articleBinding: FragmentArticleBinding? = null
    private val articleBinding get() = checkNotNull(_articleBinding) { "Binding accessed after onDestroyView" }

    private val interactor by lazy {
        ArticleInteractor(
                ArticleInteractor.InitData(),
                newsRepository,
                articleTemplate,
                articleDiskCache,
                crossScreenInteractor,
                articleMemoryCache,
                articlePrefetchService
        )
    }

    private val presenter: ArticleDetailViewModel by viewModels {
        ArticleDetailViewModel.Factory(requireContext(), interactor, router, linkHandler, errorHandler, clipboardHelper)
    }

    private var prevStatusBarColor: Int? = null
    private val networkRefreshing = AtomicBoolean(false)
    private val awaitingWebViewRender = AtomicBoolean(false)

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    fun markArticleWebViewLoadStarted() {
        if (articleContentFragment()?.hasConfirmedArticleRender() == true) {
            return
        }
        awaitingWebViewRender.set(true)
        applyLoadingIndicator()
    }

    fun onArticleWebViewRendered() {
        awaitingWebViewRender.set(false)
        applyLoadingIndicator()
    }

    /** Stops toolbar spinner only when article body is confirmed painted in the WebView. */
    fun clearArticleWebViewPendingRenderIfDisplayed() {
        if (articleContentFragment()?.hasConfirmedArticleRender() == true) {
            awaitingWebViewRender.set(false)
            applyLoadingIndicator()
        }
    }

    fun provideChildInteractor(): ArticleInteractor {
        return interactor
    }

    fun getAppBar() = appBarLayout

    fun currentArticleId(): Int = presenter.currentData.value?.id ?: interactor.initData.newsId

    fun currentCommentsCount(): Int = presenter.currentData.value?.commentsCount ?: -1

    fun currentArticle(): DetailsPage? = presenter.currentData.value

    fun reloadArticle() {
        presenter.loadArticle(forceRefresh = true)
    }

    fun isArticleLoading(): Boolean = presenter.isArticleLoading()

    fun showInlineComments() {
        appBarLayout.setExpanded(false, true)
        (childFragmentManager.findFragmentById(R.id.article_container) as? ArticleContentFragment)
                ?.loadInlineComments()
    }

    public override fun attachWebView(webView: ExtendedWebView) {
        super.attachWebView(webView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_news)
        configuration.fitSystemWindow = true
        arguments?.apply {
            interactor.initData.newsUrl = getString(ARG_NEWS_URL)
            interactor.initData.newsId = getInt(ARG_NEWS_ID, 0)
            interactor.initData.commentId = getInt(ARG_NEWS_COMMENT_ID, 0)
            interactor.initData.hintCommentsCount = getInt(ARG_NEWS_COMMENTS_COUNT, 0)
            interactor.initData.openSource = getString(ARG_NEWS_OPEN_SOURCE, "news_list")
        }
        interactor.resetForNewOpen()
        presenter.start()
    }

    override fun onDestroy() {
        interactor.close()
        super.onDestroy()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        _articleBinding = FragmentArticleBinding.inflate(inflater, fragmentContent, true)

        clearToolbarScrollFlags()
        (toolbar.layoutParams as? CollapsingToolbarLayout.LayoutParams)?.let { params ->
            if (params.collapseMode != CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN) {
                params.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PIN
                toolbar.layoutParams = params
            }
        }
        appBarLayout.setExpanded(true, false)

        return viewFragment
    }

    override fun onDestroyViewBinding() {
        _articleBinding = null
        super.onDestroyViewBinding()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.visibility = View.VISIBLE
        toolbar.setBackgroundColor(topBarSurfaceColor())

        val iconColor = requireContext().getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        arguments?.apply {
            val newsTitle = getString(ARG_NEWS_TITLE)
            if (newsTitle != null) {
                setTitle(newsTitle)
                setTabTitle(String.format(getString(R.string.fragment_tab_title_article), newsTitle))
            }
        }

        toolbarTitleView.visibility = View.VISIBLE
        toolbar.navigationIcon?.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        toolbar.overflowIcon?.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP)
        toolbarProgress.indeterminateTintList = ColorStateList.valueOf(
                requireContext().getColorFromAttr(R.attr.colorAccent)
        )

        if (childFragmentManager.findFragmentById(R.id.article_container) == null) {
            childFragmentManager.commitNow {
                replace(R.id.article_container, ArticleContentFragment())
            }
        }

        networkRefreshing.set(true)
        awaitingWebViewRender.set(true)
        applyLoadingIndicator()
        observeViewModel()
    }

    override fun onDestroyView() {
        restoreStatusBar()
        super.onDestroyView()
    }

    override fun toggleScrollTop() {
        (childFragmentManager.findFragmentById(R.id.article_container) as? TabTopScroller)?.toggleScrollTop()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.write).apply {
            setIcon(R.drawable.ic_comment_outline)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                NewsCommentComposeBottomSheet()
                        .show(childFragmentManager, NewsCommentComposeBottomSheet.TAG)
                true
            }
        }
        menu.add(R.string.copy_link)
                .setOnMenuItemClickListener {
                    presenter.copyLink()
                    false
                }
        menu.add(R.string.share)
                .setOnMenuItemClickListener {
                    presenter.shareLink()
                    false
                }
        menu.add(R.string.create_note)
                .setOnMenuItemClickListener {
                    presenter.createNote()
                    false
                }
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        updateStatusBar()
        presenter.reloadIfContentMissing()
        articleContentFragment()?.ensureRendered()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        restoreStatusBar()
    }

    private fun updateStatusBar() {
        val act = activity ?: return
        // Экран статьи новости визуально не должен "просвечивать" статус-бар в светлой теме.
        // Тема MainActivity работает edge-to-edge со статус-баром #0000; поэтому здесь явно
        // задаём непрозрачный фон, совпадающий с верхней плашкой.
        if (prevStatusBarColor == null) prevStatusBarColor = act.window.statusBarColor
        SystemBarAppearance.syncStatusBar(act, topBarSurfaceColor())
    }

    private fun restoreStatusBar() {
        val act = activity
        val prev = prevStatusBarColor
        if (act != null && prev != null) {
            act.window.statusBarColor = prev
        }
        prevStatusBarColor = null
        act?.let(SystemBarAppearance::syncStatusBarIconContrast)
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
                    presenter.uiState
                            .collect { state ->
                                when (state) {
                                    is ArticleUiState.Idle -> Unit
                                    is ArticleUiState.Loading -> {
                                        awaitingWebViewRender.set(true)
                                        applyLoadingIndicator()
                                    }
                                    is ArticleUiState.Content -> {
                                        showArticle(state.page)
                                        clearArticleWebViewPendingRenderIfDisplayed()
                                    }
                                    is ArticleUiState.Error -> {
                                        awaitingWebViewRender.set(false)
                                        applyLoadingIndicator()
                                        val contentFragment = childFragmentManager
                                                .findFragmentById(R.id.article_container) as? ArticleContentFragment
                                        if (contentFragment?.hasConfirmedArticleRender() != true) {
                                            contentFragment?.showError(
                                                    state.throwable.message
                                                            ?: getString(R.string.error_occurred)
                                            )
                                        }
                                    }
                                }
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

    private fun handleUiEvent(event: ArticleDetailUiEvent) {
        when (event) {
            is ArticleDetailUiEvent.ShowCreateNote -> showCreateNote(event.title, event.url)
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        networkRefreshing.set(isRefreshing)
        if (isRefreshing) {
            val articleBodyReady = articleContentFragment()?.hasConfirmedArticleRender() == true
            if (!articleBodyReady) {
                awaitingWebViewRender.set(true)
            }
        } else {
            clearArticleWebViewPendingRenderIfDisplayed()
        }
        applyLoadingIndicator()
        // TabFragment.setRefreshing shows contentProgress (center); article load uses toolbar only.
        if (!isRefreshing) {
            stopRefreshing()
        }
    }

    private fun applyLoadingIndicator() {
        val articleBodyReady = articleContentFragment()?.hasConfirmedArticleRender() == true
        val showNetwork = networkRefreshing.get() && !articleBodyReady
        val showWebRender = awaitingWebViewRender.get() && !articleBodyReady
        val show = showNetwork || showWebRender
        toolbarProgress.visibility = if (show) View.VISIBLE else View.INVISIBLE
        contentProgress.visibility = View.GONE
    }

    private fun showArticle(data: DetailsPage) {
        setTitle(data.title)
        setTabTitle(String.format(getString(R.string.fragment_tab_title_article), data.title))
        articleContentFragment()?.scheduleInlineCommentsBinding()
        if (data.commentId > 0) {
            showInlineComments()
        }
    }

    private fun articleContentFragment(): ArticleContentFragment? =
            childFragmentManager.findFragmentById(R.id.article_container) as? ArticleContentFragment

    private fun showCreateNote(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(context, title, url, notesRepository)
    }

    companion object {
        const val ARG_NEWS_URL = "ARG_NEWS_URL"
        const val ARG_NEWS_ID = "ARG_NEWS_ID"
        const val ARG_NEWS_COMMENT_ID = "ARG_NEWS_COMMENT_ID"
        const val ARG_NEWS_TITLE = "ARG_NEWS_TITLE"
        const val ARG_NEWS_AUTHOR_NICK = "ARG_NEWS_AUTHOR_NICK"
        //const val ARG_NEWS_AUTHOR_ID = "ARG_NEWS_AUTHOR_ID"
        const val ARG_NEWS_COMMENTS_COUNT = "ARG_NEWS_COMMENTS_COUNT"
        const val ARG_NEWS_DATE = "ARG_NEWS_DATE"
        const val ARG_NEWS_IMAGE = "ARG_NEWS_IMAGE"
        const val ARG_NEWS_OPEN_SOURCE = "ARG_NEWS_OPEN_SOURCE"
    }

}
