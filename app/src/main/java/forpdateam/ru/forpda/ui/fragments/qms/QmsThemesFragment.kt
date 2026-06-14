package forpdateam.ru.forpda.ui.fragments.qms

import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.getVecDrawable
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import forpdateam.ru.forpda.common.showSnackbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.qms.QmsTheme
import forpdateam.ru.forpda.entity.remote.qms.QmsThemes
import forpdateam.ru.forpda.presentation.qms.themes.QmsThemesViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.notes.NotesAddPopup
import forpdateam.ru.forpda.ui.fragments.qms.adapters.QmsThemesAdapter
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import javax.inject.Inject

/**
 * Created by radiationx on 25.08.16.
 */
@AndroidEntryPoint
class QmsThemesFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<QmsTheme> {

    @Inject lateinit var notesRepository: NotesRepository
    private lateinit var blackListMenuItem: MenuItem
    private lateinit var noteMenuItem: MenuItem
    private lateinit var adapter: QmsThemesAdapter
    private val dialogMenu = DynamicDialogMenu<QmsThemesFragment, QmsTheme>()

    private val viewModel: QmsThemesViewModel by viewModels()

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_dialogs)
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

        refreshLayout.setOnRefreshListener { viewModel.loadThemes() }
        recyclerView.layoutManager = LinearLayoutManager(context)

        fab.setImageDrawable(requireContext().getVecDrawable(R.drawable.ic_fab_create))
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
                        renderState(state)
                        state.themes?.let { showThemesData(it) }
                    }
                }
                launch {
                    viewModel.blockDone.collect {
                        showSnackbar(R.string.user_added_to_blacklist)
                    }
                }
                launch {
                    viewModel.noteEffect.collect { effect ->
                        when (effect) {
                            is QmsThemesViewModel.NoteEffect.ForUser -> {
                                val title = String.format(getString(R.string.dialogs_Nick), effect.nick)
                                NotesAddPopup.showAddNoteDialog(requireContext(), title, effect.url, notesRepository)
                            }
                            is QmsThemesViewModel.NoteEffect.ForTheme -> {
                                val title = String.format(getString(R.string.dialog_Title_Nick), effect.name, effect.nick)
                                NotesAddPopup.showAddNoteDialog(requireContext(), title, effect.url, notesRepository)
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

    private fun renderState(state: QmsThemesViewModel.UiState) {
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
            toolbarImageView.contentDescription = getString(R.string.user_avatar)
        }
    }

    private fun showThemesData(data: QmsThemes) {
        recyclerView.scrollToPosition(0)

        setTabTitle(String.format(getString(R.string.dialogs_Nick), data.nick))
        setTitle(data.nick)

        adapter.addAll(data.themes)
    }

    override fun onItemClick(item: QmsTheme) {
        viewModel.onItemClick(item)
    }

    override fun onItemLongClick(item: QmsTheme): Boolean {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(requireContext(), this@QmsThemesFragment, item)
        }
        return false
    }

    companion object {
        const val USER_ID_ARG = "USER_ID_ARG"
        const val USER_AVATAR_ARG = "USER_AVATAR_ARG"
    }
}
