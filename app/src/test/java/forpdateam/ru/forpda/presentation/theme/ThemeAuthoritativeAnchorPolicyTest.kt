package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-back anchor loss fix (device log 239158, in-tab findpost).
 *
 * The in-tab findpost case (broken): push `st=14040 anchor=132558585`, then a trailing
 * source-anchor snapshot for the click-time visible post `132558226` must NOT overwrite the
 * authoritative `132558585` — BACK must return `#entry132558585`, not the neighbor `132558226`.
 *
 * The cross-topic case (working, e.g. `143876380`): the click-time source post EQUALS the page's
 * authoritative anchor, so this policy is a no-op and restore-in-place is preserved.
 */
class ThemeAuthoritativeAnchorPolicyTest {

    // --- Regression fix (log 25_06-09-41-46): authoritative anchor must be recorded ONLY for a
    // genuine in-tab findpost link tap, never for a fresh already-read / unread topic open. ---

    @Test
    fun recordsAuthoritative_onlyForInTabFindpostExplicitPostTarget() {
        // Genuine in-tab findpost link tap: findpost URL, NOT a fresh open, resolver = EXPLICIT_POST.
        assertTrue(
                ThemeAuthoritativeAnchorPolicy.shouldRecordAuthoritativeAnchor(
                        openedViaFindPostLink = true,
                        isFreshTopicOpen = false,
                        isExplicitPostTarget = true,
                )
        )
    }

    @Test
    fun doesNotRecordAuthoritative_onFreshAlreadyReadOrUnreadOpen() {
        // Fresh favorites/unread open: rides a getnewpost/findpost redirect (openedViaFindPostLink
        // incidentally true) but is a FRESH open and the resolver picked READ_RESUME /
        // SERVER_UNREAD_FALLBACK (isExplicitPostTarget=false). Must NOT pin an authoritative anchor.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRecordAuthoritativeAnchor(
                        openedViaFindPostLink = true,
                        isFreshTopicOpen = true,
                        isExplicitPostTarget = false,
                )
        )
        // Even if the resolver somehow yielded EXPLICIT_POST, a fresh open still must not pin it.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRecordAuthoritativeAnchor(
                        openedViaFindPostLink = true,
                        isFreshTopicOpen = true,
                        isExplicitPostTarget = true,
                )
        )
        // A non-explicit-post target (last-read/unread bookmark) never pins, even if not fresh.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRecordAuthoritativeAnchor(
                        openedViaFindPostLink = true,
                        isFreshTopicOpen = false,
                        isExplicitPostTarget = false,
                )
        )
    }

    @Test
    fun doesNotRecordAuthoritative_whenNotFindpost() {
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRecordAuthoritativeAnchor(
                        openedViaFindPostLink = false,
                        isFreshTopicOpen = false,
                        isExplicitPostTarget = true,
                )
        )
    }

    @Test
    fun keepsAuthoritative_whenTrailingSnapshotPostDiffers() {
        // Log 239158: push anchor=132558585, trailing snapshot anchor=132558226 (neighbor).
        assertTrue(
                ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                        authoritativeAnchorPostId = "132558585",
                        candidateAnchorPostId = "132558226",
                )
        )
        assertEquals(
                "132558585",
                ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                        authoritativeAnchorPostId = "132558585",
                        candidateAnchorPostId = "132558226",
                )
        )
    }

    @Test
    fun doesNotKeepAuthoritative_whenSnapshotPostEqualsIt_crossTopicCase() {
        // Cross-topic 1121483: source post 143876380 == page authoritative 143876380 → no-op.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                        authoritativeAnchorPostId = "143876380",
                        candidateAnchorPostId = "143876380",
                )
        )
        assertEquals(
                "143876380",
                ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                        authoritativeAnchorPostId = "143876380",
                        candidateAnchorPostId = "143876380",
                )
        )
    }

    @Test
    fun normalScrollPage_withoutAuthoritativeAnchor_usesCandidate() {
        // Ordinary scroll-opened page: no authoritative anchor, genuine viewed anchor wins.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                        authoritativeAnchorPostId = null,
                        candidateAnchorPostId = "999",
                )
        )
        assertEquals(
                "999",
                ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                        authoritativeAnchorPostId = null,
                        candidateAnchorPostId = "999",
                )
        )
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                        authoritativeAnchorPostId = "  ",
                        candidateAnchorPostId = "999",
                )
        )
    }

    // --- Geometry-consistency back-snapshot guard (device log 25_06-22-18-38): cross-topic / in-tab
    // BACK lands on the wrong post (143860995) instead of the authoritative source (143876380),
    // because a back snapshot pairs the authoritative post id with the VISIBLE post's pixel geometry.

    @Test
    fun rejectsBackSnapshotGeometry_whenVisiblePostDiffersFromAuthoritative() {
        // Device log: page authoritative anchor 143876380 (bookmark findpost open), but the user
        // scrolled down and tapped the 239158 link while VISUALLY at 143860995 (y=11810). The
        // snapshot would carry post=143876380 + y=11810 → BACK loads #entry143876380 but scrolls to
        // 143860995's location. The guard must reject that geometry overwrite.
        assertTrue(
                ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                        authoritativeAnchorPostId = "143876380",
                        candidateVisiblePostId = "143860995",
                )
        )
    }

    @Test
    fun acceptsBackSnapshotGeometry_whenVisiblePostEqualsAuthoritative() {
        // Normal cross-topic case: the user tapped the link from the SAME post the page was opened
        // at (143876380 == 143876380). The geometry is genuine for that post, so capture it.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                        authoritativeAnchorPostId = "143876380",
                        candidateVisiblePostId = "143876380",
                )
        )
    }

    @Test
    fun acceptsBackSnapshotGeometry_whenNoAuthoritativeAnchor() {
        // Ordinary scroll page (no in-tab findpost authoritative anchor): the visible post's
        // geometry is the genuine viewed position; never reject it.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                        authoritativeAnchorPostId = null,
                        candidateVisiblePostId = "143860995",
                )
        )
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                        authoritativeAnchorPostId = "   ",
                        candidateVisiblePostId = "143860995",
                )
        )
    }

    @Test
    fun acceptsBackSnapshotGeometry_whenVisiblePostUnknown() {
        // No tapped/visible post id available (e.g. JS source-anchor TTL expired): there is nothing
        // to prove a mismatch, so do not reject (the unconditional capture path is the safe default).
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldRejectAuthoritativeMismatchedBackSnapshot(
                        authoritativeAnchorPostId = "143876380",
                        candidateVisiblePostId = null,
                )
        )
    }

    @Test
    fun nullCandidate_neverDisplacesAuthoritative() {
        // A blank/null trailing snapshot (e.g. anchor lost entirely) must not clear the
        // authoritative anchor; the entry keeps restoring its source post.
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                        authoritativeAnchorPostId = "132558585",
                        candidateAnchorPostId = null,
                )
        )
        // resolveEntryAnchor returns the candidate (null) here because there is nothing to keep
        // against; the live history anchor is left untouched by the caller in that case.
        assertNull(
                ThemeAuthoritativeAnchorPolicy.resolveEntryAnchor(
                        authoritativeAnchorPostId = "132558585",
                        candidateAnchorPostId = null,
                )
        )
        assertFalse(
                ThemeAuthoritativeAnchorPolicy.shouldKeepAuthoritative(
                        authoritativeAnchorPostId = "132558585",
                        candidateAnchorPostId = "   ",
                )
        )
    }
}
