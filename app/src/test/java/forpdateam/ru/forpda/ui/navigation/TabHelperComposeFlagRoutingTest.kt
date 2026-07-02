package forpdateam.ru.forpda.ui.navigation

import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesComposeFragment
import forpdateam.ru.forpda.ui.fragments.favorites.FavoritesFragment
import forpdateam.ru.forpda.ui.fragments.news.main.NewsMainComposeFragment
import forpdateam.ru.forpda.ui.fragments.news.main.NewsMainFragment
import forpdateam.ru.forpda.ui.fragments.qms.QmsContactsFragment
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for the §3.2 Compose flag routing in [TabHelper.createTab].
 * The flags must default to false (no behaviour change for users) and
 * only flip routing when explicitly enabled.
 */
class TabHelperComposeFlagRoutingTest {

    @After
    fun resetFlags() {
        TabHelper.useComposeArticleList = false
        TabHelper.useComposeFavorites = false
    }

    @Test
    fun qmsContacts_routesToComposeHost() {
        // QmsContactsFragment теперь сам Compose-хост (флаг/стаб удалены).
        val fragment = TabHelper.createTab(Screen.QmsContacts())
        assertTrue(
            "QmsContacts should route to QmsContactsFragment (Compose host), got ${fragment::class.java.simpleName}",
            fragment is QmsContactsFragment
        )
    }

    @Test
    fun articleList_defaultsToLegacy() {
        TabHelper.useComposeArticleList = false
        val fragment = TabHelper.createTab(Screen.ArticleList())
        assertTrue(
            "default flag should route to legacy NewsMainFragment, got ${fragment::class.java.simpleName}",
            fragment is NewsMainFragment
        )
    }

    @Test
    fun articleList_flagTrueRoutesToCompose() {
        TabHelper.useComposeArticleList = true
        val fragment = TabHelper.createTab(Screen.ArticleList())
        assertSame(NewsMainComposeFragment::class.java, fragment::class.java)
    }

    @Test
    fun favorites_defaultsToLegacy() {
        TabHelper.useComposeFavorites = false
        val fragment = TabHelper.createTab(Screen.Favorites())
        assertTrue(
            "default flag should route to legacy FavoritesFragment, got ${fragment::class.java.simpleName}",
            fragment is FavoritesFragment
        )
    }

    @Test
    fun favorites_flagTrueRoutesToCompose() {
        TabHelper.useComposeFavorites = true
        val fragment = TabHelper.createTab(Screen.Favorites())
        assertSame(FavoritesComposeFragment::class.java, fragment::class.java)
    }
}
