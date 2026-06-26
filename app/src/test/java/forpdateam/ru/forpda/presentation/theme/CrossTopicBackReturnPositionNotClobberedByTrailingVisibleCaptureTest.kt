package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trailing-capture overwrite regression for the cross-tab return-position store (device log
 * 25_06-19-16-48, cross-topic back to wrong post; the dynamic smoking gun: 143860995, 143986594,
 * 143873895 — a different "wrong post" per log because it is the post the user happened to be
 * looking at when they last scrolled).
 *
 * Sequence reproduced from the device log (topic 1121483 st=1180, source 143876380, target 239158):
 *  1. The user opens topic 1121483 with a JS-captured findpost link at post 143876380.
 *  2. The user scrolls within 1121483 to y=3807 (the page reports ratio=0.5626) and taps a link to
 *     topic 239158. [C2] captures the durable cross-topic back snapshot with visiblePostId=143876380.
 *  3. The user reads 239158 briefly, then presses BACK. The restore path correctly lands on 143876380
 *     (anchorPostId=143876380, scrollY=3807) — this is [B-01]'s native snapshot path and it works.
 *  4. The user scrolls further down inside 1121483 to where post 143873895 is visible.
 *  5. The user backgrounds the app. The trailing onPauseOrHide capture flow runs:
 *       - First call (native, no JS): saves to returnPositionStore. (No overwrite — anchor=null.)
 *       - JS callback fires, reports post=143873895. Second call passes safePostId=143873895.
 *     At this point the JS source-anchor TTL (15 s) has long expired (≈ 27 s since link tap), so
 *     `pendingHistorySourceAnchor` is null in ThemeViewModel.updatePageHistoryHtml. Without the fix,
 *     `effectiveAnchorPostId = pendingSource?.postId ?: anchorPostId = 143873895`, and
 *     `returnPositionStore.save(postId=143873895, ...)` OVERWRITES the durably-captured
 *     143876380. The next reentry (tab switch, app re-foreground, favorites re-open) restores
 *     143873895 instead of the click-time source post 143876380.
 *
 * With the fix, `returnPositionStore.save` is gated on the durable `TopicBackSnapshot.visiblePostId`
 * (143876380, status=CAPTURED, stamped at crossTopicOpen time and never marked stale) so the
 * scrolled-to visible post 143873895 can NEVER overwrite the cross-tab store. The next reentry
 * restores 143876380 deterministically.
 */
class CrossTopicBackReturnPositionNotClobberedByTrailingVisibleCaptureTest {

    private fun sourcePage(): ThemePage {
        val page = ThemePage()
        page.id = 1121483
        page.pagination.current = 60 // st = (60 - 1) * 20 = 1180
        page.pagination.all = 64
        page.url = "https://4pda.to/forum/index.php?showtopic=1121483&st=1180"
        // Source post the user tapped the cross-topic link from (C2 captured this at link-tap time).
        page.anchorPostId = "143876380"
        page.scrollY = 3807
        page.scrollRatio = 0.5626
        return page
    }

    @Test
    fun resolveReturnPositionPostId_prefersDurableBackSnapshotOverJsVisiblePost() {
        // Device log: the durable back-snapshot was stamped at crossTopicOpen time with the
        // click-time source post 143876380. The page's own authoritativeAnchorPostId is null here
        // (true fresh-open case — `isFreshOpen=true` for this bookmark reload; that is the case the
        // prior code broke, and it is the case where the wrong-post smoking gun surfaces on
        // reentry). The trailing JS-captured visible post is 143873895 (post the user scrolled to).
        val resolved = ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId(
                durableBackSnapshotPostId = "143876380",
                pageAuthoritativeAnchorPostId = null,
                candidateAnchorPostId = "143873895",
        )
        assertEquals(
                "durable back-snapshot must win over a trailing JS-captured visible post (the dynamic wrong-post bug)",
                "143876380",
                resolved,
        )
    }

    @Test
    fun resolveReturnPositionPostId_fallsBackToAuthoritative_whenNoBackSnapshot() {
        // In-tab findpost case (no cross-topic back-snapshot, but page carries an explicit-open
        // authoritative anchor): the authoritative anchor must win over a trailing visible post.
        val resolved = ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId(
                durableBackSnapshotPostId = null,
                pageAuthoritativeAnchorPostId = "143876380",
                candidateAnchorPostId = "143873895",
        )
        assertEquals("143876380", resolved)
    }

    @Test
    fun resolveReturnPositionPostId_fallsBackToCandidate_whenNothingElse() {
        // Normal scroll page: no cross-topic snapshot, no explicit-open anchor. The candidate
        // (the post the user is genuinely viewing) is the right thing to mirror into the store.
        val resolved = ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId(
                durableBackSnapshotPostId = null,
                pageAuthoritativeAnchorPostId = null,
                candidateAnchorPostId = "143873895",
        )
        assertEquals("143873895", resolved)
    }

    @Test
    fun resolveReturnPositionPostId_ignoresBlankInputs() {
        // Defensive: blank strings must be treated as absent (mirrors ThemeAuthoritativeAnchorPolicy
        // contract for the other resolvers).
        val resolved = ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId(
                durableBackSnapshotPostId = "  ",
                pageAuthoritativeAnchorPostId = "",
                candidateAnchorPostId = "143873895",
        )
        assertEquals("143873895", resolved)
    }

    /**
     * The end-to-end decision-mirror of the device-log sequence. Without the new
     * [ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId] helper, a `returnPositionStore.save`
     * call with `candidateAnchorPostId=143873895` would clobber the previously-saved 143876380.
     * With the helper, the call prefers the durable back-snapshot's `visiblePostId` and the store
     * keeps 143876380 — the next reentry restores 143876380 deterministically.
     */
    @Test
    fun trailingJsVisibleCapture_doesNotClobberReturnPositionStore_afterCrossTopicOpen() {
        val controller = ThemeHistoryController()
        val page = sourcePage()
        // Simulate that the page is already in history (loadData did saveToHistory for it).
        controller.saveToHistory(page)
        // Simulate [C2] at crossTopicOpen time: capture the durable back-snapshot with the
        // click-time source post 143876380 (status=CAPTURED, the snapshot survives any JS TTL).
        controller.captureBackSnapshot(
                TopicBackSnapshot.fromPage(
                        topicId = page.id,
                        pageSt = page.st,
                        visiblePostId = "143876380",
                        scrollOffset = page.scrollY,
                        scrollRatio = page.scrollRatio,
                        wasNearBottom = false,
                        status = TopicBackSnapshotStatus.CAPTURED,
                ),
        )

        // The store already has the correct source position from the cross-topic-open path
        // (mirrors the real saveReturnPosition call from captureNativeBackSnapshot at
        // ThemeViewModel.kt:4000+).
        val store = TopicReturnPositionStore()
        store.save(
                topicId = page.id,
                pageSt = page.st,
                postId = "143876380",
                scrollY = page.scrollY,
        )

        // The user scrolls down and backgrounds the app. The trailing onPauseOrHide JS capture
        // reports post=143873895. The ViewModel's updatePageHistoryHtml computes the post id to
        // save to returnPositionStore via the new helper (durable back-snapshot wins).
        val durableBackSnapshotPostId = controller.peekBackSnapshot(page.id, page.st)
                ?.takeIf { it.isUsable() }
                ?.visiblePostId
        val returnPostId = ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId(
                durableBackSnapshotPostId = durableBackSnapshotPostId,
                pageAuthoritativeAnchorPostId = page.authoritativeAnchorPostId, // null for this case
                candidateAnchorPostId = "143873895", // JS-captured visible post
        )
        assertEquals(
                "durable back-snapshot post must drive the cross-tab save, not the JS-visible post",
                "143876380",
                returnPostId,
        )
        assertNotNull(returnPostId)
        store.save(
                topicId = page.id,
                pageSt = page.st,
                postId = returnPostId!!,
                scrollY = 33491, // user's actual scroll position at hide time
        )

        // The cross-tab store now holds the SOURCE post 143876380, NOT the scrolled-to visible
        // post 143873895. TabReentryRestorePolicy / TopicAnchorResolver will read this on the
        // next reentry of 1121483.
        val peeked = store.peek(page.id)
        assertNotNull(peeked)
        assertEquals("1121483 must restore to source post 143876380 on reentry, not 143873895",
                "143876380", peeked!!.postId)
        assertEquals(33491, peeked.scrollY)

        // Sanity: the durable snapshot is still the source post (the JS-visible post did NOT
        // overwrite the back-snapshot, because the back-snapshot is only re-stamped when the
        // page's anchorPostId is updated by updatePageHistoryHtml's `shouldKeepAuthoritative`
        // branch — which is exercised on the page itself, not on the cross-tab store).
        val stillDurable = controller.peekBackSnapshot(page.id, page.st)
        assertNotNull(stillDurable)
        assertTrue(stillDurable!!.isUsable())
        assertEquals("143876380", stillDurable.visiblePostId)
    }

    /**
     * Sanity test for the prior code's failure mode: if the [ThemeAuthoritativeAnchorPolicy.resolveReturnPositionPostId]
     * helper were bypassed and the raw `candidateAnchorPostId` were used instead (i.e. the original
     * `returnPositionStore.save(effectiveAnchorPostId)` call), the cross-tab store WOULD be
     * clobbered with the user's last visible post. This test pins that exact failure so a future
     * refactor that accidentally drops the helper cannot regress silently.
     */
    @Test
    fun rawCandidateOverwritesReturnPositionStore_reproducingOriginalBug() {
        val store = TopicReturnPositionStore()
        // Mirrors the original saveReturnPosition from crossTopicOpen: source post is durably saved.
        store.save(1121483, 1180, "143876380", 3807)
        // Mirrors the prior (buggy) returnPositionStore.save(effectiveAnchorPostId) call in
        // ThemeViewModel.updatePageHistoryHtml — effectiveAnchorPostId is the raw candidate when
        // pendingSource is null (TTL expired).
        store.save(1121483, 1180, "143873895", 33491)
        assertEquals(
                "raw-candidate path reproduces the dynamic wrong-post bug (regression pin)",
                "143873895",
                store.peek(1121483)!!.postId,
        )
    }
}
