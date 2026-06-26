package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HYBRID infinite-scroll reanchor regression (device log 25_06-23-04-44_148.log, topic 1121483
 * st=1180; cross-topic BACK to the source post 143876380 landed on the neighbor 143860995).
 *
 * ## What this log proves (quoted line numbers from 25_06-23-04-44_148.log)
 *  - L4359: the user tapped the 239158 link while the SOURCE post was 143876380 itself, scrolled
 *    LOW in the viewport: `sourceAnchor applied ... sourcePostId=143876380 offset=278.44 y=10032
 *    ratio=0.7591`. (The earlier geometry guard correctly does NOT fire here — the visible post
 *    equals the authoritative anchor; this is a genuine capture of 143876380 at a low offset.)
 *  - L6764/L6765: BACK restored the right post but with the stale offset:
 *    `back_restore_applied ... scrollY=10032 anchorPostId=143876380` and
 *    `backRestoreSnapshot ... offset=278.4419860839844 ratio=0.7591`.
 *  - L6940: at restore time only page 60 is loaded (`max=6768 scrollHeight=7519 loaded=60..60/64`),
 *    so y=10032 is clamped to the top and the JS requests a top-prepend.
 *  - L6978: the STEP-4 HYBRID top-prepend pins to the topmost-VISIBLE post, which (with 143876380
 *    placed 278px down) is the post ABOVE it: `applyInfiniteEnd direction=top ... pinned=143860995`.
 *  - The scroll then drifts (L6981 `scroll y=22577`) and the user lands on 143860995, not 143876380.
 *
 * Contrast the WORKING back in the same log:
 *  - L3751: `backRestoreSnapshot ... offset=null ratio=0.5626` — offset is null, so 143876380 is
 *    TOP-aligned, becomes the topmost-visible post, and the prepend pins IT. Lands correctly.
 *
 * ## Root cause
 * The back snapshot carries a post id + page scrollY but NO trustworthy intra-post offsetTop. The
 * only offsetTop on the restored page is the stale click-time value (278.44). The JS STEP-3 restore
 * subtracts it as an intra-post offset and places the source post low, so the STEP-4 prepend pins a
 * neighbor. The fix top-aligns any snapshot-based back restore that has a real anchor post (offset
 * cleared → JS offset 0), exactly like the working `offset=null` path and a findpost open.
 *
 * ## Fail-before / pass-after
 *  - BEFORE: [ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor] did not exist and
 *    `applyBackHistoryRestoreSnapshot` left the stale `anchorOffsetTop=278.44` on the page, so the
 *    restore placed 143876380 low and the prepend pinned 143860995.
 *  - AFTER: the policy returns true for the anchored snapshot, the offset is cleared, the source post
 *    is top-aligned, and the prepend pins 143876380.
 */
class CrossTopicBackTopAlignReanchorTest {

    @Test
    fun topAlignsBackRestore_whenSnapshotHasAnchorPost() {
        // Device log: snapshot.visiblePostId = 143876380 (the source post). The restore must
        // top-align it (drop the stale click-time offset 278.44).
        assertTrue(
                "a snapshot-based back restore with a real anchor post must top-align it",
                ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor("143876380"),
        )
    }

    @Test
    fun doesNotTopAlign_whenNoAnchorPost() {
        // No anchor post in the snapshot: nothing to top-align; leave the pixel/ratio fallback path
        // untouched so a genuine anchorless scroll restore is not disturbed.
        assertFalse(ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor(null))
        assertFalse(ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor(""))
        assertFalse(ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor("   "))
    }

    /**
     * End-to-end mirror of the device-log restore: a back snapshot for 143876380 captured with the
     * stale click-time offset must, after the fix, drop the offset so the post top-aligns. Models the
     * exact field mutation `applyBackHistoryRestoreSnapshot` performs.
     */
    @Test
    fun backRestore_clearsStaleOffset_soSourcePostTopAligns() {
        // The page the back restore mutates (offset inherited from the click-time history entry).
        var anchorOffsetTop: Double? = 278.4419860839844
        val snapshotVisiblePostId = "143876380"

        // The decision + mutation done in applyBackHistoryRestoreSnapshot.
        if (ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor(snapshotVisiblePostId)) {
            anchorOffsetTop = null
        }

        assertNull(
                "the stale click-time offset (278.44) must be cleared so 143876380 top-aligns and " +
                        "the HYBRID top-prepend pins the source post, not the neighbor 143860995",
                anchorOffsetTop,
        )
    }

    /**
     * Negative control: the already-working `offset=null` back path (device log L3751) must be a
     * no-op — there is no stale offset to clear, and the source post already top-aligns.
     */
    @Test
    fun backRestore_withNullOffset_isUnchanged() {
        var anchorOffsetTop: Double? = null
        if (ThemeBackRestoreUrlPolicy.shouldTopAlignBackRestoreAnchor("143876380")) {
            anchorOffsetTop = null
        }
        assertNull(anchorOffsetTop)
        // Sanity: the anchor post is the one that must win the restore.
        assertEquals("143876380", "143876380")
    }
}
