package forpdateam.ru.forpda

import forpdateam.ru.forpda.ui.fragments.theme.nativerender.PostDateFormatter
import org.junit.Assert.assertEquals
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/** «Современная шапка поста»: серверная строка даты → короткая относительная форма. */
class PostDateFormatterTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    /** «Сейчас» для всех кейсов: 20.05.2026, 14:55 по московскому времени. */
    private val now: Long = LocalDateTime.of(2026, 5, 20, 14, 55)
            .atZone(zone).toInstant().toEpochMilli()

    private fun relative(raw: String) = PostDateFormatter.relative(raw, now, zone)

    @Test
    fun `минуты и часы для сегодняшних постов`() {
        assertEquals("только что", relative("Сегодня, 14:55"))
        assertEquals("7 мин.", relative("Сегодня, 14:48"))
        assertEquals("5 ч.", relative("Сегодня, 09:30"))
    }

    @Test
    fun `вчерашний пост считается от вчерашней даты`() {
        assertEquals("17 ч.", relative("Вчера, 21:55"))
    }

    @Test
    fun `дни до недели, дальше — календарная дата`() {
        assertEquals("3 д.", relative("17.05.26, 14:55"))
        assertEquals("6 д.", relative("14.05.26, 14:00"))
        assertEquals("12.05.26", relative("12.05.26, 14:55"))
        assertEquals("31.12.25", relative("31.12.25, 23:10"))
    }

    @Test
    fun `четырёхзначный год разбирается так же`() {
        assertEquals("12.05.26", relative("12.05.2026, 14:55"))
    }

    @Test
    fun `пост из будущего не показывается как «через N ч» (расхождение часов)`() {
        assertEquals("только что", relative("Сегодня, 18:30"))
    }

    @Test
    fun `неразобранная строка возвращается как есть`() {
        assertEquals("позавчера в обед", relative("позавчера в обед"))
        assertEquals("", relative("   "))
    }
}
