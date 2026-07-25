package forpdateam.ru.forpda.settingsbackup

import android.net.Uri
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.common.SecureCookiesPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SettingsBackupServiceTest {

    @Test
    fun settingsOnlyBackupDoesNotContainOrReplaceCurrentAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit()
            .clear()
            .putString(TEST_SETTING, "from-backup")
            .putString(Preferences.Auth.USER_ID, "101")
            .putString("auth_state", "AUTH")
            .putString("current_user", "backup-profile")
            .commit()

        val file = File(context.cacheDir, "settings-only-backup.json")
        val service = SettingsBackupService(context)
        service.write(Uri.fromFile(file), includeSession = false)

        val backupText = file.readText()
        assertFalse(backupText.contains("backup-profile"))
        assertFalse(backupText.contains("\"cookie_member_id\""))

        preferences.edit()
            .putString(TEST_SETTING, "changed-after-backup")
            .putString(Preferences.Auth.USER_ID, "202")
            .putString("auth_state", "AUTH")
            .putString("current_user", "current-profile")
            .commit()

        service.restore(Uri.fromFile(file))

        assertEquals("from-backup", preferences.getString(TEST_SETTING, null))
        assertEquals("202", preferences.getString(Preferences.Auth.USER_ID, null))
        assertEquals("AUTH", preferences.getString("auth_state", null))
        assertEquals("current-profile", preferences.getString("current_user", null))
    }

    @Test
    fun backupWithSessionRestoresProfileAndCookies() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val securePreferences = SecureCookiesPreferences.getInstance(context)
        preferences.edit()
            .clear()
            .putString(Preferences.Auth.USER_ID, "101")
            .putString("auth_state", "AUTH")
            .putString("current_user", "backup-profile")
            .commit()
        securePreferences.restoreAuthCookies(
            mapOf(
                Preferences.Auth.COOKIE_MEMBER_ID to "member-cookie",
                Preferences.Auth.COOKIE_PASS_HASH to "pass-cookie",
            ),
        )

        val file = File(context.cacheDir, "backup-with-session.json")
        val service = SettingsBackupService(context)
        service.write(Uri.fromFile(file), includeSession = true)

        preferences.edit()
            .putString(Preferences.Auth.USER_ID, "202")
            .putString("auth_state", "NO_AUTH")
            .putString("current_user", "changed-profile")
            .commit()
        securePreferences.restoreAuthCookies(emptyMap())

        service.restore(Uri.fromFile(file))

        assertEquals("101", preferences.getString(Preferences.Auth.USER_ID, null))
        assertEquals("AUTH", preferences.getString("auth_state", null))
        assertEquals("backup-profile", preferences.getString("current_user", null))
        assertEquals(
            "member-cookie",
            securePreferences.getString(Preferences.Auth.COOKIE_MEMBER_ID),
        )
        assertEquals(
            "pass-cookie",
            securePreferences.getString(Preferences.Auth.COOKIE_PASS_HASH),
        )
    }

    private companion object {
        const val TEST_SETTING = "backup_test_setting"
    }
}
