package forpdateam.ru.forpda.common

import forpdateam.ru.forpda.common.ClipboardHelper
import android.content.*
import android.os.Build
import timber.log.Timber
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.diagnostic.FpdaDebugLog
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import java.io.UnsupportedEncodingException
import java.text.ParseException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Created by isanechek on 30.07.16.
 */
object Utils {
    val isMM: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    fun getFileNameFromUrl(url: String): String {
        return DownloadFileName.fromUrl(url)
    }

    @JvmStatic
    @Deprecated("Use ClipboardHelper via DI", ReplaceWith("clipboardHelper.copyToClipboard(s)"))
    fun copyToClipBoard(s: String?, clipboardHelper: ClipboardHelper) {
        clipboardHelper.copyToClipboard(s)
    }

    @JvmStatic
    @Deprecated("Use ClipboardHelper via DI", ReplaceWith("clipboardHelper.readFromClipboard()"))
    fun readFromClipboard(clipboardHelper: ClipboardHelper): String? {
        return clipboardHelper.readFromClipboard()
    }

    fun shareText(context: Context, text: String?) {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        sendIntent.putExtra(Intent.EXTRA_TEXT, text)
        sendIntent.type = "text/plain"
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun <T> checkNotNull(value: T?, message: String?): T {
        if (value == null) {
            throw NullPointerException(message)
        }
        return value
    }

    fun <T> checkNotNull(value: T?): T {
        if (value == null) {
            throw NullPointerException()
        }
        return value
    }

    fun longLog(msg: String) {
        val maxLogSize = 1000
        for (i in 0..msg.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > msg.length) msg.length else end
            Timber.v(msg.substring(start, end))
        }
    }

    fun log(msg: String) {
        Timber.d(msg)
    }

    private const val FORUM_DATE_TIME_TAG = "ForumDateTime"
    private const val FAV_DATE_SYNC_TAG = "FavDateSync"
    private val forumSourceZone: ZoneId = ZoneId.of("Europe/Moscow")

    // DateTimeFormatter is thread-safe by default, no need for ThreadLocal
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val parseDateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.ROOT)
            .withZone(forumSourceZone)
    private val parseDateTimeNoZoneFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.ROOT)

    val day: String
        get() {
            val now = ZonedDateTime.now(forumSourceZone)
            return dateFormat.format(now)
        }
    val yesterday: String
        get() {
            val now = ZonedDateTime.now(forumSourceZone).minusDays(1)
            return dateFormat.format(now)
        }

    @JvmStatic
    fun getForumDateTime(date: Date?): String {
        return if (date == null) "" else {
            val localDateTime = LocalDateTime.ofInstant(date.toInstant(), forumSourceZone)
            formatForumAbsoluteDateTime(localDateTime)
        }
    }

    @JvmStatic
    fun getForumDisplayDateTime(date: Date?): String {
        return getForumDisplayDateTime(date, forumSourceZone)
    }

    internal fun getForumDisplayDateTime(date: Date?, displayZone: ZoneId): String {
        if (date == null) return ""

        val localDateTime = LocalDateTime.ofInstant(date.toInstant(), forumSourceZone)
        val localDate = localDateTime.toLocalDate()
        val today = LocalDate.now(forumSourceZone)
        val localTime = timeFormat.format(localDateTime)

        val result = when (localDate) {
            today -> "Сегодня, $localTime"
            today.minusDays(1) -> "Вчера, $localTime"
            else -> formatForumAbsoluteDateTime(localDateTime)
        }
        logForumDateTime(
                "Display forum instant without device shift: context=%s, raw=%s, sourceZone=%s, deviceZone=%s, instant=%s, output=%s",
                "instant",
                null,
                forumSourceZone,
                displayZone,
                date.toInstant(),
                result
        )
        return result
    }

    @JvmStatic
    fun formatForumDisplayDateTime(dateTime: String?): String? {
        return formatForumDisplayDateTime(dateTime, "forum")
    }

    @JvmStatic
    fun formatForumDisplayDateTime(dateTime: String?, context: String): String? {
        return formatForumDisplayDateTime(dateTime, currentDeviceZone(), context)
    }

    @JvmStatic
    fun resolveForumQuoteDate(rawDateTime: String?, displayedDateTime: String?, context: String): String {
        val displayed = displayedDateTime?.trim().takeUnless { it.isNullOrBlank() }
        if (displayed != null) {
            logForumDateTime(
                    "Use displayed quote date: context=%s, raw=%s, sourceZone=%s, deviceZone=%s, instant=%s, output=%s",
                    context,
                    rawDateTime?.trim(),
                    forumSourceZone,
                    currentDeviceZone(),
                    null,
                    displayed
            )
            return displayed
        }
        return formatForumDisplayDateTime(rawDateTime, context).orEmpty()
    }

    internal fun formatForumDisplayDateTime(dateTime: String?, displayZone: ZoneId): String? {
        return formatForumDisplayDateTime(dateTime, displayZone, "forum")
    }

    internal fun formatForumDisplayDateTime(dateTime: String?, displayZone: ZoneId, context: String): String? {
        return formatForumDisplayDateTime(dateTime, displayZone, context, Clock.system(displayZone))
    }

    internal fun formatForumDisplayDateTime(dateTime: String?, displayZone: ZoneId, context: String, clock: Clock): String? {
        val raw = dateTime?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val output = normalizeForumProfileDateTime(raw, clock)
        logForumDateTime(
                "Display forum profile date without device shift: context=%s, raw=%s, sourceZone=%s, deviceZone=%s, instant=%s, output=%s",
                context,
                raw,
                forumSourceZone,
                displayZone,
                null,
                output
        )
        return output
    }

    @JvmStatic
    fun formatFavoritesDisplayDateTime(dateTime: String?): String? {
        return formatFavoritesDisplayDateTime(dateTime, currentDeviceZone(), Clock.system(currentDeviceZone()))
    }

    internal fun formatFavoritesDisplayDateTime(dateTime: String?, displayZone: ZoneId, clock: Clock): String? {
        val raw = dateTime?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val output = normalizeFavoritesProfileDateTime(raw, clock)
        logFavDateSync(
                "Display favorite profile date without device shift: raw=%s, sourceZone=%s, deviceZone=%s, instant=%s, output=%s",
                raw,
                forumSourceZone,
                displayZone,
                null,
                output
        )
        return output
    }

    private fun formatForumAbsoluteDateTime(dateTime: LocalDateTime): String {
        return "${dateFormat.format(dateTime)}, ${timeFormat.format(dateTime)}"
    }

    fun getNewsDateTime(date: Date?): String {
        return if (date == null) "" else {
            val localDateTime = LocalDateTime.ofInstant(date.toInstant(), currentDeviceZone())
            dateFormat.format(localDateTime)
        }
    }

    @JvmStatic
    fun parseForumDateTime(dateTime: String?): Date? {
        dateTime ?: return null
        val s = dateTime.trim().replace("Сегодня", day).replace("Вчера", yesterday)
        // Полный формат: 15.05.2026, 18:30 или 15.05.2026 18:30
        try {
            val parsed = parseDateTimeFormat.parse(s)
                    ?: return null
            val localDateTime = LocalDateTime.from(parsed)
            return localDateTime.toForumSourceDate(dateTime, s)
        } catch (_: Exception) {
        }
        // Двузначный год: 15.05.26, 18:30
        try {
            val fmt2 = DateTimeFormatter.ofPattern("dd.MM.yy, HH:mm", Locale.getDefault())
                    .withZone(forumSourceZone)
            val parsed = fmt2.parse(s) ?: return null
            val localDateTime = LocalDateTime.from(parsed)
            return localDateTime.toForumSourceDate(dateTime, s)
        } catch (_: Exception) {
        }
        // Без запятой: 15.05.2026 18:30
        try {
            val fmt3 = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .withZone(forumSourceZone)
            val parsed = fmt3.parse(s) ?: return null
            val localDateTime = LocalDateTime.from(parsed)
            return localDateTime.toForumSourceDate(dateTime, s)
        } catch (_: Exception) {
        }
        // Без года: 15.05, 18:30 или 15.05 18:30 — считаем год по текущей дате сайта.
        for (pattern in listOf("dd.MM, HH:mm", "dd.MM HH:mm")) {
            try {
                val fmtNoYear = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                val parsed = fmtNoYear.parse(s)
                val monthDay = java.time.MonthDay.from(parsed)
                val time = LocalTime.from(parsed)
                val siteYear = LocalDate.now(forumSourceZone).year
                val localDateTime = LocalDateTime.of(monthDay.atYear(siteYear), time)
                return localDateTime.toForumSourceDate(dateTime, s)
            } catch (_: Exception) {
            }
        }
        // Только время (например, "23:15") — считаем сегодня
        try {
            val timeOnly = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            val parsed = timeOnly.parse(s)
            val now = ZonedDateTime.now(forumSourceZone)
            val t = LocalTime.from(parsed)
            val localDateTime = LocalDateTime.of(now.toLocalDate(), t)
            return localDateTime.toForumSourceDate(dateTime, s)
        } catch (_: Exception) {
        }
        // Только дата (например, "15.05.2026")
        try {
            val dateOnly = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault())
            val parsed = dateOnly.parse(s)
            val localDate = LocalDate.from(parsed)
            val localDateTime = LocalDateTime.of(localDate, LocalTime.MIN)
            return localDateTime.toForumSourceDate(dateTime, s)
        } catch (_: Exception) {
        }
        return null
    }

    private fun parseForumLocalDateTime(dateTime: String, sourceZone: ZoneId, clock: Clock): LocalDateTime? {
        val sourceToday = LocalDate.now(clock.withZone(sourceZone))
        val s = dateTime.trim()
                .replace("Сегодня", dateFormat.format(sourceToday))
                .replace("Вчера", dateFormat.format(sourceToday.minusDays(1)))
        try {
            val parsed = parseDateTimeNoZoneFormat.parse(s) ?: return null
            return LocalDateTime.from(parsed)
        } catch (_: Exception) {
        }
        try {
            val fmt2 = DateTimeFormatter.ofPattern("dd.MM.yy, HH:mm", Locale.ROOT)
            val parsed = fmt2.parse(s) ?: return null
            return LocalDateTime.from(parsed)
        } catch (_: Exception) {
        }
        try {
            val fmt3 = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)
            val parsed = fmt3.parse(s) ?: return null
            return LocalDateTime.from(parsed)
        } catch (_: Exception) {
        }
        for (pattern in listOf("dd.MM, HH:mm", "dd.MM HH:mm")) {
            try {
                val fmtNoYear = DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
                val parsed = fmtNoYear.parse(s)
                val monthDay = java.time.MonthDay.from(parsed)
                val time = LocalTime.from(parsed)
                val year = sourceToday.year
                return LocalDateTime.of(monthDay.atYear(year), time)
            } catch (_: Exception) {
            }
        }
        try {
            val timeOnly = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            val parsed = timeOnly.parse(s)
            val t = LocalTime.from(parsed)
            return LocalDateTime.of(sourceToday, t)
        } catch (_: Exception) {
        }
        try {
            val dateOnly = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
            val parsed = dateOnly.parse(s)
            return LocalDateTime.of(LocalDate.from(parsed), LocalTime.MIN)
        } catch (_: Exception) {
        }
        return null
    }

    private fun normalizeForumProfileDateTime(dateTime: String, clock: Clock): String {
        normalizeForumRelativeDateTime(dateTime)?.let { return it }
        val parsed = parseForumLocalDateTime(dateTime, forumSourceZone, clock) ?: return dateTime
        return formatForumAbsoluteDateTime(parsed)
    }

    private fun normalizeFavoritesProfileDateTime(dateTime: String, clock: Clock): String {
        normalizeForumRelativeDateTime(dateTime)?.let { return it }
        val parsed = parseForumLocalDateTime(dateTime, forumSourceZone, clock) ?: return dateTime
        val sourceToday = LocalDate.now(clock.withZone(forumSourceZone))
        val localDate = parsed.toLocalDate()
        val localTime = timeFormat.format(parsed)
        return when (localDate) {
            sourceToday -> "Сегодня, $localTime"
            sourceToday.minusDays(1) -> "Вчера, $localTime"
            else -> formatForumAbsoluteDateTime(parsed)
        }
    }

    private fun normalizeForumRelativeDateTime(dateTime: String): String? {
        val match = Regex("""^(Сегодня|Вчера)\s*,?\s*(\d{1,2}):(\d{2})$""").matchEntire(dateTime) ?: return null
        val hour = match.groupValues[2].toIntOrNull() ?: return null
        val minute = match.groupValues[3].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return "${match.groupValues[1]}, ${hour.toString().padStart(2, '0')}:${match.groupValues[3]}"
    }

    private fun LocalDateTime.toForumSourceDate(raw: String, normalized: String): Date {
        val instant = atZone(forumSourceZone).toInstant()
        logForumDateTime(
                "Parsed forum date: context=%s, raw=%s, sourceZone=%s, deviceZone=%s, instant=%s, output=%s",
                "legacy-parse",
                raw.trim(),
                forumSourceZone,
                currentDeviceZone(),
                instant,
                normalized
        )
        return Date.from(instant)
    }

    private fun currentDeviceZone(): ZoneId = ZoneId.systemDefault()

    private fun logForumDateTime(message: String, vararg args: Any?) {
        // Per-date-parse log: gated behind VERBOSE_HOT_PATH so it doesn't flood logcat on list binds.
        if (BuildConfig.DEBUG && FpdaDebugLog.VERBOSE_HOT_PATH) {
            Timber.tag(FORUM_DATE_TIME_TAG).d(message, *args)
        }
    }

    private fun logFavDateSync(message: String, vararg args: Any?) {
        // Per-item favorite date log: gated behind VERBOSE_HOT_PATH (fires for every row on rebind).
        if (BuildConfig.DEBUG && FpdaDebugLog.VERBOSE_HOT_PATH) {
            Timber.tag(FAV_DATE_SYNC_TAG).d(message, *args)
        }
    }

    fun showNeedAuthDialog(context: Context, router: TabRouter) {
        MaterialAlertDialogBuilder(context)
                .setMessage("Необходимо войти в аккаунт 4pda")
                .setPositiveButton("Войти") { _, _ ->
                    router.navigateTo(Screen.Auth())
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
    }
}