package forpdateam.ru.forpda.diagnostic

import android.content.Context
import android.content.Intent
import android.os.Build
import forpdateam.ru.forpda.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Локальный логгер аварийных завершений.
 *
 * Прежний обработчик ([forpdateam.ru.forpda.App.setupAppMetrica]) слал краши только в
 * AppMetrica и только во флейворе `store` — на dev/beta/parallel/stable падения нигде не
 * фиксировались, а до дашборда AppMetrica у пользователя доступа нет.
 *
 * Этот репортер ставит глобальный [Thread.UncaughtExceptionHandler] НЕЗАВИСИМО от флейвора:
 * пишет читаемый отчёт (время, версия, устройство, поток, полный стек) в файл в каталоге
 * приложения, который можно достать файловым менеджером по пути
 * `Android/data/<package>/files/crash/`, и помечает отчёт как «непоказанный», чтобы при
 * следующем запуске предложить отправить его (см. [consumePendingReport] / [buildShareIntent]).
 *
 * Цепочка обработчиков сохраняется: ранее установленный handler (система / AppMetrica)
 * вызывается после записи файла, поэтому штатное поведение (диалог ANR/краша, отчёт в
 * AppMetrica для store) не теряется.
 */
object CrashReporter {

    private const val DIR = "crash"
    private const val LAST_FILE = "last_crash.txt"
    private const val HANDLED_FILE = "handled_errors.txt"
    private const val PENDING_FLAG = "pending_crash.flag"
    private const val MAX_ARCHIVED = 10
    private const val MAX_HANDLED_BYTES = 256 * 1024

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context) ?: return
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("=== ForPDA crash report ===")
            appendLine("time: $ts")
            appendLine(
                    "app: ${BuildConfig.APPLICATION_ID} v${BuildConfig.VERSION_NAME} " +
                            "(${BuildConfig.VERSION_CODE}) flavor=${BuildConfig.FLAVOR} debug=${BuildConfig.DEBUG}"
            )
            appendLine(
                    "device: ${Build.MANUFACTURER} ${Build.MODEL} | " +
                            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            appendLine("thread: ${thread.name}")
            appendLine()
            append(throwable.stackTraceToString())
        }
        // last_crash.txt — всегда самый свежий; плюс архивная копия с таймстампом для истории.
        runCatching { File(dir, LAST_FILE).writeText(report) }
        runCatching {
            File(dir, "crash_$ts.txt").writeText(report)
            pruneArchive(dir)
        }
        runCatching { File(dir, PENDING_FLAG).writeText(ts) }
    }

    private fun pruneArchive(dir: File) {
        val archived = dir
                .listFiles { f -> f.name.startsWith("crash_") }
                ?.sortedByDescending { it.lastModified() }
                ?: return
        archived.drop(MAX_ARCHIVED).forEach { runCatching { it.delete() } }
    }

    private fun crashDir(context: Context): File? {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, DIR)
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) return null
        return dir
    }

    /** Есть ли неотправленный/непоказанный отчёт о фатальном краше. */
    fun hasPendingReport(context: Context): Boolean =
            crashDir(context)?.let { File(it, PENDING_FLAG).exists() } == true

    /** Текст последнего краша БЕЗ снятия флага (для авто-отправки: флаг снимаем только при успехе). */
    fun peekPendingReport(context: Context): String? {
        val dir = crashDir(context) ?: return null
        if (!File(dir, PENDING_FLAG).exists()) return null
        val last = File(dir, LAST_FILE)
        return if (last.exists()) runCatching { last.readText() }.getOrNull() else null
    }

    /** Снять флаг «ожидает отправки» — после успешной авто-отправки или ручного шеринга. */
    fun clearPending(context: Context) {
        val dir = crashDir(context) ?: return
        runCatching { File(dir, PENDING_FLAG).delete() }
    }

    /**
     * Текст последнего краша, если есть «непоказанный» отчёт; иначе null.
     * Снимает флаг, поэтому повторный вызов вернёт null до следующего падения.
     */
    fun consumePendingReport(context: Context): String? {
        val text = peekPendingReport(context)
        clearPending(context)
        return text
    }

    /**
     * Запись «пойманной» (не фатальной) ошибки в отдельный файл `handled_errors.txt`.
     * Нужна там, где исключение перехвачено защитной сеткой и НЕ доходит до фатального
     * обработчика (например, [forpdateam.ru.forpda.ui.fragments.theme.ThemeFragment.guardThemeRender]):
     * краша нет, но стек всё равно сохраняется и его можно достать тем же файловым менеджером.
     * Файл с простой ротацией по размеру, чтобы не рос бесконечно.
     */
    fun logHandled(context: Context, tag: String, throwable: Throwable) =
            appendHandled(context, tag, throwable.stackTraceToString())

    /**
     * Текстовая заметка в тот же `handled_errors.txt` (без стека) — например, причина неудачной
     * отправки отчёта. Нужна, чтобы такие события были видны и в РЕЛИЗЕ, где Timber не пишет
     * никуда (дерево логов сажается только в debug).
     */
    fun logNote(context: Context, tag: String, message: String) =
            appendHandled(context, tag, message)

    private fun appendHandled(context: Context, tag: String, body: String) {
        runCatching {
            val dir = crashDir(context) ?: return
            val file = File(dir, HANDLED_FILE)
            if (file.exists() && file.length() > MAX_HANDLED_BYTES) {
                val tail = runCatching { file.readText().takeLast(MAX_HANDLED_BYTES / 2) }.getOrDefault("")
                file.writeText(tail)
            }
            val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            file.appendText(
                    buildString {
                        appendLine("=== handled $ts [$tag] ===")
                        appendLine(
                                "app v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                                        "${BuildConfig.FLAVOR} | ${Build.MANUFACTURER} ${Build.MODEL} API ${Build.VERSION.SDK_INT}"
                        )
                        appendLine(body)
                        appendLine()
                    }
            )
        }
    }

    /**
     * Share-intent с текстом отчёта. Без FileProvider — пользователь сам выбирает приёмник
     * (Telegram / почта / заметки), отчёт уходит как обычный текст.
     */
    fun buildShareIntent(report: String): Intent {
        val trimmed = if (report.length > MAX_SHARE_CHARS) {
            report.substring(0, MAX_SHARE_CHARS) + "\n…(обрезано)"
        } else {
            report
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ForPDA crash report")
            putExtra(Intent.EXTRA_TEXT, trimmed)
        }
    }

    // Некоторые приёмники (мессенджеры) обрезают/отклоняют слишком длинный EXTRA_TEXT.
    private const val MAX_SHARE_CHARS = 90_000
}
