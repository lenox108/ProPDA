package forpdateam.ru.forpda.common

/**
 * Created by radiationx on 28.05.17.
 */
object Preferences {

    object Auth {
        const val USER_ID = "member_id"
        const val AUTH_KEY = "auth_key"
        const val COOKIE_MEMBER_ID = "cookie_member_id"
        const val COOKIE_PASS_HASH = "cookie_pass_hash"
        const val COOKIE_SESSION_ID = "cookie_session_id"
        const val COOKIE_ANONYMOUS = "cookie_anonymous"
        const val COOKIE_CF_CLEARANCE = "cookie_cf_clearance"
    }

    object Other {
        const val APP_FIRST_START = "main.is_first_start"
        const val APP_VERSIONS_HISTORY = "app.versions.history"
        const val SEARCH_SETTINGS = "search_settings_v2"
        const val MESSAGE_PANEL_BBCODES_SORT = "message_panel.bb_codes.sorted"
        const val SHOW_REPORT_WARNING = "show_report_warning"
        const val TOOLTIP_SEARCH_SETTINGS = "search.tooltip.settings"
        const val TOOLTIP_THEME_LONG_CLICK_SEND = "theme.tooltip.long_click_send"
        const val TOOLTIP_MESSAGE_PANEL_SORTING = "message_panel.tooltip.user_sorting"
        const val SMART_NAV_LONG_PRESS_HINT_DISABLED = "smart_nav_long_press_hint_disabled"
    }

    object Main {
        private const val PREFIX = "main."
        const val WEBVIEW_FONT_SIZE = PREFIX + "webview.font_size_v2"
        const val IS_SYSTEM_DOWNLOADER = PREFIX + "is_system_downloader"
        const val DOWNLOAD_METHOD = PREFIX + "download_method"
        const val DOWNLOAD_FOLDER_URI = PREFIX + "download_folder_uri"
        const val IS_EDITOR_MONOSPACE = "message_panel.is_monospace"
        const val IS_EDITOR_DEFAULT_HIDDEN = "message_panel.is_default_hidden"
        const val SCROLL_BUTTON_ENABLE = PREFIX + "scroll_button.enable"
        const val TOPIC_PAGINATION_PANEL_ENABLE = PREFIX + "topic_pagination_panel.enable"
        const val TOPIC_SCROLL_MODE = PREFIX + "topic_scroll_mode"
        const val TOPIC_POST_DENSITY = PREFIX + "topic_post_density"
        const val TOPIC_TOOLBAR_BEHAVIOR = PREFIX + "topic_toolbar_behavior"
        const val TOPIC_PAGE_SWIPE_ENABLE = PREFIX + "topic_page_swipe.enable"
        const val TOPIC_BOTTOM_REFRESH_GESTURE_ENABLE = PREFIX + "topic_bottom_refresh_gesture.enable"
        const val TOPIC_BACK_BEHAVIOR = PREFIX + "topic_back_behavior"
        const val TOPIC_OPEN_TARGET = PREFIX + "topic_open_target"
        const val TOPIC_HEADER_INITIAL_STATE = PREFIX + "topic_header_initial_state"
        const val SHOW_BOTTOM_ARROW = PREFIX + "show_bottom_arrow"
        const val BOTTOM_NAV_COLUMNS = PREFIX + "bottom_nav_columns"
        const val UI_PALETTE = PREFIX + "ui.palette"
        const val ACCENT_PALETTE = PREFIX + "accent"
        const val ACCENT_CUSTOM_COLOR = PREFIX + "accent_custom_color"
        const val ACCENT_VIBRANT = PREFIX + "accent_vibrant"
        const val APP_FONT_MODE = PREFIX + "app_font_mode"
        const val USE_SYSTEM_FONT = PREFIX + "use_system_font"
        const val STARTUP_SCREEN = PREFIX + "startup_screen"
        const val USE_MATERIAL_YOU = PREFIX + "use_material_you"
        const val WEBVIEW_COMPATIBILITY_MODE = PREFIX + "webview.compatibility_mode"
        const val WEBVIEW_SMART_PRELOAD = PREFIX + "webview.smart_preload"

        object Theme {
            private const val PREFIX = Main.PREFIX + "theme."
            const val MODE = PREFIX + "mode"
        }

        enum class ThemeMode { LIGHT, DARK, AMOLED, SYSTEM, SYSTEM_AMOLED }
        enum class UiPalette { SYSTEM, SEPIA_READING, SEPIA_BLUE, MINIMAL_READER }

        /**
         * Курируемые акцент-палитры «смены цвета». NEUTRAL — текущий монохромный
         * бренд (без оверлея). Остальные накладывают M3-акцент (см. AccentApplier +
         * ThemeOverlay.ForPDA.Accent.*). CUSTOM — произвольный seed-цвет
         * пользователя (см. [ACCENT_CUSTOM_COLOR]): на API 31+ генерится динамикой
         * из seed, на API < 31 — снап к ближайшей курируемой палитре.
         * Порядок = порядок в гриде настроек.
         */
        enum class AccentPalette {
            NEUTRAL, BLUE, INDIGO, VIOLET, PURPLE, PINK, RED,
            DEEPORANGE, ORANGE, AMBER, GREEN, TEAL, CYAN, CUSTOM
        }
        enum class DownloadMethod { SYSTEM, EXTERNAL_MANAGER, BROWSER, ASK }
        enum class TopicScrollMode { HYBRID, CLASSIC }
        enum class TopicPostDensity { COMFORTABLE, COMPACT, SUPER_COMPACT }
        enum class TopicToolbarBehavior { PINNED, HIDE_ON_SCROLL }
        enum class TopicBackBehavior { HISTORY, ORIGIN }
        enum class TopicOpenTarget { FIRST_PAGE, LAST_UNREAD }
        enum class TopicHeaderInitialState { EXPANDED, COLLAPSED }
        enum class StartupScreen { NEWS, FAVORITES, FORUM, REPLIES, QMS }
    }

    object Lists {
        private const val PREFIX = "lists."

        object Topic {
            private const val PREFIX = Lists.PREFIX + "topic."
            const val UNREAD_TOP = PREFIX + "unread_top"
            const val SHOW_DOT = PREFIX + "show_dot"
        }

        object Favorites {
            private const val PREFIX = Lists.PREFIX + "favorites."
            const val LOAD_ALL = PREFIX + "load_all"
            const val SHOW_UNREAD_BADGE = PREFIX + "show_unread_badge"
            const val SORTING_KEY = PREFIX + "sorting_key"
            const val SORTING_ORDER = PREFIX + "sorting_order"
        }

        object News {
            private const val PREFIX = Lists.PREFIX + "news."
            const val CATEGORY = PREFIX + "category"
        }
    }

    object Theme {
        private const val PREFIX = "theme."
        const val SHOW_AVATARS = PREFIX + "show_avatars"
        const val CIRCLE_AVATARS = PREFIX + "circle_avatars"
        const val ANCHOR_HISTORY = PREFIX + "anchor_history"
        const val HAT_OPENED = PREFIX + "hat_opened"
        const val FORUM_BLACKLIST = PREFIX + "forum_blacklist"
    }

    object Notifications {
        private const val PREFIX = "notifications."

        object Data {
            private const val PREFIX = Notifications.PREFIX + "data."
            const val QMS_EVENTS = PREFIX + "qms_events"
            const val FAVORITES_EVENTS = PREFIX + "favorites_events"
        }

        object MainNotif {
            private const val PREFIX = Notifications.PREFIX + "main."
            const val ENABLED = PREFIX + "enabled"
            const val SOUND_ENABLED = PREFIX + "sound_enabled"
            const val VIBRATION_ENABLED = PREFIX + "vibration_enabled"
            const val INDICATOR_ENABLED = PREFIX + "indicator_enabled"
            const val AVATARS_ENABLED = PREFIX + "avatars_enabled"
        }

        object FavoritesNotif {
            private const val PREFIX = Notifications.PREFIX + "fav."
            const val ENABLED = PREFIX + "enabled"
            const val ONLY_IMPORTANT = PREFIX + "only_important"
            const val LIVE_TAB = PREFIX + "live_tab"
        }

        object Qms {
            private const val PREFIX = Notifications.PREFIX + "qms."
            const val ENABLED = PREFIX + "enabled"
        }

        object Mentions {
            private const val PREFIX = Notifications.PREFIX + "mentions."
            const val ENABLED = PREFIX + "enabled"
        }

        object Downloads {
            private const val PREFIX = Notifications.PREFIX + "downloads."
            const val ENABLED = PREFIX + "enabled"
        }
    }
}
