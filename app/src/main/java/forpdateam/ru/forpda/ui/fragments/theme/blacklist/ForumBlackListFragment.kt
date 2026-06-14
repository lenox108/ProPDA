package forpdateam.ru.forpda.ui.fragments.theme.blacklist

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.showSnackbar
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.presentation.theme.blacklist.ForumBlackListViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForumBlackListFragment : RecyclerFragment() {

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private lateinit var adapter: ForumBlackListAdapter
    private lateinit var dialogMenu: DynamicDialogMenu<ForumBlackListFragment, ForumBlacklistedUser>

    private val viewModel: ForumBlackListViewModel by viewModels()

    private val adapterListener = object : BaseAdapter.OnItemClickListener<ForumBlacklistedUser> {
        override fun onItemClick(item: ForumBlacklistedUser) {
            showUserMenu(item)
        }

        override fun onItemLongClick(item: ForumBlacklistedUser): Boolean {
            showUserMenu(item)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_forum_blacklist)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clearToolbarScrollFlags()

        dialogMenu = DynamicDialogMenu()
        dialogMenu.apply {
            addItem(getString(R.string.profile)) { _, data ->
                viewModel.openProfile(data)
            }
            addItem(getString(R.string.forum_blacklist_remove)) { _, data ->
                viewModel.removeUser(data)
            }
        }

        adapter = ForumBlackListAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        adapter.setItemClickListener(adapterListener)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        showUsers(state.users)
                    }
                }
                launch {
                    viewModel.userRemoved.collect {
                        showSnackbar(R.string.forum_blacklist_removed)
                    }
                }
            }
        }
    }

    private fun showUserMenu(item: ForumBlacklistedUser) {
        dialogMenu.apply {
            disallowAll()
            if (item.userId > 0) {
                allow(0)
            }
            allow(1)
            show(requireContext(), this@ForumBlackListFragment, item)
        }
    }

    private fun showUsers(users: List<ForumBlacklistedUser>) {
        if (users.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_account_circle)
                        .setTitle(R.string.funny_forum_blacklist_nodata_title)
                        .setDesc(R.string.funny_forum_blacklist_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        adapter.addAll(users)
    }
}
