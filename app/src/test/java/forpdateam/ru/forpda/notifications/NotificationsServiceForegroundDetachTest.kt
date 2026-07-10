package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Снятие FGS обязано УДАЛЯТЬ «служебное» уведомление, а не отцеплять его.
 *
 * Исторически здесь стоял STOP_FOREGROUND_DETACH — под предпосылкой, что уведомления
 * в шторке всё равно не видно (канал VISIBILITY_SECRET) и что при возврате в foreground
 * его восстановит `promoteToForegroundIfNeeded()`. Обе предпосылки неверны:
 * VISIBILITY_SECRET прячет уведомление только на экране блокировки, а re-promote при
 * возврате на передний план из кода убран (в foreground FGS не поднимается вовсе).
 * В результате DETACH оставлял в шторке уведомление, снять которое было уже некому.
 *
 * Отдельно пиннится граница ответственности: смахивание приложения из Recents убирает только
 * служебное уведомление. Уведомления о новых сообщениях (и о неотправленном быстром ответе,
 * который несёт набранный пользователем текст) переживают свайп.
 *
 * Source-level вместо Robolectric, потому что `NotificationsService` — `@AndroidEntryPoint`,
 * и поднимать Hilt-граф ради одного assert'а избыточно.
 */
class NotificationsServiceForegroundDetachTest {

    @Test
    fun detachForegroundIfPromoted_removesTheNotification() {
        val detachMethod = extractMethodBody(readServiceSource(), "detachForegroundIfPromoted")
        assertNotNull("метод detachForegroundIfPromoted должен существовать", detachMethod)
        assertTrue(
            "detachForegroundIfPromoted должен вызывать stopForeground(STOP_FOREGROUND_REMOVE): " +
                    "иначе «служебное» уведомление залипает в шторке.\n$detachMethod",
            detachMethod!!.contains("STOP_FOREGROUND_REMOVE")
        )
        assertTrue(
            "STOP_FOREGROUND_DETACH оставляет уведомление без владельца; его сюда возвращать нельзя.\n$detachMethod",
            !detachMethod.contains("STOP_FOREGROUND_DETACH")
        )
    }

    @Test
    fun removeForegroundNotification_usesStopForegroundRemove() {
        val method = extractMethodBody(readServiceSource(), "removeForegroundNotification")
        assertNotNull("метод removeForegroundNotification должен существовать", method)
        assertTrue(
            "снятие FGS обязано удалять уведомление, а не отцеплять его.\n$method",
            method!!.contains("STOP_FOREGROUND_REMOVE")
        )
    }

    @Test
    fun onTaskRemoved_doesNotCancelEventNotifications() {
        val method = extractMethodBody(readServiceSource(), "onTaskRemoved", modifier = "override")
        assertNotNull("метод onTaskRemoved должен существовать", method)
        assertTrue(
            "свайп из Recents не значит «я всё прочитал»: события и неотправленный ответ " +
                    "обязаны пережить его.\n$method",
            !method!!.contains("cancelAllNotifications")
        )
        assertTrue(
            "служебное FGS-уведомление при этом снять надо.\n$method",
            method.contains("removeForegroundNotification")
        )
    }

    @Test
    fun cancelAllNotifications_neverTouchesForeignChannels() {
        val method = extractMethodBody(readServiceSource(), "cancelAllNotifications")
        assertNotNull("метод cancelAllNotifications должен существовать", method)
        assertTrue(
            "cancelAll() снёс бы прогресс загрузки и уведомление об обновлении.\n$method",
            !method!!.contains("cancelAll()")
        )
    }

    private fun readServiceSource(): String {
        val resource = javaClass.classLoader
                ?.getResource("forpdateam/ru/forpda/notifications/NotificationsService.kt")
        if (resource != null) return resource.readText()
        // Fallback: читаем напрямую с диска, относительно workingDir.
        val file = java.io.File(
            "src/main/java/forpdateam/ru/forpda/notifications/NotificationsService.kt"
        )
        check(file.exists()) {
            "NotificationsService.kt не найден ни в classpath, ни на диске: ${file.absolutePath}"
        }
        return file.readText()
    }

    private fun extractMethodBody(body: String, name: String, modifier: String = "private"): String? {
        val signature = Regex("""\b$modifier\s+fun\s+${Regex.escape(name)}\s*\([^)]*\)[^{]*\{""")
        val match = signature.find(body) ?: return null
        val start = match.range.last + 1
        var depth = 1
        var i = start
        while (i < body.length && depth > 0) {
            when (body[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return body.substring(start, i - 1)
    }
}
