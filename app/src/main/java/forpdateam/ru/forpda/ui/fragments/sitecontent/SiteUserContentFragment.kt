package forpdateam.ru.forpda.ui.fragments.sitecontent

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.entity.remote.sitecontent.SiteComment
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.sitecontent.SiteUserContentViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.news.main.NewsListAdapter
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import kotlinx.coroutines.launch

/**
 * Нативный список сайтового контента пользователя («Постов» / «Комментов» из профиля). Раньше эти
 * ссылки (`/<ник>/posts|comments/`) уходили во внешний браузер (LinkHandler их не знал).
 * «Постов» переиспользует рендер ленты новостей ([NewsListAdapter]); «Комментов» — свой адаптер.
 */
@AndroidEntryPoint
class SiteUserContentFragment : RecyclerFragment() {

    companion object {
        const val ARG_URL = "site_content_url"
        const val ARG_KIND = "site_content_kind"
    }

    private val presenter: SiteUserContentViewModel by viewModels()

    private var kind: Screen.SiteUserContent.Kind = Screen.SiteUserContent.Kind.POSTS
    private var postsAdapter: NewsListAdapter? = null
    private var commentsAdapter: SiteCommentsAdapter? = null

    private val newsListener = object : NewsListAdapter.ItemClickListener {
        override fun onLongItemClick(view: View, item: NewsItem, position: Int): Boolean = false
        override fun onItemClick(view: View, item: NewsItem, position: Int) = presenter.onPostClick(item)
        override fun onNickClick(view: View, item: NewsItem, position: Int) = presenter.onPostClick(item)
        override fun onLoadMoreClick() { /* пагинации нет */ }
    }

    private val commentsListener = object : BaseAdapter.OnItemClickListener<SiteComment> {
        override fun onItemClick(item: SiteComment) = presenter.onCommentClick(item)
        override fun onItemLongClick(item: SiteComment): Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = runCatching {
            Screen.SiteUserContent.Kind.valueOf(arguments?.getString(ARG_KIND).orEmpty())
        }.getOrDefault(Screen.SiteUserContent.Kind.POSTS)
        configuration.defaultTitle = getString(
                if (kind == Screen.SiteUserContent.Kind.COMMENTS) R.string.site_content_comments_title
                else R.string.site_content_posts_title
        )
        presenter.setArgs(arguments?.getString(ARG_URL).orEmpty(), kind)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()
        arguments?.getString(TabFragment.ARG_TITLE)?.takeIf { it.isNotBlank() }?.let { setTitle(it) }

        recyclerView.layoutManager = LinearLayoutManager(context)
        if (kind == Screen.SiteUserContent.Kind.COMMENTS) {
            commentsAdapter = SiteCommentsAdapter().also {
                it.setOnItemClickListener(commentsListener)
                recyclerView.adapter = it
            }
        } else {
            postsAdapter = NewsListAdapter().also {
                it.setOnClickListener(newsListener)
                recyclerView.adapter = it
            }
        }

        refreshLayout.setOnRefreshListener { presenter.load() }
        presenter.start()
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { presenter.refreshing.collect { setRefreshing(it) } }
                if (kind == Screen.SiteUserContent.Kind.COMMENTS) {
                    launch { presenter.comments.collect { showComments(it) } }
                } else {
                    launch { presenter.posts.collect { showPosts(it) } }
                }
            }
        }
    }

    private fun showPosts(items: List<NewsItem>) {
        toggleEmpty(items.isEmpty())
        postsAdapter?.addAll(items)
    }

    private fun showComments(items: List<SiteComment>) {
        toggleEmpty(items.isEmpty())
        commentsAdapter?.addAll(items)
    }

    private fun toggleEmpty(empty: Boolean) {
        if (empty) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                contentController.addContent(
                        FunnyContent(requireContext()).setImage(R.drawable.ic_history).setTitle(R.string.nothing_found),
                        ContentController.TAG_NO_DATA,
                )
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
    }
}
