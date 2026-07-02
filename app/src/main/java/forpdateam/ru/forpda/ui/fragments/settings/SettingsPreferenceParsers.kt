package forpdateam.ru.forpda.ui.fragments.settings

import forpdateam.ru.forpda.common.Preferences

/**
 * Чистые парсеры значений ListPreference (строка → enum с безопасным дефолтом),
 * вынесенные из god-фрагмента [SettingsFragment] (декомпозиция §god-fragments).
 *
 * Без состояния и без Android-зависимостей → полностью юнит-тестируемы. Логика
 * byte-identical оригиналу: неизвестное/битое значение (или null) → дефолт enum.
 */
object SettingsPreferenceParsers {

    fun parseTopicScrollMode(value: String?): Preferences.Main.TopicScrollMode = try {
        Preferences.Main.TopicScrollMode.valueOf(value ?: Preferences.Main.TopicScrollMode.HYBRID.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.TopicScrollMode.HYBRID
    }

    fun parseTopicPostDensity(value: String?): Preferences.Main.TopicPostDensity = try {
        Preferences.Main.TopicPostDensity.valueOf(value?.uppercase() ?: Preferences.Main.TopicPostDensity.COMFORTABLE.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.TopicPostDensity.COMFORTABLE
    }

    fun parseTopicToolbarBehavior(value: String?): Preferences.Main.TopicToolbarBehavior = try {
        Preferences.Main.TopicToolbarBehavior.valueOf(value?.uppercase() ?: Preferences.Main.TopicToolbarBehavior.PINNED.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.TopicToolbarBehavior.PINNED
    }

    fun parseTopicBackBehavior(value: String?): Preferences.Main.TopicBackBehavior = try {
        Preferences.Main.TopicBackBehavior.valueOf(value ?: Preferences.Main.TopicBackBehavior.HISTORY.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.TopicBackBehavior.HISTORY
    }

    fun parseTopicOpenTarget(value: String?): Preferences.Main.TopicOpenTarget = try {
        Preferences.Main.TopicOpenTarget.valueOf(value ?: Preferences.Main.TopicOpenTarget.LAST_UNREAD.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.TopicOpenTarget.LAST_UNREAD
    }

    fun parseStartupScreen(value: String?): Preferences.Main.StartupScreen = try {
        Preferences.Main.StartupScreen.valueOf(value ?: Preferences.Main.StartupScreen.NEWS.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.StartupScreen.NEWS
    }

    fun parseTopicHeaderInitialState(value: String?): Preferences.Main.TopicHeaderInitialState = try {
        Preferences.Main.TopicHeaderInitialState.valueOf(value ?: Preferences.Main.TopicHeaderInitialState.EXPANDED.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.TopicHeaderInitialState.EXPANDED
    }

    fun parseDownloadMethod(value: String?): Preferences.Main.DownloadMethod = try {
        Preferences.Main.DownloadMethod.valueOf(value ?: Preferences.Main.DownloadMethod.SYSTEM.name)
    } catch (_: IllegalArgumentException) {
        Preferences.Main.DownloadMethod.SYSTEM
    }
}
