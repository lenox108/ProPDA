package forpdateam.ru.forpda.ui.fragments.qms

import android.app.SearchManager
import android.content.Context
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.fragments.qms.adapters.QmsContactsAdapter
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter

/**
 * Created by radiationx on 25.08.16.
 */
class QmsContactsFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<QmsContact> {

    private lateinit var adapter: QmsContactsAdapter
    private val dialogMenu = DynamicDialogMenu<QmsContactsFragment, QmsContact>()

    private val viewModel: QmsContactsViewModel by viewModels {
        QmsContactsViewModel.Factory(
                App.get().Di().qmsInteractor,
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().countersHolder,
                App.get().Di().eventsRepository,
                App.get().Di().errorHandler
        )
    }

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_contacts)
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
        setScrollFlagsEnterAlways()
        refreshLayout.setOnRefreshListener { viewModel.loadContacts() }
        recyclerView.layoutManager = LinearLayoutManager(context)

        fab.setImageDrawable(App.getVecDrawable(context, R.drawable.ic_fab_create))
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
        viewModel.loadContacts()

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
                            Toast.makeText(context, R.string.user_added_to_blacklist, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                launch {
                    viewModel.createNote.collect { (nick, url) ->
                        val title = String.format(getString(R.string.dialogs_Nick), nick)
                        NotesAddPopup.showAddNoteDialog(context, title, url)
                    }
                }
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        toolbar.inflateMenu(R.menu.qms_contacts_menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        val searchManager = activity?.getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity?.componentName))

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
        return if (toolbar.menu.findItem(R.id.action_search).isActionViewExpanded) {
            recyclerView.adapter = adapter
            toolbar.collapseActionView()
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
            show(context, this@QmsContactsFragment, item)
        }
        return false
    }
}
