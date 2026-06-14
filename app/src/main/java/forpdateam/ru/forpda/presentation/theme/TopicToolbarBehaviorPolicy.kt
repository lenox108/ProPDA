package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences

fun isToolbarAutoHideEnabled(toolbarBehavior: AppPreferences.Main.TopicToolbarBehavior): Boolean =
        toolbarBehavior == AppPreferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL
