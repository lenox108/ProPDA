package forpdateam.ru.forpda.notifications

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Персистентный журнал фоновых проверок уведомлений, работающий и в release-сборках.
 *
 * Нужен, потому что жалобы «в фоне не приходят уведомления» невозможно диагностировать:
 * [forpdateam.ru.forpda.common.BatteryDebugLogger] пишет только в DEBUG, а на устройстве
 * пользователя нечем отличить «WorkManager вообще не запускает воркер (OEM/Doze)» от
 * «воркер запускается, но выходит пустым (гость/кэш/скип)». Журнал показывается в
 * настройках уведомлений («Диагностика фоновых проверок»).
 *
 * Стоимость: одна маленькая запись в файл на цикл воркера (раз в 15+ минут) — ничто.
 */
object NotifDiagLog {

    private const val FILE_NAME = "notif_diag.log"
    private const val MAX_LINES = 200
    // Подрезаем не на каждой записи, а с гистерезисом, чтобы не переписывать файл постоянно.
    private const val TRIM_THRESHOLD = 260

    private val timeFormat = SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
    private val lock = Any()

    fun log(context: Context, message: String) {
        synchronized(lock) {
            runCatching {
                val file = file(context)
                file.appendText("${timeFormat.format(Date())} $message\n")
                trimIfNeeded(file)
            }
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        runCatching { file(context).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()
    }

    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).delete() }
        }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun trimIfNeeded(file: File) {
        val lines = file.readLines()
        if (lines.size <= TRIM_THRESHOLD) return
        file.writeText(lines.takeLast(MAX_LINES).joinToString("\n", postfix = "\n"))
    }
}
