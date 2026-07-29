package forpdateam.ru.forpda.ui.fragments.settings

import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import forpdateam.ru.forpda.R

/**
 * Раздел настроек = один preferences-xml + заголовок экрана.
 *
 * Корневой экран ([ROOT]) — только навигация по разделам; всё остальное живёт на втором
 * уровне. Обработчики пунктов по-прежнему в одном [SettingsFragment]: findPreference
 * возвращает null для пунктов чужого раздела, поэтому лишняя проводка просто не срабатывает.
 *
 * [externalScreen] — разделы, у которых свой фрагмент (уведомления, прокси): открываются
 * отдельной активити, как и раньше.
 */
enum class SettingsSection(
        val id: String,
        @XmlRes val xmlRes: Int,
        @StringRes val titleRes: Int,
        val externalScreen: String? = null,
) {
    ROOT("root", R.xml.preferences_root, R.string.activity_title_settings),
    APPEARANCE("appearance", R.xml.preferences_appearance, R.string.pref_title_appereance),
    ICONS("icons", R.xml.preferences_icons, R.string.settings_section_icons),
    READING("reading", R.xml.preferences_reading, R.string.settings_section_reading),
    LISTS("lists", R.xml.preferences_lists, R.string.settings_section_lists),
    NAVIGATION("navigation", R.xml.preferences_navigation, R.string.settings_section_navigation),
    DOWNLOADS("downloads", R.xml.preferences_downloads, R.string.settings_section_downloads),
    ACCOUNT("account", R.xml.preferences_account, R.string.pref_title_account),
    ABOUT("about", R.xml.preferences_about, R.string.pref_title_about),
    NOTIFICATIONS(
            "notifications",
            R.xml.preferences_notifications,
            R.string.pref_title_notifications,
            externalScreen = NotificationsSettingsFragment.PREFERENCE_SCREEN_NAME,
    ),
    PROXY(
            "proxy",
            R.xml.preferences_proxy,
            R.string.pref_title_proxy,
            externalScreen = ProxySettingsFragment.PREFERENCE_SCREEN_NAME,
    ),
    PRO(
            "pro",
            R.xml.preferences_pro,
            R.string.pref_title_pro_section,
            externalScreen = ProSettingsFragment.PREFERENCE_SCREEN_NAME,
    );

    /** Раздел рисует [SettingsFragment] — значит, его переключатели можно менять прямо в выдаче поиска. */
    val isInternal: Boolean get() = externalScreen == null

    companion object {

        /** Ключ строки-раздела на корневом экране → раздел, который она открывает. */
        fun byRowKey(key: String?): SettingsSection? = when (key) {
            "settings.section.appearance" -> APPEARANCE
            "settings.section.icons" -> ICONS
            "settings.section.reading" -> READING
            "settings.section.lists" -> LISTS
            "settings.section.navigation" -> NAVIGATION
            "settings.section.downloads" -> DOWNLOADS
            "settings.section.account" -> ACCOUNT
            "settings.section.about" -> ABOUT
            else -> null
        }

        fun byId(id: String?): SettingsSection = entries.firstOrNull { it.id == id } ?: ROOT

        /** Разделы, по которым строится сквозной поиск: корень — навигация, искать в нём нечего. */
        val indexed: List<SettingsSection> get() = entries.filter { it != ROOT }
    }
}
