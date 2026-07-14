package forpdateam.ru.forpda.ui.views.drawers.adapters

import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.notes.NoteFolder
import forpdateam.ru.forpda.entity.app.notes.NoteItem
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.remote.editpost.AttachmentItem
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel

sealed class ListItem

data class NoteListItem(
        val item: NoteItem,
        val isNested: Boolean = false,
        val selectionMode: Boolean = false,
        val isSelected: Boolean = false
) : ListItem()

data class NoteFolderListItem(
        val folder: NoteFolder,
        val notesCount: Int,
        val isExpanded: Boolean
) : ListItem()

data class NoteSectionHeaderListItem(val title: String) : ListItem()

class CloseableInfoListItem(val item: CloseableInfo) : ListItem()
class ProfileListItem(val profileItem: ProfileModel?) : ListItem()
class MenuListItem(
        val menuItem: DrawerMenuItem,
        var section: OtherMenuSection = OtherMenuSection.LEGACY,
        val columns: Int = 1
) : ListItem()
class OtherMenuExitListItem : ListItem()
class OtherMenuSectionListItem(val section: OtherMenuSection) : ListItem()

/** Плашка режима редактирования: подсказка + «Сбросить» / «Готово». */
class OtherMenuEditBarListItem : ListItem()

/**
 * Пустая секция в режиме редактирования. Без неё секция, из которой утащили последнюю
 * плитку, переставала рендериться вместе с заголовком — и вернуть в неё что-либо было
 * уже нельзя (цели для дропа не существовало).
 */
class OtherMenuDropZoneListItem(val section: OtherMenuSection) : ListItem()

/** Плитка «+ Добавить» в конце секции — только в режиме редактирования. */
class OtherMenuAddTileListItem(val section: OtherMenuSection, val columns: Int = 3) : ListItem()

/**
 * Заголовок блока меню, не привязанного к секции плиток. В режиме редактирования несёт кнопки
 * «Изменить» (только у быстрых настроек) и «Скрыть»/«Показать».
 */
class OtherMenuHeaderListItem(
        val titleRes: Int,
        val block: forpdateam.ru.forpda.entity.app.other.OtherMenuBlock? = null,
        val editMode: Boolean = false,
        val hidden: Boolean = false,
        val configurable: Boolean = false
) : ListItem()

/** Строка блока «Продолжить чтение» — последняя открытая тема из истории. */
class OtherMenuContinueListItem(val item: forpdateam.ru.forpda.entity.app.history.HistoryItem) : ListItem()

/** Ряд быстрых настроек: состав выбирает пользователь (см. QuickSetting). */
class OtherMenuQuickSettingsListItem(
        val items: List<forpdateam.ru.forpda.entity.app.other.QuickSetting>
) : ListItem()
class DividerShadowListItem : ListItem()

class BottomTabListItem(val item: DrawerMenuItem, var selected: Boolean = false) : ListItem()

class AttachmentListItem(val item: AttachmentItem) : ListItem()
class AttachmentSelectorListItem(var isLinear: Boolean, var isReverse: Boolean) : ListItem()

enum class OtherMenuSection {
    LEGACY,
    QUICK,
    PERSONAL,
    TOOLS,
}

