package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage

/**
 * Sealed class для представления состояния UI в ThemeViewModel
 * Предоставляет единую точку для всех состояний темы вместо множества StateFlow
 * 
 * Примечание: Это базовая структура для будущего рефакторинга ThemeViewModel.
 * Текущая реализация использует отдельные StateFlow для обратной совместимости.
 */
sealed class ThemeState {
    /**
     * Данные темы загружены
     */
    data class DataLoaded(val page: ThemePage) : ThemeState()

    /**
     * Идет загрузка
     */
    data class Loading(val isRefreshing: Boolean) : ThemeState()

    /**
     * Ошибка загрузки
     */
    data class Error(val message: String, val cause: Throwable? = null) : ThemeState()

    /**
     * Сообщение отправлено
     */
    object MessageSent : ThemeState()

    /**
     * Добавлено в избранное
     */
    data class AddedToFavorite(val success: Boolean) : ThemeState()

    /**
     * Удалено из избранного
     */
    data class DeletedFromFavorite(val success: Boolean) : ThemeState()

    /**
     * Состояние message panel
     */
    data class MessagePanelRefreshing(val isRefreshing: Boolean) : ThemeState()

    /**
     * Начальное состояние
     */
    object Idle : ThemeState()
}
