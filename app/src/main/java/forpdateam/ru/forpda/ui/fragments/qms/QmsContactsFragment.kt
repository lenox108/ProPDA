package forpdateam.ru.forpda.ui.fragments.qms

import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.getVecDrawable
import android.app.SearchManager
import android.content.Context
import forpdateam.ru.forpda.common.showSnackbar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel
import forpdateam.ru.forpda.ui.compose.screens.QmsContactsScreen
import forpdateam.ru.forpda.ui.compose.theme.ForpdaTheme
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by radiationx on 25.08.16.
 *
 * Пилот миграции на Compose (§3.2): содержимое списка рендерит [QmsContactsScreen]
 * в ComposeView вместо RecyclerView. Общий chrome вкладки (тулбар/поиск/FAB/back/
 * appbar) остаётся на legacy [RecyclerFragment] — он уже темизирован (этапы A–O).
 * Pull-to-refresh делает сам Compose (PullToRefreshBox), поэтому legacy
 * SwipeRefreshLayout отключается.
 */
@AndroidEntryPoint
class QmsContactsFragment : RecyclerFragment() {

    @Inject lateinit var notesRepository: NotesRepository
    @Inject lateinit var menuShortcutPinner: forpdateam.ru.forpda.model.interactors.other.MenuShortcutPinner
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
        clearToolbarScrollFlags()

        fab.setImageDrawable(requireContext().getVecDrawable(R.drawable.ic_fab_create))
        fab.setOnClickListener { viewModel.openChatCreator() }
        fab.visibility = View.VISIBLE

        dialogMenu.apply {
            addItem(getString(R.string.profile)) { _, data -> viewModel.openProfile(data) }
            addItem(getString(R.string.add_to_blacklist)) { _, data -> viewModel.blockUser(data) }
            addItem(getString(R.string.delete)) { _, data -> viewModel.deleteDialog(data.id) }
            addItem(getString(R.string.create_note)) { _, data -> viewModel.createNote(data) }
            addItem(getString(R.string.other_menu_pin_to_menu)) { _, data ->
                menuShortcutPinner.pinDialog(data.id, data.nick.orEmpty())
                showSnackbar(R.string.other_menu_shortcut_added)
            }
        }

        mountComposeList()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.blockUserResult.collect { res ->
                        if (res) showSnackbar(R.string.user_added_to_blacklist)
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

    /**
     * Заменяет весь legacy SwipeRefreshLayout (корень fragment_base_list, несёт
     * appbar-scrolling behavior) на ComposeView в родителе-CoordinatorLayout,
     * наследуя его LayoutParams. Подмену ВНУТРИ SwipeRefreshLayout делать нельзя —
     * он кэширует scroll-target и не измеряет новый child (0×0). Pull-to-refresh
     * теперь на стороне Compose (PullToRefreshBox).
     */
    private fun mountComposeList() {
        val bottomPaddingDp =
                resources.getDimension(R.dimen.qms_contacts_list_bottom_padding) /
                        resources.displayMetrics.density
        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val circleAvatars by topicPreferencesHolder.observeCircleAvatarsFlow()
                        .collectAsStateWithLifecycle(
                                initialValue = topicPreferencesHolder.getCircleAvatars(),
                        )
                ForpdaTheme {
                    QmsContactsScreen(
                            viewModel = viewModel,
                            onItemClick = { viewModel.onItemClick(it) },
                            onItemLongClick = { showContactMenu(it) },
                            circleAvatars = circleAvatars,
                            bottomPadding = bottomPaddingDp.dp,
                    )
                }
            }
        }
        val parent = refreshLayout.parent as ViewGroup
        val index = parent.indexOfChild(refreshLayout)
        val lp = refreshLayout.layoutParams
        parent.removeView(refreshLayout)
        parent.addView(composeView, index, lp)
    }

    private fun showContactMenu(item: QmsContact) {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(requireContext(), this@QmsContactsFragment, item)
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
            override fun onQueryTextSubmit(query: String): Boolean = false
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
            searchItem.collapseActionView()
            true
        } else {
            super.onBackPressed()
        }
    }

    // Read-only зеркало onBackPressed: перехватываем «назад» пока раскрыт поиск
    // (см. hasBackHandling в TabFragment).
    override fun hasBackHandling(): Boolean =
            toolbar.menu.findItem(R.id.action_search)?.isActionViewExpanded == true
}
