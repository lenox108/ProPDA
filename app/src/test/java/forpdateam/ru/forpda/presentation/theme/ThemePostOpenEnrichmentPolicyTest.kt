package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePostOpenEnrichmentPolicyTest {

    @Test
    fun shouldStartEnrichment_onlyWhenPrimaryOpenComplete() {
        assertFalse(ThemePostOpenEnrichmentPolicy.shouldStartEnrichment(null, "abc"))
        assertFalse(ThemePostOpenEnrichmentPolicy.shouldStartEnrichment("abc", "xyz"))
        assertTrue(ThemePostOpenEnrichmentPolicy.shouldStartEnrichment("abc", "abc"))
    }

    @Test
    fun shouldDeferRevealUntilPrimaryOpenComplete() {
        assertTrue(ThemePostOpenEnrichmentPolicy.shouldDeferRevealUntilPrimaryOpenComplete(false))
        assertFalse(ThemePostOpenEnrichmentPolicy.shouldDeferRevealUntilPrimaryOpenComplete(true))
    }

    @Test
    fun enrichmentTasks_favoritesUnreadOpen_includesAllExceptHybridWhenNotPage1() {
        val page = pageOn(current = 5, all = 10).apply {
            posts.add(ThemePost().apply { id = 100 })
        }
        val tasks = ThemePostOpenEnrichmentPolicy.enrichmentTasks(
                sessionKind = TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                loadAction = ThemeLoadAction.Normal,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                page = page,
        )
        assertTrue(tasks.favoriteSync)
        assertTrue(tasks.metadata)
        assertTrue(tasks.hatMetadata)
        assertFalse(tasks.hybridPrefetch)
        assertTrue(tasks.editorPrefetch)
    }

    @Test
    fun enrichmentTasks_ambiguousAllRead_suppressesHybridBootstrap() {
        val page = pageOn(current = 1, all = 10).apply {
            posts.add(ThemePost().apply { id = 100 })
        }
        val tasks = ThemePostOpenEnrichmentPolicy.enrichmentTasks(
                sessionKind = TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                loadAction = ThemeLoadAction.Normal,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                page = page,
        )
        assertFalse(tasks.hybridPrefetch)
        assertTrue(tasks.metadata)
        assertTrue(tasks.favoriteSync)
    }

    @Test
    fun enrichmentTasks_getnewpostAllReadBottomRedirect_noHybridOnRefresh() {
        val page = pageOn(current = 1, all = 3).apply {
            ambiguousLastUnreadBottomRedirect = true
            hasUnreadTarget = false
            openSessionKind = "AMBIGUOUS_ALL_READ"
            posts.add(ThemePost().apply { id = 100 })
        }
        val tasks = ThemePostOpenEnrichmentPolicy.enrichmentTasks(
                sessionKind = TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                loadAction = ThemeLoadAction.Refresh,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                page = page,
        )
        assertFalse(tasks.hybridPrefetch)
        assertTrue(tasks.metadata)
    }

    @Test
    fun enrichmentTasks_firstUnreadPage1_hybridPrefetchAllowed() {
        val page = pageOn(current = 1, all = 10)
        val tasks = ThemePostOpenEnrichmentPolicy.enrichmentTasks(
                sessionKind = TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                loadAction = ThemeLoadAction.Normal,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                page = page,
        )
        assertTrue(tasks.hybridPrefetch)
    }

    @Test
    fun controller_blocksEnrichmentUntilPrimaryOpenComplete() {
        var favoriteSync = 0
        val controller = ThemePostOpenEnrichmentController(
                object : ThemePostOpenEnrichmentController.Callbacks {
                    override fun scheduleFavoriteSync(page: ThemePage, traceId: String) {
                        favoriteSync++
                    }

                    override fun scheduleMetadataEnrichment(page: ThemePage, traceId: String) = Unit
                    override fun scheduleHatMetadata(page: ThemePage) = Unit
                    override fun scheduleHybridPrefetch(page: ThemePage, traceId: String) = Unit
                    override fun prefetchEditor(page: ThemePage) = Unit
                }
        )
        val page = pageOn(current = 1, all = 1).apply {
            posts.add(ThemePost().apply { id = 1 })
        }
        controller.startPostOpenEnrichment(
                page,
                "t1",
                TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                ThemeLoadAction.Normal,
                AppPreferences.Main.TopicScrollMode.HYBRID,
        )
        assertFalse(controller.isPrimaryOpenComplete("t1"))
        assertFalse(controller.isPostOpenEnrichStarted("t1"))
        assertEquals(0, favoriteSync)

        controller.markPrimaryOpenComplete("t1")
        controller.startPostOpenEnrichment(
                page,
                "t1",
                TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                ThemeLoadAction.Normal,
                AppPreferences.Main.TopicScrollMode.HYBRID,
        )
        assertTrue(controller.isPrimaryOpenComplete("t1"))
        assertTrue(controller.isPostOpenEnrichStarted("t1"))
        assertEquals(1, favoriteSync)
    }

    @Test
    fun enrichmentTasks_backLoad_skipsHybridPrefetch() {
        val page = pageOn(current = 1, all = 5).apply {
            posts.add(ThemePost().apply { id = 1 })
        }
        val tasks = ThemePostOpenEnrichmentPolicy.enrichmentTasks(
                sessionKind = TopicUnreadOpenPolicy.TopicOpenSessionKind.READ_RESUME,
                loadAction = ThemeLoadAction.Back,
                scrollMode = AppPreferences.Main.TopicScrollMode.HYBRID,
                page = page,
        )
        assertFalse(tasks.hybridPrefetch)
        assertTrue(tasks.metadata)
        assertTrue(tasks.favoriteSync)
    }

    @Test
    fun controller_startsEnrichOncePerTrace() {
        var favoriteSync = 0
        var metadata = 0
        var editor = 0
        val controller = ThemePostOpenEnrichmentController(
                object : ThemePostOpenEnrichmentController.Callbacks {
                    override fun scheduleFavoriteSync(page: ThemePage, traceId: String) {
                        favoriteSync++
                    }

                    override fun scheduleMetadataEnrichment(page: ThemePage, traceId: String) {
                        metadata++
                    }

                    override fun scheduleHatMetadata(page: ThemePage) = Unit
                    override fun scheduleHybridPrefetch(page: ThemePage, traceId: String) = Unit
                    override fun prefetchEditor(page: ThemePage) {
                        editor++
                    }
                }
        )
        val page = pageOn(current = 1, all = 1).apply {
            posts.add(ThemePost().apply { id = 1 })
        }
        controller.markPrimaryOpenComplete("t1")
        controller.startPostOpenEnrichment(
                page,
                "t1",
                TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                ThemeLoadAction.Normal,
                AppPreferences.Main.TopicScrollMode.HYBRID,
        )
        controller.startPostOpenEnrichment(
                page,
                "t1",
                TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                ThemeLoadAction.Normal,
                AppPreferences.Main.TopicScrollMode.HYBRID,
        )
        assertTrue(controller.isPrimaryOpenComplete("t1"))
        assertTrue(controller.isPostOpenEnrichStarted("t1"))
        assertEquals(1, favoriteSync)
        assertEquals(1, metadata)
        assertEquals(1, editor)
    }

    @Test
    fun controller_resetClearsLifecycleMarkers() {
        val controller = ThemePostOpenEnrichmentController(
                object : ThemePostOpenEnrichmentController.Callbacks {
                    override fun scheduleFavoriteSync(page: ThemePage, traceId: String) = Unit
                    override fun scheduleMetadataEnrichment(page: ThemePage, traceId: String) = Unit
                    override fun scheduleHatMetadata(page: ThemePage) = Unit
                    override fun scheduleHybridPrefetch(page: ThemePage, traceId: String) = Unit
                    override fun prefetchEditor(page: ThemePage) = Unit
                }
        )
        controller.markPrimaryOpenComplete("t1")
        controller.startPostOpenEnrichment(
                pageOn(1, 1),
                "t1",
                null,
                ThemeLoadAction.Normal,
                AppPreferences.Main.TopicScrollMode.HYBRID,
        )
        controller.reset()
        assertFalse(controller.isPrimaryOpenComplete("t1"))
        assertFalse(controller.isPostOpenEnrichStarted("t1"))
    }

    private fun pageOn(current: Int, all: Int): ThemePage =
            ThemePage().apply {
                id = 1106099
                pagination = Pagination().apply {
                    this.current = current
                    this.all = all
                    perPage = 15
                }
            }
}
