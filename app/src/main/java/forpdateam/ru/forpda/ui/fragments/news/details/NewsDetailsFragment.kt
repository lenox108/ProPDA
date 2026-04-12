package forpdateam.ru.forpda.ui.fragments.news.details

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView

import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.common.ForPdaCoil

import java.util.ArrayList

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.news.DetailsPage
import forpdateam.ru.forpda.model.interactors.news.ArticleInteractor
import forpdateam.ru.forpda.presentation.articles.detail.ArticleDetailView
import forpdateam.ru.forpda.presentation.articles.detail.ArticleDetailViewModel
import forpdateam.ru.forpda.ui.activities.MainActivity
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.TabTopScroller
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.views.ExtendedWebView
import forpdateam.ru.forpda.ui.views.ScrimHelper

/**
 * Created by isanechek on 8/19/17.
 */

class NewsDetailsFragment : TabFragment(), ArticleDetailView, TabTopScroller {


    lateinit var fragmentsPager: androidx.viewpager.widget.ViewPager
        private set
    private lateinit var progressBar: ProgressBar
    private lateinit var imageProgressBar: ProgressBar
    private lateinit var detailsImage: ImageView

    private lateinit var detailsTitle: TextView
    private lateinit var detailsNick: TextView
    private lateinit var detailsCount: TextView
    private lateinit var detailsDate: TextView

    private var isResume = false
    private var isScrim = false

    private val interactor = ArticleInteractor(
            ArticleInteractor.InitData(),
            App.get().Di().newsRepository,
            App.get().Di().articleTemplate
    )

    private val presenter: ArticleDetailViewModel by viewModels {
        ArticleDetailViewModel.Factory(
                interactor,
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().errorHandler
        )
    }

    fun provideChildInteractor(): ArticleInteractor {
        return interactor
    }

    fun getAppBar() = appBarLayout

    public override fun attachWebView(webView: ExtendedWebView) {
        super.attachWebView(webView)
    }

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_news)
        configuration.isFitSystemWindow = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.apply {
            interactor.initData.newsUrl = getString(ARG_NEWS_URL)
            interactor.initData.newsId = getInt(ARG_NEWS_ID, 0)
            interactor.initData.commentId = getInt(ARG_NEWS_COMMENT_ID, 0)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_article)
        val viewStub = findViewById(R.id.toolbar_content) as ViewStub
        viewStub.layoutResource = R.layout.toolbar_news_details
        viewStub.inflate()
        fragmentsPager = findViewById(R.id.view_pager) as androidx.viewpager.widget.ViewPager
        progressBar = findViewById(R.id.progress_bar) as ProgressBar
        detailsImage = findViewById(R.id.article_image) as ImageView
        detailsTitle = findViewById(R.id.article_title) as TextView
        detailsNick = findViewById(R.id.article_nick) as TextView
        detailsCount = findViewById(R.id.article_comments_count) as TextView
        detailsDate = findViewById(R.id.article_date) as TextView
        imageProgressBar = findViewById(R.id.article_progress_bar) as ProgressBar

        detailsImage.maxHeight = App.px24 * 10

        setScrollFlagsExitUntilCollapsed()

        return viewFragment
    }

    @Suppress("DEPRECATION")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val scrimHelper = ScrimHelper(appBarLayout, toolbarLayout)
        scrimHelper.setScrimListener { scrim1 ->
            isScrim = scrim1
            if (scrim1) {
                toolbar.navigationIcon?.clearColorFilter()
                toolbar.overflowIcon?.clearColorFilter()
                toolbarTitleView.visibility = View.VISIBLE
            } else {
                toolbar.navigationIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                toolbar.overflowIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                toolbarTitleView.visibility = View.GONE
            }
            updateStatusBar()
        }


        arguments?.apply {
            val newsTitle = getString(ARG_NEWS_TITLE)
            val newsNick = getString(ARG_NEWS_AUTHOR_NICK)
            val newsDate = getString(ARG_NEWS_DATE)
            val newsImageUrl = getString(ARG_NEWS_IMAGE)
            val newsCount = getInt(ARG_NEWS_COMMENTS_COUNT, -1)
            if (newsTitle != null) {
                setTitle(newsTitle)
                setTabTitle(String.format(getString(R.string.fragment_tab_title_article), newsTitle))
                detailsTitle.text = newsTitle
            }
            if (newsNick != null) {
                detailsNick.text = newsNick
            }
            if (newsCount != -1) {
                detailsCount.text = newsCount.toString()
            }
            if (newsDate != null) {
                detailsDate.text = newsDate
            }
            if (newsImageUrl != null) {
                showArticleImage(newsImageUrl)
            }
        }

        toolbarTitleView.visibility = View.GONE
        toolbar.navigationIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        toolbar.overflowIcon?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        detailsNick.setOnClickListener { presenter.openAuthorProfile() }

        presenter.attachView(this)
        presenter.start()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
    }

    override fun toggleScrollTop() {
        ((fragmentsPager.adapter as FragmentPagerAdapter).getItem(fragmentsPager.currentItem) as? TabTopScroller)?.toggleScrollTop()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
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

    override fun onBackPressed(): Boolean {
        if (fragmentsPager.currentItem == 1) {
            fragmentsPager.currentItem = 0
            return true
        }
        return super.onBackPressed()
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        isResume = true
        updateStatusBar()
    }

    override fun onPauseOrHide() {
        super.onPauseOrHide()
        isResume = false
        updateStatusBar()
    }

    private fun updateStatusBar() {
        val defaultSb = MainActivity.getDefaultLightStatusBar(activity!!)
        if (isResume) {
            MainActivity.setLightStatusBar(activity!!, isScrim && defaultSb)
        } else {
            MainActivity.setLightStatusBar(activity!!, defaultSb)
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        progressBar.visibility = if (isRefreshing) View.VISIBLE else View.GONE
    }

    override fun showArticle(data: DetailsPage) {
        setTitle(data.title)
        setTabTitle(String.format(getString(R.string.fragment_tab_title_article), data.title))
        detailsTitle.text = data.title
        detailsNick.text = data.author
        detailsDate.text = data.date
        detailsCount.text = data.commentsCount.toString()

        data.imgUrl?.also {
            showArticleImage(it)
        }

        val pagerAdapter = FragmentPagerAdapter(childFragmentManager)
        fragmentsPager.adapter = pagerAdapter
        if (data.commentId > 0) {
            appBarLayout.setExpanded(false, true)
            fragmentsPager.setCurrentItem(1, true)
        }
    }

    override fun showCreateNote(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(context, title, url)
    }

    override fun showArticleImage(imageUrl: String) {
        ForPdaCoil.loadIntoWithProgress(detailsImage, imageUrl, imageProgressBar)
    }

    @Suppress("DEPRECATION")
    private inner class FragmentPagerAdapter(
            fm: androidx.fragment.app.FragmentManager
    ) : androidx.fragment.app.FragmentPagerAdapter(fm) {
        private val fragments = ArrayList<androidx.fragment.app.Fragment>()
        private val titles = ArrayList<String>()

        init {
            fragments.add(ArticleContentFragment())
            titles.add(App.get().getString(R.string.news_page_content))

            fragments.add(ArticleCommentsFragment())
            titles.add(App.get().getString(R.string.news_page_comments))
        }

        override fun getItem(position: Int): androidx.fragment.app.Fragment {
            return fragments[position]
        }

        override fun getCount(): Int {
            return fragments.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return titles[position]
        }
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
    }

}
