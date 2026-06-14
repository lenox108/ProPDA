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

