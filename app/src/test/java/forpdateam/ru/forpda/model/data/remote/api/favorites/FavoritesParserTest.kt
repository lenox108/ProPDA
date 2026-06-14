package forpdateam.ru.forpda.model.data.remote.api.favorites

import forpdateam.ru.forpda.entity.remote.favorites.FavItem
import forpdateam.ru.forpda.entity.remote.favorites.FavoriteReadState
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FavoritesParserTest {

    private val parser: FavoritesParser by lazy { FavoritesParser(loadProductionPatterns()) }

    @Test
    fun modifierPlusMarksTopicUnreadWithoutStrongTitle() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = "+2",
                        strongTitle = false,
                        topicId = 111,
                        title = "Unread by modifier"
                )
        ).items.single()

        assertTrue("plus modifier should mark favorite unread", item.isNew)
        assertEquals(2, item.unreadPostCount)
    }

    @Test
    fun strongTitleStillMarksTopicUnreadWhenPlusPresent() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = "+1",
                        strongTitle = true,
                        topicId = 222,
                        title = "Unread by strong and plus"
                )
        ).items.single()

        assertTrue("strong title with + modifier should mark favorite unread", item.isNew)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun barePlusInModifierMarksTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 672,
                title = "Unread by bare plus"
        ).replace(
                "<span class=\"modifier\"></span>",
                """<span class="modifier"><font color="green">+</font></span>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertTrue(item.isNew)
        assertEquals(FavoriteReadState.UNREAD, item.readState)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun fNormIconWithoutAltDoesNotMarkTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 673,
                title = "Read topic with f_norm icon"
        ).replace(
                "<span class=\"modifier\"></span>",
                """<span class="modifier"><a href="#"><img src="/style_images/1/f_norm.gif" border="0"></a></span>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertFalse("f_norm without >N alt is not an unread marker on modern favorites HTML", item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
    }

    @Test
    fun titleLinkStyleBoldAloneDoesNotMarkTopicUnread() {
        val rowHtml = """
            <div data-item-fid="77" data-item-track="immediate" data-item-pin="0">
                <span class="modifier"></span>
                <a style="font-weight: bold" href="https://4pda.to/forum/index.php?showtopic=674&amp;view=getnewpost">Bold nav link only</a>
            </div>
        """.trimIndent()
        val unread = FavoritesParser.detectFavoriteRowUnread(
                rowHtml = rowHtml,
                modifierRegion = "",
                modifierText = ""
        )
        assertEquals(FavoriteReadState.READ, unread.readState)
    }

    @Test
    fun readTopicWithoutUnreadMarkersRemainsRead() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = "",
                        strongTitle = false,
                        topicId = 333,
                        title = "Read topic"
                )
        ).items.single()

        assertFalse(item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun strongTitleTagMarksTopicUnread() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = "+1",
                        strongTitle = true,
                        titleTag = "b",
                        topicId = 444,
                        title = "Unread by b tag and plus"
                )
        ).items.single()

        assertTrue(item.isNew)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun strongTitleWithoutPlusMarksTopicUnread() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = "",
                        strongTitle = true,
                        topicId = 445,
                        title = "Unread by strong title only"
                )
        ).items.single()

        assertTrue("4pda unread favorites use bold title without +N", item.isNew)
        assertEquals(FavoriteReadState.UNREAD, item.readState)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun spacedModifierPlusMarksTopicUnread() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = " +4",
                        strongTitle = false,
                        topicId = 555,
                        title = "Spaced plus modifier"
                )
        ).items.single()

        assertTrue(item.isNew)
        assertEquals(4, item.unreadPostCount)
    }

    @Test
    fun modifierUnreadClassMarksTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "+2",
                strongTitle = false,
                topicId = 666,
                title = "Modifier unread class"
        ).replace("class=\"modifier\"", "class=\"modifier unread\"")
        val item = parser.parseFavorites(html).items.single()

        assertTrue(item.isNew)
        assertEquals(2, item.unreadPostCount)
    }

    @Test
    fun modifierUnreadClassWithoutPlusMarksTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 667,
                title = "Unread by modifier class only"
        ).replace("class=\"modifier\"", "class=\"modifier unread\"")
        val item = parser.parseFavorites(html).items.single()

        assertTrue("modifier unread class should mark favorite unread without +N", item.isNew)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun newPostImgAltMarksTopicUnreadWithoutBoldTitle() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 668,
                title = "Unread by >N icon"
        ).replace(
                "<span class=\"modifier\"></span>",
                """<span class="modifier"><a href="#"><img alt=">N" src="/style_images/1/f_norm.gif" border="0"></a></span>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertTrue("img alt='>N' should mark favorite unread", item.isNew)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun forumImgWithLinkAltMarksTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 670,
                title = "Unread by forum_img_with_link"
        ).replace(
                "<span class=\"modifier\"></span>",
                """<span class="forum_img_with_link unread"><a href="#"><img alt=">N" src="/style_images/1/f_norm.gif" border="0"></a></span>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertTrue("forum_img_with_link + >N should mark favorite unread", item.isNew)
        assertEquals(1, item.unreadPostCount)
    }

    @Test
    fun getlastpostLinkAloneDoesNotMarkTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 672,
                title = "Read with last post link"
        ).replace(
                """<a href="https://4pda.to/forum/index.php?showtopic=672">Read with last post link</a>""",
                """<a href="https://4pda.to/forum/index.php?showtopic=672">Read with last post link</a>""" +
                        """<a href="https://4pda.to/forum/index.php?showtopic=672&amp;view=getlastpost">•</a>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertFalse("getlastpost alone must not mark unread", item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
    }

    @Test
    fun getnewpostLinkAloneDoesNotMarkTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 671,
                title = "Read with newpost nav link"
        ).replace(
                """<a href="https://4pda.to/forum/index.php?showtopic=671">Read with newpost nav link</a>""",
                """<a href="https://4pda.to/forum/index.php?showtopic=671&amp;view=getnewpost">Read with newpost nav link</a>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertFalse("view=getnewpost on every favorites row must not mark unread alone", item.isNew)
        assertEquals(FavoriteReadState.READ, item.readState)
        assertEquals(0, item.unreadPostCount)
    }

    /** Fixture from device log 2026-06-07: real rows carry getnewpost; only +/+N/modifier.unread/>N alt mark unread. */
    @Test
    fun realWorldRowWithGetnewpostOnlyIsRead() {
        val rowHtml = """
            <div data-item-fid="11375242" data-item-track="immediate" data-item-pin="0">
                <span class="modifier forum_img_with_link"></span>
                <a href="https://4pda.to/forum/index.php?showtopic=934059&amp;view=getnewpost">Topic title</a>
                <a href="https://4pda.to/forum/index.php?showtopic=934059&amp;view=getlastpost">•</a>
            </div>
        """.trimIndent()
        val unread = FavoritesParser.detectFavoriteRowUnread(rowHtml, "", "")
        assertEquals(FavoriteReadState.READ, unread.readState)
    }

    @Test
    fun unreadTopicLinkWithPlusModifierMarksUnread() {
        val html = favoriteTopicHtml(
                modifier = "+3",
                strongTitle = false,
                topicId = 671,
                title = "Unread by plus and getnewpost"
        ).replace(
                "showtopic=671",
                "showtopic=671&amp;view=getnewpost"
        )
        val item = parser.parseFavorites(html).items.single()

        assertTrue(item.isNew)
        assertEquals(3, item.unreadPostCount)
        assertTrue(
                item.listingHref.orEmpty().contains("view=getnewpost", ignoreCase = true)
        )
    }

    @Test
    fun plusInsideModifierHtmlMarksTopicUnread() {
        val html = favoriteTopicHtml(
                modifier = "",
                strongTitle = false,
                topicId = 669,
                title = "Unread by nested plus"
        ).replace(
                "<span class=\"modifier\"></span>",
                """<span class="modifier"><font color="green">+5</font></span>"""
        )
        val item = parser.parseFavorites(html).items.single()

        assertTrue(item.isNew)
        assertEquals(5, item.unreadPostCount)
    }

    @Test
    fun lastPostDescendingPutsTodayBeforeYesterday() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Yesterday", date = "Вчера, 19:47"),
                favoriteTopic(favId = 2, topicId = 102, title = "Today", date = "Сегодня, 10:36")
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC), unreadTop = true)

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun lastPostDescendingWithoutUnreadTopUsesOnlyDateOrder() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Read today", date = "Сегодня, 10:36", isNew = false),
                favoriteTopic(favId = 2, topicId = 102, title = "Unread yesterday", date = "Вчера, 19:47", isNew = true)
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC), unreadTop = false)

        assertEquals(listOf(101, 102), items.map { it.topicId })
    }

    @Test
    fun titleAscendingWithoutUnreadTopUsesOnlyTitleOrder() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Beta", date = "Вчера, 19:47", isNew = true),
                favoriteTopic(favId = 2, topicId = 102, title = "Alpha", date = "Сегодня, 10:36", isNew = false)
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.ASC), unreadTop = false)

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun lastPostAscendingPutsYesterdayBeforeToday() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Today", date = "Сегодня, 10:36"),
                favoriteTopic(favId = 2, topicId = 102, title = "Yesterday", date = "Вчера, 19:47")
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.ASC))

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun lastPostSortingKeepsPinnedAndNormalItemsSortableByAdapterSections() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Pinned old", date = "Вчера, 19:47", isPin = true),
                favoriteTopic(favId = 2, topicId = 102, title = "Forum old", date = "Вчера, 20:47", isForum = true),
                favoriteTopic(favId = 3, topicId = 103, title = "Pinned new", date = "Сегодня, 10:36", isPin = true),
                favoriteTopic(favId = 4, topicId = 104, title = "Normal new", date = "Сегодня, 11:36", isNew = true)
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC))

        val pinnedSection = items.filter { it.isPin }
        val normalSection = items.filterNot { it.isPin }
        assertEquals(listOf(103, 101), pinnedSection.map { it.topicId })
        assertEquals(listOf(104, 102), normalSection.map { it.topicId })
        assertEquals(items.size, items.map { it.favId }.toSet().size)
    }

    @Test
    fun titleAscendingSortsTopicsAndForumsByName() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Beta", date = "Сегодня, 10:36"),
                favoriteTopic(favId = 2, topicId = 102, title = "alpha", date = "Вчера, 19:47", isForum = true),
                favoriteTopic(favId = 3, topicId = 103, title = "Gamma", date = "15.05.2026, 12:00")
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.ASC))

        assertEquals(listOf(102, 101, 103), items.map { it.topicId })
    }

    @Test
    fun titleDescendingReversesTitleOrder() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Beta", date = "Сегодня, 10:36"),
                favoriteTopic(favId = 2, topicId = 102, title = "alpha", date = "Вчера, 19:47"),
                favoriteTopic(favId = 3, topicId = 103, title = "Gamma", date = "15.05.2026, 12:00")
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.TITLE, Sorting.Companion.Order.DESC))

        assertEquals(listOf(103, 101, 102), items.map { it.topicId })
    }

    @Test
    fun lastPostDescendingKeepsUnreadNormalTopicsBeforeReadNormalTopics() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Read today", date = "Сегодня, 10:36", isNew = false),
                favoriteTopic(favId = 2, topicId = 102, title = "Unread yesterday", date = "Вчера, 19:47", isNew = true)
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC), unreadTop = true)

        assertEquals(listOf(102, 101), items.map { it.topicId })
    }

    @Test
    fun lastPostDescendingMatchesScreenshotWithUnreadGroupBeforeReadGroup() {
        val items = mutableListOf(
                favoriteTopic(favId = 1, topicId = 101, title = "Обсуждение модулей для Magisk", date = "Сегодня, 12:46", isNew = true),
                favoriteTopic(favId = 2, topicId = 102, title = "Apple MacBook Pro 13", date = "Сегодня, 12:13"),
                favoriteTopic(favId = 3, topicId = 103, title = "Энергопотребление", date = "Сегодня, 11:31"),
                favoriteTopic(favId = 4, topicId = 104, title = "Adguard", date = "Сегодня, 11:25"),
                favoriteTopic(favId = 5, topicId = 105, title = "Kinopub", date = "Сегодня, 11:04"),
                favoriteTopic(favId = 6, topicId = 106, title = "KernelSU", date = "Сегодня, 10:55"),
                favoriteTopic(favId = 7, topicId = 107, title = "WPS Office + PDF", date = "Сегодня, 10:36", isNew = true),
                favoriteTopic(favId = 8, topicId = 108, title = "Обход блокировок WhatsApp и Telegram", date = "Сегодня, 09:33", isNew = true)
        )

        FavoritesSort.apply(items, Sorting(Sorting.Companion.Key.LAST_POST, Sorting.Companion.Order.DESC), unreadTop = true)

        assertEquals(listOf(101, 107, 108, 102, 103, 104, 105, 106), items.map { it.topicId })
    }

    @Test
    fun favoriteTopic_parsesDistinctStarterLastPosterAndPages() {
        val item = parser.parseFavorites(
                favoriteTopicHtml(
                        modifier = "",
                        strongTitle = false,
                        topicId = 999,
                        title = "OnePlus 15 - Обсуждение",
                        authorNick = "#Санёк",
                        lastUserNick = "Lenox30",
                        pageOffset = 23080
                )
        ).items.single()

        assertEquals("#Санёк", item.authorUserNick)
        assertEquals("Lenox30", item.lastUserNick)
        assertEquals(1155, item.pages)
    }

    private fun favoriteTopicHtml(
            modifier: String,
            strongTitle: Boolean,
            topicId: Int,
            title: String,
            titleTag: String = "strong",
            authorNick: String = "Author",
            lastUserNick: String = "LastUser",
            pageOffset: Int = 40
    ): String {
        val titleHtml = when {
            strongTitle -> "<$titleTag>$title</$titleTag>"
            else -> title
        }
        return """
            <div data-item-fid="77" data-item-track="immediate" data-item-pin="0">
                <span class="modifier">$modifier</span>
                <a href="https://4pda.to/forum/index.php?showtopic=$topicId">$titleHtml</a>
                <a onclick="return tpg(20,$pageOffset)">3</a>
            </div><div class="topic_body"><span class="topic_desc">Description<br /></span>
                <a href="https://4pda.to/forum/index.php?showforum=10">Forum title</a><br />
                автор: <a href="https://4pda.to/forum/index.php?showuser=1">$authorNick</a><br />
                посл.: <a href="https://4pda.to/forum/index.php?showuser=2">$lastUserNick</a> 19.05.26, 10:30
            </div>
            <script>wr_fav_subscribe('77',"immediate")</script>
        """.trimIndent()
    }

    private fun favoriteTopic(
            favId: Int,
            topicId: Int,
            title: String,
            date: String,
            isPin: Boolean = false,
            isForum: Boolean = false,
            isNew: Boolean = false
    ) = FavItem().apply {
        this.favId = favId
        this.topicId = topicId
        topicTitle = title
        this.date = date
        this.isPin = isPin
        this.isForum = isForum
        this.isNew = isNew
    }

    private fun loadProductionPatterns(): IPatternProvider {
        val patternsJson = File("src/main/assets/patterns.json").readText()
        val root = JSONObject(patternsJson)
        val scopes = root.getJSONArray("scopes")
        val patternsByScope = mutableMapOf<String, MutableMap<String, Pattern>>()
        for (i in 0 until scopes.length()) {
            val scope = scopes.getJSONObject(i)
            val name = scope.getString("scope")
            val map = mutableMapOf<String, Pattern>()
            val patterns = scope.getJSONArray("patterns")
            for (j in 0 until patterns.length()) {
                val p = patterns.getJSONObject(j)
                map[p.getString("key")] = Pattern.compile(p.getString("value"))
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
