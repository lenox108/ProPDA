package forpdateam.ru.forpda.ui.compose.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import forpdateam.ru.forpda.entity.db.offline.OfflineItemRoom
import forpdateam.ru.forpda.presentation.offline.OfflineListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compose view for the offline-reading list (§5.1 of REFACTOR_PLAN.md).
 *
 * Mirrors the [QmsContactsScreen] pattern: it consumes the existing
 * [OfflineListViewModel] (which already exposes `StateFlow<UiState>` plus a
 * one-shot `SharedFlow` of navigation events) and renders the saved items
 * in a `LazyColumn`. The actual navigation when a row is clicked is handled
 * by the host fragment so the composable stays presentational.
 */
@Composable
fun OfflineListScreen(
        viewModel: OfflineListViewModel,
        onItemClick: (OfflineItemRoom) -> Unit = {},
        modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
    ) {
        if (state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                        text = "Сохранённых страниц нет",
                        style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(items = state.items, key = { it.id }) { item ->
                    OfflineItemRow(item = item, onClick = { onItemClick(item) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun OfflineItemRow(item: OfflineItemRoom, onClick: () -> Unit) {
    val savedAt = remember(item.savedAtMs) { formatSavedAt(item.savedAtMs) }
    val sizeKb = (item.sizeBytes / 1024L).coerceAtLeast(0L)
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
                    text = item.title.ifBlank { "(без названия)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                    text = "$savedAt · ${sizeKb} КБ · ${item.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val savedAtFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

private fun formatSavedAt(savedAtMs: Long): String {
    if (savedAtMs <= 0L) return "—"
    return savedAtFormatter.format(Date(savedAtMs))
}

