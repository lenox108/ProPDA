package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.presentation.Screen

class TabItem {
    var tag: String = ""
    var screen: TabScreen? = null
    var parent: TabItem? = null
    val children = mutableListOf<TabItem>()

    /**
     * Исходный [Screen], которым вкладка была открыта — им же её можно открыть заново
     * («Отменить» после закрытия). В отличие от [screen] хранит все параметры (url, id),
     * но НЕ переживает пересоздание процесса: восстановление и «Копировать ссылку»
     * доступны только в рамках текущей сессии (нет origin — нет пункта меню).
     */
    var origin: Screen? = null
}