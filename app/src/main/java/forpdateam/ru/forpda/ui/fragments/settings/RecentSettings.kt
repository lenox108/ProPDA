package forpdateam.ru.forpda.ui.fragments.settings

import android.content.Context

/**
 * «Недавно изменённые» на корневом экране настроек.
 *
 * Список автоматический: набор не настраивается, ничего не надо выбирать вручную. Ключи
 * пишутся при фактическом изменении — из общего слушателя SharedPreferences (переключатели,
 * списки) и явными вызовами [record] там, где значение уходит мимо SharedPreferences
 * (диалоговые пикеры темы, палитры, шрифта — они пишут в DataStore).
 *
 * Хранится в отдельном файле, а не в основных настройках: это состояние UI, ему нечего делать
 * ни в резервной копии, ни среди слушателей настроек.
 */
object RecentSettings {

    private const val FILE = "settings_recent"
    private const val KEY_ORDER = "order"
    private const val SEPARATOR = "\n"

    /** Сколько пунктов показываем в блоке. Больше — и блок начинает конкурировать с разделами. */
    const val MAX_SHOWN = 4

    /** Помним чуть больше показанного: пункт мог исчезнуть из индекса (например, после переименования ключа). */
    private const val MAX_STORED = 12

    fun record(context: Context, key: String) {
        if (key.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_ORDER, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        val updated = (listOf(key) + current.filter { it != key }).take(MAX_STORED)
        prefs.edit().putString(KEY_ORDER, updated.joinToString(SEPARATOR)).apply()
    }

    /** Ключи от самого свежего к старому; пункты, которых больше нет в индексе, отсеиваются. */
    fun keys(context: Context): List<String> {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ORDER, null)
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                .orEmpty()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().remove(KEY_ORDER).apply()
    }
}
