package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone

class ForumDateTimeTest {

    @Test
    fun `legacy instant forum display keeps parsed forum profile time`() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
            val parsed = Utils.parseForumDateTime("15.05.2026, 18:30")

            assertEquals("15.05.2026, 18:30", Utils.getForumDisplayDateTime(parsed))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `explicit instant display zone does not shift forum profile time`() {
        val parsed = Utils.parseForumDateTime("15.05.2026, 18:30")

        assertEquals("15.05.2026, 18:30", Utils.getForumDisplayDateTime(parsed, ZoneId.of("Asia/Dubai")))
    }

    @Test
    fun `same device time zone keeps parsed forum time unchanged`() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
            val parsed = Utils.parseForumDateTime("15.05.2026, 18:30")

            assertEquals("15.05.2026, 18:30", Utils.getForumDisplayDateTime(parsed))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `forum display computes today label in stable forum parse zone`() {
        val displayZone = ZoneId.of("Asia/Dubai")
        val deviceDateTime = LocalDate.now(ZoneId.of("Europe/Moscow"))
                .atTime(18, 30)
                .atZone(ZoneId.of("Europe/Moscow"))
                .toInstant()
        val parsed = Date.from(deviceDateTime)

        assertEquals("Сегодня, 18:30", Utils.getForumDisplayDateTime(parsed, displayZone))
    }

    @Test
    fun `forum parser accepts dates without year`() {
        val parsed = Utils.parseForumDateTime("15.05, 18:30")
        val parsedWithoutComma = Utils.parseForumDateTime("15.05 18:30")

        assertNotNull(parsed)
        assertNotNull(parsedWithoutComma)
    }

    @Test
    fun `forum display keeps profile dates without device conversion`() {
        val clock = fixedClock()

        assertEquals(
                "16.05.2026, 23:02",
                Utils.formatForumDisplayDateTime("16.05.2026, 23:02", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
        assertEquals(
                "17.05.2026, 00:11",
                Utils.formatForumDisplayDateTime("17.05.2026, 00:11", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
        assertEquals(
                "15.05.2026, 18:30",
                Utils.formatForumDisplayDateTime("15.05.2026, 18:30", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
        assertEquals(
                "15.05.2026, 18:30",
                Utils.formatForumDisplayDateTime("15.05.2026, 18:30", ZoneId.of("Europe/Kaliningrad"), "test", clock)
        )
        assertEquals(
                "15.05.2026, 18:30",
                Utils.formatForumDisplayDateTime("15.05.2026, 18:30", ZoneId.of("Europe/Moscow"), "test", clock)
        )
        assertEquals(
                "15.05.2026, 18:30",
                Utils.formatForumDisplayDateTime("15.05.2026, 18:30", ZoneId.of("UTC"), "test", clock)
        )
    }

    @Test
    fun `forum display preserves relative profile labels without device conversion`() {
        val clock = fixedClock("2026-05-16T00:30:00Z")

        assertEquals(
                "Сегодня, 07:32",
                Utils.formatForumDisplayDateTime("Сегодня, 07:32", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
        assertEquals(
                "Сегодня, 00:30",
                Utils.formatForumDisplayDateTime("Сегодня, 00:30", ZoneId.of("UTC"), "test", clock)
        )
        assertEquals(
                "Вчера, 23:30",
                Utils.formatForumDisplayDateTime("Вчера, 23:30", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
    }

    @Test
    fun `forum display keeps relative labels near device midnight without double shift`() {
        val clock = fixedClock("2026-05-16T21:30:00Z")

        assertEquals(
                "Сегодня, 00:11",
                Utils.formatForumDisplayDateTime("Сегодня, 00:11", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
        assertEquals(
                "Вчера, 21:55",
                Utils.formatForumDisplayDateTime("Вчера, 21:55", ZoneId.of("Asia/Yekaterinburg"), "test", clock)
        )
    }

    @Test
    fun `quote date keeps post header display string instead of converting it again`() {
        assertEquals(
                "Сегодня, 13:02",
                Utils.resolveForumQuoteDate(
                        rawDateTime = "Сегодня, 13:02",
                        displayedDateTime = "Сегодня, 13:02",
                        context = "theme.quote"
                )
        )
    }

    @Test
    fun `forum instant display formats parsed profile time without device shift`() {
        val forumInstant = Utils.parseForumDateTime("17.05.2026, 00:11")

        assertEquals(
                "17.05.2026, 00:11",
                Utils.getForumDisplayDateTime(forumInstant, ZoneId.of("Asia/Yekaterinburg"))
        )
    }

    @Test
    fun `forum wall display keeps ambiguous raw text`() {
        assertEquals(
                "15 мая 2026, 18:30",
                Utils.formatForumDisplayDateTime("15 мая 2026, 18:30", ZoneId.of("Asia/Dubai"), "test")
        )
    }

    @Test
    fun `forum display normalizes two digit year without device shift`() {
        assertEquals(
                "14.05.2026, 18:33",
                Utils.formatForumDisplayDateTime("14.05.26, 18:33", ZoneId.of("Asia/Yekaterinburg"), "test", fixedClock())
        )
    }

    @Test
    fun `favorites display keeps today profile label without future clamp`() {
        val clock = fixedClock("2026-05-17T08:55:00Z")

        assertEquals(
                "Сегодня, 00:39",
                Utils.formatFavoritesDisplayDateTime("Сегодня, 00:39", ZoneId.of("Asia/Dubai"), clock)
        )
    }

    @Test
    fun `favorites display keeps absolute profile time without device conversion`() {
        val clock = fixedClock("2026-05-17T08:55:00Z")

        assertEquals(
                "15.05.2026, 23:39",
                Utils.formatFavoritesDisplayDateTime("15.05.2026, 23:39", ZoneId.of("Asia/Dubai"), clock)
        )
    }

    @Test
    fun `favorites display converts today absolute date to relative label`() {
        val clock = fixedClock("2026-05-17T08:55:00Z")

        assertEquals(
                "Сегодня, 12:34",
                Utils.formatFavoritesDisplayDateTime("17.05.2026, 12:34", ZoneId.of("Asia/Dubai"), clock)
        )
    }

    @Test
    fun `favorites display converts yesterday absolute date to relative label`() {
        val clock = fixedClock("2026-05-17T08:55:00Z")

        assertEquals(
                "Вчера, 23:39",
                Utils.formatFavoritesDisplayDateTime("16.05.2026, 23:39", ZoneId.of("Asia/Dubai"), clock)
        )
    }

    private fun fixedClock(instant: String = "2026-05-16T12:00:00Z"): Clock {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"))
    }
}
