package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSmartEndNavigationTest {

    @Test
    fun hybridLastPageLoaded_scrollsToLastPostAnchorNotPageTop() {
        val page = ThemePage().apply {
            posts.add(ThemePost().apply { id = 143734055 })
            posts.add(ThemePost().apply { id = 143734120 })
        }
        val event = ThemeSmartEndNavigation.resolveEndScrollEvent(
                transition = null,
                page = page,
                safeAll = 15,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertTrue(event is ThemeUiEvent.ScrollToEndAnchorOrBottom)
        assertEquals("143734120", (event as ThemeUiEvent.ScrollToEndAnchorOrBottom).anchorPostId)
    }

    @Test
    fun classicLastPageLoaded_scrollsBottomOnly() {
        val page = ThemePage()
        val event = ThemeSmartEndNavigation.resolveEndScrollEvent(
                transition = null,
                page = page,
                safeAll = 15,
                scrollMode = AppPreferences.Main.TopicScrollMode.CLASSIC
        )

        assertEquals(ThemeUiEvent.ScrollToBottom, event)
    }

    @Test
    fun showLoadedPageTransition_usesEndAnchorNotPageSeparator() {
        val page = ThemePage().apply {
            posts.add(ThemePost().apply { id = 100 })
            posts.add(ThemePost().apply { id = 200 })
        }
        val event = ThemeSmartEndNavigation.resolveEndScrollEvent(
                transition = ThemePageTransition.ShowLoadedPage(149),
                page = page,
                safeAll = 149,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertTrue(event is ThemeUiEvent.ScrollToEndAnchorOrBottom)
        assertEquals("200", (event as ThemeUiEvent.ScrollToEndAnchorOrBottom).anchorPostId)
    }

    @Test
    fun pageNotInDom_defersFallbackWhileThemeLoadInFlight() {
        assertTrue(
                ThemeSmartEndNavigation.shouldDeferFallbackWhileLoadInFlight(loadInFlight = true)
        )
        assertFalse(
                ThemeSmartEndNavigation.shouldDeferFallbackWhileLoadInFlight(loadInFlight = false)
        )
    }

    @Test
    fun pageNotInDom_triggersHybridFallbackOnly() {
        assertTrue(
                ThemeSmartEndNavigation.shouldFallbackToLastPageLoad(
                        ThemeSmartEndNavigation.PAGE_NOT_IN_DOM,
                        AppPreferences.Main.TopicScrollMode.HYBRID
                )
        )
        assertFalse(
                ThemeSmartEndNavigation.shouldFallbackToLastPageLoad(
                        ThemeSmartEndNavigation.PAGE_NOT_IN_DOM,
                        AppPreferences.Main.TopicScrollMode.CLASSIC
                )
        )
        assertFalse(
                ThemeSmartEndNavigation.shouldFallbackToLastPageLoad(
                        "scroll_failed",
                        AppPreferences.Main.TopicScrollMode.HYBRID
                )
        )
    }

    @Test
    fun resolveEndScrollAnchorPostId_usesHighestPostIdWhenParsedOrderDiffersFromDom() {
        val page = ThemePage().apply {
            addAnchor("entry143764290")
            posts.add(ThemePost().apply { id = 143764290 })
            posts.add(ThemePost().apply { id = 143660818 })
            posts.add(ThemePost().apply { id = 143764350 })
        }

        assertEquals("143764350", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(page))
    }

    @Test
    fun resolveEndScrollAnchorPostId_prefersLastParsedPostOverServerFirstPageAnchor() {
        val page = ThemePage().apply {
            addAnchor("entry143734055")
            posts.add(ThemePost().apply { id = 143734055 })
            posts.add(ThemePost().apply { id = 143734099 })
            posts.add(ThemePost().apply { id = 143734120 })
        }

        assertEquals("143734120", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(page))
    }

    @Test
    fun resolveEndScrollAnchorPostId_usesLastParsedWhenServerAnchorMatchesLast() {
        val page = ThemePage().apply {
            addAnchor("entry143734120")
            posts.add(ThemePost().apply { id = 143734055 })
            posts.add(ThemePost().apply { id = 143734120 })
        }

        assertEquals("143734120", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(page))
    }

    @Test
    fun resolveEndScrollAnchorPostId_fallsBackToServerAnchorWithoutPosts() {
        val page = ThemePage().apply {
            addAnchor("entry143733158")
        }

        assertEquals("143733158", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(page))
    }

    @Test
    fun loadedEndPage_usesLastParsedPostAndBottomIntent() {
        val page = ThemePage().apply {
            addAnchor("entry143734055")
            posts.add(ThemePost().apply { id = 143734055 })
            posts.add(ThemePost().apply { id = 143734120 })
        }

        ThemeSmartEndNavigation.applyLoadedEndScrollTarget(page)

        assertEquals("143734120", page.anchorPostId)
        assertTrue(page.wasNearBottom)
    }

    @Test
    fun endScrollCommand_usesResolvedLastPostFromPage() {
        val page = ThemePage().apply {
            addAnchor("entry143734055")
            posts.add(ThemePost().apply { id = 143734055 })
            posts.add(ThemePost().apply { id = 143734120 })
        }

        val command = ThemeSmartEndNavigation.endScrollCommand(page)

        assertEquals(ThemeScrollCommand.Kind.END_ANCHOR_OR_BOTTOM, command.kind)
        assertEquals("143734120", command.anchorPostId)
    }

    @Test
    fun pageForEndScrollAnchor_prefersExplicitTargetPageOverFallback() {
        val firstPage = ThemePage().apply {
            id = 42
            pagination.current = 1196
            posts.add(ThemePost().apply { id = 100 })
            posts.add(ThemePost().apply { id = 101 })
        }
        val lastPage = ThemePage().apply {
            id = 42
            pagination.current = 1197
            posts.add(ThemePost().apply { id = 200 })
            posts.add(ThemePost().apply { id = 143734120 })
        }
        val loaded = mapOf(1196 to firstPage, 1197 to lastPage)

        val resolved = ThemeSmartEndNavigation.pageForEndScrollAnchor(loaded, firstPage, 1197)

        assertEquals(lastPage, resolved)
        assertEquals("143734120", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(resolved))
    }

    @Test
    fun pageForEndScrollAnchor_fallsBackToHighestLoadedPageBelowTarget() {
        val firstPage = ThemePage().apply {
            id = 42
            pagination.current = 1
            posts.add(ThemePost().apply { id = 10 })
        }
        val middlePage = ThemePage().apply {
            id = 42
            pagination.current = 148
            posts.add(ThemePost().apply { id = 500 })
            posts.add(ThemePost().apply { id = 501 })
        }
        val loaded = mapOf(1 to firstPage, 148 to middlePage)

        val resolved = ThemeSmartEndNavigation.pageForEndScrollAnchor(loaded, firstPage, 149)

        assertEquals(middlePage, resolved)
        assertEquals("501", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(resolved))
    }

    @Test
    fun endScrollCommand_fallsBackToBottomWithoutAnchor() {
        val command = ThemeSmartEndNavigation.endScrollCommand("")

        assertEquals(ThemeScrollCommand.Kind.BOTTOM, command.kind)
    }

    @Test
    fun resolveEndScrollAnchorPostId_getlastpostFirstEntry_usesHighestParsedPost() {
        val page = ThemePage().apply {
            addAnchor("entry143764889")
            posts.add(ThemePost().apply { id = 143764889 })
            posts.add(ThemePost().apply { id = 143764920 })
            posts.add(ThemePost().apply { id = 143765001 })
        }

        assertEquals("143765001", ThemeSmartEndNavigation.resolveEndScrollAnchorPostId(page))
    }

    @Test
    fun parseScrollYFromCommandReason_readsJsMetricsSuffix() {
        val reason = "bottom|y=5670|max=5671|lastPost=143768856"
        val match = Regex("""\|y=(-?\d+)""").find(reason)
        assertEquals(5670, match?.groupValues?.get(1)?.toIntOrNull())
    }

    @Test
    fun loadStTransition_emitsEndAnchorNotBottom() {
        val page = ThemePage().apply {
            posts.add(ThemePost().apply { id = 143764889 })
            posts.add(ThemePost().apply { id = 143765001 })
        }
        val event = ThemeSmartEndNavigation.resolveEndScrollEvent(
                transition = ThemePageTransition.LoadSt(st = 24080),
                page = page,
                safeAll = 1205,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID
        )

        assertTrue(event is ThemeUiEvent.ScrollToEndAnchorOrBottom)
        assertEquals("143765001", (event as ThemeUiEvent.ScrollToEndAnchorOrBottom).anchorPostId)
    }
}
