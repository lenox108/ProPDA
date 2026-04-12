package forpdateam.ru.forpda.ui.fragments.qms

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.common.ForPdaCoil

import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.presentation.qms.themes.QmsThemesViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.fragments.qms.adapters.QmsThemesAdapter
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter

/**
 * Created by radiationx on 25.08.16.
 */
class QmsThemesFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<QmsTheme> {

    private lateinit var blackListMenuItem: MenuItem
    private lateinit var noteMenuItem: MenuItem
    private lateinit var adapter: QmsThemesAdapter
    private val dialogMenu = DynamicDialogMenu<QmsThemesFragment, QmsTheme>()

    private val viewModel: QmsThemesViewModel by viewModels {
        val args = arguments ?: Bundle()
        QmsThemesViewModel.Factory(
                args.getInt(USER_ID_ARG),
                args.getString(USER_AVATAR_ARG),
                App.get().Di().qmsInteractor,
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().errorHandler
        )
    }

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_dialogs)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        contentController.setFirstLoad(false)
        return viewFragment
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initFabBehavior()
        setScrollFlagsEnterAlways()

        refreshLayout.setOnRefreshListener { viewModel.loadThemes() }
        recyclerView.layoutManager = LinearLayoutManager(context)

        fab.setImageDrawable(App.getVecDrawable(context, R.drawable.ic_fab_create))
        fab.setOnClickListener { viewModel.openChat() }
        fab.visibility = View.VISIBLE

        dialogMenu.apply {
            addItem(getString(R.string.delete)) { _, data ->
                viewModel.deleteTheme(data.id)
            }
            addItem(getString(R.string.create_note)) { _, data ->
                viewModel.createThemeNote(data)
            }
        }

        adapter = QmsThemesAdapter()
        adapter.setOnItemClickListener(this)
        recyclerView.adapter = adapter
        viewModel.loadThemes()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        setRefreshing(state.loading)
                        val avatar = state.toolbarAvatarUrl
                        if (avatar.isNullOrEmpty()) {
                            toolbarImageView.visibility = View.GONE
                            toolbarImageView.setOnClickListener(null)
                        } else {
                            ForPdaCoil.loadInto(toolbarImageView, avatar)
                            toolbarImageView.visibility = View.VISIBLE
                            toolbarImageView.setOnClickListener {
                                viewModel.openProfile(viewModel.themesUserId)
                            }
                            toolbarImageView.contentDescription = App.get().getString(R.string.user_avatar)
                        }
                        state.themes?.let { showThemesData(it) }
                    }
                }
                launch {
                    viewModel.blockDone.collect {
                        Toast.makeText(context, R.string.user_added_to_blacklist, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.noteEffect.collect { effect ->
                        when (effect) {
                            is QmsThemesViewModel.NoteEffect.ForUser -> {
                                val title = String.format(getString(R.string.dialogs_Nick), effect.nick)
                                NotesAddPopup.showAddNoteDialog(context, title, effect.url)
                            }
                            is QmsThemesViewModel.NoteEffect.ForTheme -> {
                                val title = String.format(getString(R.string.dialog_Title_Nick), effect.name, effect.nick)
                                NotesAddPopup.showAddNoteDialog(context, title, effect.url)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        blackListMenuItem = menu
                .add(R.string.add_to_blacklist)
                .setOnMenuItemClickListener {
                    viewModel.blockUser()
                    false
                }
        noteMenuItem = menu
                .add(R.string.create_note)
                .setOnMenuItemClickListener {
                    viewModel.createNote()
                    true
                }
        refreshToolbarMenuItems(false)
    }

    override fun refreshToolbarMenuItems(enable: Boolean) {
        super.refreshToolbarMenuItems(enable)
        if (enable) {
            blackListMenuItem.isEnabled = true
            noteMenuItem.isEnabled = true
        } else {
            blackListMenuItem.isEnabled = false
            noteMenuItem.isEnabled = false
        }
    }

    override fun setRefreshing(isRefreshing: Boolean) {
        super.setRefreshing(isRefreshing)
        refreshToolbarMenuItems(!isRefreshing)
    }

    private fun showThemesData(data: QmsThemes) {
        recyclerView.scrollToPosition(0)

        setTabTitle(String.format(getString(R.string.dialogs_Nick), data.nick))
        setTitle(data.nick)

        adapter.addAll(data.themes)
        adapter.notifyDataSetChanged()
    }

    override fun onItemClick(item: QmsTheme) {
        viewModel.onItemClick(item)
    }

    override fun onItemLongClick(item: QmsTheme): Boolean {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(context, this@QmsThemesFragment, item)
        }
        return false
    }

    companion object {
        const val USER_ID_ARG = "USER_ID_ARG"
        const val USER_AVATAR_ARG = "USER_AVATAR_ARG"
    }
}
