package forpdateam.ru.forpda.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-02 — Source-anchor TTL loses exact post on slow network.
 *
 * Decision-mirroring tests for the back-restore URL builder: the native back snapshot
 * ([TopicBackSnapshot.visiblePostId]) is persisted in [ThemeHistoryController] independently of
 * the 15s JS source-anchor TTL, and [ThemeBackRestoreUrlPolicy] keeps `#entry<postId>` from that
 * snapshot when the page anchor is gone (TTL expired) — without regressing the existing
 * anchor-preferred / findpost-stripped contract.
 */
class BackRestoreNativeSnapshotTest {

    @Test
    fun backRestoreUrl_keepsEntryFromNativeSnapshot_whenAnchorExpired() {
        // Simulates a slow read of Topic B: by the time the user comes back, the page anchor
        // (JS source-anchor) is null because its 15s TTL expired. The native snapshot post id
        // must still produce a #entry URL.
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = null,
                pageUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180",
                nativeSnapshotPostId = "143876380",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }

    @Test
    fun backRestoreUrl_prefersPageAnchor_overNativeSnapshot() {
        // When the page anchor is still authoritative it wins; the snapshot is only a fallback.
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = "143876380",
                pageUrl = null,
                nativeSnapshotPostId = "999999999",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }

    @Test
    fun backRestoreUrl_acceptsEntryPrefixedSnapshotPostId() {
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = null,
                pageUrl = null,
                nativeSnapshotPostId = "entry143876380",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }

    @Test
    fun backRestoreUrl_fallsBackToUrlHash_whenNoAnchorAndNoSnapshot() {
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = null,
                pageUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                nativeSnapshotPostId = null,
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }

    @Test
    fun backRestoreUrl_dropsEntry_whenSnapshotPostIdNonNumeric() {
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 0,
                anchorPostId = null,
                pageUrl = null,
                nativeSnapshotPostId = "not-a-post",
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483",
                url,
        )
        assertFalse(url.contains("#entry"))
    }

    @Test
    fun findUsableBackSnapshotByPost_prefersSourceStOverTrailingViewportStamp() {
        // Cross-topic BACK st/anchor mismatch (log 1121483): the user reads topic 1121483 at post
        // 143979796 (st=1240/page-63), taps a cross-topic link, then BACK. The authoritative source
        // snapshot is captured FIRST at the genuine source st=1240. The trailing onPauseOrHide
        // viewport capture then re-stamps the SAME visible post under the loaded currentPage.st=1260
        // (st/post mismatch — 143979796 is NOT on page 64). Resolving the snapshot by post must return
        // the FIRST-captured (st=1240) entry, so the restore URL pairs the right post with the right st.
        val controller = ThemeHistoryController()
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = 1121483,
                        pageSt = 1240,
                        visiblePostId = "143979796",
                        scrollOffset = 5288,
                        scrollRatio = 0.582,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = 1121483,
                        pageSt = 1260,
                        visiblePostId = "143979796",
                        scrollOffset = 18508,
                        scrollRatio = 0.582,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )

        val resolved = controller.findUsableBackSnapshotByPost(1121483, "143979796")
        assertTrue(resolved != null)
        assertEquals("source st must win over trailing viewport stamp", 1240, resolved!!.pageSt)

        // The reconciled restore URL must carry the source st (1240), not the loaded page st (1260).
        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = resolved.pageSt,
                anchorPostId = resolved.visiblePostId,
                pageUrl = null,
                nativeSnapshotPostId = null,
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1240#entry143979796",
                url,
        )
    }

    @Test
    fun findUsableBackSnapshotByPost_acceptsEntryPrefixedQuery_andIgnoresStaleOrMismatched() {
        val controller = ThemeHistoryController()
        // A stale snapshot for the same post must be ignored.
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = 1121483,
                        pageSt = 1240,
                        visiblePostId = "143979796",
                        scrollOffset = 5288,
                        scrollRatio = 0.582,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.STALE,
                ),
        )
        assertEquals(null, controller.findUsableBackSnapshotByPost(1121483, "entry143979796"))

        // A different post / different topic must not match.
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = 1121483,
                        pageSt = 1260,
                        visiblePostId = "144001606",
                        scrollOffset = 100,
                        scrollRatio = 0.1,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )
        assertEquals(null, controller.findUsableBackSnapshotByPost(1121483, "143979796"))
        assertEquals(null, controller.findUsableBackSnapshotByPost(9999999, "144001606"))
        // The usable, matching one resolves with the entry-prefixed query form too.
        assertEquals(
                1260,
                controller.findUsableBackSnapshotByPost(1121483, "entry144001606")?.pageSt,
        )
    }

    @Test
    fun nativeSnapshot_survivesIndependentOfJsTtl_inHistoryController() {
        // The history controller holds the back snapshot keyed by topicId+st. Unlike the JS
        // source-anchor, it has no TTL — peeking it after an arbitrarily long delay still returns
        // the captured post so buildBackRestoreUrl can rebuild #entry.
        val controller = ThemeHistoryController()
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = 1121483,
                        pageSt = 1180,
                        visiblePostId = "143876380",
                        scrollOffset = 10099,
                        scrollRatio = 0.764,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )
        val snapshot = controller.peekBackSnapshot(1121483, 1180)
        assertTrue(snapshot != null && snapshot.isUsable())
        assertEquals("143876380", snapshot!!.visiblePostId)

        val url = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = 1121483,
                st = 1180,
                anchorPostId = null,
                pageUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180",
                nativeSnapshotPostId = snapshot.visiblePostId,
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                url,
        )
    }
}
