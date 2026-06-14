package forpdateam.ru.forpda.ui.fragments.other

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
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
        topicPreferencesHolder: TopicPreferencesHolder
) : ListDelegationAdapter<MutableList<ListItem>>() {

    private var customOrderIds: List<Int> = emptyList()
    private var customLayout: Map<OtherMenuSection, List<Int>> = emptyMap()
    private var isEditMode = false
    private var hasBoundMenuOnce = false

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
            addDelegate(MenuItemDelegate(::onMenuItemClick, ::onMenuItemLongClick, ::isMenuEditMode))
            addDelegate(ExitMenuItemDelegate(exitClickListener))
            addDelegate(OtherSectionHeaderDelegate())
            addDelegate(CloseableInfoDelegate(::onInfoCloseClicked))
        }
    }

    var editModeChangeListener: ((Boolean) -> Unit)? = null

    fun setEditMode(value: Boolean) {
        if (isEditMode == value) return
        isEditMode = value
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
            is CloseableInfoListItem -> 10_000L + item.item.id.toLong()
            is OtherMenuSectionListItem -> 50_000L + item.section.ordinal.toLong()
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

    fun bindItems(
            profileItem: ProfileModel?,
            infoList: List<CloseableInfo>,
            newItems: List<List<AppMenuItem>>,
            bottomNavDuplicateIds: Set<Int>
    ) {
        val oldList = (items ?: mutableListOf()).toList()
        val newList = buildList<ListItem> {
            add(ProfileListItem(profileItem))

            infoList.forEach { add(CloseableInfoListItem(it)) }
            if (infoList.isNotEmpty()) add(DividerShadowListItem())

            val flatItems = newItems.flatten()
            addSection(OtherMenuSection.QUICK, columns = MENU_COLUMNS, flatItems, bottomNavDuplicateIds)
            addSection(OtherMenuSection.PERSONAL, columns = MENU_COLUMNS, flatItems, bottomNavDuplicateIds)
            addSection(OtherMenuSection.TOOLS, columns = MENU_COLUMNS, flatItems, bottomNavDuplicateIds)
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
                    oldItem is MenuListItem && newItem is MenuListItem -> {
                        val om = oldItem.menuItem
                        val nm = newItem.menuItem
                        om.title == nm.title &&
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
        if (orderedSectionItems.isEmpty()) return
        add(OtherMenuSectionListItem(section))
        addAll(orderedSectionItems.map { MenuListItem(MenuMapper.mapToDrawer(it), section, columns) })
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
        val item = items?.getOrNull(position)
        return if (item is MenuListItem) {
            spanCount / item.columns.coerceAtLeast(1)
        } else {
            spanCount
        }
    }

    fun isDraggableItem(position: Int): Boolean = items?.getOrNull(position) is MenuListItem

    fun canMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        val currentItems = items ?: return false
        val fromItem = currentItems.getOrNull(fromPosition) as? MenuListItem ?: return false
        val toItem = currentItems.getOrNull(toPosition)
        return fromItem != toItem && (toItem is MenuListItem || toItem is OtherMenuSectionListItem)
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
            else -> item.section
        }
        currentItems.add(insertPosition, item)
        notifyItemMoved(fromPosition, insertPosition)
        return true
    }

    fun currentVisibleMenuLayout(): Map<OtherMenuSection, List<AppMenuItem>> =
            listOf(OtherMenuSection.QUICK, OtherMenuSection.PERSONAL, OtherMenuSection.TOOLS)
                    .associateWith { section ->
                        items.orEmpty()
                                .filterIsInstance<MenuListItem>()
                                .filter { it.section == section }
                                .map { it.menuItem.appItem }
                    }

    private fun defaultIdsFor(section: OtherMenuSection): List<Int> = when (section) {
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
    }
}