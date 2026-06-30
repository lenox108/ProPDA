package forpdateam.ru.forpda.diagnostic

import android.content.Context
import androidx.preference.PreferenceManager
import forpdateam.ru.forpda.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Автоотправка отчётов о сбоях в Telegram-бота.
 *
 * Сам краш отправить нельзя — на момент падения процесс умирает. Поэтому [CrashReporter]
 * сохраняет отчёт в файл, а отправка происходит при СЛЕДУЮЩЕМ запуске: [App] на старте
 * вызывает [trySendPending] в фоне.
 *
 * Токен и chat_id берутся из BuildConfig (см. app/build.gradle ← gradle.properties), поэтому
 * не лежат в исходниках. Если они пусты — фича выключена. Дополнительно есть тумблер в
 * настройках ([PREF_AUTO_SEND], по умолчанию включён), чтобы можно было отключить отправку,
 * не пересобирая приложение.
 *
 * Отчёт уходит как ДОКУМЕНТ (sendDocument), а не текстом: логи часто длиннее лимита sendMessage
 * (4096 символов), а документ Telegram принимает до 50 МБ.
 */
object CrashTelegramUploader {

    const val PREF_AUTO_SEND = "crash_report_tg_auto_send"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    /** Токен и chat_id заданы в сборке. */
    fun isConfigured(): Boolean =
            BuildConfig.TELEGRAM_BOT_TOKEN.isNotBlank() && BuildConfig.TELEGRAM_CHAT_ID.isNotBlank()

    /** Тумблер в настройках (по умолчанию включён). */
    fun isAutoSendEnabled(context: Context): Boolean =
            PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_AUTO_SEND, true)

    /** Автоотправка реально активна = бот настроен И тумблер включён. */
    fun isAutoSendActive(context: Context): Boolean =
            isConfigured() && isAutoSendEnabled(context)

    /**
     * Если включена автоотправка и есть неотправленный отчёт — шлём его боту.
     * Флаг «ожидает отправки» снимаем ТОЛЬКО при успехе: при сбое сети отчёт переотправится
     * при следующем запуске. Метод блокирующий — вызывать с IO-диспетчера.
     */
    fun trySendPending(context: Context) {
        if (!isAutoSendActive(context)) return
        val report = CrashReporter.peekPendingReport(context) ?: return
        val error = sendDocument(
                fileName = "forpda_crash_v${BuildConfig.VERSION_CODE}.txt",
                caption = buildCaption(report),
                content = report
        )
        if (error == null) {
            CrashReporter.clearPending(context)
            Timber.i("Crash report sent to Telegram")
        } else {
            // Флаг НЕ снимаем — повторим при следующем запуске. Причину пишем в файл-лог,
            // т.к. в релизе Timber молчит, а знать «почему не ушло» полезно.
            Timber.w("Crash report Telegram send failed: %s; will retry next launch", error)
            CrashReporter.logNote(context, "tg_send_failed", error)
        }
    }

    private fun buildCaption(report: String): String {
        // Первая содержательная строка стека (тип исключения + сообщение) информативнее всего.
        val firstStackLine = report.lineSequence()
                .firstOrNull { it.contains("Exception") || it.contains("Error") }
                ?.trim()
                .orEmpty()
        val header = "ForPDA crash v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}"
        return (header + if (firstStackLine.isNotEmpty()) "\n$firstStackLine" else "")
                .take(1000) // лимит caption Telegram — 1024
    }

    /** @return null при успехе, иначе короткая причина ошибки (для лога). */
    private fun sendDocument(fileName: String, caption: String, content: String): String? {
        val token = BuildConfig.TELEGRAM_BOT_TOKEN
        val chatId = BuildConfig.TELEGRAM_CHAT_ID
        if (token.isBlank() || chatId.isBlank()) return "not configured (empty token/chat_id)"
        return runCatching {
            val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart(
                            "document",
                            fileName,
                            content.toByteArray().toRequestBody("text/plain".toMediaType())
                    )
                    .build()
            val request = Request.Builder()
                    .url("https://api.telegram.org/bot$token/sendDocument")
                    .post(body)
                    .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) null
                else "HTTP ${response.code}: ${response.body?.string()?.take(300).orEmpty()}"
            }
        }.getOrElse { "${it.javaClass.simpleName}: ${it.message}" }
    }
}
