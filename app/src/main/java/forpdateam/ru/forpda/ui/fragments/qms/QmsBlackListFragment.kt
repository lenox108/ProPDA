package forpdateam.ru.forpda.ui.fragments.qms

import android.os.Bundle
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.*
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.entity.remote.others.user.ForumUser
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.blacklist.QmsBlackListViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.qms.adapters.QmsContactsAdapter
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter

/**
 * Created by radiationx on 22.03.17.
 */

class QmsBlackListFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<QmsContact> {

    private lateinit var nickField: AppCompatAutoCompleteTextView
    private lateinit var adapter: QmsContactsAdapter
    private val dialogMenu = DynamicDialogMenu<QmsBlackListFragment, QmsContact>()

    private val viewModel: QmsBlackListViewModel by viewModels {
        QmsBlackListViewModel.Factory(
                App.get().Di().qmsInteractor,
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().errorHandler
        )
    }

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_blacklist)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val viewStub = findViewById(R.id.toolbar_content) as ViewStub
        viewStub.layoutResource = R.layout.toolbar_qms_black_list
        viewStub.inflate()
        nickField = findViewById(R.id.qms_black_list_nick_field) as AppCompatAutoCompleteTextView
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setScrollFlagsEnterAlways()
        nickField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                viewModel.searchUser(s.toString())
            }
        })

        refreshLayout.setOnRefreshListener { viewModel.loadContacts() }
        recyclerView.layoutManager = LinearLayoutManager(context)

        dialogMenu.apply {
            addItem(getString(R.string.profile)) { _, data ->
                viewModel.openProfile(data)
            }
            addItem(getString(R.string.dialogs)) { _, data ->
                viewModel.openDialogs(data)
            }
            addItem(getString(R.string.delete)) { _, data ->
                viewModel.unBlockUser(data.id)
            }
        }

        adapter = QmsContactsAdapter()
        recyclerView.adapter = adapter
        adapter.setOnItemClickListener(this)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        setRefreshing(state.loading)
                        bindContactsList(state.contacts)
                        bindSuggestions(state.nickSuggestions)
                    }
                }
                launch {
                    viewModel.clearNick.collect {
                        nickField.setText("")
                    }
                }
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.add)
                .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_add))
                .setOnMenuItemClickListener {
                    var nick = ""
                    if (nickField.text != null)
                        nick = nickField.text.toString()
                    viewModel.blockUser(nick)
                    false
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun bindContactsList(items: List<QmsContact>) {
        if (items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(context)
                        .setImage(R.drawable.ic_contacts)
                        .setTitle(R.string.funny_blacklist_nodata_title)
                        .setDesc(R.string.funny_blacklist_nodata_desc)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        recyclerView.scrollToPosition(0)
        adapter.addAll(items)
    }

    private fun bindSuggestions(items: List<ForumUser>) {
        if (context == null) return
        val nicks = items.map { it.nick.orEmpty() }
        nickField.setAdapter(ArrayAdapter(context!!, android.R.layout.simple_dropdown_item_1line, nicks))
    }

    override fun onItemClick(item: QmsContact) {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(context, this@QmsBlackListFragment, item)
        }
    }

    override fun onItemLongClick(item: QmsContact): Boolean {
        onItemClick(item)
        return false
    }
}
