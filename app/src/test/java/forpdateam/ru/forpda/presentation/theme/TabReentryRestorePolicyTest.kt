package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-back anchor loss (log 24_06-23-12-50, lines ~10543-11942): once a Theme tab's in-tab
 * history is exhausted the next BACK is consumed by the TabNavigator, which RE-OPENS the topic via
 * the `list_read_use_getlastpost` (READ_RESUME) resolver instead of RESTORING the tab's saved
 * position. That fresh getlastpost lands on the server last-read bookmark (1121483 st=1260
 * #entry143992836, page 64) rather than the post the user was on (st=1180 #entry143876380, page 60).
 *
 * ThemeViewModel cannot be instantiated on the JVM (Hilt deps), so this mirrors the exact decision
 * [ThemeViewModel.loadUrl] delegates to: given the resolver output + a saved session position,
 * should the open be overridden with a clean back-restore URL? Strategy matches ThemeBackNavigationTest /
 * CrossTopicBackRestoreTest.
 */
class TabReentryRestorePolicyTest {

    private fun readResumeResolution() = TopicOpenResolution(
            url = "https://4pda.to/forum/index.php?showtopic=1121483&view=getlastpost",
            targetType = TopicOpenTargetType.READ_RESUME,
            reason = "list_read_use_getlastpost",
            suppressScrollRestore = true,
    )

    private fun savedTopicAPosition() = TopicReturnPositionStore.Position(
            topicId = 1121483,
            pageSt = 1180,
            postId = "143876380",
            scrollY = 10099,
    )

    @Test
    fun reReadResume_withSavedPosition_overridesGetlastpostWithCleanRestoreUrl() {
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = readResumeResolution(),
                saved = savedTopicAPosition(),
        )
        assertTrue("getlastpost re-open of a saved topic must be overridden", restore != null)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                restore!!.url,
        )
        // Must NOT re-open at the server last-read bookmark.
        assertFalse(restore.url.contains("getlastpost"))
        assertFalse(restore.url.contains("getnewpost"))
        assertEquals(1180, restore.position.pageSt)
        assertEquals("143876380", restore.position.postId)
    }

    @Test
    fun firstOpen_noSavedPosition_keepsResolverGetlastpost() {
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = readResumeResolution(),
                saved = null,
        )
        assertNull("genuine first open must fall through to the LAST_UNREAD/getlastpost resolver", restore)
    }

    @Test
    fun explicitPostOpen_isNeverOverridden_evenWithSavedPosition() {
        val explicit = TopicOpenResolution(
                url = "https://4pda.to/forum/index.php?showtopic=1121483&view=findpost&p=143999999",
                targetType = TopicOpenTargetType.EXPLICIT_POST,
                reason = "explicit_post",
        )
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = explicit,
                saved = savedTopicAPosition(),
        )
        assertNull("an explicit post open must land on the requested post, not the saved one", restore)
    }

    @Test
    fun pagination_isNeverOverridden() {
        val pagination = TopicOpenResolution(
                url = "https://4pda.to/forum/index.php?showtopic=1121483&st=1200",
                targetType = TopicOpenTargetType.EXPLICIT_PAGE,
                reason = "explicit_page",
        )
        assertNull(
                TabReentryRestorePolicy.resolveRestore(1121483, pagination, savedTopicAPosition()),
        )
    }

    @Test
    fun savedPositionForDifferentTopic_isIgnored() {
        val otherTopicSaved = TopicReturnPositionStore.Position(
                topicId = 239158,
                pageSt = 0,
                postId = "100000001",
                scrollY = 0,
        )
        assertNull(
                TabReentryRestorePolicy.resolveRestore(1121483, readResumeResolution(), otherTopicSaved),
        )
    }

    @Test
    fun savedPositionWithoutPostId_doesNotOverride() {
        val noPost = TopicReturnPositionStore.Position(
                topicId = 1121483,
                pageSt = 1180,
                postId = null,
                scrollY = 10099,
        )
        assertNull(
                "a bare page-top save must not override a server bookmark with an anchorless restore",
                TabReentryRestorePolicy.resolveRestore(1121483, readResumeResolution(), noPost),
        )
    }

    private fun markedUnreadResolution() = TopicOpenResolution(
            url = "https://4pda.to/forum/index.php?showtopic=1121483&view=getnewpost",
            targetType = TopicOpenTargetType.SETTING_LAST_UNREAD,
            reason = "list_marked_unread_use_getnewpost",
            suppressScrollRestore = true,
    )

    /**
     * A NON-fresh re-entry (genuine tab re-entry / back) of an unread topic may still restore the
     * saved in-progress position — the user is resuming where they were within the session.
     */
    @Test
    fun markedUnreadReentry_nonFresh_withSavedPosition_isRestored() {
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = markedUnreadResolution(),
                saved = savedTopicAPosition(),
                isFreshOpen = false,
        )
        assertTrue(restore != null)
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                restore!!.url,
        )
    }

    /**
     * Stuck-on-already-read fix (log 25_06-14-34-48, topic 1103268, post 143998164): a FRESH
     * favorites open of a still-UNREAD topic must resolve to the server first-unread post
     * (getnewpost) and must NOT be overridden by a saved in-progress scroll position that points at
     * an already-read post. Even with a saved position, the override is skipped for fresh unread opens.
     */
    @Test
    fun freshFavoritesOpen_ofUnreadTopic_isNeverOverriddenBySavedPosition() {
        val staleAlreadyRead = TopicReturnPositionStore.Position(
                topicId = 1103268,
                pageSt = 26320,
                postId = "143998164",
                scrollY = 6736,
        )
        val serverUnread = TopicOpenResolution(
                url = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                targetType = TopicOpenTargetType.SERVER_UNREAD_FALLBACK,
                reason = "server_unread_url_from_list",
                suppressScrollRestore = true,
        )
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1103268,
                resolution = serverUnread,
                saved = staleAlreadyRead,
                isFreshOpen = true,
        )
        assertNull("a fresh unread favorites open must honor the server first-unread target", restore)
    }

    /** Repeated fresh unread opens (3+) must EACH defer to the server first-unread, never stick. */
    @Test
    fun repeatedFreshUnreadOpens_neverStickOnSavedAlreadyReadPost() {
        val store = TopicReturnPositionStore()
        val serverUnread = TopicOpenResolution(
                url = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                targetType = TopicOpenTargetType.SERVER_UNREAD_FALLBACK,
                reason = "server_unread_url_from_list",
                suppressScrollRestore = true,
        )
        repeat(3) {
            // Each leave records a drifted-up mid-page already-read post.
            store.save(topicId = 1103268, pageSt = 26320, postId = "143998164", scrollY = 6736)
            val restore = TabReentryRestorePolicy.resolveRestore(
                    topicId = 1103268,
                    resolution = serverUnread,
                    saved = store.peek(1103268),
                    isFreshOpen = true,
            )
            assertNull("fresh unread open #$it must not stick on 143998164", restore)
        }
    }

    /**
     * A FRESH open of a READ topic must honor the server getlastpost target — NOT a saved position.
     *
     * Device logs 26_06-10-30 / 26_06-10-34: restoring the saved position on a fresh favorites tap
     * walked the user backward through the topic (HYBRID top-insert drifted the saved post earlier on
     * each restore→leave cycle). A fresh open of a read topic now always lands on the true last post;
     * genuine "resume where I was" is a back action (isFreshOpen=false / TAB_REENTRY), not a fresh tap.
     */
    @Test
    fun freshOpen_ofReadTopic_usesServerTarget_neverRestoresSavedPosition() {
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = readResumeResolution(),
                saved = savedTopicAPosition(),
                isFreshOpen = true,
        )
        assertNull("a fresh read-topic open must honor server getlastpost, not a saved position", restore)
    }

    /**
     * End-to-end multi-back across a tab boundary, mirroring the device log:
     *  1. The user viewed Topic A at st=1180 #entry143876380 (saved to the cross-tab store on the
     *     in-tab scroll settle).
     *  2. The in-tab history is exhausted; the next BACK is consumed by the TabNavigator, which
     *     re-shows Topic A's tab and the resolver produces a `list_read_use_getlastpost` re-open.
     *  3. The policy overrides that fresh getlastpost with the saved st=1180 #entry143876380 — the
     *     original post — instead of the server last-read bookmark (st=1260 #entry143992836).
     */
    @Test
    fun multiBack_acrossTabBoundary_restoresOriginalAnchor_notServerBookmark() {
        val store = TopicReturnPositionStore()
        // Step 1: in-tab scroll settle persists the real viewed position.
        store.save(topicId = 1121483, pageSt = 1180, postId = "143876380", scrollY = 10099)

        // Step 2/3: tab re-entry resolves to getlastpost; policy consults the store.
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = readResumeResolution(),
                saved = store.peek(1121483),
        )

        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                restore!!.url,
        )
        // Regression assertion: the random-post bookmark (st=1260 / page 64) must never be the target.
        assertFalse(restore.url.contains("st=1260"))
        assertFalse(restore.url.contains("143992836"))
    }

    /**
     * Repeated-open lifecycle (log 25_06-10-52-43, topic 1103268): a still-unread topic is opened to
     * its first-unread post; on leave it is marked READ and the store is sealed. Every subsequent
     * fresh re-open must therefore fall through to the server getlastpost bookmark — the policy must
     * NOT override with a drifted-up "scrolled to top" snapshot that previously caused the stick on
     * 143999521.
     */
    @Test
    fun afterMarkRead_repeatedReopens_neverRestoreStaleSnapshot_alwaysServerBookmark() {
        val store = TopicReturnPositionStore()
        val topicId = 1103268

        // Open #1: topic finished -> mark read seals the store (and any pending save is dropped).
        store.markRead(topicId)

        // Open #2: user re-reads from the top of the last page and leaves; the store ignores the save.
        store.save(topicId = topicId, pageSt = 26340, postId = "143999521", scrollY = 20794)
        val openTwo = TabReentryRestorePolicy.resolveRestore(
                topicId = topicId,
                resolution = TopicOpenResolution(
                        url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getlastpost",
                        targetType = TopicOpenTargetType.READ_RESUME,
                        reason = "list_read_use_getlastpost",
                        suppressScrollRestore = true,
                ),
                saved = store.peek(topicId),
        )
        assertNull("a sealed read topic must defer to the server getlastpost bookmark", openTwo)

        // Open #3+: still no override -> the stale 143999521 never hijacks the resolution.
        store.save(topicId = topicId, pageSt = 26340, postId = "143999521", scrollY = 16000)
        val openThree = TabReentryRestorePolicy.resolveRestore(
                topicId = topicId,
                resolution = TopicOpenResolution(
                        url = "https://4pda.to/forum/index.php?showtopic=$topicId&view=getlastpost",
                        targetType = TopicOpenTargetType.READ_RESUME,
                        reason = "list_read_use_getlastpost",
                        suppressScrollRestore = true,
                ),
                saved = store.peek(topicId),
        )
        assertNull("the 3rd open must NOT stick on 143999521", openThree)
    }

    /**
     * Regression guard for the documented multi-back scenario: a topic that was NOT marked read keeps
     * its restorable position, so tab re-entry still lands on the original post (not the server
     * bookmark). This must not be broken by the read-seal added for the stuck-anchor fix.
     */
    @Test
    fun unsealedTopic_stillRestoresSavedPosition_forTabReentry() {
        val store = TopicReturnPositionStore()
        store.save(topicId = 1121483, pageSt = 1180, postId = "143876380", scrollY = 10099)
        val restore = TabReentryRestorePolicy.resolveRestore(
                topicId = 1121483,
                resolution = readResumeResolution(),
                saved = store.peek(1121483),
        )
        assertEquals(
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1180#entry143876380",
                restore!!.url,
        )
    }

    @Test
    fun isServerBookmarkReopen_classifiesReasonsAndTypes() {
        assertTrue(TabReentryRestorePolicy.isServerBookmarkReopen(readResumeResolution()))
        assertFalse(
                TabReentryRestorePolicy.isServerBookmarkReopen(
                        TopicOpenResolution(
                                url = "u",
                                targetType = TopicOpenTargetType.EXPLICIT_POST,
                                reason = "explicit_post",
                        ),
                ),
        )
    }
}
