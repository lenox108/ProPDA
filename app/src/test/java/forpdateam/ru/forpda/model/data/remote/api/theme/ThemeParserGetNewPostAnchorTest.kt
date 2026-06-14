package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class ThemeParserGetNewPostAnchorTest {

    private val parser = ThemeParser(loadProductionPatterns())

    @Test
    fun getNewPost_prefersUnreadMarkerOverPageTopHash() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry100"></a><div class="post_container read">read</div>
            <div class="post_wrap unread"><a name="entry101"></a></div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20#entry100",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry101", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_skipsPageTopHashWhenMultiplePostsOnPage() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry200"></a><div class="post_container">old</div>
            <a name="entry201"></a><div class="post_container">next</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20#entry200",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry201", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_skipsLastReadPParamWhenMultiplePostsOnPage() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry200"></a><div class="post_container">old</div>
            <a name="entry201"></a><div class="post_container">next</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20&p=200",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry201", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_skipsPageBottomHashWhenMultiplePostsOnPage() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry300"></a><div class="post_container read">read</div>
            <a name="entry301"></a><div class="post_container read">read</div>
            <div class="post_wrap unread"><a name="entry302"></a></div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=40#entry302",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry302", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_unreadListHint_bottomRedirectDoesNotScrollToRedirect() {
        val html = """
            <script>var topic_id = parseInt(1103268); var forum_id = parseInt(1);</script>
            <a name="entry143784154"></a><div class="post_container">read</div>
            <a name="entry143785990"></a><div class="post_container">read</div>
            <a name="entry143785993"></a><div class="post_container">latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1103268&st=24240#entry143785993",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertNull(page.anchorPostId)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
        assertEquals("AMBIGUOUS_ALL_READ", page.openSessionKind)
    }

    @Test
    fun getNewPost_firstUnread_setsOpenSessionKind() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry100"></a><div class="post_container read">read</div>
            <div class="post_wrap unread"><a name="entry101"></a></div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20#entry100",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("FIRST_UNREAD", page.openSessionKind)
    }

    @Test
    fun getNewPost_listUnreadEntryAnchorWinsOverAmbiguousBottomRedirect() {
        val html = """
            <script>var topic_id = parseInt(1103268); var forum_id = parseInt(1);</script>
            <a name="entry143785990"></a><div class="post_container">first unread from list</div>
            <a name="entry143785993"></a><div class="post_container">latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1103268&st=24240#entry143785993",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&st=24240&view=getnewpost#entry143785990",
                openFromUnreadListHint = true
        )
        assertEquals("entry143785990", page.anchor)
        assertEquals("143785990", page.anchorPostId)
        assertTrue(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_unreadListHint_manyPostsBottomHash_onLastPageDoesNotScrollToRedirect() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry300"></a><div class="post_container">first unread window</div>
            <a name="entry301"></a><div class="post_container">next</div>
            <a name="entry302"></a><div class="post_container">latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=40#entry302",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_unreadListHint_deviceLog928862_bottomRedirectUnconfirmed() {
        val html = """
            <script>var topic_id = parseInt(928862); var forum_id = parseInt(1);</script>
            <a name="entry143799900"></a><div class="post_container">first unread window</div>
            <a name="entry143799950"></a><div class="post_container">read</div>
            <a name="entry143799968"></a><div class="post_container">latest on page</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=928862&st=104140#entry143799968",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=928862&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_unreadListHint_deviceLog1068409_bottomRedirectUnconfirmed() {
        val html = """
            <script>var topic_id = parseInt(1068409); var forum_id = parseInt(1);</script>
            <a name="entry143801127"></a><div class="post_container">p1</div>
            <a name="entry143801128"></a><div class="post_container">p2</div>
            <a name="entry143801129"></a><div class="post_container">p3</div>
            <a name="entry143801130"></a><div class="post_container">p4</div>
            <a name="entry143801131"></a><div class="post_container">p5</div>
            <a name="entry143801132"></a><div class="post_container">p6 latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1068409&st=880#entry143801132",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1068409&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_unreadListHint_deviceLog934059_bottomRedirectUnconfirmed() {
        val html = """
            <script>var topic_id = parseInt(934059); var forum_id = parseInt(1);</script>
            <a name="entry143801160"></a><div class="post_container">unread window</div>
            <a name="entry143801161"></a><div class="post_container">latest read</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=934059&st=203600#entry143801161",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=934059&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_unreadListHint_deviceLog1103268_twoPostsWithPrependedHat_bottomRedirectUnconfirmed() {
        val html = """
            <script>var topic_id = parseInt(1103268); var forum_id = parseInt(1);</script>
            <a name="entry135617646"></a><div class="post_container">hat</div>
            <a name="entry143801181"></a><div class="post_container">latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1103268&st=24320#entry143801181",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_unreadListHint_deviceLog1103268_manyPosts_bottomRedirectUnconfirmed() {
        val entries = (0 until 21).joinToString("\n") { i ->
            val id = 143801098 + i
            """<a name="entry$id"></a><div class="post_container">post ${i + 1}</div>"""
        }
        val html = """
            <script>var topic_id = parseInt(1103268); var forum_id = parseInt(1);</script>
            $entries
        """.trimIndent()
        val lastId = 143801118
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1103268&st=24300#entry$lastId",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_log724_1103268_fourPosts_bottomRedirectUnconfirmed() {
        val html = """
            <script>var topic_id = parseInt(1103268); var forum_id = parseInt(1);</script>
            <a name="entry135617646"></a><div class="post_container">hat</div>
            <a name="entry143807897"></a><div class="post_container">read</div>
            <a name="entry143807939"></a><div class="post_container">read</div>
            <a name="entry143808043"></a><div class="post_container">latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1103268&st=24380#entry143808043",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertNull(page.anchorPostId)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_allReadBottomRedirect_scrollsToLastReadWithoutUnreadTarget() {
        val html = """
            <script>var topic_id = parseInt(1121483); var forum_id = parseInt(1);</script>
            <a name="entry143179849"></a><div class="post_container">hat</div>
            <a name="entry143784670"></a><div class="post_container">read</div>
            <a name="entry143784679"></a><div class="post_container">latest read</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1140#entry143784679",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1121483&view=getnewpost"
        )
        assertEquals("entry143784679", page.anchor)
        assertEquals(false, page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_log486_1103268_lastUnreadHint_bottomRedirectUnconfirmed() {
        val html = """
            <script>var topic_id = parseInt(1103268); var forum_id = parseInt(1);</script>
            <a name="entry135617646"></a><div class="post_container">first</div>
            <a name="entry143807897"></a><div class="post_container">unread</div>
            <a name="entry143808100"></a><div class="post_container">read</div>
            <a name="entry143808200"></a><div class="post_container">read</div>
            <a name="entry143809100"></a><div class="post_container">read</div>
            <a name="entry143809300"></a><div class="post_container">read</div>
            <a name="entry143809388"></a><div class="post_container">latest</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1103268&st=24380#entry143809388",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1103268&view=getnewpost",
                openFromUnreadListHint = true,
        )
        assertNull(page.anchor)
        assertNull(page.anchorPostId)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_log486_1121483_lastUnreadHint_bottomRedirectUnconfirmed() {
        val hatId = 143179849
        val lastId = 143805431
        val middleIds = listOf(
                143803001, 143803002, 143803003, 143803004, 143803005,
                143803006, 143803007, 143803008, 143803009, 143803010,
                143803011, 143803012, 143803013,
        )
        val entryHtml = (listOf(hatId) + middleIds + lastId).joinToString("\n") { id ->
            """<a name="entry$id"></a><div class="post_container">post</div>"""
        }
        val html = """
            <script>var topic_id = parseInt(1121483); var forum_id = parseInt(1);</script>
            $entryHtml
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1121483&st=1140#entry$lastId",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1121483&view=getnewpost",
                openFromUnreadListHint = true,
        )
        assertNull(page.anchor)
        assertNull(page.anchorPostId)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
    }

    @Test
    fun getNewPost_twoPostsBottomHash_usesSecondWhenOnlyLastUnread() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry400"></a><div class="post_container read">read</div>
            <div class="post_wrap unread"><a name="entry401"></a></div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20#entry401",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry401", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_twoPostsAllReadBottomRedirect_usesLastReadWithoutUnreadTarget() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry400"></a><div class="post_container">read</div>
            <a name="entry401"></a><div class="post_container">read</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20#entry401",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry401", page.anchor)
        assertFalse(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_usesHighlightQueryWhenPresent() {
        val html = """
            <script>var topic_id = parseInt(1); var forum_id = parseInt(1);</script>
            <a name="entry555"></a><div class="post_container">target</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1&st=20&highlight=555",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1&view=getnewpost"
        )
        assertEquals("entry555", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    @Test
    fun getNewPost_log1122662_firstPageTopRedirect_doesNotAnchorReadTopOfPage1() {
        // Log 1122662: getnewpost redirects to the top of page 1 (st=0, #entry{first post}) with no
        // HTML unread markers. The first post is already read; the real unread is on a later page.
        // Anchoring to the page-1 top would open the topic at its very first read post ("first page").
        // Expect ambiguous all-read instead of a confirmed unread target on page-1 top.
        val html = """
            <script>var topic_id = parseInt(1122662); var forum_id = parseInt(1);</script>
            <a name="entry10000001"></a><div class="post_container">read p1</div>
            <a name="entry10000002"></a><div class="post_container">read p2</div>
            <a name="entry10000003"></a><div class="post_container">read p3</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1122662&st=0#entry10000001",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1122662&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertNull(page.anchor)
        assertNull(page.anchorPostId)
        assertFalse(page.hasUnreadTarget)
        assertTrue(page.ambiguousLastUnreadBottomRedirect)
        assertEquals("AMBIGUOUS_ALL_READ", page.openSessionKind)
    }

    @Test
    fun getNewPost_genuineFirstUnreadOnPage1_stillAnchorsWithMarker() {
        // Regression guard: a real first-unread on page 1 carries HTML unread markers and must keep
        // anchoring (the page-top guard only applies when there are no unread markers).
        val html = """
            <script>var topic_id = parseInt(1122662); var forum_id = parseInt(1);</script>
            <a name="entry10000001"></a><div class="post_container read">read p1</div>
            <div class="post_wrap unread"><a name="entry10000002"></a></div>
            <a name="entry10000003"></a><div class="post_container">p3</div>
        """.trimIndent()
        val page = parser.parsePage(
                html,
                "https://4pda.to/forum/index.php?showtopic=1122662&st=0#entry10000001",
                initialRequestUrl = "https://4pda.to/forum/index.php?showtopic=1122662&view=getnewpost",
                openFromUnreadListHint = true
        )
        assertEquals("entry10000002", page.anchor)
        assertTrue(page.hasUnreadTarget)
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsFile = listOf(
                File("src/main/assets/patterns.json"),
                File("app/src/main/assets/patterns.json")
        ).first { it.exists() }
        val patternsJson = patternsFile.readText()
        val root = Json.parseToJsonElement(patternsJson).jsonObject
        val scopes = root.getValue("scopes").jsonArray
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        scopes.forEach { scopeElement ->
            val scope = scopeElement.jsonObject
            val name = scope.getValue("scope").jsonPrimitive.content
            val map = mutableMapOf<String, Pattern>()
            val patterns = scope.getValue("patterns").jsonArray
            patterns.forEach { patternElement ->
                val p = patternElement.jsonObject
                map[p.getValue("key").jsonPrimitive.content] = Pattern.compile(p.getValue("value").jsonPrimitive.content)
            }
            patternsByScope[name] = map
        }
        return object : IPatternProvider {
            override fun getCurrentVersion(): Int = -1
            override fun getPattern(scope: String, key: String): Pattern {
                return patternsByScope[scope]?.get(key)
                        ?: error("No pattern $scope/$key in production patterns.json")
            }

            override fun update(jsonString: String) = Unit
        }
    }
}
