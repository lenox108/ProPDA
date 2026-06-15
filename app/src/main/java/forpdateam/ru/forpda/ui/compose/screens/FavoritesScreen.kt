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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.presentation.favorites.FavoritesUiEvent
import forpdateam.ru.forpda.presentation.favorites.FavoritesViewModel

/**
 * Compose preview of the favorites list (§3.2 of REFACTOR_PLAN.md).
 *
 * Sits next to the legacy [forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment]
 * (a 600+ line RecyclerView god-fragment). The Composable consumes
 * the existing [FavoritesViewModel] (which already exposes a
 * `SharedFlow<FavoritesUiEvent>`) and folds the relevant events
 * into Compose state:
 * - OnLoadFavorites.data.items / OnShowFavorite.list → item list
 * - ShowLoadError / ShowNeedAuth → error/empty states
 *
 * Pull-to-refresh, popup menu, search, and the sort/mark-all-read
 * panel are intentionally left to the legacy fragment. The full
 * wiring in [forpdateam.ru.forpda.ui.navigation.TabHelper] is
 * deferred to a follow-up commit.
 */
@Composable
fun FavoritesScreen(
        viewModel: FavoritesViewModel,
        onItemClick: (FavItem) -> Unit = {},
        modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf<List<FavItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var needAuth by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is FavoritesUiEvent.OnLoadFavorites -> {
                    items = event.data.items
                    loading = false
                }
                is FavoritesUiEvent.OnShowFavorite -> {
                    items = event.list
                    loading = false
                }
                is FavoritesUiEvent.ShowLoadError -> {
                    loading = false
                    errorMessage = event.message
                }
                is FavoritesUiEvent.ShowNeedAuth -> {
                    needAuth = true
                    loading = false
                }
                else -> {
                    // Sorting / dialog / mark-all-read / mute events
                    // are forwarded to the host fragment.
                }
            }
        }
    }

    Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                needAuth -> Text(
                        text = "Authorization required",
                        modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                )
                items.isEmpty() && loading -> CircularProgressIndicator(
                        modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                )
                items.isEmpty() -> Text(
                        text = errorMessage ?: "No favorites",
                        modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                )
                else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = items, key = { it.favId }) { fav ->
                        FavoriteRow(item = fav, onClick = { onItemClick(fav) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(item: FavItem, onClick: () -> Unit) {
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
                    text = item.topicTitle.orEmpty().ifEmpty { item.forumTitle.orEmpty() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.isUnreadForDisplay()) FontWeight.Bold else FontWeight.Normal,
            )
            val meta = buildString {
                if (item.isPin) append("📌 ")
                if (item.isPoll) append("📊 ")
                if (item.isClosed) append("🔒 ")
                item.authorUserNick?.let { append(it) }
                item.date?.let { if (isNotEmpty()) append(" · "); append(it) }
                if (item.unreadPostCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("+${item.unreadPostCount}")
                }
            }
            if (meta.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
