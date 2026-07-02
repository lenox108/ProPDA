package forpdateam.ru.forpda.ui.fragments.settings

import forpdateam.ru.forpda.common.Preferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Пинит контракт парсеров ListPreference, вынесенных из SettingsFragment: валидное
 * имя → enum, null/битое → безопасный дефолт, регистронезависимость там, где
 * оригинал делал uppercase(). Раньше эта логика жила в god-фрагменте и была
 * непокрыта — теперь чистый object тестируется напрямую.
 */
class SettingsPreferenceParsersTest {

    @Test
    fun topicScrollMode_validNullInvalid() {
        assertEquals(Preferences.Main.TopicScrollMode.HYBRID,
                SettingsPreferenceParsers.parseTopicScrollMode(Preferences.Main.TopicScrollMode.HYBRID.name))
        assertEquals(Preferences.Main.TopicScrollMode.HYBRID,
                SettingsPreferenceParsers.parseTopicScrollMode(null))
        assertEquals(Preferences.Main.TopicScrollMode.HYBRID,
                SettingsPreferenceParsers.parseTopicScrollMode("garbage"))
    }

    @Test
    fun topicPostDensity_isCaseInsensitive() {
        // Оригинал делал value?.uppercase() — строчный ввод должен парситься.
        val expected = Preferences.Main.TopicPostDensity.COMFORTABLE
        assertEquals(expected, SettingsPreferenceParsers.parseTopicPostDensity(expected.name.lowercase()))
        assertEquals(expected, SettingsPreferenceParsers.parseTopicPostDensity(expected.name))
        assertEquals(expected, SettingsPreferenceParsers.parseTopicPostDensity(null))
        assertEquals(expected, SettingsPreferenceParsers.parseTopicPostDensity("nope"))
    }

    @Test
    fun topicToolbarBehavior_isCaseInsensitive() {
        val expected = Preferences.Main.TopicToolbarBehavior.PINNED
        assertEquals(expected, SettingsPreferenceParsers.parseTopicToolbarBehavior(expected.name.lowercase()))
        assertEquals(expected, SettingsPreferenceParsers.parseTopicToolbarBehavior("x"))
    }

    @Test
    fun otherParsers_defaultOnNullAndGarbage() {
        assertEquals(Preferences.Main.TopicBackBehavior.HISTORY,
                SettingsPreferenceParsers.parseTopicBackBehavior("x"))
        assertEquals(Preferences.Main.TopicOpenTarget.LAST_UNREAD,
                SettingsPreferenceParsers.parseTopicOpenTarget(null))
        assertEquals(Preferences.Main.StartupScreen.NEWS,
                SettingsPreferenceParsers.parseStartupScreen("x"))
        assertEquals(Preferences.Main.TopicHeaderInitialState.EXPANDED,
                SettingsPreferenceParsers.parseTopicHeaderInitialState(null))
        assertEquals(Preferences.Main.DownloadMethod.SYSTEM,
                SettingsPreferenceParsers.parseDownloadMethod("x"))
    }

    @Test
    fun otherParsers_roundTripValidNames() {
        assertEquals(Preferences.Main.TopicBackBehavior.HISTORY,
                SettingsPreferenceParsers.parseTopicBackBehavior(Preferences.Main.TopicBackBehavior.HISTORY.name))
        assertEquals(Preferences.Main.StartupScreen.NEWS,
                SettingsPreferenceParsers.parseStartupScreen(Preferences.Main.StartupScreen.NEWS.name))
        assertEquals(Preferences.Main.DownloadMethod.SYSTEM,
                SettingsPreferenceParsers.parseDownloadMethod(Preferences.Main.DownloadMethod.SYSTEM.name))
    }
}
