package forpdateam.ru.forpda.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регрессия полевого сбоя: после обрыва WebSocket 138 alarm подряд дошли до receiver, но
 * EventsCheckWorker не стартовал. Constrained unique-work оставалась pending, а KEEP молча
 * отбрасывал каждый новый запрос.
 *
 * Source-level тест уместен здесь, потому что receiver — @AndroidEntryPoint, а поднимать полный
 * Hilt + WorkManager граф ради проверки параметров постановки работы избыточно.
 */
class EventsCheckAlarmContractTest {

    @Test
    fun alarmWork_replacesStaleRequestAndHasNoNetworkConstraint() {
        val receiverBody = extractReceiverBody(readAlarmSource())

        assertTrue(
            "Alarm обязан заменять stale ENQUEUED работу, иначе один сбой блокирует все " +
                    "последующие alarm.\n$receiverBody",
            receiverBody.contains("ExistingWorkPolicy.REPLACE")
        )
        assertFalse(
            "ExistingWorkPolicy.KEEP снова превратит первую зависшую задачу в вечную пробку.\n" +
                    receiverBody,
            receiverBody.contains("ExistingWorkPolicy.KEEP")
        )
        assertFalse(
            "Alarm-path должен быть немедленным и unconstrained; сетевой constraint остаётся " +
                    "только у periodic safety-net.\n$receiverBody",
            receiverBody.contains("setRequiredNetworkType")
        )
    }

    @Test
    fun receiverWaitsUntilWorkManagerHasPersistedTheRequest() {
        val receiverBody = extractReceiverBody(readAlarmSource())

        assertTrue("receiver обязан использовать goAsync()", receiverBody.contains("goAsync()"))
        assertTrue(
            "BroadcastReceiver нельзя завершать до окончания enqueue operation",
            receiverBody.contains("operation.await()")
        )
        assertTrue(
            "PendingResult должен завершаться даже при ошибке постановки",
            receiverBody.contains("pendingResult.finish()")
        )
    }

    private fun readAlarmSource(): String {
        val resource = javaClass.classLoader
                ?.getResource("forpdateam/ru/forpda/notifications/EventsCheckAlarm.kt")
        if (resource != null) return resource.readText()
        val file = java.io.File(
            "src/main/java/forpdateam/ru/forpda/notifications/EventsCheckAlarm.kt"
        )
        check(file.exists()) {
            "EventsCheckAlarm.kt не найден ни в classpath, ни на диске: ${file.absolutePath}"
        }
        return file.readText()
    }

    private fun extractReceiverBody(source: String): String {
        val marker = "class EventsCheckAlarmReceiver"
        val start = source.indexOf(marker)
        check(start >= 0) { "$marker не найден" }
        return source.substring(start)
    }
}
