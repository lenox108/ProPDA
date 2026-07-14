package forpdateam.ru.forpda.ui.fragments.other

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.history.HistoryItem
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.app.other.MenuShortcut
import forpdateam.ru.forpda.entity.app.other.OtherMenuBlock
import forpdateam.ru.forpda.entity.app.other.QuickSetting
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.MenuMapper
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.ui.views.drawers.adapters.*

class OtherAdapter(
        private val profileClickListener: (ProfileModel?) -> Unit,
        private val menuClickListener: (DrawerMenuItem) -> Unit,
        private val exitClickListener: () -> Unit,
        private val infoClickListener: (CloseableInfo) -> Unit,
        private val editDoneListener: () -> Unit,
        private val editResetListener: () -> Unit,
        private val addShortcutListener: (OtherMenuSection) -> Unit,
        private val removeShortcutListener: (DrawerMenuItem) -> Unit,
        private val continueClickListener: (HistoryItem) -> Unit,
        private val quickSettingClickListener: (QuickSetting) -> Unit,
        private val blockVisibilityListener: (OtherMenuBlock) -> Unit,
        private val blockConfigureListener: (OtherMenuBlock) -> Unit,
        topicPreferencesHolder: TopicPreferencesHolder
) : ListDelegationAdapter<MutableList<ListItem>>() {

    private var customOrderIds: List<Int> = emptyList()
    private var customLayout: Map<OtherMenuSection, List<Int>> = emptyMap()
    private var shortcuts: List<MenuShortcut> = emptyList()
    private var isEditMode = false
    private var hasBoundMenuOnce = false
    private var lastBind: BindArgs? = null

    private class BindArgs(
            val profileItem: ProfileModel?,
            val infoList: List<CloseableInfo>,
            val menuItems: List<List<AppMenuItem>>,
            val bottomNavDuplicateIds: Set<Int>,
            val continueItems: List<HistoryItem>,
            val quickSettings: List<QuickSetting>,
            val hiddenBlocks: Set<OtherMenuBlock>
    )

    init {
        setHasStableIds(true)
    }

    private fun onInfoCloseClicked(item: CloseableInfo) {
        val currentItems = items ?: return
        val infoIndex = currentItems.indexOfFirst { it is CloseableInfoListItem && it.item.id == item.id }
        val closeableInfoCount = currentItems.filterIsInstance(CloseableInfoListItem::class.java).size
        if (infoIndex >= 0) {
            currentItems.removeAt(infoIndex)
            if (closeableInfoCount > 1) {
                notifyItemRangeRemoved(infoIndex, 1)
            } else {
                // Удаляем один элемент info + один DividerShadow, но info уже удалён выше
                if (infoIndex < currentItems.size) {
                    currentItems.removeAt(infoIndex)
                }
                notifyItemRangeRemoved(infoIndex, 2)
            }
        }
        infoClickListener.invoke(item)
    }

    init {
        items = mutableListOf()
        delegatesManager.apply {
            addDelegate(ProfileItemDelegate(profileClickListener, topicPreferencesHolder))
            addDelegate(DividerShadowItemDelegate())
            addDelegate(MenuItemDelegate(::onMenuItemClick, ::onMenuItemLongClick, ::isMenuEditMode, removeShortcutListener))
            addDelegate(ExitMenuItemDelegate(exitClickListener))
            addDelegate(OtherSectionHeaderDelegate())
            addDelegate(CloseableInfoDelegate(::onInfoCloseClicked))
            addDelegate(OtherMenuEditBarDelegate(editDoneListener, editResetListener))
            addDelegate(OtherMenuDropZoneDelegate())
            addDelegate(OtherMenuAddTileDelegate(addShortcutListener))
            addDelegate(OtherMenuHeaderDelegate(blockVisibilityListener, blockConfigureListener))
            addDelegate(OtherMenuContinueDelegate(continueClickListener))
            addDelegate(OtherMenuQuickSettingsDelegate(quickSettingClickListener))
        }
    }

    var editModeChangeListener: ((Boolean) -> Unit)? = null

    fun setEditMode(value: Boolean) {
        if (isEditMode == value) return
        isEditMode = value
        // Плашка редактирования и зоны пустых секций живут только в режиме редактирования,
        // поэтому список пересобирается целиком; плитки отдельно перебиндиваем ради рамки.
        rebuild()
        notifyMenuItemsChanged()
        editModeChangeListener?.invoke(value)
    }

    fun isMenuEditMode(): Boolean = isEditMode

    private fun onMenuItemClick(item: DrawerMenuItem) {
        if (!isEditMode) menuClickListener(item)
    }

    private fun onMenuItemLongClick(): Boolean {
        setEditMode(true)
        return true
    }

    private fun notifyMenuItemsChanged() {
        items.orEmpty().forEachIndexed { index, item ->
            if (item is MenuListItem) notifyItemChanged(index)
        }
    }

    override fun getItemId(position: Int): Long {
        val item = items?.getOrNull(position) ?: return RecyclerView.NO_ID
        return stableIdFor(item)
    }

    private fun stableIdFor(item: ListItem): Long {
        // Keep IDs stable across updates to avoid RV rebinding "everything" (blink).
        return when (item) {
            is ProfileListItem -> 1L
            is DividerShadowListItem -> 2L
            is OtherMenuEditBarListItem -> 3L
            is CloseableInfoListItem -> 10_000L + item.item.id.toLong()
            is OtherMenuSectionListItem -> 50_000L + item.section.ordinal.toLong()
            is OtherMenuDropZoneListItem -> 60_000L + item.section.ordinal.toLong()
            is OtherMenuAddTileListItem -> 70_000L + item.section.ordinal.toLong()
            // Полный res-id, а не остаток: два заголовка с одинаковым «хвостом» дали бы
            // одинаковый stable id и RecyclerView начал бы путать их между собой.
            is OtherMenuHeaderListItem -> 10_000_000L + item.titleRes.toLong()
            // Отдельный высокий диапазон: id тем большие и легко наехали бы на плитки меню.
            is OtherMenuContinueListItem -> 1_000_000L + item.item.id.toLong()
            is OtherMenuQuickSettingsListItem -> 94_000L
            is OtherMenuExitListItem -> 95_000L
            is MenuListItem -> 100_000L + item.menuItem.appItem.id.toLong()
            else -> RecyclerView.NO_ID
        }
    }

    sealed interface Payload {
        data object MenuCountChanged : Payload
    }

    fun setCustomLayout(layout: Map<OtherMenuSection, List<Int>>) {
        customLayout = layout
        customOrderIds = layout.values.flatten()
    }

    fun setShortcuts(items: List<MenuShortcut>) {
        shortcuts = items
    }

    fun bindItems(
            profileItem: ProfileModel?,
            infoList: List<CloseableInfo>,
            newItems: List<List<AppMenuItem>>,
            bottomNavDuplicateIds: Set<Int>,
            continueItems: List<HistoryItem>,
            quickSettings: List<QuickSetting>,
            hiddenBlocks: Set<OtherMenuBlock>
    ) {
        lastBind = BindArgs(
                profileItem,
                infoList,
                newItems,
                bottomNavDuplicateIds,
                continueItems,
                quickSettings,
                hiddenBlocks
        )
        rebuild()
    }

    private fun rebuild() {
        val args = lastBind ?: return
        val oldList = (items ?: mutableListOf()).toList()
        val newList = buildList<ListItem> {
            if (isEditMode) add(OtherMenuEditBarListItem())
            add(ProfileListItem(args.profileItem))

            args.infoList.forEach { add(CloseableInfoListItem(it)) }
            if (args.infoList.isNotEmpty()) add(DividerShadowListItem())

            // «Продолжить чтение» — самый частый сценарий возврата в приложение, поэтому идёт
            // выше плиток. Скрытый блок вне режима редактирования исчезает совсем, а в режиме
            // остаётся одним заголовком с кнопкой «Показать» — иначе вернуть его было бы нечем.
            addBlock(
                    block = OtherMenuBlock.CONTINUE,
                    titleRes = R.string.other_menu_section_continue,
                    hidden = args.hiddenBlocks.contains(OtherMenuBlock.CONTINUE),
                    hasContent = args.continueItems.isNotEmpty(),
                    configurable = false
            ) {
                args.continueItems.forEach { add(OtherMenuContinueListItem(it)) }
            }

            // Пользовательские плитки живут в той же сетке, что и штатные пункты: у них
            // отрицательные id, поэтому и порядок, и секции хранятся тем же механизмом.
            val flatItems = args.menuItems.flatten() +
                    shortcuts.map { AppMenuItem(it.id, null, it) }
            SECTIONS.forEach { section ->
                addSection(section, columns = MENU_COLUMNS, flatItems, args.bottomNavDuplicateIds)
            }
            addBlock(
                    block = OtherMenuBlock.QUICK_SETTINGS,
                    titleRes = R.string.other_menu_section_quick_settings,
                    hidden = args.hiddenBlocks.contains(OtherMenuBlock.QUICK_SETTINGS),
                    hasContent = args.quickSettings.isNotEmpty(),
                    configurable = true
            ) {
                add(OtherMenuQuickSettingsListItem(args.quickSettings))
            }
            add(OtherMenuExitListItem())
        }

        val newMutableList = newList.toMutableList()
        if (!hasBoundMenuOnce) {
            items = newMutableList
            hasBoundMenuOnce = true
            notifyDataSetChanged()
            return
        }

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(o: Int, n: Int): Boolean {
                val oldItem = oldList[o]
                val newItem = newList[n]
                if (oldItem::class != newItem::class) return false
                return stableIdFor(oldItem) == stableIdFor(newItem)
            }

            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val oldItem = oldList[o]
                val newItem = newList[n]
                return when {
                    oldItem is ProfileListItem && newItem is ProfileListItem ->
                        oldItem.profileItem?.id == newItem.profileItem?.id
                    oldItem is CloseableInfoListItem && newItem is CloseableInfoListItem ->
                        oldItem.item == newItem.item
                    oldItem is OtherMenuSectionListItem && newItem is OtherMenuSectionListItem ->
                        oldItem.section == newItem.section
                    oldItem is OtherMenuContinueListItem && newItem is OtherMenuContinueListItem ->
                        oldItem.item == newItem.item
                    oldItem is OtherMenuHeaderListItem && newItem is OtherMenuHeaderListItem ->
                        oldItem.editMode == newItem.editMode &&
                            oldItem.hidden == newItem.hidden &&
                            oldItem.configurable == newItem.configurable
                    oldItem is OtherMenuQuickSettingsListItem && newItem is OtherMenuQuickSettingsListItem ->
                        oldItem.items == newItem.items
                    oldItem is MenuListItem && newItem is MenuListItem -> {
                        val om = oldItem.menuItem
                        val nm = newItem.menuItem
                        om.title == nm.title &&
                            om.titleText == nm.titleText &&
                            om.icon == nm.icon &&
                            om.appItem.count == nm.appItem.count &&
                            oldItem.section == newItem.section &&
                            oldItem.columns == newItem.columns
                    }
                    else -> true // dividers etc.
                }
            }

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                if (oldItem is MenuListItem && newItem is MenuListItem) {
                    val oc = oldItem.menuItem.appItem.count
                    val nc = newItem.menuItem.appItem.count
                    if (oc != nc) return Payload.MenuCountChanged
                }
                return null
            }
        }, false)

        items = newMutableList
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Блок с заголовком («Продолжить чтение», «Быстрые настройки»). Вне режима редактирования
     * скрытый или пустой блок не рендерится вовсе; в режиме редактирования заголовок остаётся
     * всегда — он и есть точка управления видимостью и составом.
     */
    private fun MutableList<ListItem>.addBlock(
            block: OtherMenuBlock,
            titleRes: Int,
            hidden: Boolean,
            hasContent: Boolean,
            configurable: Boolean,
            content: MutableList<ListItem>.() -> Unit
    ) {
        if (!isEditMode && (hidden || !hasContent)) return
        add(OtherMenuHeaderListItem(titleRes, block, isEditMode, hidden, configurable))
        if (!hidden && hasContent) content()
    }

    private fun MutableList<ListItem>.addSection(
            section: OtherMenuSection,
            columns: Int,
            sourceItems: List<AppMenuItem>,
            bottomNavDuplicateIds: Set<Int>
    ) {
        val itemsById = sourceItems.associateBy { it.id }
        val sectionIds = layoutIdsFor(section)
        val orderedSectionItems = sectionIds.mapNotNull { id ->
            when {
                bottomNavDuplicateIds.contains(id) -> null
                id == MenuRepository.item_auth -> AppMenuItem(MenuRepository.item_auth)
                else -> itemsById[id]
            }
        }
        // Пустая секция вне режима редактирования просто не показывается, но в режиме
        // редактирования обязана существовать — иначе вернуть в неё плитку невозможно.
        if (orderedSectionItems.isEmpty() && !isEditMode) return
        add(OtherMenuSectionListItem(section))
        if (orderedSectionItems.isEmpty()) {
            add(OtherMenuDropZoneListItem(section))
        } else {
            addAll(orderedSectionItems.map { MenuListItem(MenuMapper.mapToDrawer(it), section, columns) })
        }
        if (isEditMode) add(OtherMenuAddTileListItem(section, columns))
    }

    private fun layoutIdsFor(section: OtherMenuSection): List<Int> {
        val availableDefaultIds = defaultIdsFor(section).filter { id ->
            id == MenuRepository.item_auth || customLayout.isNotEmpty() || customOrderIds.isEmpty() || customOrderIds.contains(id)
        }
        if (customLayout.isEmpty()) {
            return availableDefaultIds.sortedWith(compareBy {
                customOrderIds.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            })
        }
        val allCustomIds = customLayout.values.flatten().toSet()
        val savedIds = customLayout[section].orEmpty()
        val missingDefaultIds = availableDefaultIds.filterNot { allCustomIds.contains(it) }
        return savedIds + missingDefaultIds
    }

    fun getSpanSize(position: Int, spanCount: Int): Int {
        val columns = when (val item = items?.getOrNull(position)) {
            is MenuListItem -> item.columns
            is OtherMenuAddTileListItem -> item.columns
            else -> return spanCount
        }
        return spanCount / columns.coerceAtLeast(1)
    }

    fun isDraggableItem(position: Int): Boolean = items?.getOrNull(position) is MenuListItem

    fun canMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        val currentItems = items ?: return false
        val fromItem = currentItems.getOrNull(fromPosition) as? MenuListItem ?: return false
        val toItem = currentItems.getOrNull(toPosition)
        return fromItem != toItem &&
                (toItem is MenuListItem || toItem is OtherMenuSectionListItem || toItem is OtherMenuDropZoneListItem)
    }

    fun moveItem(fromPosition: Int, toPosition: Int): Boolean {
        val currentItems = items ?: return false
        if (!canMoveItem(fromPosition, toPosition)) return false
        val item = currentItems.removeAt(fromPosition) as MenuListItem
        val adjustedTargetPosition = if (fromPosition < toPosition) toPosition - 1 else toPosition
        val target = currentItems.getOrNull(adjustedTargetPosition)
        val insertPosition = when (target) {
            is OtherMenuSectionListItem -> (adjustedTargetPosition + 1).coerceAtMost(currentItems.size)
            else -> adjustedTargetPosition
        }
        item.section = when (target) {
            is MenuListItem -> target.section
            is OtherMenuSectionListItem -> target.section
            is OtherMenuDropZoneListItem -> target.section
            else -> item.section
        }
        currentItems.add(insertPosition, item)
        notifyItemMoved(fromPosition, insertPosition)
        return true
    }

    /**
     * Фиксирует раскладку после перетаскивания: секции, оставшиеся пустыми, сохраняются
     * пустыми (а не откатываются к дефолту), а скрытые сейчас плитки (дубли нижней панели,
     * пункты под авторизацией) остаются в своих секциях. Возвращает видимую раскладку для VM.
     */
    fun commitDragLayout(): Map<OtherMenuSection, List<AppMenuItem>> {
        val visible = currentVisibleMenuLayout()
        val visibleIds = visible.values.flatten().map { it.id }.toSet()
        setCustomLayout(SECTIONS.associateWith { section ->
            visible[section].orEmpty().map { it.id } +
                    customLayout[section].orEmpty().filterNot { visibleIds.contains(it) }
        })
        rebuild()
        return visible
    }

    fun currentVisibleMenuLayout(): Map<OtherMenuSection, List<AppMenuItem>> =
            SECTIONS.associateWith { section ->
                items.orEmpty()
                        .filterIsInstance<MenuListItem>()
                        .filter { it.section == section }
                        .map { it.menuItem.appItem }
            }

    /**
     * Дефолтная раскладка секции + пользовательские плитки, закреплённые за ней. Новый ярлык
     * попадает сюда сразу после создания, даже если сохранённый порядок плиток его ещё не знает.
     */
    private fun defaultIdsFor(section: OtherMenuSection): List<Int> =
            builtInIdsFor(section) + shortcuts.filter { it.section == section }.map { it.id }

    private fun builtInIdsFor(section: OtherMenuSection): List<Int> = when (section) {
        OtherMenuSection.QUICK -> listOf(
                MenuRepository.item_article_list,
                MenuRepository.item_forum,
                MenuRepository.item_qms_contacts,
                MenuRepository.item_search,
                MenuRepository.item_favorites,
                MenuRepository.item_mentions,
        )
        OtherMenuSection.PERSONAL -> listOf(
                MenuRepository.item_notes,
                MenuRepository.item_history,
                MenuRepository.item_my_messages,
        )
        OtherMenuSection.TOOLS -> listOf(
                MenuRepository.item_downloads,
                MenuRepository.item_dev_db,
                MenuRepository.item_settings,
        )
        OtherMenuSection.LEGACY -> emptyList()
    }

    private companion object {
        const val MENU_COLUMNS = 3

        val SECTIONS = listOf(OtherMenuSection.QUICK, OtherMenuSection.PERSONAL, OtherMenuSection.TOOLS)
    }
}