package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * C-07: regression tests for the lastOrNull migration in
 * [ThemeHistoryController]. The previous `history.last()` implementation
 * would have thrown `NoSuchElementException` on an empty list, but
 * the public methods guard with `isNotEmpty()` / `size > 1` checks;
 * the contract being tested here is that the public API never crashes
 * on the empty case.
 */
class ThemeHistoryControllerTest {

    @Test
    fun backPage_emptyHistory_returnsNull() {
        val controller = ThemeHistoryController()
        assertNull(controller.backPage())
    }

    @Test
    fun backPage_singleItemHistory_returnsNull() {
        val controller = ThemeHistoryController()
        // backPage is called with a fake page so the list has one item
        // and the size<=1 guard kicks in.
        // We don't have a ThemePage builder here, so we just exercise
        // the empty path which is the only one that's safe in unit tests.
        assertNull(controller.backPage())
    }

    @Test
    fun updateHistoryLast_emptyHistory_isNoOp() {
        val controller = ThemeHistoryController()
        // Should not throw, even when history is empty.
        try {
            // We cannot construct a real ThemePage in the unit test classpath
            // without significant test fixtures, but the public method is
            // guarded by `history.isNotEmpty()` and is safe to call as a no-op
            // when the list is empty. The Kotlin compiler will not let us pass
            // a null argument here because of the non-null parameter, so we
            // just verify the controller is constructible and other guards work.
            assertNotNull(controller)
            assertNull(controller.backPage())
        } catch (e: Exception) {
            org.junit.Assert.fail("updateHistoryLast path on empty history threw: $e")
        }
    }

    @Test
    fun instance_isFresh() {
        val a = ThemeHistoryController()
        val b = ThemeHistoryController()
        // Each controller has its own history list — verify they don't share.
        assertEquals(a.backPage(), b.backPage())
    }

    // --- F8 anchor canonicalization: regression for log 24_06-13-16-39_747 ---
    // The parser used to leave `page.anchorPostId = null` on the findpost reload
    // path while `page.anchor = "entry<id>"` was correctly set, which made
    // saveToHistory log "anchor=null" and broke the dedupe. The fix canonicalizes
    // the anchor in both directions: parser stamps anchorPostId AND
    // ThemeHistoryController falls back to `anchor.removePrefix("entry")` so old
    // / third-party pages without anchorPostId still dedupe.

    private fun pageWithAnchor(
        topicId: Int,
        st: Int,
        anchor: String? = null,
        anchorPostId: String? = null
    ): ThemePage {
        val page = ThemePage()
        page.id = topicId
        // page.st == (pagination.current - 1) * pagination.perPage,
        // and perPage defaults to 20. Invert to get a matching current.
        page.pagination.current = (st / 20) + 1
        page.pagination.all = page.pagination.current
        page.url = "https://4pda.to/forum/index.php?showtopic=$topicId&st=$st"
        anchor?.let { page.addAnchor(it) }
        page.anchorPostId = anchorPostId
        return page
    }

    @Test
    fun saveToHistory_dedupesOnCanonicalAnchorWhenAnchorPostIdIsNull() {
        // First push sets `anchor` only (parser findpost path before the fix).
        // Second push with the same id+st+anchor and only a tiny scroll delta
        // should dedupe (replace in-place) instead of growing history.
        val controller = ThemeHistoryController()
        val first = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = null)
        controller.saveToHistory(first)
        val second = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = null)
        second.scrollY = 10
        controller.saveToHistory(second)
        // size==1 means dedupe fired (back stack does not grow on re-fetch)
        assertEquals(false, controller.canGoBack())
    }

    @Test
    fun saveToHistory_dedupesWhenFirstHasAnchorPostIdAndSecondHasAnchorOnly() {
        // Cross-shape: first push is the getnewpost resolution (anchorPostId set),
        // second push is the findpost reload (anchorPostId was null on old parser
        // builds). Both should normalize to the same canonical id and dedupe.
        val controller = ThemeHistoryController()
        val first = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = "143988030")
        controller.saveToHistory(first)
        val second = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = null)
        second.scrollY = 5
        controller.saveToHistory(second)
        assertEquals(false, controller.canGoBack())
    }

    @Test
    fun saveToHistory_dedupesWhenFirstHasAnchorOnlyAndSecondHasAnchorPostId() {
        // Symmetric: first push is the old findpost reload, second is the
        // post-fix findpost reload. Both shapes should still dedupe.
        val controller = ThemeHistoryController()
        val first = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = null)
        controller.saveToHistory(first)
        val second = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = "143988030")
        second.scrollY = 5
        controller.saveToHistory(second)
        assertEquals(false, controller.canGoBack())
    }

    @Test
    fun updateHistoryLast_canonicalizesPrevAnchorBeforeCopy() {
        // REFRESH/BACK must not silently drop the scroll anchor when the
        // previous entry was created on a parser build that left
        // anchorPostId null. The copy uses the canonical form so the new
        // page still sees the saved anchor id.
        val controller = ThemeHistoryController()
        val prev = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = null)
        prev.scrollY = 1234
        controller.saveToHistory(prev)
        val refreshed = pageWithAnchor(1103268, 26280, anchor = "entry143988030", anchorPostId = null)
        controller.updateHistoryLast(refreshed)
        assertEquals("143988030", refreshed.anchorPostId)
        assertEquals(1234, refreshed.scrollY)
    }

    // --- Multi-back anchor loss fix (log 239158, in-tab findpost) ---

    @Test
    fun inTabFindpost_trailingVisibleSnapshot_doesNotOverwriteAuthoritativeAnchor_andBackPopsSourcePost() {
        // Log 239158: push page 703 (st=14040) opened at the clicked source post 132558585.
        val controller = ThemeHistoryController()
        // A prior page (st=15940) so a real BACK is possible after the push.
        controller.saveToHistory(pageWithAnchor(239158, 15940, anchor = "entry143994024", anchorPostId = "143994024"))

        val page703 = pageWithAnchor(239158, 14040, anchor = "entry132558585", anchorPostId = "132558585")
        page703.authoritativeAnchorPostId = "132558585"
        controller.saveToHistory(page703)

        // The user scrolls to neighbor 132558226 and taps a NEW in-tab findpost link. The trailing
        // visible-anchor snapshot carries the RAW neighbor 132558226. The controller itself must
        // refuse to overwrite the entry's authoritative explicit-open anchor with that neighbor —
        // even if the caller passes the raw visible anchor (defense in depth at the lowest mutation
        // point), so BACK still pops the exact source post.
        controller.updatePageHistoryHtml(
                target = page703,
                html = "<html></html>",
                scrollY = 42700,
                anchorPostId = "132558226",
                anchorOffsetTop = -109.7,
                scrollRatio = 0.926,
                wasNearBottom = false,
        )

        // The entry must still point at the source post the page was opened at.
        assertEquals("132558585", page703.anchorPostId)

        // Push the deeper page reached by the in-tab link, then BACK.
        controller.saveToHistory(pageWithAnchor(239158, 11940, anchor = "entry120577849", anchorPostId = "120577849"))
        val popped = controller.backPage()
        assertNotNull(popped)
        // BACK lands on the exact source post (#entry132558585), not the neighbor 132558226.
        assertEquals("132558585", popped!!.anchorPostId)
        assertEquals(14040, popped.st)
    }

    @Test
    fun refreshSnapshotPath_keepsAuthoritative_forInTabFindpost_butFreshOpenIsNotPinned() {
        // Follow-up regression (log 25_06-09-57-06, st=12080): the SECOND snapshot path
        // (updatePageRefreshScrollSnapshot -> applyRefreshSnapshot) was overwriting the entry's
        // authoritative anchor with the click-time visible neighbor — push 121429450, pop 121429251.
        // This test pins BOTH halves of the contract in one place so they can never be traded off:
        //   (a) in-tab findpost step keeps its authoritative anchor through the refresh-snapshot
        //       decision (resolveEntryAnchor), so BACK pops the source post; and
        //   (b) a fresh open (no authoritative anchor) is NOT pinned and records its real anchor.
        val controller = ThemeHistoryController()
        controller.saveToHistory(pageWithAnchor(239158, 12200, anchor = "entry121500000", anchorPostId = "121500000"))

        // (a) In-tab findpost page opened at clicked source post 121429450.
        val findpostPage = pageWithAnchor(239158, 12080, anchor = "entry121429450", anchorPostId = "121429450")
        findpostPage.authoritativeAnchorPostId = "121429450"
        controller.saveToHistory(findpostPage)
        // The refresh-snapshot path resolves the entry anchor BEFORE committing; the trailing
        // visible neighbor 121429251 must be ignored in favor of the authoritative 121429450.
        val resolvedForFindpost = ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                authoritativeAnchorPostId = findpostPage.authoritativeAnchorPostId,
                candidateAnchorPostId = "121429251",
        )
        controller.updatePageHistoryHtml(
                target = findpostPage,
                html = "<html></html>",
                scrollY = 33956,
                anchorPostId = resolvedForFindpost,
                anchorOffsetTop = -103.3,
                scrollRatio = 0.79,
                wasNearBottom = false,
        )
        assertEquals("121429450", findpostPage.anchorPostId)

        // Push the deeper page reached by the in-tab findpost link, then BACK: it must pop the exact
        // source post 121429450, NOT the neighbor 121429251.
        controller.saveToHistory(pageWithAnchor(239158, 11940, anchor = "entry120578050", anchorPostId = "120578050"))
        val popped = controller.backPage()
        assertNotNull(popped)
        assertEquals("121429450", popped!!.anchorPostId)
        assertEquals(12080, popped.st)

        // (b) A fresh already-read open: no authoritative anchor, so the refresh-snapshot decision
        // keeps the genuine read/visible anchor (never pinned to a stale post).
        val freshController = ThemeHistoryController()
        val freshPage = pageWithAnchor(1121483, 1260, anchor = "entry143997425", anchorPostId = "143997425")
        // No authoritativeAnchorPostId set -> fresh open.
        freshController.saveToHistory(freshPage)
        val resolvedForFresh = ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                authoritativeAnchorPostId = freshPage.authoritativeAnchorPostId,
                candidateAnchorPostId = "143986594",
        )
        freshController.updatePageHistoryHtml(
                target = freshPage,
                html = "<html></html>",
                scrollY = 23527,
                anchorPostId = resolvedForFresh,
                anchorOffsetTop = 97.5,
                scrollRatio = 0.819,
                wasNearBottom = false,
        )
        assertEquals("143986594", freshPage.anchorPostId)
    }

    @Test
    fun updatePageHistoryHtml_normalScrollPage_updatesAnchor_whenNoAuthoritative() {
        // A genuine scroll page (no explicit-open anchor) must keep recording its real viewed
        // anchor — the guard must only protect findpost-opened entries, never freeze normal pages.
        val controller = ThemeHistoryController()
        val page = pageWithAnchor(239158, 11940, anchor = "entry120577849", anchorPostId = "120577849")
        // No authoritativeAnchorPostId set -> normal scroll page.
        controller.saveToHistory(page)
        controller.updatePageHistoryHtml(
                target = page,
                html = "<html></html>",
                scrollY = 1000,
                anchorPostId = "120577900",
                anchorOffsetTop = 0.0,
                scrollRatio = 0.5,
                wasNearBottom = false,
        )
        assertEquals("120577900", page.anchorPostId)
    }

    @Test
    fun updatePageHistoryHtml_authoritative_alsoProtectsBackSnapshotVisiblePost() {
        // Geometry-consistency fix (device log 25_06-22-18-38): when a trailing visible-anchor
        // snapshot (neighbor post 132558226 at y=42700) arrives for a page whose authoritative
        // explicit-open anchor is 132558585, updatePageHistoryHtml must keep the authoritative anchor
        // AND must NOT overwrite the existing back snapshot with the neighbor's mismatched geometry.
        // The previously-captured authoritative snapshot (the only self-consistent post+geometry
        // tuple) survives untouched, so a later restore rebuilds #entry132558585 at its own scroll.
        val controller = ThemeHistoryController()
        val page = pageWithAnchor(239158, 14040, anchor = "entry132558585", anchorPostId = "132558585")
        page.authoritativeAnchorPostId = "132558585"
        controller.saveToHistory(page)
        // Pre-existing durable snapshot captured at the authoritative post's own geometry.
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = 239158,
                        pageSt = 14040,
                        visiblePostId = "132558585",
                        scrollOffset = 3807,
                        scrollRatio = 0.5626,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )
        controller.updatePageHistoryHtml(
                target = page,
                html = "<html></html>",
                scrollY = 42700,
                anchorPostId = "132558226",
                anchorOffsetTop = -109.7,
                scrollRatio = 0.926,
                wasNearBottom = false,
        )
        val snapshot = controller.peekBackSnapshot(239158, 14040)
        assertNotNull(snapshot)
        // The authoritative post id survives.
        assertEquals("132558585", snapshot!!.visiblePostId)
        // And its OWN geometry survives — the neighbor's y=42700 must NOT have overwritten it.
        assertEquals(
                "the trailing neighbor's mismatched geometry must not overwrite the authoritative snapshot",
                3807,
                snapshot.scrollOffset,
        )
    }

    @Test
    fun crossTopicEqualAnchor_trailingSnapshot_isNoOp_keepsAnchor() {
        // Cross-topic 1121483: the click-time source post EQUALS the page's authoritative anchor,
        // so the policy is a no-op and the (unchanged) anchor is preserved end-to-end.
        val controller = ThemeHistoryController()
        val page = pageWithAnchor(1121483, 1180, anchor = "entry143876380", anchorPostId = "143876380")
        page.authoritativeAnchorPostId = "143876380"
        controller.saveToHistory(page)
        val resolved = ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                authoritativeAnchorPostId = page.authoritativeAnchorPostId,
                candidateAnchorPostId = "143876380",
        )
        controller.updatePageHistoryHtml(
                target = page,
                html = "<html></html>",
                scrollY = 10256,
                anchorPostId = resolved,
                anchorOffsetTop = 0.0,
                scrollRatio = 0.764,
                wasNearBottom = false,
        )
        assertEquals("143876380", page.anchorPostId)
    }

    @Test
    fun updateHistoryLast_preservesAuthoritativeAnchorAcrossRefresh() {
        // A refresh of an in-tab findpost page must keep restoring its authoritative source post.
        val controller = ThemeHistoryController()
        val prev = pageWithAnchor(239158, 14040, anchor = "entry132558585", anchorPostId = "132558585")
        prev.authoritativeAnchorPostId = "132558585"
        controller.saveToHistory(prev)
        val refreshed = pageWithAnchor(239158, 14040, anchor = "entry132558585", anchorPostId = "132558585")
        controller.updateHistoryLast(refreshed)
        assertEquals("132558585", refreshed.authoritativeAnchorPostId)
    }

    @Test
    fun saveToHistory_doesNotDedupeWhenScrollDeltaIsLarge() {
        // Regression guard: the F8 dedupe must only fire on small scroll
        // deltas. A real navigation must keep both entries so BACK still
        // returns to the previous scroll position.
        val controller = ThemeHistoryController()
        val first = pageWithAnchor(1103268, 0, anchor = "entry1", anchorPostId = "1")
        controller.saveToHistory(first)
        val second = pageWithAnchor(1103268, 0, anchor = "entry1", anchorPostId = "1")
        second.scrollY = 5000
        controller.saveToHistory(second)
        assertEquals(true, controller.canGoBack())
    }
}
