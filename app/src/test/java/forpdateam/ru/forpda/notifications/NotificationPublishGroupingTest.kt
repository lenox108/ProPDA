package forpdateam.ru.forpda.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Сквозная проверка ровно того, что видит пользователь: после двух `publish()` в шторке должна
 * появиться сводка группы. Прежний тест дёргал [NotificationPublisher.refreshGroupSummaries]
 * напрямую и потому не покрывал саму проводку publish → сводка.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationPublishGroupingTest {

    private lateinit var context: Context
    private lateinit var prefs: NotificationPreferencesHolder

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context as Application)
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        prefs = NotificationPreferencesHolder(context)
        // Реестр свежих публикаций — состояние object'а, между тестами его надо обнулять.
        NotificationPublisher.forgetAllChildren()
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun twoFavoriteEvents_produceAGroupSummary() {
        NotificationPublisher.publish(context, prefs, favoriteEvent(1, "Тема 1"))
        NotificationPublisher.publish(context, prefs, favoriteEvent(2, "Тема 2"))

        val summary = activeById(NotificationGroups.SUMMARY_FAV_ID)
        assertNotNull("после двух событий избранного сводка обязана быть в шторке", summary)
        assertEquals(NotificationGroups.FAV, summary!!.group)
        assertEquals(
                "сводка без FLAG_GROUP_SUMMARY не сворачивает группу",
                Notification.FLAG_GROUP_SUMMARY,
                summary.flags and Notification.FLAG_GROUP_SUMMARY
        )
        assertEquals(2, summary.number)
    }

    @Test
    fun childrenCarryTheGroupKey() {
        NotificationPublisher.publish(context, prefs, favoriteEvent(1, "Тема 1"))
        NotificationPublisher.publish(context, prefs, favoriteEvent(2, "Тема 2"))

        val children = context.getSystemService(NotificationManager::class.java)
                .activeNotifications
                .filter { it.id != NotificationGroups.SUMMARY_FAV_ID }
        assertEquals(2, children.size)
        for (child in children) {
            assertEquals(
                    "ребёнок без group key не попадёт в бандл",
                    NotificationGroups.FAV,
                    child.notification.group
            )
        }
    }

    /**
     * Полевой баг: `notify()` — асинхронный binder, и `getActiveNotifications()` сразу после него
     * своих же уведомлений ещё не видит. На пачке каждая публикация видела «детей меньше двух» и
     * сводку не ставила. Здесь пустая шторка эмулирует это отставание.
     */
    @Test
    fun summary_survivesAShadeThatHasNotCaughtUpYet() {
        NotificationPublisher.publish(context, prefs, favoriteEvent(1, "Тема 1"), refreshSummary = false)
        NotificationPublisher.publish(context, prefs, favoriteEvent(2, "Тема 2"), refreshSummary = false)
        // Шторка «ещё не отдаёт» только что опубликованное.
        androidx.core.app.NotificationManagerCompat.from(context).cancelAll()

        NotificationPublisher.refreshGroupSummaries(context)

        assertNotNull(
                "сводка обязана строиться и по свежим публикациям, а не только по показаниям шторки",
                activeById(NotificationGroups.SUMMARY_FAV_ID)
        )
    }

    @Test
    fun batch_publishesChildrenAndOneSummary() {
        val events = (1..6).map { favoriteEvent(it, "Тема $it") }

        NotificationPublisher.publishBatch(context, prefs, events)

        val active = context.getSystemService(NotificationManager::class.java).activeNotifications
        val summary = activeById(NotificationGroups.SUMMARY_FAV_ID)
        assertNotNull("пачка обязана дать сводку", summary)
        assertEquals(6, active.size - 1)
        assertEquals(6, summary!!.number)
    }

    private fun favoriteEvent(sourceId: Int, title: String) = NotificationEvent(
            type = NotificationEvent.Type.NEW,
            source = NotificationEvent.Source.THEME,
            sourceId = sourceId,
            sourceTitle = title,
            userNick = "User$sourceId",
    )

    private fun activeById(id: Int): Notification? = context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == id }
            ?.notification
}
