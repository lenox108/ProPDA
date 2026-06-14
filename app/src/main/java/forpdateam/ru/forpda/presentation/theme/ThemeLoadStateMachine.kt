package forpdateam.ru.forpda.presentation.theme

/**
 * Состояние загрузки темы.
 * Определяет, как обрабатывать HTML, историю и позицию прокрутки при загрузке страницы.
 */
sealed class ThemeLoadAction {
    /**
     * Обычная загрузка темы (первичная или навигация на новую страницу).
     * Сохраняет страницу в историю.
     */
    data object Normal : ThemeLoadAction()

    /**
     * Обновление текущей страницы (pull-to-refresh).
     * Обновляет последнюю страницу в истории с сохранением позиции прокрутки.
     */
    data object Refresh : ThemeLoadAction()

    /**
     * Возврат к предыдущей странице в истории.
     * Обновляет последнюю страницу в истории с сохранением позиции прокрутки.
     */
    data object Back : ThemeLoadAction()

    /**
     * Загрузка последней страницы темы (переход в конец).
     * Сохраняет страницу в историю.
     */
    data object End : ThemeLoadAction()

    /**
     * Возвращает строковое представление для JS-интеграции.
     * JS использует эти значения для определения типа загрузки.
     */
    override fun toString(): String {
        return when (this) {
            Normal -> "NORMAL"
            Refresh -> "REFRESH"
            Back -> "BACK"
            End -> "END"
        }
    }

    companion object {
        /**
         * Создаёт состояние из строкового представления (для JS-интеграции).
         */
        fun fromString(value: String): ThemeLoadAction {
            return when (value) {
                "BACK" -> Back
                "REFRESH" -> Refresh
                "END" -> End
                else -> Normal
            }
        }
    }
}
