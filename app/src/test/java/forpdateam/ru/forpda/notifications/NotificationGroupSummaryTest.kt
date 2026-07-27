package forpdateam.ru.forpda.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import forpdateam.ru.forpda.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows

/**
 * Сводки групп ([NotificationPublisher.refreshGroupSummaries]) — то, чего не хватало шторке:
 * без опубликованного group summary Android не сворачивает группу и заодно исключает её из
 * авто-бандлинга, поэтому события висели отдельными карточками.
 *
 * Состояние берётся из реальной шторки Robolectric, а не из моков: именно на рассинхроне
 * собственного счётчика со свайпами пользователя ломался прежний stacked-путь.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationGroupSummaryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context as android.app.Application)
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        NotificationsService.createEventChannels(context)
        NotificationPublisher.forgetAllChildren()
        NotificationManagerCompat.from(context).cancelAll()
    }

    @Test
    fun summary_isPostedOnceTwoChildrenShareAGroup() {
        postChild(1, "Тема 1")
        postChild(2, "Тема 2")

        NotificationPublisher.refreshGroupSummaries(context)

        val summary = activeById(NotificationGroups.SUMMARY_FAV_ID)
        assertNotNull("сводка избранного должна появиться при двух детях", summary)
        assertTrue(
                "уведомление обязано нести FLAG_GROUP_SUMMARY, иначе группа не свернётся",
                (summary!!.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        )
        assertEquals(NotificationGroups.FAV, summary.group)
        assertEquals(2, summary.number)
    }

    @Test
    fun summary_isNotPostedForASingleChild() {
        postChild(1, "Тема 1")

        NotificationPublisher.refreshGroupSummaries(context)

        assertNull(
                "одному уведомлению бандл не нужен — сводка была бы лишней карточкой",
                activeById(NotificationGroups.SUMMARY_FAV_ID)
        )
    }

    @Test
    fun summary_isRemovedWhenChildrenDropBelowTwo() {
        postChild(1, "Тема 1")
        postChild(2, "Тема 2")
        NotificationPublisher.refreshGroupSummaries(context)
        assertNotNull(activeById(NotificationGroups.SUMMARY_FAV_ID))

        NotificationManagerCompat.from(context).cancel(2)
        NotificationPublisher.refreshGroupSummaries(context)

        assertNull(
                "сводка не должна пережить своих детей",
                activeById(NotificationGroups.SUMMARY_FAV_ID)
        )
    }

    @Test
    fun summary_countsOnlyItsOwnGroup() {
        postChild(1, "Тема 1")
        postChild(2, "Тема 2")
        postChild(3, "Диалог", NotificationGroups.QMS, NotificationsService.CHANNEL_QMS_ID)

        NotificationPublisher.refreshGroupSummaries(context)

        assertEquals(2, activeById(NotificationGroups.SUMMARY_FAV_ID)?.number)
        assertNull(
                "у QMS всего один ребёнок — своей сводки быть не должно",
                activeById(NotificationGroups.SUMMARY_QMS_ID)
        )
    }

    @Test
    fun summary_isSilentForSingleEventsAndAlertsForBatches() {
        postChild(1, "Тема 1")
        postChild(2, "Тема 2")

        NotificationPublisher.refreshGroupSummaries(context)
        assertEquals(
                "одиночная публикация уже прозвучала ребёнком — сводка обязана молчать",
                NotificationCompat.GROUP_ALERT_CHILDREN,
                activeById(NotificationGroups.SUMMARY_FAV_ID)?.groupAlertBehavior
        )

        NotificationPublisher.refreshGroupSummaries(context, alertGroups = setOf(NotificationGroups.FAV))
        assertEquals(
                "в пачке звучит сводка: дети публикуются с GROUP_ALERT_SUMMARY",
                NotificationCompat.GROUP_ALERT_ALL,
                activeById(NotificationGroups.SUMMARY_FAV_ID)?.groupAlertBehavior
        )
    }

    private fun postChild(
            id: Int,
            title: String,
            group: String = NotificationGroups.FAV,
            channelId: String = NotificationsService.CHANNEL_FAV_ID,
    ) {
        val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notify_favorites)
                .setContentTitle(title)
                .setContentText("новое сообщение")
                .setGroup(group)
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun activeById(id: Int): Notification? = context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == id }
            ?.notification
}
