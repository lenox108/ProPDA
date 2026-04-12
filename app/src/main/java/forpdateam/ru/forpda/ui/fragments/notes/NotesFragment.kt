package forpdateam.ru.forpda.ui.fragments.notes

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.presentation.notes.NotesViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.fragments.notes.adapters.NotesAdapter
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter

/**
 * Created by radiationx on 06.09.17.
 */

class NotesFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<NoteItem> {

    private val importNotesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val files = FilePickHelper.onActivityResult(context, data)
        if (files.isNotEmpty()) {
            viewModel.importNotes(files[0])
        }
    }

    private lateinit var adapter: NotesAdapter
    private val dialogMenu = DynamicDialogMenu<NotesFragment, NoteItem>()

    private val viewModel: NotesViewModel by viewModels {
        NotesViewModel.Factory(
                App.get().Di().notesRepository,
                App.get().Di().closeableInfoHolder,
                App.get().Di().router,
                App.get().Di().linkHandler,
                App.get().Di().errorHandler
        )
    }

    init {
        configuration.defaultTitle = App.get().getString(R.string.fragment_title_notes)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setCardsBackground()
        setScrollFlagsEnterAlways()
        adapter = NotesAdapter(this, viewModel::onInfoClick)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        refreshLayout.setOnRefreshListener { viewModel.loadNotes() }
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(App.px8, false))

        dialogMenu.apply {
            addItem(getString(R.string.copy_link)) { _, data ->
                viewModel.copyLink(data)
            }
            addItem(getString(R.string.edit)) { _, data ->
                viewModel.editNote(data)
            }
            addItem(getString(R.string.delete)) { _, data ->
                viewModel.deleteNote(data.id)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        refreshLayout.isRefreshing = state.refreshing
                        bindNotes(state.items, state.info)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is NotesViewModel.UiEffect.ShowEditPopup ->
                                NotesAddPopup(context, effect.item)
                            NotesViewModel.UiEffect.ShowAddPopup ->
                                NotesAddPopup(context, null)
                            NotesViewModel.UiEffect.ImportDone ->
                                Toast.makeText(context, "Заметки успешно импортированы", Toast.LENGTH_SHORT).show()
                            is NotesViewModel.UiEffect.ExportDone ->
                                Toast.makeText(context, "Заметки успешно экспортированы в ${effect.path}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu
                .add(R.string.add)
                .setIcon(App.getVecDrawable(context, R.drawable.ic_toolbar_add))
                .setOnMenuItemClickListener {
                    viewModel.addNote()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu
                .add(R.string.import_s)
                .setOnMenuItemClickListener {
                    importNotesLauncher.launch(FilePickHelper.pickFile(false))
                    true
                }
        menu
                .add(R.string.export_s)
                .setOnMenuItemClickListener {
                    App.get().checkStoragePermission({ viewModel.exportNotes() }, App.getActivity())
                    true
                }

    }

    private fun bindNotes(items: List<NoteItem>, info: List<CloseableInfo>) {
        if (items.isEmpty()) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(context)
                        .setImage(R.drawable.ic_bookmark)
                        .setTitle(R.string.funny_notes_nodata_title)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        adapter.bindItems(items, info)
    }

    override fun onItemClick(item: NoteItem) {
        viewModel.onItemClick(item)
    }

    override fun onItemLongClick(item: NoteItem): Boolean {
        dialogMenu.apply {
            disallowAll()
            allowAll()
            show(context, this@NotesFragment, item)
        }
        return true
    }
}
