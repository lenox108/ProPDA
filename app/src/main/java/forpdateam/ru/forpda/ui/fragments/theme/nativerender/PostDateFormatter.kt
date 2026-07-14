package forpdateam.ru.forpda.ui.fragments.theme.nativerender

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * «Современная» дата поста: короткая относительная форма («17 ч.») вместо серверной строки
 * («20.05.26, 14:55»), чтобы дата помещалась в одну строку рядом с ником.
 *
 * У поста нет собственного timestamp — 4PDA отдаёт только строку, поэтому её приходится разбирать.
 * Часовой пояс форума пользователь задаёт в «Настройках форума» (act=usercp, tz-autoset/time-offset),
 * то есть сервер уже рендерит время в его шкале и сравнивать с часами устройства можно напрямую,
 * без конвертации поясов.
 */
object PostDateFormatter {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS
    /** Дальше этого возраста «N д.» перестаёт что-либо говорить — показываем календарную дату. */
    private const val ABSOLUTE_AFTER_MS = 7 * DAY_MS

    private val TODAY_PREFIX = "сегодня"
    private val YESTERDAY_PREFIX = "вчера"

    /** `20.05.26, 14:55` / `20.05.2026, 14:55` (год бывает и двух-, и четырёхзначным). */
    private val ABSOLUTE = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2}|\d{4})\D+(\d{1,2}):(\d{2})""")
    /** Время внутри «Сегодня, 14:55» / «Вчера, 8:12». */
    private val TIME_ONLY = Regex("""(\d{1,2}):(\d{2})""")

    /**
     * Относительная дата для [raw]; если строку разобрать не удалось (сервер поменял формат, пришёл
     * мусор), возвращаем её как есть — в худшем случае получаем сегодняшнее поведение, а не пустую
     * или сломанную шапку.
     */
    fun relative(
            raw: String?,
            nowMillis: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        val postMillis = parseEpochMillis(text, nowMillis, zone) ?: return text
        // Часы устройства и пояс форума могут разойтись (ручной offset в профиле, уехал в другой
        // пояс, сбились часы) — тогда свежий пост получает отрицательный возраст и без клампа мы
        // показали бы «через 2 ч.». Всё, что «из будущего», считаем только что написанным.
        val age = (nowMillis - postMillis).coerceAtLeast(0L)
        return when {
            age < MINUTE_MS -> "только что"
            age < HOUR_MS -> "${age / MINUTE_MS} мин."
            age < DAY_MS -> "${age / HOUR_MS} ч."
            age < ABSOLUTE_AFTER_MS -> "${age / DAY_MS} д."
            else -> absoluteDate(postMillis, zone)
        }
    }

    /** Серверная строка → epoch millis в шкале [zone], либо null если формат неизвестен. */
    fun parseEpochMillis(raw: String, nowMillis: Long, zone: ZoneId): Long? {
        val text = raw.trim().lowercase(Locale.ROOT)
        val today = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
        val dateTime = when {
            text.startsWith(TODAY_PREFIX) -> timeOf(text)?.let { LocalDateTime.of(today, it) }
            text.startsWith(YESTERDAY_PREFIX) ->
                timeOf(text)?.let { LocalDateTime.of(today.minusDays(1), it) }
            else -> absoluteOf(text)
        } ?: return null
        return dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    private fun timeOf(text: String): LocalTime? {
        val m = TIME_ONLY.find(text) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun absoluteOf(text: String): LocalDateTime? {
        val m = ABSOLUTE.find(text) ?: return null
        val (d, mo, y, h, min) = m.destructured
        val day = d.toIntOrNull() ?: return null
        val month = mo.toIntOrNull() ?: return null
        val rawYear = y.toIntOrNull() ?: return null
        val hour = h.toIntOrNull() ?: return null
        val minute = min.toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
        val year = if (y.length == 2) 2000 + rawYear else rawYear
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
    }

    private fun absoluteDate(millis: Long, zone: ZoneId): String {
        val date = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).toLocalDate()
        return String.format(Locale.ROOT, "%02d.%02d.%02d", date.dayOfMonth, date.monthValue, date.year % 100)
    }
}
