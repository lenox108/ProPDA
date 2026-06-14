package forpdateam.ru.forpda.ui.fragments.qms

import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.getVecDrawable
import android.app.SearchManager
import android.content.Context
import forpdateam.ru.forpda.common.showSnackbar
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.ui.fragments.qms.adapters.QmsContactsAdapter
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import timber.log.Timber
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import javax.inject.Inject

/**
 * Created by radiationx on 25.08.16.
 */
@AndroidEntryPoint
class QmsContactsFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<QmsContact> {

    @Inject lateinit var notesRepository: NotesRepository
    private lateinit var adapter: QmsContactsAdapter
    private val dialogMenu = DynamicDialogMenu<QmsContactsFragment, QmsContact>()

    private val viewModel: QmsContactsViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_contacts)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        contentController.setFirstLoad(false)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initFabBehavior()
        refreshLayoutStyle(refreshLayout)
        clearToolbarScrollFlags()
        refreshLayout.setOnRefreshListener { viewModel.loadContacts() }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.updatePadding(
                bottom = resources.getDimensionPixelSize(R.dimen.qms_contacts_list_bottom_padding)
        )

        fab.setImageDrawable(requireContext().getVecDrawable(R.drawable.ic_fab_create))
        fab.setOnClickListener { viewModel.openChatCreator() }
        fab.visibility = View.VISIBLE

        dialogMenu.apply {
            addItem(getString(R.string.profile)) { _, data ->
                viewModel.openProfile(data)
            }
            addItem(getString(R.string.add_to_blacklist)) { _, data ->
                viewModel.blockUser(data)
            }
            addItem(getString(R.string.delete)) { _, data ->
                viewModel.deleteDialog(data.id)
            }
            addItem(getString(R.string.create_note)) { _, data ->
                viewModel.createNote(data)
            }
        }

        adapter = QmsContactsAdapter()
        adapter.setOnItemClickListener(this)
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        setRefreshing(state.loading)
                        recyclerView.scrollToPosition(0)
                        adapter.addAll(state.contacts)
                    }
                }
                launch {
                    viewModel.blockUserResult.collect { res ->
                        if (res) {
                            showSnackbar(R.string.user_added_to_blacklist)
                        }
                    }
                }
                launch {
                    viewModel.createNote.collect { (nick, url) ->
                        val title = String.format(getString(R.string.dialogs_Nick), nick)
                        NotesAddPopup.showAddNoteDialog(requireContext(), title, url, notesRepository)
                    }
                }
            }
        }
    }

    override fun onResumeOrShow() {
        super.onResumeOrShow()
        Timber.d("QMS Contacts onResumeOrShow - calling loadContacts")
        viewModel.loadContacts()
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        toolbar.inflateMenu(R.menu.qms_contacts_menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView ?: SearchView(requireContext()).also {
            searchItem.actionView = it
        }

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
        if (searchManager != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))
        }

        searchView.setIconifiedByDefault(true)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.searchLocal(newText)
                return false
            }
        })
        searchView.queryHint = getString(R.string.user)
        menu.add(R.string.blacklist)
                .setOnMenuItemClickListener {
                    viewModel.openBlackList()
                    false
                }
    }

    override fun onBackPressed(): Boolean {
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        return if (searchItem?.isActionViewExpanded == true) {
            recyclerView.adapter = adapter
            searchItem.collapseActionView()
            true
        } else {
            super.onBackPressed()
        }
    }

    override fun onItemClick(item: QmsContact) {
        viewModel.onItemClick(item)
    }

    override fun onItemLongClick(item: QmsContact): Boolean {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(requireContext(), this@QmsContactsFragment, item)
        }
        return false
    }
}
