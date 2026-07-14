package forpdateam.ru.forpda.ui.fragments.notes.adapters

import android.content.Context
import forpdateam.ru.forpda.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Дата создания закладки в списке. Чем свежее заметка, тем подробнее подпись:
 * «Сегодня, 12:25» → «Вчера, 12:25» → «14 июля» (этот год) → «14.07.24».
 *
 * Форматтеры кэшируются (bind вызывается на каждый прокрут), но пересобираются при смене
 * локали — SimpleDateFormat запоминает её в момент создания, иначе месяцы остались бы на
 * языке, который стоял при первом рендере.
 */
object NoteDateFormatter {

    private var locale: Locale? = null
    private lateinit var timeFormat: SimpleDateFormat
    private lateinit var monthDayFormat: SimpleDateFormat
    private lateinit var fullFormat: SimpleDateFormat

    /** @return null, если времени создания нет (заметки, заведённые до появления createdAt). */
    @Synchronized
    fun format(context: Context, createdAt: Long, now: Long = System.currentTimeMillis()): String? {
        if (createdAt <= 0L) return null
        ensureFormats()

        val date = Date(createdAt)
        val then = Calendar.getInstance().apply { timeInMillis = createdAt }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val sameYear = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
        val dayDiff = if (sameYear) {
            today.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        } else {
            Int.MAX_VALUE
        }

        return when {
            dayDiff == 0 -> context.getString(R.string.note_date_today, timeFormat.format(date))
            dayDiff == 1 -> context.getString(R.string.note_date_yesterday, timeFormat.format(date))
            sameYear -> monthDayFormat.format(date)
            else -> fullFormat.format(date)
        }
    }

    private fun ensureFormats() {
        val current = Locale.getDefault()
        if (locale == current) return
        locale = current
        timeFormat = SimpleDateFormat("HH:mm", current)
        monthDayFormat = SimpleDateFormat("d MMMM", current)
        fullFormat = SimpleDateFormat("dd.MM.yy", current)
    }
}
