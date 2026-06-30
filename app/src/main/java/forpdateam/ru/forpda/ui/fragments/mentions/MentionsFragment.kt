package forpdateam.ru.forpda.ui.fragments.mentions

import android.os.Bundle
import android.os.SystemClock
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.common.showSnackbar
import timber.log.Timber

import androidx.fragment.app.viewModels

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.mentions.MentionItem
import forpdateam.ru.forpda.entity.remote.mentions.MentionsData
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.mentions.MentionsUiEvent
import forpdateam.ru.forpda.presentation.mentions.MentionsViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.ui.views.pagination.PaginationHelper
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Created by radiationx on 21.01.17.
 */

@AndroidEntryPoint
class MentionsFragment : RecyclerFragment() {
    @Inject lateinit var authHolder: AuthHolder


    private lateinit var dialogMenu: DynamicDialogMenu<MentionsFragment, MentionItem>
    private lateinit var adapter: MentionsAdapter
    private lateinit var paginationHelper: PaginationHelper

    private val presenter: MentionsViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private val paginationListener = object : PaginationHelper.PaginationListener {
        override fun onTabSelected(tab: TabLayout.Tab): Boolean {
            return refreshLayout.isRefreshing
        }

        override fun onSelectedPage(pageNumber: Int) {
            presenter.setCurrentSt(pageNumber)
            presenter.getMentions()
        }
    }

    private val adapterListener = object : BaseAdapter.OnItemClickListener<MentionItem> {
        override fun onItemClick(item: MentionItem) {
            presenter.onItemClick(item)
        }

        override fun onItemLongClick(item: MentionItem): Boolean {
            presenter.onItemLongClick(item)
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_mentions)
        Timber.d("MentionsFragment created")
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
            addItem(getString(R.string.copy_link)) { _, data ->
                presenter.copyLink(data)
            }
            addItem(getString(R.string.add_to_favorites)) { _, data ->
                presenter.addToFavorites(data)
            }
        }

        adapter = MentionsAdapter()
        adapter.paginationFooterBinder = { container ->
            paginationHelper.addInList(layoutInflater, container)
        }

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        recyclerView.adapter = adapter

        adapter.setOnItemClickListener(adapterListener)
        refreshLayout.setOnRefreshListener { presenter.getMentions() }
        paginationHelper.setListener(paginationListener)

        observeViewModel()
        presenter.start()
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        Timber.d("MentionsFragment onResumeOrShow")
        // Перезагружаем список при показе вкладки, чтобы бейдж «Ответы» сверился с реальным
        // состоянием упоминаний, а не оставался на устаревшем счётчике из шапки форума.
        presenter.onShown()
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

    private fun handleUiEvent(event: MentionsUiEvent) {
        when (event) {
            is MentionsUiEvent.ShowMentions -> showMentions(event.data)
            is MentionsUiEvent.MentionMarkedRead -> {
                for (index in 0 until adapter.itemCount - 1) {
                    if (adapter.getItem(index) === event.item) {
                        adapter.notifyItemChanged(index)
                        break
                    }
                }
            }
            is MentionsUiEvent.ShowItemDialogMenu -> showItemDialogMenu(event.item)
            is MentionsUiEvent.ShowAddFavoritesDialog -> showAddFavoritesDialog(event.id)
            is MentionsUiEvent.OnAddToFavorite -> showSnackbar(if (event.result) getString(R.string.favorites_added) else getString(R.string.error_occurred))
            is MentionsUiEvent.ShowLoadError -> showLoadError(event.message)
        }
    }

    private fun showMentions(data: MentionsData) {
        contentController.hideContent(ContentController.TAG_ERROR)
        if (data.items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_notifications)
                        .setTitle(R.string.funny_mentions_nodata_title)
                        .setDesc(R.string.funny_mentions_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }

        val diffStartedAt = SystemClock.uptimeMillis()
        adapter.addAll(data.items)
        if (BuildConfig.DEBUG) {
            Timber.d(
                    "MentionsPerf DiffUtil submit took %dms items=%d",
                    SystemClock.uptimeMillis() - diffStartedAt,
                    data.items.size
            )
        }
        paginationHelper.updatePagination(data.pagination)
        clearToolbarPaginationSubtitle()
        listScrollTop()
    }

    private fun showLoadError(message: String?) {
        if (adapter.itemCount > 1) {
            showSnackbar(message ?: getString(R.string.error_occurred))
            return
        }
        contentController.hideContent(ContentController.TAG_NO_DATA)
        if (!contentController.contains(ContentController.TAG_ERROR)) {
            val funnyContent = FunnyContent(requireContext())
                    .setImage(R.drawable.ic_notifications)
                    .setTitle(R.string.funny_mentions_error_title)
                    .setDesc(R.string.funny_mentions_error_desc)
                    .addAction(R.string.retry) { presenter.getMentions() }
            contentController.addContent(funnyContent, ContentController.TAG_ERROR)
        }
        contentController.showContent(ContentController.TAG_ERROR)
        adapter.clear()
        message?.let { showSnackbar(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        paginationHelper.destroy()
    }

    private fun showItemDialogMenu(item: MentionItem) {
        dialogMenu.apply {
            disallowAll()
            allow(0)
            if (item.isTopic && authHolder.get().isAuth()) {
                allow(1)
            }
            show(requireContext(), this@MentionsFragment, item)
        }
    }

    private fun showAddFavoritesDialog(id: Int) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setItems(FavoritesFragment.getSubNames(requireContext())) { _, which ->
                    presenter.addTopicToFavorite(id, FavoritesApi.SUB_TYPES[which])
                }
                .showWithStyledButtons()
    }
}
