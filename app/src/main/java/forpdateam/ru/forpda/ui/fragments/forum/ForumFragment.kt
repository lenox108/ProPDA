package forpdateam.ru.forpda.ui.fragments.forum

import android.os.Bundle
import com.google.android.material.appbar.AppBarLayout
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import forpdateam.ru.forpda.common.showSnackbar

import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.forum.ForumItemTree
import forpdateam.ru.forpda.model.data.remote.api.favorites.FavoritesApi
import forpdateam.ru.forpda.presentation.forum.ForumUiEvent
import forpdateam.ru.forpda.presentation.forum.ForumViewModel
import forpdateam.ru.forpda.ui.fragments.TabFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.model.AuthHolder
import javax.inject.Inject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 15.02.17.
 */

@AndroidEntryPoint
class ForumFragment : TabFragment() {
    @Inject lateinit var authHolder: AuthHolder


    private lateinit var forumRecycler: RecyclerView
    private lateinit var forumAdapter: ForumTreeRecyclerAdapter

    private lateinit var dialogMenu: DynamicDialogMenu<ForumFragment, ForumItemTree>

    private var listScrollY = 0
    private var appBarOffset = 0
    private var pendingRecyclerState: android.os.Parcelable? = null

    private val presenter: ForumViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_forum)
        arguments?.apply {
            presenter.targetForumId = getInt(ARG_FORUM_ID, -1)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        baseInflateFragment(inflater, R.layout.fragment_forum)
        forumRecycler = findViewById(R.id.forum_recycler_view) as? RecyclerView ?: throw IllegalStateException("forumRecycler not found")
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListsBackground()
        pinStaticOpaqueToolbar()

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.open_forum)) { _, data ->
                presenter.navigateToForum(data)
            }
            addItem(getString(R.string.copy_link)) { _, data ->
                presenter.copyLink(data)
            }
            addItem(getString(R.string.mark_read)) { _, data ->
                openMarkReadDialog(data)
            }
            addItem(getString(R.string.add_to_favorites)) { _, data ->
                openAddToFavoriteDialog(data.id)
            }
            addItem(getString(R.string.fragment_title_search)) { _, data ->
                presenter.navigateToSearch(data)
            }
        }

        forumAdapter = ForumTreeRecyclerAdapter(
                onOpenForum = { presenter.navigateToForum(it) },
                onLongClick = { item ->
                    dialogMenu.apply {
                        disallowAll()
                        if (item.level > 0)
                            allow(0)
                        allow(1)
                        if (authHolder.get().isAuth()) {
                            allow(2)
                            allow(3)
                        }
                        allow(4)

                        show(requireContext(), this@ForumFragment, item)
                    }
                    false
                }
        )
        forumRecycler.layoutManager = LinearLayoutManager(context)
        forumRecycler.adapter = forumAdapter
        (forumRecycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        forumRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                listScrollY = recyclerView.computeVerticalScrollOffset()
                updateToolbarShadow()
            }
        })

        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, i ->
            appBarOffset = i
            updateToolbarShadow()
        })

        presenter.start()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (presenter.needsRefresh) {
            // Была просмотрена тема — обновляем форум с сервера, чтобы статусы прочитанности были актуальны
            presenter.clearNeedsRefresh()
            presenter.loadForums()
        }
    }

    override fun onDestroyView() {
        presenter.forumListState = forumAdapter.snapshotState()
        presenter.recyclerState = forumRecycler.layoutManager?.onSaveInstanceState()
        super.onDestroyView()
    }

    override fun isShadowVisible(): Boolean {
        return appBarOffset != 0 || listScrollY > 0
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.forum_refresh)
                .setOnMenuItemClickListener {
                    presenter.loadForums()
                    false
                }
        menu.add(R.string.mark_all_read)
                .setOnMenuItemClickListener {
                    openMarkAllReadDialog()
                    false
                }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: ForumUiEvent) {
        when (event) {
            is ForumUiEvent.ShowForums -> showForums(event.tree)
            is ForumUiEvent.ScrollToForum -> scrollToForum(event.forumId)
            is ForumUiEvent.OnMarkRead -> onMarkRead()
            is ForumUiEvent.OnMarkAllRead -> onMarkAllRead()
            is ForumUiEvent.OnAddToFavorite -> onAddToFavorite(event.result)
        }
    }

    private fun showForums(forumRoot: ForumItemTree) {
        if (presenter.targetForumId == -1) {
            pendingRecyclerState = forumRecycler.layoutManager?.onSaveInstanceState()
                    ?: pendingRecyclerState
        }
        forumAdapter.submit(forumRoot, presenter.forumListState)
        val stateToRestore = pendingRecyclerState ?: presenter.recyclerState
        if (presenter.targetForumId == -1 && stateToRestore != null) {
            forumRecycler.post {
                forumRecycler.layoutManager?.onRestoreInstanceState(stateToRestore)
                pendingRecyclerState = null
            }
        }
    }

    private fun openAddToFavoriteDialog(forumId: Int) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.favorites_subscribe_email)
                .setItems(FavoritesFragment.getSubNames(requireContext())) { _, which ->
                    presenter.addToFavorite(forumId, FavoritesApi.SUB_TYPES[which])
                }
                .showWithStyledButtons()
    }

    private fun openMarkReadDialog(item: ForumItemTree) {
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.mark_read) + "?")
                .setPositiveButton(R.string.ok) { _, _ ->
                    presenter.markRead(item.id)
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun openMarkAllReadDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.mark_all_read) + "?")
                .setPositiveButton(R.string.ok) { _, _ ->
                    presenter.markAllRead()
                }
                .setNegativeButton(R.string.no, null)
                .showWithStyledButtons()
    }

    private fun onMarkRead() {
        showSnackbar(R.string.action_complete)
    }

    private fun onMarkAllRead() {
        showSnackbar(R.string.action_complete)
    }

    private fun onAddToFavorite(result: Boolean) {
        showSnackbar(if (result) getString(R.string.favorites_added) else getString(R.string.error_occurred))
    }

    private fun scrollToForum(id: Int) {
        val pos = forumAdapter.expandPathTo(id)
        if (pos >= 0) {
            forumRecycler.post {
                (forumRecycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(pos, 0)
            }
        }
    }

    companion object {
        const val ARG_FORUM_ID = "ARG_FORUM_ID"
    }
}
