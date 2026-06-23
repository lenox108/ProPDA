package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 (perf(fgs)): на process_stop сервис должен детачить FGS-уведомление
 * через [Service.stopForeground] с флагом STOP_FOREGROUND_DETACH, а не
 * STOP_FOREGROUND_REMOVE. Это снимает foreground-привилегии и ограничения
 * background-launch'ей, не удаляя обязательное FGS-уведомление из системы
 * (его в шторке и не было — канал VISIBILITY_SECRET).
 *
 * Тест source-level: проверяет, что в `NotificationsService.detachForegroundIfPromoted`
 * используется именно `STOP_FOREGROUND_DETACH` (не `STOP_FOREGROUND_REMOVE`),
 * и что `cancelAllNotifications` по-прежнему использует `STOP_FOREGROUND_REMOVE`
 * (для настоящего удаления уведомления при логауте/выключении/свайпе из Recents).
 * Source-level вместо Robolectric, потому что `NotificationsService` — `@AndroidEntryPoint`,
 * и поднимать Hilt-граф ради одного assert'а избыточно.
 */
class NotificationsServiceForegroundDetachTest {

    @Test
    fun detachForegroundIfPromoted_usesStopForegroundDetach() {
        val body = readServiceSource()
        val detachMethod = extractMethodBody(body, "detachForegroundIfPromoted")
        assertNotNull("метод detachForegroundIfPromoted должен существовать", detachMethod)
        assertTrue(
            "detachForegroundIfPromoted должен вызывать stopForeground(STOP_FOREGROUND_DETACH); получили:\n$detachMethod",
            detachMethod!!.contains("STOP_FOREGROUND_DETACH")
        )
        assertTrue(
            "cancelAllNotifications обязан остаться на STOP_FOREGROUND_REMOVE; " +
                    "иначе на логауте/выключении уведомление не уберётся из шторки.\n$detachMethod",
            !detachMethod.contains("STOP_FOREGROUND_REMOVE")
        )
    }

    @Test
    fun cancelAllNotifications_stillUsesStopForegroundRemove() {
        val body = readServiceSource()
        val cancelMethod = extractMethodBody(body, "cancelAllNotifications")
        assertNotNull("метод cancelAllNotifications должен существовать", cancelMethod)
        assertTrue(
            "cancelAllNotifications обязан вызывать stopForeground(STOP_FOREGROUND_REMOVE); " +
                    "иначе при логауте/выключении/свайпе уведомление не уберётся из шторки.\n$cancelMethod",
            cancelMethod!!.contains("STOP_FOREGROUND_REMOVE")
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

    private fun extractMethodBody(body: String, name: String): String? {
        val signature = Regex("""\bprivate\s+fun\s+${Regex.escape(name)}\s*\([^)]*\)\s*\{""")
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
