package forpdateam.ru.forpda.ui.fragments.notes

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.common.getVecDrawable
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import forpdateam.ru.forpda.common.showSnackbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.ui.dp8
import forpdateam.ru.forpda.common.FilePickHelper
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.app.notes.NoteSortMode
import forpdateam.ru.forpda.presentation.notes.NotesViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.fragments.devdb.brand.DevicesFragment
import forpdateam.ru.forpda.ui.fragments.notes.adapters.NoteFolderAdapterDelegate
import forpdateam.ru.forpda.ui.fragments.notes.adapters.NotesAdapter
import forpdateam.ru.forpda.ui.views.ContentController
import forpdateam.ru.forpda.ui.views.FunnyContent
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.model.repository.note.NotesRepository
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import javax.inject.Inject

/**
 * Created by radiationx on 06.09.17.
 */

@AndroidEntryPoint
class NotesFragment : RecyclerFragment(), BaseAdapter.OnItemClickListener<NoteItem>, NoteFolderAdapterDelegate.Listener {
    @Inject lateinit var notesRepository: NotesRepository

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private val importNotesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val files = FilePickHelper.onActivityResult(requireContext(), data)
        if (files.isNotEmpty()) {
            viewModel.importNotes(files[0])
        }
    }

    private val exportNotesLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            requireContext().contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть файл для экспорта")
        }.onSuccess { outputStream ->
            viewModel.exportNotes(outputStream)
        }.onFailure {
            showSnackbar(it.message ?: getString(R.string.error))
        }
    }

    private lateinit var adapter: NotesAdapter
    private var latestState = NotesViewModel.UiState()
    private var addMenuItem: MenuItem? = null
    private var folderSelectMenuItem: MenuItem? = null
    private var searchMenuItem: MenuItem? = null
    private var closeSearchMenuItem: MenuItem? = null
    private var selectMenuItem: MenuItem? = null
    private var selectionCopyMenuItem: MenuItem? = null
    private var selectionEditMenuItem: MenuItem? = null
    private var selectionSelectAllMenuItem: MenuItem? = null
    private var selectionMoveMenuItem: MenuItem? = null
    private var selectionDeleteMenuItem: MenuItem? = null
    private var sortMenuItem: MenuItem? = null
    private var createFolderMenuItem: MenuItem? = null
    private var manageFoldersMenuItem: MenuItem? = null
    private var importMenuItem: MenuItem? = null
    private var exportMenuItem: MenuItem? = null
    private var searchField: EditText? = null
    private var folderManagementDialog: AlertDialog? = null
    private var isSelectionMode = false
    private val selectedNoteIds = linkedSetOf<Long>()

    private val viewModel: NotesViewModel by viewModels()

    private val selectionMode: Boolean
        get() = isSelectionMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_notes)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setCardsBackground()
        clearToolbarScrollFlags()
        adapter = NotesAdapter(this, this, viewModel::onInfoClick)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        refreshLayout.setOnRefreshListener { viewModel.loadNotes() }
        recyclerView.addItemDecoration(DevicesFragment.SpacingItemDecoration(dp8, false))
        titlesWrapper.isClickable = false
        titlesWrapper.setOnClickListener(null)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        latestState = state
                        refreshLayout.isRefreshing = state.refreshing
                        updateSelectionUi()
                        bindNotes(state)
                    }
                }
                launch {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is NotesViewModel.UiEffect.ShowEditPopup ->
                                NotesAddPopup(context, effect.item, notesRepository)
                            NotesViewModel.UiEffect.ShowAddPopup ->
                                NotesAddPopup(context, null, notesRepository)
                            NotesViewModel.UiEffect.ImportDone ->
                                showSnackbar("Заметки успешно импортированы")
                            NotesViewModel.UiEffect.ExportDone ->
                                showSnackbar("Заметки успешно экспортированы")
                        }
                    }
                }
            }
        }
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        addMenuItem = menu
                .add(R.string.add)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_add))
                .setOnMenuItemClickListener {
                    viewModel.addNote()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        folderSelectMenuItem = menu
                .add(R.string.note_select_folder)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_folder))
                .setOnMenuItemClickListener {
                    showFolderSelectorPopup()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        searchMenuItem = menu
                .add(R.string.search)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_search))
                .setOnMenuItemClickListener {
                    showSearch()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        closeSearchMenuItem = menu
                .add(R.string.cancel)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_transcribe_close))
                .setOnMenuItemClickListener {
                    hideSearch()
                    true
                }
                .setVisible(false)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        selectionCopyMenuItem = menu
                .add(R.string.copy_link)
                .setOnMenuItemClickListener {
                    selectedNoteIds.firstOrNull()
                            ?.let { id -> latestState.items.firstOrNull { it.id == id } }
                            ?.let { note ->
                                viewModel.copyLink(note)
                                clearSelection()
                            }
                    true
                }
                .setVisible(false)
        selectionEditMenuItem = menu
                .add(R.string.edit)
                .setOnMenuItemClickListener {
                    selectedNoteIds.firstOrNull()
                            ?.let { id -> latestState.items.firstOrNull { it.id == id } }
                            ?.let { note ->
                                viewModel.editNote(note)
                                clearSelection()
                            }
                    true
                }
                .setVisible(false)
        selectMenuItem = menu
                .add(R.string.note_select_bookmarks)
                .setOnMenuItemClickListener {
                    enterSelectionMode()
                    true
                }
        selectionSelectAllMenuItem = menu
                .add(0, R.id.action_notes_selection_select_all, 0, R.string.note_select_all_bookmarks)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_select_all))
                .setOnMenuItemClickListener {
                    toggleVisibleNotesSelection()
                    true
                }
                .setVisible(false)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        selectionMoveMenuItem = menu
                .add(0, R.id.action_notes_selection_move, 0, R.string.note_move_to_folder)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_folder))
                .setOnMenuItemClickListener {
                    showMoveSelectedToFolderDialog()
                    true
                }
                .setVisible(false)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        selectionDeleteMenuItem = menu
                .add(0, R.id.action_notes_selection_delete, 0, R.string.delete)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_delete))
                .setOnMenuItemClickListener {
                    confirmDeleteSelectedNotes()
                    true
                }
                .setVisible(false)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        sortMenuItem = menu
                .add(R.string.sorting_title)
                .setIcon(requireContext().getVecDrawable(R.drawable.ic_toolbar_sort))
                .setOnMenuItemClickListener {
                    showSortDialog()
                    true
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        createFolderMenuItem = menu
                .add(R.string.note_create_folder)
                .setOnMenuItemClickListener {
                    showCreateFolderDialog()
                    true
                }
        manageFoldersMenuItem = menu
                .add(R.string.note_manage_folders)
                .setOnMenuItemClickListener {
                    showFolderManagementDialog()
                    true
                }
        importMenuItem = menu
                .add(R.string.import_s)
                .setOnMenuItemClickListener {
                    importNotesLauncher.launch(FilePickHelper.pickFile(false))
                    true
                }
        exportMenuItem = menu
                .add(R.string.export_s)
                .setOnMenuItemClickListener {
                    exportNotesLauncher.launch(viewModel.createExportFileName())
                    true
                }
        updateSelectionUi()

    }

    private fun updateFolderSubtitle(state: NotesViewModel.UiState) {
        val folderTitle = when {
            state.includeAllFolders -> getString(R.string.note_tree_view)
            state.selectedFolderId == null -> getString(R.string.note_without_folder)
            else -> state.folders.firstOrNull { it.id == state.selectedFolderId }?.name
                    ?: getString(R.string.note_folder)
        }
        setSubtitle(folderTitle)
    }

    private fun showFolderSelectorPopup() {
        clearSelection()
        PopupMenu(requireContext(), toolbar).apply {
            menu.add(R.string.note_all_folders).setOnMenuItemClickListener {
                viewModel.selectAllFolders()
                true
            }
            menu.add(R.string.note_without_folder).setOnMenuItemClickListener {
                viewModel.selectFolder(null)
                true
            }
            latestState.folders.forEach { folder ->
                menu.add(folder.name).setOnMenuItemClickListener {
                    viewModel.selectFolder(folder.id)
                    true
                }
            }
        }.show()
    }

    private fun showSearch() {
        if (searchField != null) return
        clearSelection()
        val field = EditText(requireContext()).apply {
            hint = getString(R.string.note_search_hint)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(requireContext().getColorFromAttr(R.attr.default_text_color))
            setHintTextColor(requireContext().getColorFromAttr(R.attr.second_text_color))
            background = ColorDrawable(Color.TRANSPARENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                textCursorDrawable = requireContext().getDrawable(R.drawable.text_cursor)
            }
            setText(latestState.searchQuery)
            setSelectAllOnFocus(false)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    clearSelection()
                    viewModel.setSearchQuery(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        searchField = field
        searchMenuItem?.isVisible = false
        closeSearchMenuItem?.isVisible = true
        titlesWrapper.visibility = View.GONE
        toolbar.addView(field, androidx.appcompat.widget.Toolbar.LayoutParams(
                androidx.appcompat.widget.Toolbar.LayoutParams.MATCH_PARENT,
                androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
        ))
        field.requestFocus()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearch() {
        searchField?.let { field ->
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(field.windowToken, 0)
            toolbar.removeView(field)
        }
        searchField = null
        viewModel.clearSearch()
        titlesWrapper.visibility = View.VISIBLE
        updateSelectionUi()
    }

    private fun showSortDialog() {
        clearSelection()
        val modes = arrayOf(NoteSortMode.CREATED_DESC, NoteSortMode.UPDATED_DESC, NoteSortMode.TITLE_ASC)
        val titles = arrayOf(
                getString(R.string.note_sort_created),
                getString(R.string.note_sort_updated),
                getString(R.string.note_sort_title)
        )
        val checkedIndex = modes.indexOf(latestState.sortMode).takeIf { it >= 0 } ?: 0
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sorting_title)
                .setSingleChoiceItems(titles, checkedIndex) { dialog, which ->
                    viewModel.setSortMode(modes[which])
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showCreateFolderDialog(onCreated: ((NoteFolder) -> Unit)? = null) {
        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.note_folder_name)
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_create_folder)
                .setView(inputLayout)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                inputLayout.error = getString(R.string.note_folder_name_empty)
                return@setOnClickListener
            }
            inputLayout.error = null
            viewModel.createFolder(name, onCreated)
            dialog.dismiss()
        }
    }

    private fun showMoveToFolderDialog(note: NoteItem) {
        val folders = latestState.folders
        val titles = listOf(getString(R.string.note_without_folder)) + folders.map { it.name }
        val checkedIndex = note.folderId?.let { id -> folders.indexOfFirst { it.id == id } + 1 }
                ?.takeIf { it > 0 } ?: 0
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_move_to_folder)
                .setSingleChoiceItems(titles.toTypedArray(), checkedIndex) { dialog, which ->
                    viewModel.moveNoteToFolder(note.id, if (which == 0) null else folders[which - 1].id)
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.note_create_folder) { _, _ ->
                    showCreateFolderDialog { folder -> viewModel.moveNoteToFolder(note.id, folder.id) }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showMoveSelectedToFolderDialog() {
        val ids = selectedNoteIds.toList()
        if (ids.isEmpty()) return
        val folders = latestState.folders
        val titles = listOf(getString(R.string.note_without_folder)) + folders.map { it.name }
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_move_to_folder)
                .setItems(titles.toTypedArray()) { dialog, which ->
                    viewModel.moveNotesToFolder(ids, if (which == 0) null else folders[which - 1].id)
                    clearSelection()
                    dialog.dismiss()
                }
                .setNeutralButton(R.string.note_create_folder) { _, _ ->
                    showCreateFolderDialog { folder ->
                        viewModel.moveNotesToFolder(ids, folder.id)
                        clearSelection()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun confirmDeleteSelectedNotes() {
        val ids = selectedNoteIds.toList()
        if (ids.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_delete_selected_title)
                .setMessage(getString(R.string.note_delete_selected_message, ids.size))
                .setPositiveButton(R.string.delete) { _, _ ->
                    viewModel.deleteNotes(ids)
                    clearSelection()
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showFolderManagementDialog() {
        if (folderManagementDialog?.isShowing == true) return
        val folders = latestState.folders
        if (folders.isEmpty()) {
            Toast.makeText(requireContext(), R.string.funny_notes_nodata_title, Toast.LENGTH_SHORT).show()
            return
        }
        folderManagementDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_manage_folders)
                .setItems(folders.map { it.name }.toTypedArray()) { _, which ->
                    showFolderActionsDialog(folders[which])
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
                .also { dialog ->
                    dialog.setOnDismissListener { folderManagementDialog = null }
                }
    }

    private fun showFolderActionsDialog(folder: NoteFolder) {
        val actions = arrayOf(getString(R.string.note_rename_folder), getString(R.string.note_delete_folder))
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(folder.name)
                .setItems(actions) { _, which ->
                    when (which) {
                        0 -> showRenameFolderDialog(folder)
                        1 -> confirmDeleteFolder(folder)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun showRenameFolderDialog(folder: NoteFolder) {
        val input = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            setText(folder.name)
            selectAll()
        }
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.note_folder_name)
            val padding = resources.getDimensionPixelSize(R.dimen.content_padding_horizontal)
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_rename_folder)
                .setView(inputLayout)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                inputLayout.error = getString(R.string.note_folder_name_empty)
                return@setOnClickListener
            }
            inputLayout.error = null
            viewModel.renameFolder(folder.id, name)
            dialog.dismiss()
        }
    }

    private fun confirmDeleteFolder(folder: NoteFolder) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.note_delete_folder)
                .setMessage(getString(R.string.note_delete_folder_message, folder.name))
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteFolder(folder.id) }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }

    private fun bindNotes(state: NotesViewModel.UiState) {
        val filteredItems = filterNotes(state.items, state.searchQuery)
        val searchActive = state.searchQuery.isNotBlank()
        val visibleFolders = if (!searchActive) {
            state.folders
        } else {
            val visibleFolderIds = filteredItems.mapNotNull { it.folderId }.toSet()
            state.folders.filter { visibleFolderIds.contains(it.id) }
        }
        val hasVisibleTree = filteredItems.isNotEmpty() || (state.includeAllFolders && visibleFolders.isNotEmpty())
        if (!hasVisibleTree) {
            if (!contentController.contains(ContentController.TAG_NO_DATA)) {
                val funnyContent = FunnyContent(requireContext())
                        .setImage(R.drawable.ic_bookmark)
                        .setTitle(R.string.funny_notes_nodata_title)
                contentController.addContent(funnyContent, ContentController.TAG_NO_DATA)
            }
            contentController.showContent(ContentController.TAG_NO_DATA)
        } else {
            contentController.hideContent(ContentController.TAG_NO_DATA)
        }
        adapter.bindItems(
                notes = filteredItems,
                folders = visibleFolders,
                expandedFolderIds = if (searchActive) {
                    state.expandedFolderIds + filteredItems.mapNotNull { it.folderId }
                } else {
                    state.expandedFolderIds
                },
                includeAllFolders = state.includeAllFolders,
                selectedFolderId = state.selectedFolderId,
                selectionMode = selectionMode,
                selectedNoteIds = selectedNoteIds,
                infoList = state.info,
                foldersTitle = getString(R.string.note_folders_section),
                withoutFolderTitle = getString(R.string.note_without_folder)
        )
    }

    private fun filterNotes(items: List<NoteItem>, query: String): List<NoteItem> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return items
        return items.filter { item ->
            item.title.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    item.content.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    item.link.orEmpty().contains(normalizedQuery, ignoreCase = true)
        }
    }

    private fun getVisibleNoteIds(state: NotesViewModel.UiState = latestState): Set<Long> {
        val filteredItems = filterNotes(state.items, state.searchQuery)
        val expandedFolderIds = if (state.searchQuery.isNotBlank()) {
            state.expandedFolderIds + filteredItems.mapNotNull { it.folderId }
        } else {
            state.expandedFolderIds
        }
        return when {
            state.includeAllFolders -> filteredItems
                    .filter { note -> note.folderId == null || expandedFolderIds.contains(note.folderId) }
                    .map { it.id }
                    .toSet()
            state.selectedFolderId == null -> filteredItems.map { it.id }.toSet()
            expandedFolderIds.contains(state.selectedFolderId) -> filteredItems.map { it.id }.toSet()
            else -> emptySet()
        }
    }

    override fun onItemClick(item: NoteItem) {
        if (selectionMode) {
            toggleSelection(item.id)
        } else {
            viewModel.onItemClick(item)
        }
    }

    override fun onItemLongClick(item: NoteItem): Boolean {
        if (selectionMode) {
            toggleSelection(item.id)
        } else {
            enterSelectionMode(item.id)
        }
        return true
    }

    private fun enterSelectionMode(initialNoteId: Long? = null) {
        if (searchField != null) hideSearch()
        isSelectionMode = true
        initialNoteId?.let { selectedNoteIds.add(it) }
        updateSelectionUi()
        bindNotes(latestState)
    }

    private fun toggleSelection(noteId: Long) {
        if (!selectedNoteIds.add(noteId)) {
            selectedNoteIds.remove(noteId)
        }
        if (selectedNoteIds.isEmpty()) {
            isSelectionMode = false
        }
        updateSelectionUi()
        bindNotes(latestState)
    }

    private fun toggleVisibleNotesSelection() {
        val visibleNoteIds = getVisibleNoteIds()
        if (visibleNoteIds.isEmpty()) return
        if (selectedNoteIds.containsAll(visibleNoteIds)) {
            selectedNoteIds.removeAll(visibleNoteIds)
        } else {
            selectedNoteIds.addAll(visibleNoteIds)
        }
        if (selectedNoteIds.isEmpty()) {
            isSelectionMode = false
        }
        updateSelectionUi()
        bindNotes(latestState)
    }

    private fun clearSelection() {
        if (!isSelectionMode && selectedNoteIds.isEmpty()) return
        isSelectionMode = false
        selectedNoteIds.clear()
        updateSelectionUi()
        bindNotes(latestState)
    }

    private fun updateSelectionUi() {
        if (isSelectionMode) {
            selectedNoteIds.retainAll(latestState.items.map { it.id }.toSet())
        }
        val selectedCount = selectedNoteIds.size
        val inSelectionMode = isSelectionMode
        val visibleNoteIds = getVisibleNoteIds()
        val allVisibleNotesSelected = visibleNoteIds.isNotEmpty() && selectedNoteIds.containsAll(visibleNoteIds)
        addMenuItem?.isVisible = !inSelectionMode
        folderSelectMenuItem?.isVisible = !inSelectionMode
        sortMenuItem?.isVisible = !inSelectionMode
        createFolderMenuItem?.isVisible = !inSelectionMode
        manageFoldersMenuItem?.isVisible = !inSelectionMode
        importMenuItem?.isVisible = !inSelectionMode
        exportMenuItem?.isVisible = !inSelectionMode
        selectMenuItem?.isVisible = !inSelectionMode && searchField == null
        selectionSelectAllMenuItem?.isVisible = inSelectionMode
        selectionMoveMenuItem?.isVisible = inSelectionMode
        selectionDeleteMenuItem?.isVisible = inSelectionMode
        selectionCopyMenuItem?.isVisible = inSelectionMode && selectedCount == 1
        selectionEditMenuItem?.isVisible = inSelectionMode && selectedCount == 1
        selectionSelectAllMenuItem?.isEnabled = visibleNoteIds.isNotEmpty()
        selectionSelectAllMenuItem?.setTitle(
                if (allVisibleNotesSelected) {
                    R.string.note_clear_visible_selection
                } else {
                    R.string.note_select_all_bookmarks
                }
        )
        selectionCopyMenuItem?.isEnabled = selectedCount == 1
        selectionEditMenuItem?.isEnabled = selectedCount == 1
        selectionMoveMenuItem?.isEnabled = selectedCount > 0
        selectionDeleteMenuItem?.isEnabled = selectedCount > 0
        searchMenuItem?.isVisible = !inSelectionMode && searchField == null
        closeSearchMenuItem?.isVisible = !inSelectionMode && searchField != null
        if (inSelectionMode) {
            setTitle(
                    if (selectedCount == 0) {
                        getString(R.string.note_select_bookmarks)
                    } else {
                        getString(R.string.note_selected_count, selectedCount)
                    }
            )
            setSubtitle(null)
        } else {
            setTitle(null)
            updateFolderSubtitle(latestState)
        }
    }

    override fun onFolderClick(folder: NoteFolder) {
        viewModel.toggleFolder(folder.id)
    }

    override fun onFolderRename(folder: NoteFolder) {
        showRenameFolderDialog(folder)
    }

    override fun onFolderDelete(folder: NoteFolder) {
        confirmDeleteFolder(folder)
    }
}
