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
import forpdateam.ru.forpda.entity.remote.news.NewsItem
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListUiEvent
import forpdateam.ru.forpda.presentation.articles.list.ArticlesListViewModel

/**
 * Compose preview / list for the ArticlesList screen (§3.2 of
 * REFACTOR_PLAN.md). The legacy [forpdateam.ru.forpda.ui.fragments.news.main.NewsMainFragment]
 * is a 270+ line RecyclerView god-fragment; this Composable is the
 * first step toward migrating it to Compose.
 *
 * Strategy:
 * - The view-model's [_uiEvents] flow drives local Compose state
 *   (`mutableStateOf`) for the items list. We treat
 *   `ShowNews(withClear=true)` as a reset.
 * - The `onItemClick` lambda is the only navigation seam — the
 *   host fragment is expected to call [ArticlesListViewModel.onItemClick]
 *   which already navigates and prefetches the article.
 * - Pull-to-refresh, FAB actions, search, popup menu, and
 *   error-toast wiring are intentionally left to the legacy
 *   fragment. The full wiring in [forpdateam.ru.forpda.ui.navigation.TabHelper]
 *   is deferred to a follow-up commit.
 */
@Composable
fun ArticlesListScreen(
        viewModel: ArticlesListViewModel,
        onItemClick: (NewsItem) -> Unit = {},
        modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is ArticlesListUiEvent.ShowNews -> {
                    if (event.withClear) items = emptyList()
                    items = items + event.items
                    loading = false
                }
                is ArticlesListUiEvent.UpdateItems -> {
                    items = items.map { existing ->
                        val replacement = event.items.firstOrNull { it.id == existing.id }
                        replacement ?: existing
                    }
                }
                is ArticlesListUiEvent.ClearNews -> {
                    items = emptyList()
                }
                is ArticlesListUiEvent.ShowLoadError -> {
                    loading = false
                    errorMessage = event.message
                }
                is ArticlesListUiEvent.ShowItemDialogMenu,
                is ArticlesListUiEvent.ShowCreateNote -> {
                    // Forwarded to the host fragment for now.
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
                items.isEmpty() && loading -> CircularProgressIndicator(
                        modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                )
                items.isEmpty() -> Text(
                        text = errorMessage ?: "No articles",
                        modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                )
                else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = items, key = { it.id }) { article ->
                        ArticleRow(article = article, onClick = { onItemClick(article) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleRow(article: NewsItem, onClick: () -> Unit) {
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
                    text = article.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
            )
            val meta = buildString {
                article.author?.let { append(it) }
                article.date?.let { if (isNotEmpty()) append(" · "); append(it) }
                if (article.commentsCount > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${article.commentsCount} comments")
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
