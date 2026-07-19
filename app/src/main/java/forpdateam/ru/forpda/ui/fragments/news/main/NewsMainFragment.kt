package forpdateam.ru.forpda.ui.fragments.news.main

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.appcompat.widget.PopupMenu
import android.content.res.Configuration
import android.graphics.Rect
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.getVecDrawable
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.model.data.remote.api.news.Constants
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListUiEvent
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by isanechek on 8/8/17.
 */

@AndroidEntryPoint
class NewsMainFragment : RecyclerFragment(), NewsListAdapter.ItemClickListener {

    @Inject lateinit var notesRepository: NotesRepository
    private lateinit var adapter: NewsListAdapter
    private var skeleton: forpdateam.ru.forpda.ui.views.SkeletonListView? = null
    private val tagSkeleton = "SKELETON"
    /** Был ли уже хоть один результат загрузки (данные/пусто/ошибка) — гасит skeleton. */
    private var hadFirstResult = false
    private val dialogMenu = DynamicDialogMenu<NewsMainFragment, NewsItem>()
    private lateinit var categories: List<NewsCategoryUi>
    private var gridDecoration: RecyclerView.ItemDecoration? = null

    private val presenter: ArticlesListViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_news)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        categories = createNewsCategories()
        setListsBackground()
        pinStaticOpaqueToolbar()
        refreshLayout.setOnRefreshListener { presenter.refreshArticles() }
        val layoutManager = GridLayoutManager(context, newsSpanCount())
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Строка «Загрузить ещё» всегда во всю ширину.
                return if (position == adapter.itemCount - 1) layoutManager.spanCount else 1
            }
        }
        recyclerView.layoutManager = layoutManager
        adapter = NewsListAdapter()
        adapter.setOnClickListener(this)
        adapter.setOnItemDisplayListener { presenter.onItemDisplayed(it) }
        recyclerView.adapter = adapter
        applyNewsGridLayout()

        skeleton = forpdateam.ru.forpda.ui.views.SkeletonListView(requireContext()).apply {
            style = forpdateam.ru.forpda.ui.views.SkeletonListView.Style.CARD
            layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        contentController.addContent(skeleton!!, tagSkeleton)

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

        presenter.start()
        setupCategoryTitleDropdown()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // MainActivity обрабатывает orientation сама (configChanges), фрагмент не пересоздаётся —
        // перестраиваем сетку вручную.
        if (view != null) applyNewsGridLayout()
    }

    /** Колонок по доступной ширине: телефон-портрет 1, альбомная 2, широкий планшет 3. */
    private fun newsSpanCount(): Int =
            (resources.configuration.screenWidthDp / MIN_COLUMN_WIDTH_DP).coerceIn(1, MAX_NEWS_COLUMNS)

    private fun applyNewsGridLayout() {
        val span = newsSpanCount()
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
        val changed = layoutManager.spanCount != span || adapter.gridSpanCount != span
        layoutManager.spanCount = span
        adapter.gridSpanCount = span
        gridDecoration?.let { recyclerView.removeItemDecoration(it) }
        gridDecoration = if (span > 1) {
            NewsGridSpacingDecoration(resources.getDimensionPixelSize(R.dimen.list_plate_horizontal_inset))
                    .also { recyclerView.addItemDecoration(it) }
        } else {
            null
        }
        if (changed) adapter.notifyDataSetChanged()
    }

    /**
     * Равные горизонтальные зазоры сетки: у краёв экрана и между колонками ровно [spacing],
     * при этом все ячейки остаются одинаковой ширины. Полноширинные строки (spanSize == spanCount)
     * получают обычные боковые отступы.
     */
    private class NewsGridSpacingDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val lp = view.layoutParams as? GridLayoutManager.LayoutParams ?: return
            val span = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: return
            if (lp.spanSize >= span) {
                outRect.left = spacing
                outRect.right = spacing
                return
            }
            val column = lp.spanIndex
            outRect.left = spacing - column * spacing / span
            outRect.right = (column + 1) * spacing / span
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.fragment_title_search)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_search))
                .setOnMenuItemClickListener {
                    presenter.openSearch()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        val showSkeleton = isRefreshing && !hadFirstResult
                        if (showSkeleton) {
                            contentController.showContent(tagSkeleton)
                            refreshLayout.isRefreshing = false
                        } else {
                            contentController.hideContent(tagSkeleton)
                            refreshLayout.isRefreshing = isRefreshing
                        }
                    }
                }
                launch {
                    presenter.selectedCategory.collect { category ->
                        updateCategorySubtitle(category)
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

    private fun handleUiEvent(event: ArticlesListUiEvent) {
        when (event) {
            ArticlesListUiEvent.ClearNews -> clearNews()
            is ArticlesListUiEvent.ShowNews -> showNews(event.items, event.withClear)
            is ArticlesListUiEvent.UpdateItems -> updateItems(event.items)
            is ArticlesListUiEvent.ShowCreateNote -> showCreateNote(event.title, event.url)
            is ArticlesListUiEvent.ShowItemDialogMenu -> showItemDialogMenu(event.item)
            is ArticlesListUiEvent.ShowLoadError -> showLoadError(event.message)
        }
    }

    override fun onLoadMoreClick() {
        presenter.loadMore()
    }

    private fun showNews(items: List<NewsItem>, withClear: Boolean) {
        contentController.hideContent(ContentController.TAG_ERROR)
        synchronized(adapter) {
            if (withClear) {
                hadFirstResult = true
                contentController.hideContent(tagSkeleton)
                if (items.isNotEmpty()) {
                    adapter.setItemsDirect(items)
                    contentController.hideContent(ContentController.TAG_NO_DATA)
                } else {
                    showEmptyNews()
                }
            } else
                adapter.insertMore(items)
        }
    }

    private fun clearNews() {
        contentController.hideContent(ContentController.TAG_ERROR)
        contentController.hideContent(ContentController.TAG_NO_DATA)
        synchronized(adapter) {
            adapter.setItemsDirect(emptyList())
        }
    }

    private fun showEmptyNews() {
        if (!contentController.contains(ContentController.TAG_NO_DATA)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_newspaper)
                    .setTitle(R.string.funny_news_nodata_title)
                    .setDesc(R.string.funny_news_nodata_desc)
                    .addAction(R.string.refresh) { presenter.refreshArticles() }
            contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
        }
        contentController.showContent(ContentController.TAG_NO_DATA)
    }

    private fun showLoadError(message: String?) {
        hadFirstResult = true
        contentController.hideContent(tagSkeleton)
        if (adapter.itemCount > 1) {
            showSnackbar(message ?: getString(R.string.error_occurred))
            return
        }
        contentController.hideContent(ContentController.TAG_NO_DATA)
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_toolbar_refresh)
                    .setTitle(R.string.funny_news_error_title)
                    .setDesc(R.string.funny_news_error_desc)
                    .addAction(R.string.retry) { presenter.refreshArticles() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.showContent(ContentController.TAG_ERROR)
        message?.let { showSnackbar(it) }
    }

    private fun updateItems(items: List<NewsItem>) {
        synchronized(adapter) {
            adapter.updateItems(items)
        }
    }

    private fun showCreateNote(title: String, url: String) {
        NotesAddPopup.showAddNoteDialog(context, title, url, notesRepository)
    }

    private fun showItemDialogMenu(item: NewsItem) {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(requireContext(), this@NewsMainFragment, item)
        }
    }

    private fun setupCategoryTitleDropdown() {
        titlesWrapper.apply {
            isClickable = true
            setOnClickListener { showCategoryPopup() }
        }
        toolbarTitleView.apply {
            isClickable = true
            layoutParams = layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.dp4)
            val arrowDrawable = DrawableCompat.wrap(requireContext().getVecDrawable(R.drawable.ic_arrow_down)).mutate()
            DrawableCompat.setTintList(arrowDrawable, textColors)
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null,
                    null,
                    arrowDrawable,
                    null
            )
            setOnClickListener { showCategoryPopup() }
        }
        toolbarSubtitleView.apply {
            isClickable = true
            setOnClickListener { showCategoryPopup() }
        }
        updateCategorySubtitle(presenter.selectedCategory.value)
    }

    private fun showCategoryPopup() {
        PopupMenu(requireContext(), toolbarTitleView).apply {
            categories.forEachIndexed { index, category ->
                menu.add(Menu.NONE, index, index, category.menuTitle)
                        .setChecked(category.id == presenter.selectedCategory.value)
                        .setOnMenuItemClickListener {
                            val selectedCategory = categories.getOrNull(it.itemId) ?: return@setOnMenuItemClickListener false
                            presenter.selectCategory(selectedCategory.id)
                            true
                        }
            }
            menu.setGroupCheckable(Menu.NONE, true, true)
        }.show()
    }

    private fun updateCategorySubtitle(categoryId: String) {
        setSubtitle(categories.firstOrNull { it.id == categoryId }?.subtitle
                ?: getString(R.string.news_category_all))
    }

    private fun createNewsCategories(): List<NewsCategoryUi> = listOf(
            category(Constants.NEWS_CATEGORY_ALL, R.string.news_category_all),
            category(Constants.NEWS_CATEGORY_TECH, R.string.news_category_tech),
            category(Constants.NEWS_SUBCATEGORY_TECH_SMARTPHONES, R.string.news_category_tech_smartphones, R.string.news_category_tech),
            category(Constants.NEWS_SUBCATEGORY_TECH_LAPTOPS, R.string.news_category_tech_laptops, R.string.news_category_tech),
            category(Constants.NEWS_SUBCATEGORY_TECH_AUDIO, R.string.news_category_tech_audio, R.string.news_category_tech),
            category(Constants.NEWS_SUBCATEGORY_TECH_MONITORS, R.string.news_category_tech_monitors, R.string.news_category_tech),
            category(Constants.NEWS_SUBCATEGORY_TECH_APPLIANCES, R.string.news_category_tech_appliances, R.string.news_category_tech),
            category(Constants.NEWS_SUBCATEGORY_TECH_PC, R.string.news_category_tech_pc, R.string.news_category_tech),
            category(Constants.NEWS_CATEGORY_REVIEWS, R.string.news_category_reviews),
            category(Constants.NEWS_SUBCATEGORY_SMARTPHONES_REVIEWS, R.string.news_category_reviews_smartphones, R.string.news_category_reviews),
            category(Constants.NEWS_SUBCATEGORY_TABLETS_REVIEWS, R.string.news_category_reviews_tablets, R.string.news_category_reviews),
            category(Constants.NEWS_SUBCATEGORY_SMART_WATCH_REVIEWS, R.string.news_category_reviews_smart_watches, R.string.news_category_reviews),
            category(Constants.NEWS_SUBCATEGORY_ACCESSORIES_REVIEWS, R.string.news_category_reviews_accessories, R.string.news_category_reviews),
            category(Constants.NEWS_SUBCATEGORY_NOTEBOOKS_REVIEWS, R.string.news_category_reviews_notebooks, R.string.news_category_reviews),
            category(Constants.NEWS_SUBCATEGORY_ACOUSTICS_REVIEWS, R.string.news_category_reviews_audio, R.string.news_category_reviews),
            category(Constants.NEWS_CATEGORY_GAMES, R.string.news_category_games)
    ).also(::validateNewsCategories)

    private fun category(id: String, titleRes: Int, parentTitleRes: Int? = null): NewsCategoryUi {
        val title = getString(titleRes)
        val subtitle = parentTitleRes?.let { "${getString(it)}: $title" } ?: title
        val menuTitle = if (parentTitleRes == null) title else MENU_SUBCATEGORY_PREFIX + title
        return NewsCategoryUi(id, menuTitle, subtitle)
    }

    private fun validateNewsCategories(categories: List<NewsCategoryUi>) {
        require(categories.map { it.id }.distinct().size == categories.size) {
            "News category ids must be unique"
        }
        categories.forEach { category ->
            require(Constants.isSelectableNewsCategory(category.id)) {
                "News category has no URL mapping: ${category.id}"
            }
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

    private data class NewsCategoryUi(
            val id: String,
            val menuTitle: String,
            val subtitle: String
    )

    private companion object {
        private const val MENU_SUBCATEGORY_PREFIX = "  "
        private const val MIN_COLUMN_WIDTH_DP = 340
        private const val MAX_NEWS_COLUMNS = 3
    }
}
