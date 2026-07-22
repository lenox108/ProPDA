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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.imageview.ShapeableImageView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.applyForumAvatarShape
import forpdateam.ru.forpda.common.getColorFromAttr
import forpdateam.ru.forpda.entity.remote.qms.QmsContact
import forpdateam.ru.forpda.presentation.qms.contacts.QmsContactsViewModel
import forpdateam.ru.forpda.ui.ListPlateSegment
import forpdateam.ru.forpda.ui.chromeCanvasColor
import forpdateam.ru.forpda.ui.drawableResForListPlate
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
        circleAvatars: Boolean,
        modifier: Modifier = Modifier,
        bottomPadding: Dp = 0.dp,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Тон страницы под плитами = полотно ChromeCanvas — как setListsBackground() у
    // остальных вкладок (избранное/ответы/закладки): под Material You тонируется
    // обоями в единый тон с шапкой/нижним баром, вне MY = surfaceContainerLowest.
    val pageContext = LocalContext.current
    val pageTone = remember(pageContext) {
        Color(pageContext.chromeCanvasColor(com.google.android.material.R.attr.colorSurfaceContainerLowest))
    }

    // Список приходит дважды: сначала кэш из БД (observeContacts), затем свежий с сервера
    // (loadContacts в onResumeOrShow). Если во втором списке диалог всплыл на первое место
    // (новое сообщение), LazyColumn по key удерживает якорь на ПРЕЖНЕМ первом элементе,
    // и новый верхний контакт уезжает за верхнюю кромку — экран открывается со сдвигом
    // на одну строку (жалоба «верхний контакт не видно, надо скроллить наверх»).
    // Поэтому: если пользователь стоит у самого верха, при смене головы списка возвращаем
    // его на позицию 0. Своё положение отслеживаем только по завершении скролла — тогда
    // якорный ремап LazyColumn (он происходит без scroll in progress) не собьёт признак.
    val listState = rememberLazyListState()
    var pinnedToTop by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                pinnedToTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
            }
        }
    }
    val headKey = state.contacts.firstOrNull()?.id
    LaunchedEffect(headKey) {
        // scrollToItem сбрасывает сохранённый key-якорь (lastKnownFirstItemKey), поэтому
        // работает независимо от того, успел ли LazyColumn уже переизмериться.
        if (headKey != null && pinnedToTop) listState.scrollToItem(0)
    }

    Surface(
            modifier = modifier.fillMaxSize(),
            color = pageTone,
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
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomPadding),
                ) {
                    itemsIndexed(items = state.contacts, key = { _, c -> c.id }) { index, contact ->
                        QmsContactRow(
                                contact = contact,
                                circleAvatars = circleAvatars,
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
        circleAvatars: Boolean,
        segment: ListPlateSegment,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
) {
    val context = LocalContext.current
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
    // Плита-сегмент рисуется тем же drawable pref_plate_*, что и «Ответы» /
    // списки диалогов QMS / избранное: рамка по периметру группы + ОДИНОЧНЫЙ
    // внутренний разделитель (у top/middle-сегментов нижняя граница
    // закрашивается фоном — см. layer-list в pref_plate_middle.xml). Раньше
    // строка рисовала полную BorderStroke со всех сторон, и у соседних строк
    // границы складывались в двойную толщину — жалоба «слишком толстые
    // разделители в QMS» (палитра Sepia). Общий drawable гарантирует единый
    // стиль со всеми легаси-списками.
    val plate = remember(context, segment) {
        ContextCompat.getDrawable(context, drawableResForListPlate(segment))
    }
    Box(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                            start = inset,
                            end = inset,
                            top = if (gapAbove) groupGap else 0.dp,
                            bottom = if (gapBelow) groupGap else 0.dp,
                    )
                    .drawBehind {
                        plate?.let { d ->
                            d.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                            d.draw(drawContext.canvas.nativeCanvas)
                        }
                    }
                    .clip(shape)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
        ) {
            AndroidView(
                    factory = { ctx -> ShapeableImageView(ctx) },
                    update = { iv ->
                        iv.applyForumAvatarShape(circleAvatars)
                        ForPdaCoil.loadInto(iv, contact.avatar)
                    },
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
