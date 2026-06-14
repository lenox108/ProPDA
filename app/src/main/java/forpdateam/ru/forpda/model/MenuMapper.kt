package forpdateam.ru.forpda.model

import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.ui.views.drawers.adapters.DrawerMenuItem

object MenuMapper {

    fun mapToDrawer(item: AppMenuItem): DrawerMenuItem = DrawerMenuItem(
            getTitle(item),
            getIcon(item),
            item
    )

    fun getTitle(item: AppMenuItem): Int = when (item.id) {
        MenuRepository.item_auth -> R.string.profile
        MenuRepository.item_article_list -> R.string.fragment_title_news_list
        MenuRepository.item_favorites -> R.string.fragment_title_favorite
        MenuRepository.item_qms_contacts -> R.string.menu_item_qms
        MenuRepository.item_mentions -> R.string.fragment_title_mentions
        MenuRepository.item_my_messages -> R.string.menu_item_my_messages
        MenuRepository.item_dev_db -> R.string.fragment_title_devdb
        MenuRepository.item_forum -> R.string.fragment_title_forum
        MenuRepository.item_search -> R.string.fragment_title_search
        MenuRepository.item_downloads -> R.string.downloads
        MenuRepository.item_history -> R.string.fragment_title_history
        MenuRepository.item_notes -> R.string.fragment_title_notes
        MenuRepository.item_forum_rules -> R.string.fragment_title_forum_rules
        MenuRepository.item_settings -> R.string.activity_title_settings
        MenuRepository.item_other_menu -> R.string.fragment_title_other_menu
        else -> R.string.error
    }

    fun getIcon(item: AppMenuItem): Int = when (item.id) {
        MenuRepository.item_auth -> R.drawable.ic_account_circle
        MenuRepository.item_article_list -> R.drawable.ic_newspaper
        MenuRepository.item_favorites -> R.drawable.ic_star
        MenuRepository.item_qms_contacts -> R.drawable.ic_contacts
        MenuRepository.item_mentions -> R.drawable.ic_notifications
        MenuRepository.item_my_messages -> R.drawable.ic_comment
        MenuRepository.item_dev_db -> R.drawable.ic_devices_other
        MenuRepository.item_forum -> R.drawable.ic_forum
        MenuRepository.item_search -> R.drawable.ic_search
        MenuRepository.item_downloads -> R.drawable.ic_download
        MenuRepository.item_history -> R.drawable.ic_history
        MenuRepository.item_notes -> R.drawable.ic_bookmark
        MenuRepository.item_forum_rules -> R.drawable.ic_book_open
        MenuRepository.item_settings -> R.drawable.ic_settings
        MenuRepository.item_other_menu -> R.drawable.ic_toolbar_hamburger
        else -> R.drawable.ic_thumb_down
    }
}