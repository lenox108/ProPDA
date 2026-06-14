package forpdateam.ru.forpda.presentation.theme

/**
 * [Screen.Theme] tabs are [forpdateam.ru.forpda.ui.fragments.TabConfiguration.isAlone], so base
 * [forpdateam.ru.forpda.ui.fragments.TabFragment] skips the navigation icon. Topic screens still
 * need the back arrow whenever tab-level back would leave the topic (parent tab or another tab open).
 */
fun shouldShowTopicToolbarBack(
        tabCount: Int,
        isMenuTab: Boolean,
        hasParent: Boolean = false,
        canCloseThemeChain: Boolean = false
): Boolean =
        !isMenuTab && (hasParent || canCloseThemeChain || tabCount > 1)
