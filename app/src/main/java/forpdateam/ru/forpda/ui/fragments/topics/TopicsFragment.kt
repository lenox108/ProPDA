package forpdateam.ru.forpda.ui.fragments.topics

import javax.inject.Inject
import forpdateam.ru.forpda.common.getVecDrawable
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.*
import androidx.core.view.MenuItemCompat
import forpdateam.ru.forpda.common.showSnackbar
import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ClipboardHelper
import forpdateam.ru.forpda.common.uriForOpeningTopicFromListing
import forpdateam.ru.forpda.common.Utils
import forpdateam.ru.forpda.entity.remote.topics.TopicItem
import forpdateam.ru.forpda.entity.remote.topics.TopicsData
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.topics.TopicsUiEvent
import forpdateam.ru.forpda.presentation.topics.TopicsViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedAdapter
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.pagination.PaginationHelper
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.AuthHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 01.03.17.
 */

@AndroidEntryPoint
class TopicsFragment : RecyclerFragment() {
    @Inject lateinit var authHolder: AuthHolder
    @Inject lateinit var clipboardHelper: ClipboardHelper


    private lateinit var adapter: TopicsAdapter
    private lateinit var paginationHelper: PaginationHelper
    private lateinit var dialogMenu: DynamicDialogMenu<TopicsFragment, TopicItem>

    private val presenter: TopicsViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private val paginationListener = object : PaginationHelper.PaginationListener {
        override fun onTabSelected(tab: TabLayout.Tab): Boolean {
            // ViewModel cancels previous job automatically, no need to block UI
            return false
        }

        override fun onSelectedPage(pageNumber: Int) {
            presenter.loadPage(pageNumber)
        }
    }

    private val adapterListener = object : BaseSectionedAdapter.OnItemClickListener<TopicItem> {
        override fun onItemClick(item: TopicItem) {
            presenter.onItemClick(item)
        }

        override fun onItemLongClick(item: TopicItem): Boolean {
            val url: String = if (item.isAnnounce) {
                item.announceUrl ?: ""
            } else {
                uriForOpeningTopicFromListing(item.listingHref, item.id, item.isRelocated).toString()
            }
            clipboardHelper.copyToClipboard(url)
            showSnackbar(R.string.link_copied)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_topics)
        arguments?.apply {
            presenter.setId(getInt(TOPICS_ID_ARG))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        paginationHelper = PaginationHelper(requireActivity(), dimensionsProvider)
        paginationHelper.addInToolbar(
                inflater = inflater,
                target = toolbarLayout,
                enablePadding = configuration.fitSystemWindow,
                surfaceColorAttr = R.attr.main_toolbar_accent_surface,
        )
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.copy_link)) { _, data1 ->
                val url: String = if (data1.isAnnounce) {
                    data1.announceUrl ?: ""
                } else {
                    uriForOpeningTopicFromListing(data1.listingHref, data1.id).toString()
                }
                clipboardHelper.copyToClipboard(url)
            }
            addItem(getString(R.string.open_theme_forum)) { _, _ ->
                presenter.openTopicForum()
            }
            addItem(getString(R.string.add_to_favorites)) { _, data1 ->
                if (data1.isForum) {
                    openAddForumToFavoriteDialog(data1.id)
                } else {
                    openAddTopicToFavoriteDialog(data1.id)
                }
            }
        }

        refreshLayout.setOnRefreshListener { presenter.loadTopics() }
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        adapter = TopicsAdapter()
        adapter.paginationFooterBinder = { container ->
            paginationHelper.addInList(layoutInflater, container)
        }
        recyclerView.adapter = adapter
        adapter.setOnItemClickListener(adapterListener)
        paginationHelper.setListener(paginationListener)

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun useCompactToolbarPaginationChrome(): Boolean = true

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        refreshLayout.isRefreshing = isRefreshing
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

    private fun handleUiEvent(event: TopicsUiEvent) {
        when (event) {
            is TopicsUiEvent.ShowTopics -> showTopics(event.data)
            is TopicsUiEvent.UpdateList -> adapter.notifyDataSetChanged()
            is TopicsUiEvent.ShowItemDialogMenu -> showItemDialogMenu(event.item)
            is TopicsUiEvent.OnAddToFavorite -> showSnackbar(if (event.result) getString(R.string.favorites_added) else getString(R.string.error_occurred))
            is TopicsUiEvent.OnMarkRead -> showSnackbar(R.string.action_complete)
            is TopicsUiEvent.ShowLoadError -> showLoadError(event.message)
        }
    }

    private fun showTopics(data: TopicsData) {
        contentController.hideContent(ContentController.TAG_ERROR)
        setTitle(data.title)
        val forumItems = data.forumItems.deduplicateTopicItems()
        val announceItems = data.announceItems.deduplicateTopicItems()
        val pinnedItems = data.pinnedItems.deduplicateTopicItems()
        val topicItems = data.topicItems
                .filterNot { topic -> pinnedItems.any { it.id != 0 && it.id == topic.id } }
                .deduplicateTopicItems()
        val newSections = mutableListOf<android.util.Pair<String, List<TopicItem>>>()
        if (forumItems.isNotEmpty())
            newSections.add(android.util.Pair(getString(R.string.forum_section), forumItems))
        if (announceItems.isNotEmpty())
            newSections.add(android.util.Pair(getString(R.string.announce_section), announceItems))
        if (pinnedItems.isNotEmpty())
            newSections.add(android.util.Pair(getString(R.string.pinned_section), pinnedItems))
        newSections.add(android.util.Pair(getString(R.string.themes_section), topicItems))
        adapter.submitSections(newSections)

        // Show empty state if no topics at all
        val isEmpty = data.forumItems.isEmpty() && data.announceItems.isEmpty() &&
                data.pinnedItems.isEmpty() && data.topicItems.isEmpty()
        if (isEmpty) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_notify_mention)
                        .setTitle(R.string.funny_topics_nodata_title)
                        .setDesc(R.string.funny_topics_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }

        paginationHelper.updatePagination(data.pagination)
        clearToolbarPaginationSubtitle()
        listScrollTop()
    }

    private fun showLoadError(message: String?) {
        if (adapter.itemCount > 0) {
            showSnackbar(message ?: getString(R.string.error_occurred))
            return
        }
        contentController.hideContent(ContentController.TAG_NO_DATA)
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_toolbar_refresh)
                    .setTitle(R.string.funny_topics_error_title)
                    .setDesc(R.string.funny_topics_error_desc)
                    .addAction(R.string.retry) { presenter.loadTopics() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.showContent(ContentController.TAG_ERROR)
        message?.let { showSnackbar(it) }
    }

    private fun List<TopicItem>.deduplicateTopicItems(): List<TopicItem> {
        val seenIds = mutableSetOf<Int>()
        val seenTitles = mutableSetOf<String>()
        return filter { item ->
            val normalizedTitle = item.title.orEmpty().trim().lowercase()
            when {
                item.id != 0 -> seenIds.add(item.id)
                normalizedTitle.isNotEmpty() -> seenTitles.add(normalizedTitle)
                else -> true
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu
                .add(R.string.open_forum)
                .setOnMenuItemClickListener {
                    presenter.openForum()
                    true
                }
        if (authHolder.get().isAuth()) {
            val markReadItem = menu
                    .add(R.string.mark_read)
                    .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_done))
                    .setOnMenuItemClickListener {
                        openMarkReadDialog()
                        true
                    }
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
            MenuItemCompat.setContentDescription(markReadItem, getString(R.string.mark_read))
        }

        menu.add(R.string.fragment_title_search)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_search))
                .setOnMenuItemClickListener {
                    presenter.openSearch()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun openAddForumToFavoriteDialog(forumId: Int) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setItems(FavoritesFragment.getSubNames(requireContext())) { _, which ->
                    presenter.addForumToFavorite(forumId, FavoritesApi.SUB_TYPES[which])
                }
                .showWithStyledButtons()
    }

    private fun openAddTopicToFavoriteDialog(topicId: Int) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setItems(FavoritesFragment.getSubNames(requireContext())) { _, which ->
                    presenter.addTopicToFavorite(topicId, FavoritesApi.SUB_TYPES[which])
                }
                .showWithStyledButtons()
    }

    private fun openMarkReadDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.mark_read) + "?")
                .setPositiveButton(R.string.ok) { _, _ ->
                    presenter.markRead()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }


    override fun onDestroy() {
        super.onDestroy()
        paginationHelper.destroy()
    }

    private fun showItemDialogMenu(item: TopicItem) {
        dialogMenu.apply {
            disallowAll()
            allow(0)
            if (!item.isAnnounce) {
                allow(1)
                if (authHolder.get().isAuth()) {
                    allow(2)
                }
            }
            show(requireContext(), this@TopicsFragment, item)
        }
    }

    companion object {
        const val TOPICS_ID_ARG = "TOPICS_ID_ARG"
    }
}
