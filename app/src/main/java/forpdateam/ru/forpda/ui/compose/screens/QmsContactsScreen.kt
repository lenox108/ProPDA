package forpdateam.ru.forpda.ui.compose.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.imageview.ShapeableImageView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.applyForumAvatarShape
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.getDimensionFromAttr
import forpdateam.ru.forpda.ui.listPlateSegment

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
            // Тон страницы под плитами — как setListsBackground() у остальных вкладок
            // (избранное/ответы/закладки): плиты светлее страницы, а не наоборот.
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                        contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    itemsIndexed(items = state.contacts, key = { _, c -> c.id }) { index, contact ->
                        QmsContactRow(
                                contact = contact,
                                segment = listPlateSegment(
                                        prevInGroup = index > 0,
                                        nextInGroup = index < state.contacts.lastIndex,
                                ),
                                onClick = { onItemClick(contact) },
                                onLongClick = { onItemLongClick(contact) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка контакта как сегмент общей плиты (`pref_plate_*`) — та же геометрия и роли, что у
 * «Ответов», списков диалогов/ЧС QMS и избранного: заливка `?attr/content_card_surface`,
 * обводка `list_plate_stroke_*`, радиус `card_corner_radius` только на краях группы,
 * боковой inset `list_plate_horizontal_inset` и зазор `list_plate_group_gap_vertical`
 * над/под группой. До этого экран рисовал собственные карточки (surfaceContainerLow,
 * радиус 16dp, без обводки) и на читающих палитрах (Sepia и др.) выбивался из остальных
 * вкладок — см. жалобу «блок QMS отличается палитрой и цветами карточек».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QmsContactRow(
        contact: QmsContact,
        segment: ListPlateSegment,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val plateFill = remember(context) { Color(context.getColorFromAttr(R.attr.content_card_surface)) }
    val strokeColor = remember(context) { Color(context.getColorFromAttr(R.attr.list_plate_stroke_color)) }
    val strokeWidth = with(LocalDensity.current) {
        remember(context) { context.getDimensionFromAttr(R.attr.list_plate_stroke_width) }.toDp()
    }
    val radius = dimensionResource(R.dimen.card_corner_radius)
    val inset = dimensionResource(R.dimen.list_plate_horizontal_inset)
    val groupGap = dimensionResource(R.dimen.list_plate_group_gap_vertical)
    val shape = when (segment) {
        ListPlateSegment.SINGLE -> RoundedCornerShape(radius)
        ListPlateSegment.FIRST -> RoundedCornerShape(topStart = radius, topEnd = radius)
        ListPlateSegment.MIDDLE -> RectangleShape
        ListPlateSegment.LAST -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    }
    val gapAbove = segment == ListPlateSegment.SINGLE || segment == ListPlateSegment.FIRST
    val gapBelow = segment == ListPlateSegment.SINGLE || segment == ListPlateSegment.LAST
    Surface(
            color = plateFill,
            shape = shape,
            border = if (strokeWidth > 0.dp) BorderStroke(strokeWidth, strokeColor) else null,
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                            start = inset,
                            end = inset,
                            top = if (gapAbove) groupGap else 0.dp,
                            bottom = if (gapBelow) groupGap else 0.dp,
                    ),
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
                // Те же роли, что у legacy-бейджа `?count_background` (qms_theme_item/qms_contact_item):
                // заливка — акцент палитры, текст — contrast_text_color, а не M3 primary/onPrimary.
                val badgeFill = remember(context) {
                    Color(context.getColorFromAttr(androidx.appcompat.R.attr.colorAccent))
                }
                val badgeText = remember(context) {
                    Color(context.getColorFromAttr(R.attr.contrast_text_color))
                }
                Surface(
                        color = badgeFill,
                        shape = RoundedCornerShape(50),
                ) {
                    Text(
                            text = contact.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
