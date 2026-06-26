package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.model.repository.theme.TopicReturnPositionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicAnchorResolverTest {

    private val TOPIC = 1103268

    private fun unreadTarget() = TopicAnchorResolver.ServerTarget(
            url = "https://4pda.to/forum/index.php?showtopic=$TOPIC&view=getnewpost",
            type = TopicOpenTargetType.SERVER_UNREAD_FALLBACK,
            reason = "list_marked_unread_use_getnewpost",
    )

    private fun readTarget() = TopicAnchorResolver.ServerTarget(
            url = "https://4pda.to/forum/index.php?showtopic=$TOPIC&view=getlastpost",
            type = TopicOpenTargetType.READ_RESUME,
            reason = "list_read_use_getlastpost",
    )

    private fun explicitTarget() = TopicAnchorResolver.ServerTarget(
            url = "https://4pda.to/forum/index.php?showtopic=$TOPIC&view=findpost&p=143998164",
            type = TopicOpenTargetType.EXPLICIT_POST,
            reason = "explicit_post",
    )

    /** Mode B (global setting "open first page"): server target is the plain first page, no getnewpost. */
    private fun firstPageTarget() = TopicAnchorResolver.ServerTarget(
            url = "https://4pda.to/forum/index.php?showtopic=$TOPIC",
            type = TopicOpenTargetType.SETTING_FIRST_PAGE,
            reason = "setting_first_page",
    )

    private fun savedAt(post: String, st: Int = 26580) = TopicReturnPositionStore.Position(
            topicId = TOPIC,
            pageSt = st,
            postId = post,
            scrollY = 1200,
    )

    private fun input(
            intent: TopicAnchorResolver.OpenIntentKind,
            target: TopicAnchorResolver.ServerTarget,
            saved: TopicReturnPositionStore.Position? = null,
            readState: TopicAnchorResolver.ReadState = TopicAnchorResolver.ReadState.UNKNOWN,
            topicId: Int? = TOPIC,
    ) = TopicAnchorResolver.Input(
            topicId = topicId,
            openIntent = intent,
            readState = readState,
            serverTarget = target,
            savedReturnPosition = saved,
    )

    // ---- Plain in-topic link open (device log 25_06-22-18-38, topic 239158): DETERMINISTIC ----

    private val TOPIC_239158 = 239158

    /** The SETTING_LAST_UNREAD getnewpost target the resolver produced for the plain 239158 link. */
    private fun plainLinkUnreadTarget() = TopicAnchorResolver.ServerTarget(
            url = "https://4pda.to/forum/index.php?showtopic=$TOPIC_239158&view=getnewpost",
            type = TopicOpenTargetType.SETTING_LAST_UNREAD,
            reason = "added_getnewpost",
    )

    /**
     * Device log 25_06-22-18-38_133.log: tapping a PLAIN topic link `showtopic=239158` (no
     * `&view=findpost&p=`) from inside another topic resolves DETERMINISTICALLY to the server
     * `view=getnewpost` target every time (L797/L3128/L6607/L9799 all show identical
     * `resolverSelectedTarget=SETTING_LAST_UNREAD resolverReason=added_getnewpost
     * resolvedUrl=...showtopic=239158&view=getnewpost`). A stale locally-saved scroll position
     * (e.g. the post 143971747 the user had previously scrolled to INSIDE 239158, L3503/L3880)
     * must NEVER override the fresh-link server target. This test proves the open is stable: the
     * resolver returns the server getnewpost url regardless of any saved return position.
     */
    @Test
    fun plainInTopicLinkOpen_239158_isDeterministicServerGetNewPost_ignoringStaleSaved() {
        val target = plainLinkUnreadTarget()
        // A stale saved position pointing at the post the user previously scrolled to inside 239158.
        val staleSaved = TopicReturnPositionStore.Position(
                topicId = TOPIC_239158,
                pageSt = 15900,
                postId = "143971747",
                scrollY = 21807,
        )
        val decision = TopicAnchorResolver.resolve(
                TopicAnchorResolver.Input(
                        topicId = TOPIC_239158,
                        openIntent = TopicAnchorResolver.OpenIntentKind.FRESH_LIST,
                        readState = TopicAnchorResolver.ReadState.UNREAD,
                        serverTarget = target,
                        savedReturnPosition = staleSaved,
                ),
        )
        assertTrue(
                "a fresh plain-topic link open must use the server getnewpost target, not a saved position",
                decision is TopicAnchorResolver.Decision.UseServerTarget,
        )
        assertEquals("fresh_list_unread_uses_server_first_unread", decision.reason)
        assertEquals(
                "the plain 239158 link must open at the server getnewpost url deterministically",
                target.url,
                (decision as TopicAnchorResolver.Decision.UseServerTarget).url,
        )
    }

    /**
     * Determinism across repeated opens: the same plain link resolves to the SAME server target on
     * every open, independent of whatever local saved position drifted in between (the user reads
     * 239158, the saved post moves). The resolver decision is pure and stable.
     */
    @Test
    fun plainInTopicLinkOpen_239158_sameTargetAcrossRepeatedOpens() {
        val target = plainLinkUnreadTarget()
        val firstOpen = TopicAnchorResolver.resolve(
                TopicAnchorResolver.Input(
                        topicId = TOPIC_239158,
                        openIntent = TopicAnchorResolver.OpenIntentKind.FRESH_LIST,
                        readState = TopicAnchorResolver.ReadState.UNREAD,
                        serverTarget = target,
                        savedReturnPosition = null,
                ),
        )
        // Second open: a stale saved position now exists (user had scrolled inside 239158).
        val secondOpen = TopicAnchorResolver.resolve(
                TopicAnchorResolver.Input(
                        topicId = TOPIC_239158,
                        openIntent = TopicAnchorResolver.OpenIntentKind.FRESH_LIST,
                        readState = TopicAnchorResolver.ReadState.UNREAD,
                        serverTarget = target,
                        savedReturnPosition = TopicReturnPositionStore.Position(
                                topicId = TOPIC_239158,
                                pageSt = 15900,
                                postId = "143971747",
                                scrollY = 21807,
                        ),
                ),
        )
        assertTrue(firstOpen is TopicAnchorResolver.Decision.UseServerTarget)
        assertTrue(secondOpen is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals(
                "the same plain link must open at the same server target across repeated opens",
                (firstOpen as TopicAnchorResolver.Decision.UseServerTarget).url,
                (secondOpen as TopicAnchorResolver.Decision.UseServerTarget).url,
        )
    }

    // ---- FRESH_LIST x UNREAD: ALWAYS server first-unread, never a saved snapshot ----

    @Test
    fun freshList_unread_usesServerFirstUnread_ignoringSaved() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.FRESH_LIST, unreadTarget(), saved = savedAt("143998164"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("fresh_list_unread_uses_server_first_unread", d.reason)
        assertEquals(unreadTarget().url, (d as TopicAnchorResolver.Decision.UseServerTarget).url)
    }

    @Test
    fun freshList_unread_byReadState_usesServerFirstUnread() {
        val d = TopicAnchorResolver.resolve(
                input(
                        TopicAnchorResolver.OpenIntentKind.FRESH_LIST,
                        readTarget(),
                        saved = savedAt("143998164"),
                        readState = TopicAnchorResolver.ReadState.UNREAD,
                )
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("fresh_list_unread_uses_server_first_unread", d.reason)
    }

    @Test
    fun repeatedFreshUnreadOpens_neverStickOnSavedPost() {
        // 3+ consecutive fresh opens of a still-unread topic must each resolve to the server
        // first-unread, never the stale saved post (the 143998164 / 143999521 stick class of bugs).
        repeat(5) {
            val d = TopicAnchorResolver.resolve(
                    input(TopicAnchorResolver.OpenIntentKind.FRESH_LIST, unreadTarget(), saved = savedAt("143998164"))
            )
            assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
            assertEquals(unreadTarget().url, (d as TopicAnchorResolver.Decision.UseServerTarget).url)
        }
    }

    // ---- Global unread-open setting Mode B (FIRST_PAGE): server target is honored, not forced unread ----

    @Test
    fun freshList_firstPageSetting_usesServerFirstPage_notForcedUnread() {
        // Mode B: the global "open first page" setting resolves to SETTING_FIRST_PAGE (st=0, no
        // getnewpost). The anchor resolver must pass that server target through unchanged — it must
        // NOT treat the open as unread and must NOT restore a saved snapshot.
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.FRESH_LIST, firstPageTarget(), saved = savedAt("143998164"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        d as TopicAnchorResolver.Decision.UseServerTarget
        assertEquals(firstPageTarget().url, d.url)
        assertEquals("fresh_list_read_uses_server_last_post", d.reason)
        assertTrue("Mode B must not route through the unread branch", !d.url.contains("getnewpost"))
    }

    @Test
    fun freshList_firstPageSetting_isNotClassifiedAsServerUnread() {
        // Guard: SETTING_FIRST_PAGE is not a server-unread target, so the FRESH_LIST+UNREAD rule
        // (which would force getnewpost) never fires for Mode B.
        assertTrue(!TopicAnchorResolver.isServerUnreadReopen(firstPageTarget()))
        assertTrue(!TopicAnchorResolver.isServerBookmarkReopen(firstPageTarget()))
    }

    // ---- FRESH_LIST x READ ----

    @Test
    fun freshList_read_noSaved_usesServerLastPost() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.FRESH_LIST, readTarget())
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("fresh_list_read_uses_server_last_post", d.reason)
    }

    @Test
    fun freshList_read_withSaved_usesServerLastPost_neverSaved() {
        // A fresh list/favorites tap of a READ topic ALWAYS honors the user's open-target setting
        // (read → server getlastpost). It must NEVER be hijacked by a saved in-progress position.
        //
        // Device logs 26_06-10-30 / 26_06-10-34 ("fresh open lands on random/earlier posts"): the
        // saved post is the user's last VISIBLE post, which HYBRID top-insertion drifts EARLIER on
        // each restore→leave cycle (143876586 → 143873102 → 143860995), walking the user backward
        // through the topic. The read-seal only fires at the LAST page, so a mid-read already-read
        // topic is never sealed and the drift loops forever. Genuine resume is a back action
        // (TAB_REENTRY / native snapshots), not this fresh-open branch.
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.FRESH_LIST, readTarget(), saved = savedAt("143876380", st = 23600))
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        d as TopicAnchorResolver.Decision.UseServerTarget
        assertEquals("fresh_list_read_uses_server_last_post", d.reason)
        assertEquals(readTarget().url, d.url)
    }

    @Test
    fun repeatedFreshReadOpens_neverDriftToEarlierPost() {
        // Regression for the backward-drift loop: every fresh open of a read topic lands on the same
        // server last-post target, regardless of whatever earlier post the store drifted to.
        val drifted = listOf("143876586", "143873102", "143860995", "143861523")
        drifted.forEach { post ->
            val d = TopicAnchorResolver.resolve(
                    input(TopicAnchorResolver.OpenIntentKind.FRESH_LIST, readTarget(), saved = savedAt(post))
            )
            assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
            assertEquals(readTarget().url, (d as TopicAnchorResolver.Decision.UseServerTarget).url)
        }
    }

    // ---- TAB_REENTRY: restore saved in-progress position ----

    @Test
    fun tabReentry_withSaved_restores() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.TAB_REENTRY, readTarget(), saved = savedAt("143876380"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.RestoreSavedPosition)
    }

    @Test
    fun tabReentry_unreadTarget_withSaved_stillRestores() {
        // Genuine re-entry within the session resumes even for an unread topic (unlike a fresh tap).
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.TAB_REENTRY, unreadTarget(), saved = savedAt("143876380"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.RestoreSavedPosition)
    }

    @Test
    fun tabReentry_noSaved_usesServerTarget() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.TAB_REENTRY, readTarget())
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("tab_reentry_no_saved_position_uses_server_target", d.reason)
    }

    // ---- EXPLICIT_LINK + history-owned BACK kinds: always server target, never saved-restore ----

    @Test
    fun explicitLink_usesServerTarget_evenWithSaved() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.EXPLICIT_LINK, explicitTarget(), saved = savedAt("143876380"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("explicit_link_uses_server_target", d.reason)
        assertEquals(explicitTarget().url, (d as TopicAnchorResolver.Decision.UseServerTarget).url)
    }

    @Test
    fun inTabBack_usesServerTarget_handledByHistory() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.IN_TAB_BACK, readTarget(), saved = savedAt("143876380"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("in_tab_back_handled_by_history", d.reason)
    }

    @Test
    fun crossTopicBack_usesServerTarget_handledByNativeSnapshot() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.CROSS_TOPIC_BACK, readTarget(), saved = savedAt("143876380"))
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("cross_topic_back_handled_by_native_snapshot", d.reason)
    }

    // ---- edge cases ----

    @Test
    fun noTopicId_usesServerTarget() {
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.TAB_REENTRY, readTarget(), saved = savedAt("143876380"), topicId = null)
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
        assertEquals("server_target_no_topic_id", d.reason)
    }

    @Test
    fun tabReentry_savedForDifferentTopic_doesNotRestore() {
        val saved = TopicReturnPositionStore.Position(topicId = 999, pageSt = 0, postId = "1", scrollY = 0)
        val d = TopicAnchorResolver.resolve(
                input(TopicAnchorResolver.OpenIntentKind.TAB_REENTRY, readTarget(), saved = saved)
        )
        assertTrue(d is TopicAnchorResolver.Decision.UseServerTarget)
    }

    @Test
    fun classifyOpenIntent_mapsSignals() {
        assertEquals(
                TopicAnchorResolver.OpenIntentKind.EXPLICIT_LINK,
                TopicAnchorResolver.classifyOpenIntent(isExplicitLink = true, isFreshOpenIntent = true, isRestoreIntent = false),
        )
        assertEquals(
                TopicAnchorResolver.OpenIntentKind.IN_TAB_BACK,
                TopicAnchorResolver.classifyOpenIntent(isExplicitLink = false, isFreshOpenIntent = false, isRestoreIntent = true),
        )
        assertEquals(
                TopicAnchorResolver.OpenIntentKind.FRESH_LIST,
                TopicAnchorResolver.classifyOpenIntent(isExplicitLink = false, isFreshOpenIntent = true, isRestoreIntent = false),
        )
        // An unclassified URL-open defaults to FRESH_LIST (the audited-correct safe default).
        assertEquals(
                TopicAnchorResolver.OpenIntentKind.FRESH_LIST,
                TopicAnchorResolver.classifyOpenIntent(isExplicitLink = false, isFreshOpenIntent = false, isRestoreIntent = false),
        )
    }
}
