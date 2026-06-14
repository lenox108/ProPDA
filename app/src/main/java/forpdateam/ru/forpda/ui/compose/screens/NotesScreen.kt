package forpdateam.ru.forpda.ui.compose.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.presentation.notes.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    onNavigateToLink: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effects by viewModel.effects.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val exportNotesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Не удалось открыть файл для экспорта")
        }.onSuccess { outputStream ->
            viewModel.exportNotes(outputStream)
        }.onFailure { error ->
            Toast.makeText(context, error.message ?: "Ошибка экспорта", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedNote by remember { mutableStateOf<NoteItem?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogContent by remember { mutableStateOf("") }
    var dialogLink by remember { mutableStateOf("") }

    // Handle effects
    LaunchedEffect(effects) {
        effects?.let { effect ->
            when (effect) {
                is NotesViewModel.UiEffect.ShowEditPopup -> {
                    selectedNote?.let {
                        dialogTitle = it.title ?: ""
                        dialogContent = it.content ?: ""
                        dialogLink = it.link ?: ""
                    }
                    showNoteDialog = true
                }
                is NotesViewModel.UiEffect.ShowAddPopup -> {
                    dialogTitle = ""
                    dialogContent = ""
                    dialogLink = ""
                    showNoteDialog = true
                }
                is NotesViewModel.UiEffect.ImportDone -> {
                    Toast.makeText(context, "Закладки импортированы", Toast.LENGTH_SHORT).show()
                }
                NotesViewModel.UiEffect.ExportDone -> {
                    Toast.makeText(context, "Закладки экспортированы", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fragment_title_notes)) },
                actions = {
                    IconButton(onClick = { viewModel.addNote() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                    IconButton(onClick = { exportNotesLauncher.launch(viewModel.createExportFileName()) }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.export_s))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addNote() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.items.isEmpty()) {
                EmptyNotesState()
            } else {
                NotesList(
                    notes = uiState.items,
                    closeableInfos = uiState.info,
                    onNoteClick = { viewModel.onItemClick(it) },
                    onNoteLongClick = { note ->
                        selectedNote = note
                        showContextMenu = true
                    },
                    onCloseableInfoClick = { viewModel.onInfoClick(it) },
                    onRefresh = { viewModel.loadNotes() }
                )

                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit)) },
                        onClick = {
                            showContextMenu = false
                            selectedNote?.let { viewModel.editNote(it) }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            showContextMenu = false
                            selectedNote?.let { viewModel.deleteNote(it.id) }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }

                if (showNoteDialog) {
                    AlertDialog(
                        onDismissRequest = { showNoteDialog = false },
                        title = { Text(if (selectedNote != null) "Редактировать" else "Добавить") },
                        text = { Text("Функция редактирования/добавления в разработке") },
                        confirmButton = {
                            TextButton(onClick = { showNoteDialog = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotesList(
    notes: List<NoteItem>,
    closeableInfos: List<CloseableInfo>,
    onNoteClick: (NoteItem) -> Unit,
    onNoteLongClick: (NoteItem) -> Unit,
    onCloseableInfoClick: (CloseableInfo) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Closeable infos
        items(closeableInfos) { info ->
            CloseableInfoCard(
                info = info,
                onClose = { onCloseableInfoClick(info) }
            )
        }

        // Notes
        items(notes) { note ->
            NoteCard(
                note = note,
                onClick = { onNoteClick(note) },
                onLongClick = { onNoteLongClick(note) }
            )
        }
    }
}

@Composable
fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = note.title ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val content = note.content
            if (!content.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val link = note.link
            if (!link.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = link,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CloseableInfoCard(
    info: CloseableInfo,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Info #${info.id}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun EmptyNotesState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.funny_notes_nodata_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
