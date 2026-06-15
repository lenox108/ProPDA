package forpdateam.ru.forpda.ui.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel

/**
 * Compose view for the QMS contacts list.
 *
 * Consumes the existing [QmsContactsViewModel] (which already exposes a
 * `StateFlow<UiState>` and the navigation/error/block-note effects as
 * `SharedFlow`s). Wires the same set of events that the legacy
 * RecyclerView fragment in `ui/fragments/qms/QmsContactsFragment.kt`
 * handles:
 *
 *  - click on a row        → onItemClick
 *  - delete                → handled by the fragment host (not yet wired)
 *  - block user            → handled by the fragment host (not yet wired)
 *  - create note           → handled by the fragment host (not yet wired)
 *  - open profile          → handled by the fragment host (not yet wired)
 *  - open black list       → handled by the fragment host (not yet wired)
 *  - open chat creator     → handled by the fragment host (not yet wired)
 *
 * The actual navigation/router work stays in the
 * `QmsContactsComposeFragment` host so the Composable remains a
 * presentational layer. This file therefore contains the
 * Compose-rendering side of the §3.2 migration; turning the existing
 * `QmsContactsFragment` into a thin host that mounts this Composable
 * is a follow-up commit (see REFACTOR_PLAN §3.2 step 2).
 */
@Composable
fun QmsContactsScreen(
        viewModel: QmsContactsViewModel,
        onItemClick: (QmsContact) -> Unit = {},
        modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.contacts.isEmpty() && state.loading) {
                CircularProgressIndicator(
                        modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                )
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = state.contacts, key = { it.id }) { contact ->
                        QmsContactRow(contact = contact, onClick = { onItemClick(contact) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun QmsContactRow(contact: QmsContact, onClick: () -> Unit) {
    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                    text = contact.nick.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
            )
            if (contact.count > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = "Новых: ${contact.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
