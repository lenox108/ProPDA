package forpdateam.ru.forpda.ui.compose.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.imageview.ShapeableImageView
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.applyForumAvatarShape
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel

/**
 * Compose-экран списка контактов QMS (пилот миграции §3.2).
 *
 * Полный паритет с legacy [QmsContactsFragment]: аватар (через тот же
 * [ForPdaCoil]/forum-avatar-shape, без coil-compose), ник (жирный при непрочитанных),
 * бейдж непрочитанных, тап/лонг-тап, pull-to-refresh и загрузка. Тулбар/поиск/FAB/
 * back остаются на legacy-хосте (общий chrome вкладок), сюда прокинуты колбэки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QmsContactsScreen(
        viewModel: QmsContactsViewModel,
        onItemClick: (QmsContact) -> Unit,
        onItemLongClick: (QmsContact) -> Unit,
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
    ) {
        if (state.contacts.isEmpty() && state.loading) {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp))
            }
            return@Surface
        }
        PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = { viewModel.loadContacts() },
                modifier = Modifier.fillMaxSize(),
        ) {
            if (state.contacts.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                            text = "Нет активных диалогов",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = bottomPadding + 8.dp),
                ) {
                    items(items = state.contacts, key = { it.id }) { contact ->
                        QmsContactRow(
                                contact = contact,
                                onClick = { onItemClick(contact) },
                                onLongClick = { onItemLongClick(contact) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QmsContactRow(
        contact: QmsContact,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
        ) {
            AndroidView(
                    factory = { ctx ->
                        ShapeableImageView(ctx).apply { applyForumAvatarShape(false) }
                    },
                    update = { iv -> ForPdaCoil.loadInto(iv, contact.avatar) },
                    modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                    text = contact.nick.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (contact.count > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
            )
            if (contact.count > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                ) {
                    Text(
                            text = contact.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
