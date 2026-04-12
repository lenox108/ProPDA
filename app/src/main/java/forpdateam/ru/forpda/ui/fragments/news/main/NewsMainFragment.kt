package forpdateam.ru.forpda.ui.fragments.news.main

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListView
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu

/**
 * Created by isanechek on 8/8/17.
 */

class NewsMainFragment : RecyclerFragment(), NewsListAdapter.ItemClickListener, ArticlesListView {

    private lateinit var adapter: NewsListAdapter
    private val dialogMenu = DynamicDialogMenu<NewsMainFragment, NewsItem>()

    private val presenter: ArticlesListViewModel by viewModels {
        ArticlesListViewModel.Factory(
                App.get().Di().newsRepository,
                App.get().Di().avatarRepository,
                App.get().Di().authHolder,
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().errorHandler,
                App.get().Di().schedulers
        )
    }

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_news_list)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListsBackground()
        refreshLayout.setOnRefreshListener { presenter.refreshArticles() }
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        //recyclerView.addItemDecoration(new DevicesFragment.SpacingItemDecoration(App.px8, true));
        adapter = NewsListAdapter()
        adapter.setOnClickListener(this)
        recyclerView.adapter = adapter

        setScrollFlagsEnterAlways()

        dialogMenu.apply {
            addItem(getString(R.string.copy_link)) { _, data ->
                presenter.copyLink(data)
            }
            addItem(getString(R.string.share)) { _, data ->
                presenter.shareLink(data)
            }
            addItem(getString(R.string.create_note)) { _, data ->
                presenter.createNote(data)
            }
        }

        presenter.attachView(this)
        presenter.start()
    }

    override fun onDestroyView() {
        presenter.detachView()
        super.onDestroyView()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.fragment_title_search)
                .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_search))
                .setOnMenuItemClickListener {
                    presenter.openSearch()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    override fun onLoadMoreClick() {
        presenter.loadMore()
    }

    override fun showNews(items: List<NewsItem>, withClear: Boolean) {
        if (withClear) {
            if (!items.isEmpty()) {
                adapter.clear()
                adapter.addAll(items)
            }
        } else
            adapter.insertMore(items)
    }

    override fun updateItems(items: List<NewsItem>) {
        adapter.updateItems(items)
    }

    override fun showCreateNote(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(context, title, url)
    }

    override fun showItemDialogMenu(item: NewsItem) {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(context, this@NewsMainFragment, item)
        }
    }

    override fun onItemClick(view: View, item: NewsItem, position: Int) {
        presenter.onItemClick(item)
    }

    override fun onLongItemClick(view: View, item: NewsItem, position: Int): Boolean {
        presenter.onItemLongClick(item)
        return true
    }

    override fun onNickClick(view: View, item: NewsItem, position: Int) {
        presenter.openProfile(item)
    }
}
