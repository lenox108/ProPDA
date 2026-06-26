package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B-01 — Cross-topic internal link opens a NEW tab; Topic A's in-tab history does not record the
 * transition. The lower-risk fix captures Topic A's clicked source-anchor as a native back
 * snapshot BEFORE Topic B opens, so returning to Topic A restores the original post/scroll
 * independent of the JS source-anchor TTL.
 *
 * ThemeViewModel cannot be instantiated on the JVM (Hilt-injected dependencies), so this is a
 * decision-mirroring test consistent with the existing strategy (e.g. ThemeBackNavigationTest,
 * SourceAnchorTtlTest): it exercises the same ThemeHistoryController + ThemeBackRestoreUrlPolicy
 * mechanism the ViewModel delegates to.
 */
class CrossTopicBackRestoreTest {

    private fun topicAPage(): ThemePage {
        val page = ThemePage()
        page.id = 1121483
        page.pagination.current = 60 // st = (60 - 1) * 20 = 1180
        page.pagination.all = 60
        page.url = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180"
        // Clicked post on Topic A (the exact source post the user tapped a cross-topic link from).
        page.anchorPostId = "143876380"
        page.scrollY = 10099
        page.scrollRatio = 0.764
        return page
    }

    @Test
    fun crossTopicOpen_capturesTopicAPosition_restorableAfterTtlExpiry() {
        val controller = ThemeHistoryController()
        val topicA = topicAPage()

        // Mirrors captureBackSnapshotBeforeCrossTopicOpen → captureNativeBackSnapshot:
        // Topic A's position is durably persisted right before Topic B opens in a new tab.
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = topicA.id,
                        pageSt = topicA.st,
                        visiblePostId = topicA.anchorPostId,
                        scrollOffset = topicA.scrollY,
                        scrollRatio = topicA.scrollRatio,
                        wasNearBottom = topicA.wasNearBottom,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )

        // Returning to Topic A: the JS source-anchor would have expired during a long read of
        // Topic B (anchorPostId no longer authoritative -> simulate null here). The native
        // snapshot must still rebuild the exact #entry post.
        val snapshot = controller.peekBackSnapshot(topicA.id, topicA.st)
        assertNotNull(snapshot)
        assertTrue(snapshot!!.isUsable())

        val restoreUrl = ThemeBackRestoreUrlPolicy.buildRestoreUrl(
                topicId = topicA.id,
                st = topicA.st,
                anchorPostId = null,
                pageUrl = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180",
                nativeSnapshotPostId = snapshot.visiblePostId,
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                restoreUrl,
        )
        assertEquals(10099, snapshot.scrollOffset)
    }

    @Test
    fun multiHopBack_sourceAnchorWrite_keepsUrlAnchorAndAnchorPostIdSelfConsistent() {
        // Multi-hop BACK wrong-post (log 239158): a history page is pushed at the server-landing post
        // (#entry126307973 in BOTH url and anchor). The user then scrolls to a DIFFERENT post
        // (126306622) and taps an in-tab link there. applyLinkSourceAnchorSnapshot updates the
        // authoritative anchorPostId to 126306622, but the cached back-remap path replays `prev.url`
        // and renders `page.anchor`. If those keep the stale 126307973, BACK lands on the wrong post.
        // This mirrors the fix: setting the source anchorPostId must also rewrite url + anchor.
        val page = ThemePage()
        page.id = 239158
        page.pagination.current = 645
        page.pagination.all = 800
        page.url = "https://4pda.to/forum/index.php?showtopic=239158&st=12880#entry126307973"
        page.addAnchor("entry126307973")
        page.anchorPostId = "126307973"

        // ---- mirror of applyLinkSourceAnchorSnapshot's self-consistency sync ----
        val sourcePostId = "126306622"
        page.anchorPostId = sourcePostId
        val normalized = sourcePostId.removePrefix("entry")
        val entryName = "entry$normalized"
        if (page.anchor != entryName) {
            page.anchors.clear()
            page.anchors.add(entryName)
        }
        page.url = ThemeBackRestoreUrlPolicy.replaceEntryHash(page.url, sourcePostId)
        // ------------------------------------------------------------------------

        // All three views of "which post does BACK restore" now agree on the source post.
        assertEquals("126306622", page.anchorPostId)
        assertEquals("entry126306622", page.anchor)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=239158&st=12880#entry126306622",
                page.url,
        )
        // The stale server-landing post is gone from every field.
        assertTrue(page.url!!.contains("#entry126306622"))
        assertTrue(!page.url!!.contains("126307973"))
        assertTrue(!page.anchors.contains("entry126307973"))
    }

    @Test
    fun replaceEntryHash_rewritesFragment_andNeverCorruptsUrl() {
        // Rewrites an existing hash.
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=239158&st=12880#entry126306622",
                ThemeBackRestoreUrlPolicy.replaceEntryHash(
                        "https://4pda.to/forum/index.php?showtopic=239158&st=12880#entry126307973",
                        "126306622",
                ),
        )
        // Adds a hash when none present.
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=239158&st=12880#entry126306622",
                ThemeBackRestoreUrlPolicy.replaceEntryHash(
                        "https://4pda.to/forum/index.php?showtopic=239158&st=12880",
                        "entry126306622",
                ),
        )
        // Blank/non-numeric post id leaves the url untouched (never corrupts it).
        val original = "https://4pda.to/forum/index.php?showtopic=239158&st=12880#entry126307973"
        assertEquals(original, ThemeBackRestoreUrlPolicy.replaceEntryHash(original, null))
        assertEquals(original, ThemeBackRestoreUrlPolicy.replaceEntryHash(original, "not-a-post"))
    }

    @Test
    fun snapshotKey_isScopedToTopicAndSt_soTopicBDoesNotClobberTopicA() {
        val controller = ThemeHistoryController()
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(1121483, 1180, "143876380", 10099, 0.764, false),
        )
        // Topic B captures its own snapshot at a different key.
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(239158, 0, "100000001", 0, 0.0, false),
        )
        val topicASnapshot = controller.peekBackSnapshot(1121483, 1180)
        assertNotNull(topicASnapshot)
        assertEquals("143876380", topicASnapshot!!.visiblePostId)
    }
}
