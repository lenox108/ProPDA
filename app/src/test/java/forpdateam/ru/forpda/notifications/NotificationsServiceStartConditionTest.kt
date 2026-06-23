package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsServiceStartConditionTest {

    @Test
    fun noPushFamilies_skipsStart_evenWhenAuthed() {
        assertFalse(
            "wantsPushNotifications=false и isAuth=true → сервис НЕ стартует",
            NotificationsService.shouldStartService(wantsPushNotifications = false, isAuth = true)
        )
    }

    @Test
    fun notAuthed_skipsStart_evenWhenPushFamiliesEnabled() {
        assertFalse(
            "wantsPushNotifications=true и isAuth=false → сервис НЕ стартует",
            NotificationsService.shouldStartService(wantsPushNotifications = true, isAuth = false)
        )
    }

    @Test
    fun pushFamiliesAndAuthed_startsService() {
        assertTrue(
            "wantsPushNotifications=true и isAuth=true → сервис стартует",
            NotificationsService.shouldStartService(wantsPushNotifications = true, isAuth = true)
        )
    }

    @Test
    fun noPushFamiliesAndNotAuthed_skipsStart() {
        assertFalse(
            "wantsPushNotifications=false и isAuth=false → сервис НЕ стартует",
            NotificationsService.shouldStartService(wantsPushNotifications = false, isAuth = false)
        )
    }
}
