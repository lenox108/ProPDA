package forpdateam.ru.forpda.ui.views.drawers.adapters

import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.presentation.Screen

class DrawerMenuItem(
        val title: Int,
        val icon: Int,
        val appItem: AppMenuItem,
        /** Заголовок пользовательской плитки: у ярлыков он не ресурс, а живой текст. */
        val titleText: String? = null
)