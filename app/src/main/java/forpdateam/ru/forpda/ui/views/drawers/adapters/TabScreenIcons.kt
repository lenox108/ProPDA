package forpdateam.ru.forpda.ui.views.drawers.adapters

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.presentation.Screen

/**
 * Тип вкладки в списке открытых вкладок: иконка слева и название раздела второй строкой.
 *
 * Ключ — [Screen.getKey] (== simpleName класса экрана), тот же, что лежит в
 * [forpdateam.ru.forpda.ui.navigation.TabScreen.key] и переживает сохранение состояния.
 * Ключи берутся из самих классов, чтобы переименование экрана ломало сборку, а не иконку.
 */
object TabScreenIcons {

    private fun key(clazz: Class<out Screen>) = clazz.simpleName

    private val icons: Map<String, Int> = mapOf(
            key(Screen.Theme::class.java) to R.drawable.ic_forum,
            key(Screen.Topics::class.java) to R.drawable.ic_forum_go_to_topics,
            key(Screen.Forum::class.java) to R.drawable.ic_forum_go_to_topics,
            key(Screen.ForumRules::class.java) to R.drawable.ic_book_open,
            key(Screen.ForumBlackList::class.java) to R.drawable.ic_forum,
            key(Screen.Announce::class.java) to R.drawable.ic_notifications,
            key(Screen.Attachments::class.java) to R.drawable.ic_attachment,
            key(Screen.EditPost::class.java) to R.drawable.ic_reply,
            key(Screen.ArticleList::class.java) to R.drawable.ic_newspaper,
            key(Screen.ArticleDetail::class.java) to R.drawable.ic_newspaper,
            key(Screen.Favorites::class.java) to R.drawable.ic_star,
            key(Screen.History::class.java) to R.drawable.ic_history,
            key(Screen.Notes::class.java) to R.drawable.ic_bookmark,
            key(Screen.Downloads::class.java) to R.drawable.ic_download,
            key(Screen.Mentions::class.java) to R.drawable.ic_notifications,
            key(Screen.Search::class.java) to R.drawable.ic_search,
            key(Screen.QmsContacts::class.java) to R.drawable.ic_contacts,
            key(Screen.QmsThemes::class.java) to R.drawable.ic_contacts,
            key(Screen.QmsChat::class.java) to R.drawable.ic_comment,
            key(Screen.QmsBlackList::class.java) to R.drawable.ic_contacts,
            key(Screen.Profile::class.java) to R.drawable.ic_account_circle,
            key(Screen.Auth::class.java) to R.drawable.ic_account_circle,
            key(Screen.Reputation::class.java) to R.drawable.ic_thumb_up,
            key(Screen.SiteUserContent::class.java) to R.drawable.ic_comment,
            key(Screen.DevDbBrands::class.java) to R.drawable.ic_devices_other,
            key(Screen.DevDbDevices::class.java) to R.drawable.ic_devices_other,
            key(Screen.DevDbDevice::class.java) to R.drawable.ic_devices_other,
            key(Screen.DevDbSearch::class.java) to R.drawable.ic_devices_other,
            key(Screen.OtherMenu::class.java) to R.drawable.ic_toolbar_hamburger,
            key(Screen.Settings::class.java) to R.drawable.ic_settings,
    )

    private val sections: Map<String, Int> = mapOf(
            key(Screen.Theme::class.java) to R.string.tab_kind_topic,
            key(Screen.Topics::class.java) to R.string.fragment_title_topics,
            key(Screen.Forum::class.java) to R.string.fragment_title_forum,
            key(Screen.ForumRules::class.java) to R.string.fragment_title_forum_rules,
            key(Screen.ForumBlackList::class.java) to R.string.fragment_title_forum_blacklist,
            key(Screen.Announce::class.java) to R.string.tab_kind_announce,
            key(Screen.Attachments::class.java) to R.string.tab_kind_attachments,
            key(Screen.EditPost::class.java) to R.string.tab_kind_edit_post,
            key(Screen.ArticleList::class.java) to R.string.fragment_title_news_list,
            key(Screen.ArticleDetail::class.java) to R.string.tab_kind_article,
            key(Screen.Favorites::class.java) to R.string.fragment_title_favorite,
            key(Screen.History::class.java) to R.string.fragment_title_history,
            key(Screen.Notes::class.java) to R.string.fragment_title_notes,
            key(Screen.Downloads::class.java) to R.string.downloads,
            key(Screen.Mentions::class.java) to R.string.fragment_title_mentions,
            key(Screen.Search::class.java) to R.string.fragment_title_search,
            key(Screen.QmsContacts::class.java) to R.string.fragment_title_contacts,
            key(Screen.QmsThemes::class.java) to R.string.fragment_title_dialogs,
            key(Screen.QmsChat::class.java) to R.string.fragment_title_chat,
            key(Screen.QmsBlackList::class.java) to R.string.fragment_title_blacklist,
            key(Screen.Profile::class.java) to R.string.tab_kind_profile,
            key(Screen.Auth::class.java) to R.string.fragment_title_auth,
            key(Screen.Reputation::class.java) to R.string.fragment_title_reputation,
            key(Screen.SiteUserContent::class.java) to R.string.tab_kind_site_content,
            key(Screen.DevDbBrands::class.java) to R.string.fragment_title_devdb,
            key(Screen.DevDbDevices::class.java) to R.string.fragment_title_devdb,
            key(Screen.DevDbDevice::class.java) to R.string.fragment_title_device,
            key(Screen.DevDbSearch::class.java) to R.string.fragment_title_device_search,
            key(Screen.OtherMenu::class.java) to R.string.fragment_title_other_menu,
            key(Screen.Settings::class.java) to R.string.activity_title_settings,
    )

    @DrawableRes
    fun iconFor(screenKey: String?): Int = icons[screenKey] ?: R.drawable.ic_link

    /** Название раздела для второй строки; 0 — раздел неизвестен. */
    @StringRes
    fun sectionTitleFor(screenKey: String?): Int = sections[screenKey] ?: 0
}
