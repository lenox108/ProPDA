package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.common.Preferences as AppPreferences
import forpdateam.ru.forpda.common.TopicOpenListHints
import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi
import forpdateam.ru.forpda.presentation.favorites.FavoritesTopicNavigationPolicy
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.FRESH_FAVORITES
import forpdateam.ru.forpda.presentation.theme.TopicOpenIntentClassifier.EXPLICIT_POST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unified matrix for list read/unread open — covers device log regressions (1103268, 1121483, …).
 */
class TopicUnreadOpenPolicyTest {

    private val topic1103268 = 1103268
    private val topic1121483 = 1121483
    private val base = "https://4pda.to/forum/index.php?showtopic="

    // --- Navigation URL ---

    @Test
    fun unreadListRow_resolvesGetNewPost() {
        val hints = unreadHints(topic1103268)
        val resolution = resolveFavorite(topic1103268, hints)
        assertTrue(resolution.url.contains("view=getnewpost"))
        assertEquals(TopicOpenTargetType.SERVER_UNREAD_FALLBACK, resolution.targetType)
    }

    @Test
    fun readListRow_resolvesGetLastPost_log24_06_14() {
        // Log 24_06-14-15: a fully-read favorites row under LAST_UNREAD must
        // use `view=getlastpost` (server last-read bookmark) so the user
        // resumes at the actual last-read post and the highlight lands on
        // it. The previous `getnewpost` redirect resolved to the
        // bottom-bookmark of an already-read topic with no first-unread, so
        // the user was stranded on the last page top with no highlight.
        val hints = readHints(topic1121483)
        val resolution = resolveFavorite(topic1121483, hints)
        assertTrue(resolution.url.contains("view=getlastpost"))
        assertFalse(resolution.url.contains("view=getnewpost"))
        assertEquals("list_read_use_getlastpost", resolution.reason)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun readListRow_log24_06_14_navigatesToLastReadBookmark() {
        // Device log 24_06-14-15 (regression of log 13_06-16-24-38 /
        // 1121483): a fully-read favorites row under LAST_UNREAD must use
        // `view=getlastpost` so the user resumes at the server last-read
        // post. The previous contract was `view=getnewpost` which the
        // server redirected to the all-read bottom bookmark without
        // producing a first-unread target — leaving the user stranded on
        // the last page top with no highlight.
        val hints = readHints(topic1121483)
        val resolution = resolveFavorite(topic1121483, hints)
        assertTrue(resolution.url.contains("view=getlastpost"))
        assertFalse(resolution.url.contains("view=getnewpost"))
        assertEquals("list_read_use_getlastpost", resolution.reason)
        assertTrue(resolution.suppressScrollRestore)
    }

    @Test
    fun readListRow_usesGetLastPost() {
        val resolution = resolveFavorite(
                topic1103268,
                readHints(topic1103268)
        )
        assertFalse(resolution.reason == "list_read_no_unread_hint")
        assertTrue("must use view=getlastpost for read rows", resolution.url.contains("view=getlastpost"))
        assertEquals("list_read_use_getlastpost", resolution.reason)
    }

    @Test
    fun staleIsNew_readStateUnread_stillGetNewPost() {
        val item = FavItem().apply {
            topicId = 456
            isNew = false
            readState = FavoriteReadState.UNREAD
            unreadPostCount = 2
        }
        val hints = FavoritesTopicNavigationPolicy.buildListHints(item)
        assertTrue(hints.topicMarkedUnread)
        val resolution = resolveFavorite(456, hints)
        assertTrue(resolution.url.contains("view=getnewpost"))
    }

    // --- Parser hint lifecycle ---

    @Test
    fun parserTrustsListUnread_usesTopicMarkedUnread_notUrlPresenceAlone() {
        val hints = TopicOpenListHints(topicMarkedUnread = true)
        assertTrue(
                TopicUnreadOpenPolicy.parserTrustsListUnread(
                        hints,
                        "${base}$topic1103268&view=getnewpost"
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.parserTrustsListUnread(
                        hints,
                        "${base}$topic1103268&view=getlastpost"
                )
        )
    }

    @Test
    fun parserTrustsListUnread_falseWhenHintsClearedButUrlIsGetNewPost() {
        assertFalse(
                TopicUnreadOpenPolicy.parserTrustsListUnread(
                        null,
                        "${base}$topic1103268&view=getnewpost"
                )
        )
    }

    @Test
    fun parserTrustsGetNewPostUnread_trueForLastUnreadSettingEvenWhenListRead() {
        assertTrue(
                TopicUnreadOpenPolicy.parserTrustsGetNewPostUnread(
                        hints = null,
                        fetchUrl = "${base}$topic1121483&view=getnewpost",
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.parserTrustsGetNewPostUnread(
                        hints = null,
                        fetchUrl = "${base}$topic1121483&view=getnewpost",
                        setting = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                )
        )
    }

    @Test
    fun captureParserListUnreadHintForLoad_survivesLoadDataReset_log486() {
        val getNewPostUrl = "${base}$topic1103268&view=getnewpost"
        assertTrue(
                TopicUnreadOpenPolicy.captureParserListUnreadHintForLoad(
                        pendingHintFromResolve = true,
                        hints = null,
                        fetchUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                )
        )
        assertTrue(
                TopicUnreadOpenPolicy.captureParserListUnreadHintForLoad(
                        pendingHintFromResolve = false,
                        hints = readHints(topic1103268),
                        fetchUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Normal,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.captureParserListUnreadHintForLoad(
                        pendingHintFromResolve = false,
                        hints = readHints(topic1103268),
                        fetchUrl = getNewPostUrl,
                        loadAction = ThemeLoadAction.Refresh,
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                )
        )
    }

    @Test
    fun shouldPreserveUnreadTargetAfterLoad_findPostUpgradeAndLastUnreadGetNewPost() {
        val findPostUrl = "${base}$topic1103268&view=findpost&p=143807897"
        assertTrue(
                TopicUnreadOpenPolicy.shouldPreserveUnreadTargetAfterLoad(
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Normal,
                        parserListUnreadHint = true,
                        openedViaFindPost = true,
                        findPostUpgradeTraceMatches = true,
                        requestUrl = findPostUrl,
                        anchorPostId = "143807897",
                )
        )
        assertTrue(
                TopicUnreadOpenPolicy.shouldPreserveUnreadTargetAfterLoad(
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Normal,
                        parserListUnreadHint = true,
                        openedViaFindPost = false,
                        findPostUpgradeTraceMatches = false,
                        requestUrl = "${base}$topic1121483&view=getnewpost",
                        anchorPostId = "143803001",
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldPreserveUnreadTargetAfterLoad(
                        setting = AppPreferences.Main.TopicOpenTarget.FIRST_PAGE,
                        loadAction = ThemeLoadAction.Normal,
                        parserListUnreadHint = true,
                        openedViaFindPost = true,
                        findPostUpgradeTraceMatches = true,
                        requestUrl = findPostUrl,
                        anchorPostId = "143807897",
                )
        )
    }

    @Test
    fun shouldPreserveUnreadTargetAfterLoad_keepsAmbiguousBottomRedirectUnconfirmed() {
        assertFalse(
                TopicUnreadOpenPolicy.shouldPreserveUnreadTargetAfterLoad(
                        setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                        loadAction = ThemeLoadAction.Normal,
                        parserListUnreadHint = true,
                        openedViaFindPost = false,
                        findPostUpgradeTraceMatches = false,
                        requestUrl = "${base}$topic1121483&view=getnewpost",
                        anchorPostId = "143805431",
                        ambiguousBottomRedirect = true,
                )
        )
    }

    @Test
    fun anchor_logSession_1103268_lastUnreadSetting_bottomRedirectIsAmbiguous_log156() {
        val hatId = 135617646
        val firstUnreadId = 143807897
        val lastId = 143809388
        val middleIds = listOf(143808100, 143808200, 143809100, 143809300)
        val entryIds = listOf(hatId) + listOf(firstUnreadId) + middleIds + lastId
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1103268);</script>\n$html",
                finalUrl = "${base}$topic1103268&st=24380#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
                hatEntryIdToSkip = hatId,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
        assertFalse(resolution.bottomHashRejected)
    }

    @Test
    fun isStaleWarmGetNewPostPage_rejectsAllReadBottomPrefetch_log1121483() {
        val page = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            id = topic1121483
            hasUnreadTarget = false
            anchorPostId = "143805431"
        }
        val url = "${'$'}{base}${'$'}topic1121483&view=getnewpost"
        assertTrue(
                TopicUnreadOpenPolicy.isStaleWarmGetNewPostPage(
                        page,
                        url,
                        parserListUnreadHint = true,
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.isStaleWarmGetNewPostPage(
                        page,
                        url,
                        parserListUnreadHint = false,
                )
        )
        page.hasUnreadTarget = true
        assertFalse(
                TopicUnreadOpenPolicy.isStaleWarmGetNewPostPage(
                        page,
                        url,
                        parserListUnreadHint = true,
                )
        )
    }

    @Test
    fun isStaleWarmGetNewPostPage_rejectsOffPageAnchor_log1103268() {
        val page = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            id = topic1103268
            hasUnreadTarget = true
            anchorPostId = "135617646"
            posts.add(forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply { id = 143813742 })
        }
        val url = "${'$'}{base}${'$'}topic1103268&view=getnewpost"
        assertTrue(
                TopicUnreadOpenPolicy.isStaleWarmGetNewPostPage(page, url, parserListUnreadHint = true)
        )
    }

    @Test
    fun anchor_logSession_1121483_lastUnreadSetting_bottomRedirectIsAmbiguous_log156() {
        val hatId = 143179849
        val lastId = 143805431
        val middleIds = listOf(
                143803001, 143803002, 143803003, 143803004, 143803005,
                143803006, 143803007, 143803008, 143803009, 143803010,
                143803011, 143803012, 143803013,
        )
        val entryIds = listOf(hatId) + middleIds + lastId
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1121483);</script>\n$html",
                finalUrl = "${base}$topic1121483&st=1140#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
                hatEntryIdToSkip = hatId,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    // --- getnewpost anchor: list unread (device logs) ---

    @Test
    fun anchor_unreadListHint_log1103268_twoPosts_bottomRedirectIsAmbiguous() {
        val html = """
            <script>var topic_id = parseInt($topic1103268); var forum_id = parseInt(1);</script>
            <a name="entry135617646"></a><div class="post_container">hat</div>
            <a name="entry143801181"></a><div class="post_container">latest</div>
        """.trimIndent()
        val resolution = anchor(
                html = html,
                finalUrl = "${base}$topic1103268&st=24320#entry143801181",
                entryIds = listOf(135617646, 143801181),
                redirectHashId = 143801181,
                listUnreadHint = true,
                onLastTopicPage = true,
                hatEntryIdToSkip = 135617646,
        )
        assertNull(resolution.anchorEntry)
        assertFalse(resolution.hasUnreadTarget)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    @Test
    fun anchor_unreadListHint_log1103268_manyPosts_bottomRedirectIsAmbiguous() {
        val lastId = 143801118
        val entryIds = (0 until 21).map { 143801098 + it }
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1103268);</script>\n$html",
                finalUrl = "${base}$topic1103268&st=24300#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
        )
        assertNull(resolution.anchorEntry)
        assertFalse(resolution.hasUnreadTarget)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertTrue(resolution.ambiguousBottomRedirect)
        assertFalse(resolution.bottomHashRejected)
    }

    @Test
    fun anchor_unreadListHint_log1121483_manyPosts_bottomRedirectIsAmbiguous() {
        val topicId = topic1121483
        val hatId = 143179849
        val lastId = 143804585
        val entryIds = listOf(hatId) + (1 until 13).map { 143803000 + it } + lastId
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topicId);</script>\n$html",
                finalUrl = "${base}$topicId&st=1140#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
                hatEntryIdToSkip = hatId,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    @Test
    fun anchor_unreadListHint_log934059_bottomRedirectIsAmbiguous() {
        val topicId = 934059
        val firstId = 143804484
        val lastId = 143804497
        val entryIds = (0 until 14).map { firstId + it }
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topicId);</script>\n$html",
                finalUrl = "${base}$topicId&st=203600#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    @Test
    fun anchor_unreadListHint_log928862_trustsBottomRedirect() {
        val topicId = 928862
        val firstId = 143804162
        val lastId = 143804165
        val entryIds = listOf(firstId, firstId + 1, firstId + 2, lastId)
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topicId);</script>\n$html",
                finalUrl = "${base}$topicId&st=104160#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    @Test
    fun anchor_unreadListHint_twoContentPosts_bottomRedirectIsAmbiguous() {
        val html = """
            <script>var topic_id = parseInt(112900);</script>
            <a name="entry143801146"></a><div class="post_container">p1</div>
            <a name="entry143801147"></a><div class="post_container">p2</div>
        """.trimIndent()
        val resolution = anchor(
                html = html,
                finalUrl = "${base}112900&st=13280#entry143801147",
                entryIds = listOf(143801146, 143801147),
                redirectHashId = 143801147,
                listUnreadHint = true,
                onLastTopicPage = true,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    @Test
    fun anchor_withoutListHint_manyPostsBottomReject_usesNonBottomFallback() {
        val firstId = 143801098
        val lastId = 143801118
        val entryIds = (0 until 21).map { 143801098 + it }
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1103268);</script>\n$html",
                finalUrl = "${base}$topic1103268&st=24300#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = false,
                onLastTopicPage = false,
        )
        assertEquals("entry$firstId", resolution.anchorEntry)
        assertEquals("fallback_after_bottom_reject", resolution.reason)
        assertTrue(resolution.bottomHashRejected)
    }

    @Test
    fun anchor_logSession_1103268_fivePosts_bottomRedirectIsAmbiguous() {
        val entries = listOf(135617646, 143804664, 143804839, 143804939, 143805384)
        val html = entries.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1103268);</script>\n$html",
                finalUrl = "${base}$topic1103268&st=24360#entry143805384",
                entryIds = entries,
                redirectHashId = 143805384,
                listUnreadHint = true,
                onLastTopicPage = true,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
        val diagnostics = TopicUnreadOpenPolicy.buildAnchorDiagnostics(entries, 143805384, null)
        assertTrue(diagnostics.redirectIsBottomEntry)
        assertEquals(5, diagnostics.contentEntryCount)
    }


    @Test
    fun anchor_logSession_1121483_readRow_allReadBottomRedirect_log679() {
        val hatId = 143179849
        val lastId = 143805431
        val middleIds = listOf(
                143803001, 143803002, 143803003, 143803004, 143803005,
                143803006, 143803007, 143803008, 143803009, 143803010,
                143803011, 143803012, 143803013,
        )
        val entryIds = listOf(hatId) + middleIds + lastId
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1121483);</script>\n$html",
                finalUrl = "${base}$topic1121483&st=1140#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = false,
                onLastTopicPage = true,
                hatEntryIdToSkip = hatId,
        )
        assertEquals("entry$lastId", resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
    }


    @Test
    fun anchor_logSession_1121483_unreadListHint_bottomRedirectIsAmbiguous() {
        val hatId = 143179849
        val lastId = 143805431
        val middleIds = listOf(
                143803001, 143803002, 143803003, 143803004, 143803005,
                143803006, 143803007, 143803008, 143803009, 143803010,
                143803011, 143803012, 143803013,
        )
        val entryIds = listOf(hatId) + middleIds + lastId
        val html = entryIds.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1121483);</script>\n$html",
                finalUrl = "${base}$topic1121483&st=1140#entry$lastId",
                entryIds = entryIds,
                redirectHashId = lastId,
                listUnreadHint = true,
                onLastTopicPage = true,
                hatEntryIdToSkip = hatId,
        )
        assertNull(resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
        assertTrue(resolution.ambiguousBottomRedirect)
    }

    @Test
    fun buildAnchorDiagnostics_flagsBottomRedirectWithoutHtmlUnread() {
        val entryIds = listOf(143179849, 143804584, 143805431)
        val diagnostics = TopicUnreadOpenPolicy.buildAnchorDiagnostics(
                entryIds = entryIds,
                redirectHashId = 143805431,
                hatSkip = 143179849,
        )
        assertEquals(143179849, diagnostics.firstEntryId)
        assertEquals(143805431, diagnostics.lastEntryId)
        assertTrue(diagnostics.redirectIsBottomEntry)
        assertEquals(2, diagnostics.contentEntryCount)
    }

    @Test
    fun anchor_logSession_1103268_readRow_allReadBottomRedirect_log679() {
        val entries = listOf(135617646, 143804664, 143804839, 143804939, 143808466)
        val html = entries.joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val resolution = anchor(
                html = "<script>var topic_id = parseInt($topic1103268);</script>\n$html",
                finalUrl = "${base}$topic1103268&st=24380#entry143808466",
                entryIds = entries,
                redirectHashId = 143808466,
                listUnreadHint = false,
                onLastTopicPage = true,
        )
        assertEquals("entry143808466", resolution.anchorEntry)
        assertEquals("all_read_bottom_redirect", resolution.reason)
        assertFalse(resolution.hasUnreadTarget)
    }

    @Test
    fun anchor_withoutListHint_allReadBottomRedirect_hasUnreadTargetFalse_log1121483() {
        val html = """
            <script>var topic_id = parseInt($topic1121483);</script>
            <a name="entry143784670"></a><div class="post_container">read</div>
            <a name="entry143784679"></a><div class="post_container">latest</div>
        """.trimIndent()
        val resolution = anchor(
                html = html,
                finalUrl = "${base}$topic1121483&st=1140#entry143784679",
                entryIds = listOf(143784670, 143784679),
                redirectHashId = 143784679,
                listUnreadHint = false,
                onLastTopicPage = true,
        )
        assertEquals("entry143784679", resolution.anchorEntry)
        assertFalse(resolution.hasUnreadTarget)
        assertEquals("all_read_bottom_redirect", resolution.reason)
    }

    @Test
    fun anchor_withoutListHint_rejectsBottomHash_usesSecondEntry() {
        val html = """
            <script>var topic_id = parseInt(1);</script>
            <a name="entry200"></a><div class="post_container">old</div>
            <a name="entry201"></a><div class="post_container">next</div>
        """.trimIndent()
        val resolution = anchor(
                html = html,
                finalUrl = "${base}1&st=20#entry200",
                entryIds = listOf(200, 201),
                redirectHashId = 200,
                listUnreadHint = false,
                onLastTopicPage = false,
        )
        assertEquals("entry201", resolution.anchorEntry)
        assertTrue(resolution.hasUnreadTarget)
    }

    @Test
    fun anchor_htmlUnreadMarker_winsOverRedirectHash() {
        val html = """
            <script>var topic_id = parseInt(1);</script>
            <a name="entry100"></a><div class="post_container read">read</div>
            <div class="post_wrap unread"><a name="entry101"></a></div>
        """.trimIndent()
        val resolution = anchor(
                html = html,
                finalUrl = "${base}1&st=20#entry100",
                entryIds = listOf(100, 101),
                redirectHashId = 100,
                listUnreadHint = true,
                onLastTopicPage = false,
        )
        assertEquals("entry101", resolution.anchorEntry)
        assertEquals("html_unread_marker", resolution.reason)
    }

    @Test
    fun buildListHints_readRowWithGetNewPostInHref_upgradesToGetLastPost() {
        val hints = TopicUnreadOpenPolicy.buildListHints(
                topicId = 789,
                listingHref = "${base}789&view=getnewpost",
                topicMarkedUnread = false,
        )
        assertNull(hints.unreadUrlFromList)
        assertEquals("${base}789&view=getlastpost", hints.lastReadUrlFromList)
    }

    private fun unreadHints(topicId: Int) = TopicOpenListHints(
            unreadUrlFromList = "${base}$topicId&view=getnewpost",
            topicMarkedUnread = true,
    )

    private fun readHints(topicId: Int) = TopicOpenListHints(
            lastReadUrlFromList = "${base}$topicId&view=getlastpost",
            topicMarkedUnread = false,
    )

    private fun resolveFavorite(topicId: Int, hints: TopicOpenListHints): TopicOpenResolution =
            TopicOpenTargetResolver.resolve(
                    TopicOpenContext(
                            rawUrl = "${base}$topicId",
                            setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                            sourceScreen = "favorites",
                            openIntentRaw = FRESH_FAVORITES,
                            unreadUrlFromList = hints.unreadUrlFromList,
                            unreadPostIdFromList = hints.unreadPostIdFromList,
                            listTopicMarkedUnread = hints.topicMarkedUnread,
                            lastReadUrlFromList = hints.lastReadUrlFromList,
                    )
            )

    private fun anchor(
            html: String,
            finalUrl: String,
            entryIds: List<Int>,
            redirectHashId: Int?,
            listUnreadHint: Boolean,
            onLastTopicPage: Boolean,
            hatEntryIdToSkip: Int? = null,
    ): TopicUnreadOpenPolicy.AnchorResolution = TopicUnreadOpenPolicy.resolveGetNewPostAnchor(
            TopicUnreadOpenPolicy.GetNewPostAnchorContext(
                    html = html,
                    finalUrl = finalUrl,
                    entryIds = entryIds,
                    redirectHashId = redirectHashId,
                    hatEntryIdToSkip = hatEntryIdToSkip,
                    onLastTopicPage = onLastTopicPage,
                    listUnreadHint = listUnreadHint,
            )
    )

    // --- Phase 2.1 open-session kind ---

    @Test
    fun resolveOpenSessionKindAtResolve_explicitPost() {
        val context = TopicOpenContext(
                rawUrl = "https://4pda.to/forum/index.php?showtopic=1&view=findpost&p=42",
                setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                openIntentRaw = EXPLICIT_POST,
        )
        val resolution = TopicOpenTargetResolver.resolve(context)
        assertEquals(
                TopicUnreadOpenPolicy.TopicOpenSessionKind.EXPLICIT_POST,
                TopicUnreadOpenPolicy.resolveOpenSessionKindAtResolve(context, resolution),
        )
    }

    @Test
    fun resolveOpenSessionKindAtResolve_readListRowUsesGetLastPost() {
        // Log 24_06-14-15: a fully-read favorites row under LAST_UNREAD now
        // opens via getlastpost (server last-read bookmark), so the session
        // kind is READ_RESUME. The previous FIRST_UNREAD outcome was a
        // semantic mis-classification: the server's all-read bottom redirect
        // does not represent a first-unread intent.
        val context = TopicOpenContext(
                rawUrl = "${base}$topic1121483",
                setting = AppPreferences.Main.TopicOpenTarget.LAST_UNREAD,
                sourceScreen = "favorites",
                lastReadUrlFromList = "${base}$topic1121483&view=getlastpost",
        )
        val resolution = resolveFavorite(topic1121483, readHints(topic1121483))
        assertEquals(
                TopicUnreadOpenPolicy.TopicOpenSessionKind.READ_RESUME,
                TopicUnreadOpenPolicy.resolveOpenSessionKindAtResolve(context, resolution),
        )
    }

    @Test
    fun resolveOpenSessionKindFromPage_ambiguousAllRead_log1106099() {
        val page = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            ambiguousLastUnreadBottomRedirect = true
            hasUnreadTarget = false
        }
        assertEquals(
                TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                TopicUnreadOpenPolicy.resolveOpenSessionKindFromPage(
                        page,
                        "${base}1106099&view=getnewpost",
                        openFromUnreadListHint = true,
                ),
        )
    }

    @Test
    fun shouldSuppressHybridPreload_onlyAmbiguousAllRead() {
        assertTrue(
                TopicUnreadOpenPolicy.shouldSuppressHybridPreload(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressHybridPreload(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                )
        )
    }

    @Test
    fun shouldSuppressMarkReadForSession_onlyBottomRedirectBelowLastPage() {
        // Log 14_06-19: mark-read must FIRE for a fully-read topic opened on its last page
        // (AMBIGUOUS_ALL_READ / READ_RESUME). Suppression is only for a bottom-redirect bookmark
        // when the loaded page is NOT the final page (then it's a mid-topic bookmark, not a
        // read-completion).
        val ambiguousLastPage = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            ambiguousLastUnreadBottomRedirect = true
            pagination.current = 58
            pagination.all = 58
        }
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                        ambiguousLastPage,
                )
        )
        val ambiguousMidPage = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            ambiguousLastUnreadBottomRedirect = true
            pagination.current = 30
            pagination.all = 58
        }
        assertTrue(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                        ambiguousMidPage,
                )
        )
        // READ_RESUME on the last page (no bottom-redirect flag) must no longer be suppressed.
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.READ_RESUME,
                        forpdateam.ru.forpda.entity.remote.theme.ThemePage(),
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                        forpdateam.ru.forpda.entity.remote.theme.ThemePage(),
                )
        )
    }

    // --- Phase 2.2 warm prefetch ---

    @Test
    fun shouldAcceptWarmGetNewPostPage_ambiguousAllRead_log903891() {
        val page = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            id = 903891
            ambiguousLastUnreadBottomRedirect = true
            hasUnreadTarget = false
            url = "${base}903891&st=2740#entry143733850"
            posts.add(forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply { id = 143733850 })
        }
        val url = "${base}903891&view=getnewpost"
        assertTrue(
                TopicUnreadOpenPolicy.shouldAcceptWarmGetNewPostPage(
                        page,
                        url,
                        parserListUnreadHint = true,
                )
        )
    }

    @Test
    fun shouldAcceptWarmGetNewPostPage_rejectsLaggedUnreadWithoutAmbiguous() {
        val page = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
            id = topic1121483
            hasUnreadTarget = false
            ambiguousLastUnreadBottomRedirect = false
            anchorPostId = "143805431"
        }
        val url = "${base}$topic1121483&view=getnewpost"
        assertFalse(
                TopicUnreadOpenPolicy.shouldAcceptWarmGetNewPostPage(
                        page,
                        url,
                        parserListUnreadHint = true,
                )
        )
    }

    // --- Regression (log 25_06-10-09, topic 1103268): a genuine first-unread open that lands on the
    // last page must NOT be auto-marked read on load, or every re-open degrades to READ_RESUME and
    // restores an already-read post. Suppress only when the unread post is NOT at the page bottom. ---

    private fun firstUnreadLastPage(
            unreadAnchorPostId: Int,
            pagePostIds: List<Int>,
            hasUnreadTarget: Boolean = true,
            resumeBottom: Boolean = false,
    ) = forpdateam.ru.forpda.entity.remote.theme.ThemePage().apply {
        id = 1103268
        this.hasUnreadTarget = hasUnreadTarget
        resumeToLastPageBottom = resumeBottom
        anchorPostId = unreadAnchorPostId.toString()
        pagination.current = 1317
        pagination.all = 1317
        pagePostIds.forEach { pid ->
            posts.add(forpdateam.ru.forpda.entity.remote.theme.ThemePost().apply { id = pid })
        }
    }

    @Test
    fun firstUnreadOpen_unreadPostAbovePageBottom_suppressesEagerMarkRead() {
        // Server getnewpost resolved unread 143998112 near the TOP of the tall last page
        // (last post 143999430). Loading the page must NOT mark the whole topic read.
        val page = firstUnreadLastPage(
                unreadAnchorPostId = 143998112,
                pagePostIds = listOf(135617646, 143998112, 143998164, 143999430),
        )
        assertTrue(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                        page,
                )
        )
        // The findpost-reload reclassifies the session EXPLICIT_POST but keeps hasUnreadTarget=true;
        // suppression must still hold (that is the exact state at mark-read time in the log).
        assertTrue(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.EXPLICIT_POST,
                        page,
                )
        )
    }

    @Test
    fun firstUnreadOpen_unreadPostIsLastOnPage_doesNotSuppressMarkRead() {
        // When the first-unread post IS the last post on the page, the user is already at the end;
        // the normal end-of-topic mark-read must still fire.
        val page = firstUnreadLastPage(
                unreadAnchorPostId = 143999430,
                pagePostIds = listOf(135617646, 143998112, 143999430),
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                        page,
                )
        )
    }

    @Test
    fun firstUnreadOpen_resumeToLastPageBottom_doesNotSuppressMarkRead() {
        val page = firstUnreadLastPage(
                unreadAnchorPostId = 143998112,
                pagePostIds = listOf(135617646, 143998112, 143999430),
                resumeBottom = true,
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.FIRST_UNREAD,
                        page,
                )
        )
    }

    @Test
    fun explicitBookmarkOpen_noUnreadTarget_doesNotSuppressMarkRead() {
        // A pure explicit deep link (bookmark/mention) does NOT carry a server unread target, so it
        // must continue to mark read on the last page exactly as before.
        val page = firstUnreadLastPage(
                unreadAnchorPostId = 143998112,
                pagePostIds = listOf(135617646, 143998112, 143999430),
                hasUnreadTarget = false,
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForSession(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.EXPLICIT_POST,
                        page,
                )
        )
    }

    @Test
    fun readResumeOpen_withUnreadTarget_doesNotSuppress_sessionGate() {
        // READ_RESUME is not an unread-nav session; even with the flag set defensively it must not
        // be suppressed by the first-unread path (it has its own all-read semantics).
        val page = firstUnreadLastPage(
                unreadAnchorPostId = 143998112,
                pagePostIds = listOf(135617646, 143998112, 143999430),
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressMarkReadForFirstUnreadOpen(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.READ_RESUME,
                        page,
                )
        )
    }
}
