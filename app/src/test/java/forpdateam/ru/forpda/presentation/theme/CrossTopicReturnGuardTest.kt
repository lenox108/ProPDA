package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defect C — after a cross-topic hop (Topic A st=1180/post 143876380 → Topic B in a NEW tab),
 * pressing back from Topic B used to land on st=1260 (the FIRST post of an earlier page-64 of the
 * SAME topic) instead of the st=1180 page the user was actually reading. Root cause: the
 * cross-topic open pushes no in-tab entry, so the source page stays the in-tab history TOP; the
 * returning back press then popped that TOP and surfaced the page BELOW it.
 *
 * The cross-topic return guard (armed in captureBackSnapshotBeforeCrossTopicOpen, consumed by
 * backPage) makes the FIRST back after a cross-topic open RESTORE the top entry in place. This is a
 * decision-mirroring test consistent with ThemeBackNavigationTest / CrossTopicBackRestoreTest:
 * ThemeViewModel cannot be instantiated on the JVM, so it drives ThemeHistoryController directly.
 */
class CrossTopicReturnGuardTest {

    private fun page(topicId: Int, currentPage: Int, anchor: String?): ThemePage {
        val st = (currentPage - 1) * 20 // ThemePage.st is computed from pagination.current * perPage(20)
        return ThemePage().apply {
            id = topicId
            pagination.current = currentPage
            pagination.all = currentPage
            url = "https://4pda.to/forum/index.php?showtopic=$topicId&st=$st"
            anchorPostId = anchor
            html = "<html><body>cached</body></html>"
        }
    }

    /** Topic A history as the user lived it: an earlier page-64 then the page-60 they read from. */
    private fun controllerOnTopicAPage60(): ThemeHistoryController {
        val controller = ThemeHistoryController()
        controller.saveToHistory(page(1121483, 64, "143999999")) // st=1260, earlier page
        controller.saveToHistory(page(1121483, 60, "143876380")) // st=1180, the page being read
        return controller
    }

    @Test
    fun back_afterCrossTopicOpen_restoresSourcePageInPlace_notEarlierPage() {
        val controller = controllerOnTopicAPage60()
        val source = controller.currentPage!!
        assertEquals(1180, source.st)

        // Mirrors captureBackSnapshotBeforeCrossTopicOpen: arm the guard for the page the user is
        // on right before Topic B opens in a NEW tab (no in-tab entry is pushed for the hop).
        controller.armCrossTopicReturnGuard(source.id, source.st)
        assertTrue(controller.isCrossTopicReturnGuardArmedFor(1121483, 1180))

        // First back press after returning to Topic A's tab.
        val restored = controller.backPage()
        assertNotNull(restored)
        // The user lands back on st=1180 / post 143876380 — NOT st=1260 / the earlier page.
        assertEquals(1180, restored!!.st)
        assertEquals("143876380", restored.anchorPostId)
        assertSame(source, restored)
        // History was not shrunk: the page is restored in place, not popped.
        assertEquals(2, controller.size)
        // Guard is single-use.
        assertFalse(controller.isCrossTopicReturnGuardArmedFor(1121483, 1180))
    }

    @Test
    fun secondBack_afterGuardConsumed_popsToEarlierPageNormally() {
        val controller = controllerOnTopicAPage60()
        controller.armCrossTopicReturnGuard(1121483, 1180)

        controller.backPage() // consumes guard, restores st=1180 in place
        val second = controller.backPage() // ordinary in-tab pop
        assertNotNull(second)
        assertEquals(1260, second!!.st)
        assertEquals(1, controller.size)
    }

    @Test
    fun guard_doesNotArm_whenTopEntryDoesNotMatchSource() {
        val controller = controllerOnTopicAPage60()
        // Defensive: arming for a page that is not the in-tab top must be a no-op.
        controller.armCrossTopicReturnGuard(1121483, 1260)
        assertFalse(controller.isCrossTopicReturnGuardArmedFor(1121483, 1260))

        val back = controller.backPage()
        assertEquals(1260, back!!.st)
        assertEquals(1, controller.size)
    }

    @Test
    fun inTabForwardNavigation_clearsGuard_soNextBackPopsNormally() {
        val controller = controllerOnTopicAPage60()
        controller.armCrossTopicReturnGuard(1121483, 1180)
        assertTrue(controller.isCrossTopicReturnGuardArmedFor(1121483, 1180))

        // The user navigated forward in-tab instead of returning from a cross-topic tab.
        controller.saveToHistory(page(1121483, 61, "144000111"))
        assertFalse(controller.isCrossTopicReturnGuardArmedFor(1121483, 1180))

        val back = controller.backPage()
        assertEquals(1180, back!!.st)
    }

    @Test
    fun clear_dropsGuard() {
        val controller = controllerOnTopicAPage60()
        controller.armCrossTopicReturnGuard(1121483, 1180)
        controller.clear()
        assertFalse(controller.isCrossTopicReturnGuardArmedFor(1121483, 1180))
        assertNull(controller.currentPage)
    }
}
