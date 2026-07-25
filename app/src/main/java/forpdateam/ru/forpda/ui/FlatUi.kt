package forpdateam.ru.forpda.ui

import android.content.Context
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder

/**
 * Единая точка применения настройки «Плоский интерфейс».
 *
 * Сохранённый ключ намеренно остаётся прежним (`theme.flat_posts`): пользователи, уже включившие
 * плоские посты, автоматически получают расширенный режим без миграции и сброса настройки.
 */
object FlatUi {

    fun isEnabled(context: Context): Boolean =
            TopicPreferencesHolder(context.applicationContext).getFlatPosts()

    /**
     * Накладывается после основной палитры и динамических цветов, но до инфлейта экранов.
     * Оверлей снимает декоративные контуры общих list/card-поверхностей; функциональные рамки
     * фокуса, ошибки и выбора задаются отдельными компонентами и остаются видимыми.
     */
    fun applyThemeOverlay(context: Context): Boolean {
        val enabled = isEnabled(context)
        if (enabled) {
            context.theme.applyStyle(R.style.ThemeOverlay_ForPDA_FlatUi, true)
        }
        return enabled
    }
}

/**
 * Чистая часть политики — используется также JVM-тестами без Android Context.
 */
object FlatUiStylePolicy {
    fun decorativeSize(flat: Boolean, normal: Float): Float = if (flat) 0f else normal
    fun decorativeSize(flat: Boolean, normal: Int): Int = if (flat) 0 else normal
}
