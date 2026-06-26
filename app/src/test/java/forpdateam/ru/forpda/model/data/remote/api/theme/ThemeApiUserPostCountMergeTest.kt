package forpdateam.ru.forpda.model.data.remote.api.theme

import android.content.Context
import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.others.pagination.Pagination
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeApiUserPostCountMergeTest {

    @Test
    fun `getTheme does not block on desktop metadata fetch`() {
        val url = "https://4pda.to/forum/index.php?showtopic=10"
        val webClient = mockk<IWebClient>(relaxed = true)
        val parser = mockk<ThemeParser>(relaxed = true)
        val page = ThemePage().apply {
            id = 10
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                nick = "Vedmak08"
            })
        }

        every { webClient.get(url) } returns NetworkResponse(url = url, code = 200, redirect = url, body = mobileTopicHtmlWithoutPostCount())
        every { parser.parsePage(any(), url, any(), any(), any()) } returns page

        val loaded = ThemeApi(webClient, parser).getTheme(url, hatOpen = false, pollOpen = false)

        assertNull(loaded.posts.first().userPostCount)
        verify(exactly = 0) { webClient.requestWithoutMobileCookie(any()) }
    }

    @Test
    fun getThemeMergesUserPostCountFromDesktopTopicHtml() = runTest {
        val url = "https://4pda.to/forum/index.php?showtopic=10"
        val desktopUrl = "https://4pda.to/forum/index.php?showtopic=10&st=0"
        val desktopRequest = slot<NetworkRequest>()
        val webClient = mockk<IWebClient>(relaxed = true)
        val parser = mockk<ThemeParser>(relaxed = true)
        val initialPage = ThemePage().apply {
            id = 10
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 100
                topicId = 10
                number = 1
                userId = 7
                nick = "Vedmak08"
            })
        }

        every { webClient.get(url) } returns NetworkResponse(
                url = url,
                code = 200,
                redirect = url,
                body = mobileTopicHtmlWithoutPostCount()
        )
        every { parser.parsePage(any(), url, any(), any(), any()) } returns initialPage
        every { parser.parseUserPostCountsByPostId(desktopTopicHtmlWithPostCount()) } returns mapOf(100 to 19342)
        every { webClient.requestWithoutMobileCookie(capture(desktopRequest)) } returns NetworkResponse(
                url = desktopUrl,
                code = 200,
                redirect = desktopUrl,
                body = desktopTopicHtmlWithPostCount()
        )

        val api = ThemeApi(webClient, parser)
        val page = api.getTheme(url, hatOpen = false, pollOpen = false)
        api.enrichPageMetadata(page, url)

        assertEquals(19342, page.posts.first().userPostCount)
        assertEquals(desktopUrl, desktopRequest.captured.url)
        assertTrue(desktopRequest.captured.headers.orEmpty()["User-Agent"].orEmpty().contains("Macintosh"))
        verify { webClient.requestWithoutMobileCookie(any()) }
    }

    @Test
    fun getThemeMergesDesktopPostCountBeforeFinalTemplateRender() = runTest {
        val url = "https://4pda.to/forum/index.php?showtopic=10"
        val webClient = mockk<IWebClient>(relaxed = true)
        val parser = mockk<ThemeParser>(relaxed = true)
        val page = ThemePage().apply {
            id = 10
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 100
                topicId = 10
                number = 1
                userId = 7
                nick = "Vedmak08"
                group = "Постоянный"
                date = "22.05.2026, 12:00"
                body = "Body"
            })
        }

        every { webClient.get(url) } returns NetworkResponse(url = url, code = 200, redirect = url, body = mobileTopicHtmlWithoutPostCount())
        every { parser.parsePage(any(), url, any(), any(), any()) } returns page
        every { parser.parseUserPostCountsByPostId(desktopTopicHtmlWithPostCount()) } returns mapOf(100 to 19342)
        every { webClient.requestWithoutMobileCookie(any()) } returns NetworkResponse(
                url = "https://4pda.to/forum/index.php?showtopic=10&st=0",
                code = 200,
                redirect = "https://4pda.to/forum/index.php?showtopic=10&st=0",
                body = desktopTopicHtmlWithPostCount()
        )

        val api = ThemeApi(webClient, parser)
        val loadedPage = api.getTheme(url, hatOpen = false, pollOpen = false)
        api.enrichPageMetadata(loadedPage, url)
        val html = productionThemeTemplate().mapString(loadedPage)

        val postHeaderHtml = html.substringAfter("class=\"post_container").substringBefore("<div class=\"post_body")
        assertTrue(postHeaderHtml, postHeaderHtml.contains("class=\"inf user_post_count\""))
        assertTrue(postHeaderHtml, postHeaderHtml.contains("aria-label=\"Сообщений: 19342\""))
        assertTrue(postHeaderHtml, postHeaderHtml.contains("<span>19342</span>"))
    }

    @Test
    fun getThemeFallsBackToProfilePostCountAndCachesAuthor() = runTest {
        val url = "https://4pda.to/forum/index.php?showtopic=10"
        val profileUrl = "https://4pda.to/forum/index.php?showuser=777"
        val webClient = mockk<IWebClient>(relaxed = true)
        val parser = mockk<ThemeParser>(relaxed = true)
        val page = ThemePage().apply {
            id = 10
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 100
                topicId = 10
                number = 1
                userId = 777
                nick = "ProfileOnlyUser"
            })
            posts.add(ThemePost().apply {
                id = 101
                topicId = 10
                number = 2
                userId = 777
                nick = "ProfileOnlyUser"
            })
        }

        every { webClient.get(url) } returns NetworkResponse(url = url, code = 200, redirect = url, body = mobileTopicHtmlWithoutPostCount())
        every { parser.parsePage(any(), url, any(), any(), any()) } returns page
        every { parser.parseUserPostCountsByPostId(any()) } returns emptyMap()
        // Both the desktop-topic merge and the profile fallback now use the desktop skin via
        // requestWithoutMobileCookie; route by request URL.
        every { webClient.requestWithoutMobileCookie(match { it.url.contains("showuser=777") }) } returns NetworkResponse(
                url = profileUrl,
                code = 200,
                redirect = profileUrl,
                body = profileHtmlWithForumPostCount()
        )
        every { webClient.requestWithoutMobileCookie(match { !it.url.contains("showuser=777") }) } returns NetworkResponse(
                url = "https://4pda.to/forum/index.php?showtopic=10&st=0",
                code = 200,
                redirect = "https://4pda.to/forum/index.php?showtopic=10&st=0",
                body = desktopTopicHtmlWithoutPostCount()
        )

        val api = ThemeApi(webClient, parser)
        val loadedPage = api.getTheme(url, hatOpen = false, pollOpen = false)
        api.enrichPageMetadata(loadedPage, url)

        assertEquals(listOf(19342, 19342), loadedPage.posts.map { it.userPostCount })
        verify(exactly = 1) { webClient.requestWithoutMobileCookie(match { it.url.contains("showuser=777") }) }
    }

    @Test
    fun getThemeDoesNotOverwriteExistingPositivePostCountFromDesktopMerge() = runTest {
        val url = "https://4pda.to/forum/index.php?showtopic=10"
        val webClient = mockk<IWebClient>(relaxed = true)
        val parser = mockk<ThemeParser>(relaxed = true)
        val page = ThemePage().apply {
            id = 10
            this.url = url
            pagination = Pagination()
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                userPostCount = 3794
                nick = "Lenox30"
            })
        }

        every { webClient.get(url) } returns NetworkResponse(url = url, code = 200, redirect = url, body = mobileTopicHtmlWithoutPostCount())
        every { parser.parsePage(any(), url, any(), any(), any()) } returns page
        every { parser.parseUserPostCountsByPostId(desktopTopicHtmlWithPostCount()) } returns mapOf(100 to 19342)
        every { webClient.requestWithoutMobileCookie(any()) } returns NetworkResponse(
                url = "https://4pda.to/forum/index.php?showtopic=10&st=0",
                code = 200,
                redirect = "https://4pda.to/forum/index.php?showtopic=10&st=0",
                body = desktopTopicHtmlWithPostCount()
        )

        val api = ThemeApi(webClient, parser)
        val loadedPage = api.getTheme(url, hatOpen = false, pollOpen = false)
        api.enrichPageMetadata(loadedPage, url)

        assertEquals(3794, loadedPage.posts.first().userPostCount)
    }

    @Test
    fun parsesProfileForumPostCount() {
        assertEquals(19342, ThemeApi.parseProfileUserPostCount(profileHtmlWithForumPostCount()))
    }

    @Test
    fun parsesRealisticForumStatsProfilePostCount() {
        assertEquals(547, ThemeApi.parseProfileUserPostCount(realisticProfileHtmlForumPostsViaSearchLink()))
    }

    @Test
    fun prefersForumPostsOverPrecedingSiteStatsBlock() {
        assertEquals(547, ThemeApi.parseProfileUserPostCount(realisticProfileHtmlSiteStatsBeforeForumPosts()))
    }

    @Test
    fun parsesPostsLabelWhenSearchHrefCarriesAdjacentDigits() {
        // The «Постов» count is in a search link whose href contains the user's numeric mid AND a
        // result count; a naive "first digit run after the label" grabs href digits, not the count.
        val html = """
            <html><body>
            <div class="user-box"><h1>ja_tyt</h1></div>
            <ul class="profile-stats forum"><li>
                <span class="title">Постов</span>
                <div class="area"><a href="/forum/index.php?act=search&mid=143230576&result=1">3 794</a></div>
            </li></ul>
            </body></html>
        """.trimIndent()
        assertEquals(3794, ThemeApi.parseProfileUserPostCount(html))
    }

    @Test
    fun parsesPostsCountWhenTitleAndAreaSeparatedByMarkup() {
        // Production `forum_stats` allows arbitrary content ([\s\S]*?) between the title </span> and
        // <div class="area">. Real profiles wrap stats and can interpose an icon/tooltip/comment node
        // (NOT only whitespace) between them. The title/area branch must tolerate that.
        val html = """
            <html><body>
            <ul class="forum"><li>
                <span class="title">Постов</span>
                <i class="stat-icon" aria-hidden="true"></i><!-- forum posts -->
                <div class="area"><a href="/forum/index.php?act=search&mid=5">547</a></div>
            </li></ul>
            </body></html>
        """.trimIndent()
        assertEquals(547, ThemeApi.parseProfileUserPostCount(html))
    }

    @Test
    fun parsesPostsCountWrappedInSpanInsideArea() {
        // forum_stats pattern explicitly allows the number to be wrapped: <a ...><span ...>N</span></a>.
        val html = """
            <html><body>
            <ul class="forum"><li>
                <span class="title">Постов</span>
                <div class="area"><a href="/forum/index.php?act=search&mid=5"><span class="cnt">8 419</span></a></div>
            </li></ul>
            </body></html>
        """.trimIndent()
        assertEquals(8419, ThemeApi.parseProfileUserPostCount(html))
    }

    @Test
    fun returnsNullWhenForumProfileExposesNoPostsRow() {
        // A restricted / minimal forum profile that omits the «Постов» row entirely.
        val html = """
            <html><body>
            <div class="user-box"><h1>lexal346</h1></div>
            <ul class="forum">
                <li><span class="title">Репутация</span><div class="area"><a href="/forum/index.php?showuser=5&view=rep">10</a></div></li>
                <li><span class="title">Тем</span><div class="area">0</div></li>
            </ul>
            </body></html>
        """.trimIndent()
        assertNull(ThemeApi.parseProfileUserPostCount(html))
    }

    @Test
    fun ignoresSiteCommentsBlockAndPicksForumPosts() {
        // Site block exposes «Комментов» (comments) with a real number; must not be mistaken for posts.
        val html = """
            <html><body>
            <div class="user-box"><h1>ja_tyt</h1></div>
            <ul class="profile-stats site">
                <li><span class="title">Комментов</span><div class="area">1234</div></li>
            </ul>
            <ul class="profile-stats forum">
                <li><span class="title">Постов</span>
                    <div class="area"><a href="/forum/index.php?act=search&mid=999&result=1">42</a></div></li>
            </ul>
            </body></html>
        """.trimIndent()
        assertEquals(42, ThemeApi.parseProfileUserPostCount(html))
    }

    @Test
    fun profileWithoutMessageCountReturnsNullAndRendersNoLine() {
        assertNull(ThemeApi.parseProfileUserPostCount(profileHtmlWithoutForumPostCount()))

        val page = ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                userId = 777
                nick = "ProfileOnlyUser"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Body"
                number = 1
            })
        }

        val html = productionThemeTemplate().mapString(page)
        val postHeaderHtml = html.substringAfter("class=\"post_container").substringBefore("<div class=\"post_body")
        assertFalse(postHeaderHtml.contains("class=\"inf user_post_count\""))
    }

    @Test
    fun malformedOrZeroProfilePostCountReturnsNull() {
        assertNull(ThemeApi.parseProfileUserPostCount(profileHtmlWithEmptyForumPostCount()))
        assertNull(ThemeApi.parseProfileUserPostCount(profileHtmlWithZeroForumPostCount()))
    }

    private fun mobileTopicHtmlWithoutPostCount(): String {
        return """
            <html><body>
            <a name="entry100"></a>
            <div class="post_container" data-post-id="100">
                <a href="https://4pda.to/forum/index.php?showuser=7">Vedmak08</a>
                <div class="post_body">Body</div>
            </div>
            </body></html>
        """.trimIndent()
    }

    private fun desktopTopicHtmlWithPostCount(): String {
        return """
            <html><body>
            <a name="entry100"></a>
            <div class="post_header_container"><div class="post_header">
                <span class="post_date">22.05.2026, 12:00 | <a>#1</a></span>
                <font color="#3a8f3a">Постоянный</font>
                <a data-av="avatar.jpg">Vedmak08</a>
                <a href="index.php?showuser=7"> </a>
                <span class="post_user_info">
                    <strong>Постоянный</strong><br>
                    Сообщений: 19 342<br>
                </span>
                (<a><span data-member-rep="7">55</span></a>)<br>
                <span class="post_action"></span>
                <div class="post_body">Body</div>
            </div></div>
            <div class="topic_foot_nav"></div>
            </body></html>
        """.trimIndent()
    }

    private fun desktopTopicHtmlWithoutPostCount(): String {
        return """
            <html><body>
            <a name="entry100"></a>
            <div class="post_header_container"><div class="post_header">
                <span class="post_date">22.05.2026, 12:00 | <a>#1</a></span>
                <font color="#3a8f3a">Постоянный</font>
                <a data-av="avatar.jpg">Vedmak08</a>
                <a href="index.php?showuser=7"> </a>
                <span class="post_user_info"><strong>Постоянный</strong><br></span>
                (<a><span data-member-rep="7">55</span></a>)<br>
                <span class="post_action"></span>
                <div class="post_body">Body</div>
            </div></div>
            <div class="topic_foot_nav"></div>
            </body></html>
        """.trimIndent()
    }

    private fun profileHtmlWithForumPostCount(): String {
        return """
            <html><body>
            <div class="user-box"><h1>Vedmak08</h1></div>
            <span class="title">Постов</span>
            <div class="area"><a href="https://4pda.to/forum/index.php?act=search&source=pst&username=Vedmak08">19 342</a></div>
            </body></html>
        """.trimIndent()
    }

    /**
     * Faithful to the production `forum_stats` pattern (patterns.json): the «Постов» value lives in an
     * `<a href="...act=search...">N</a>` link inside `<div class="area">`, the title `<span>` and the
     * area `<div>` are separated by newlines/indentation (and the `<li>` wrapper), and the search href
     * itself carries digits (mid=…) that must NOT be mistaken for the post count.
     */
    private fun realisticProfileHtmlForumPostsViaSearchLink(): String {
        return """
            <html><body>
            <div class="user-box"><h1>lexal346</h1></div>
            <ul class="profile-stats forum">
                <li>
                    <span class="title">Постов</span>
                    <div class="area">
                        <a href="https://4pda.to/forum/index.php?act=search&source=pst&mid=9876543&result=posts">547</a>
                    </div>
                </li>
            </ul>
            </body></html>
        """.trimIndent()
    }

    /**
     * Real profiles render the SITE stats block (Карма/Постов/Комментов) BEFORE the forum stats block.
     * The forum «Постов» is the one shown under the nick; a naive first-match must not stop on a site
     * «Постов» whose area links out without an inline number.
     */
    private fun realisticProfileHtmlSiteStatsBeforeForumPosts(): String {
        return """
            <html><body>
            <div class="user-box"><h1>lexal346</h1></div>
            <ul class="profile-stats site">
                <li>
                    <span class="title">Карма</span>
                    <div class="area">12</div>
                </li>
                <li>
                    <span class="title">Постов</span>
                    <div class="area"><a href="https://4pda.to/lexal346/posts/"></a></div>
                </li>
            </ul>
            <ul class="profile-stats forum">
                <li>
                    <span class="title">Репутация</span>
                    <div class="area"><a href="https://4pda.to/forum/index.php?showuser=9876543&amp;view=rep">10</a></div>
                </li>
                <li>
                    <span class="title">Постов</span>
                    <div class="area">
                        <a href="https://4pda.to/forum/index.php?act=search&source=pst&mid=9876543&result=posts">547</a>
                    </div>
                </li>
            </ul>
            </body></html>
        """.trimIndent()
    }

    private fun profileHtmlWithoutForumPostCount(): String {
        return """
            <html><body>
            <div class="user-box"><h1>Vedmak08</h1></div>
            <span class="title">ID пользователя</span>
            <div class="area">777</div>
            <span class="title">Репутация</span>
            <div class="area">55</div>
            </body></html>
        """.trimIndent()
    }

    private fun profileHtmlWithEmptyForumPostCount(): String {
        return """
            <html><body>
            <span class="title">Постов</span>
            <div class="area"><a href="https://4pda.to/forum/index.php?act=search&source=pst&username=Vedmak08"></a></div>
            </body></html>
        """.trimIndent()
    }

    private fun profileHtmlWithZeroForumPostCount(): String {
        return """
            <html><body>
            <span class="title">Сообщений</span>
            <div class="area">0</div>
            </body></html>
        """.trimIndent()
    }

    private fun productionThemeTemplate(): ThemeTemplate {
        val context = mockk<Context>(relaxed = true)
        val templateManager = mockk<TemplateManager>(relaxed = true)
        val authHolder = mockk<AuthHolder>(relaxed = true)
        val mainPreferencesHolder = mockk<MainPreferencesHolder>(relaxed = true)
        val topicPreferencesHolder = mockk<TopicPreferencesHolder>(relaxed = true)

        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { templateManager.getThemeType() } returns "light"
        every { templateManager.getThemeOverridesCss() } returns ""
        every { mainPreferencesHolder.getTopicScrollMode() } returns Preferences.Main.TopicScrollMode.CLASSIC
        every { mainPreferencesHolder.getTopicPostDensity() } returns Preferences.Main.TopicPostDensity.COMFORTABLE
        every { topicPreferencesHolder.getShowAvatars() } returns true
        every { topicPreferencesHolder.getCircleAvatars() } returns false
        every { context.getString(R.string.hat) } returns "Шапка"
        every { context.getString(R.string.res_s_group) } returns "Группа"
        every { context.getString(R.string.poll_all_votes_count) } returns "Всего голосов"
        every { context.getString(R.string.poll_vote_btn) } returns "Голосовать"
        every { context.getString(R.string.poll_results_btn) } returns "Результаты"
        every { context.getString(R.string.poll_show_btn) } returns "Пункты опроса"
        return ThemeTemplate(context, templateManager, authHolder, mainPreferencesHolder, topicPreferencesHolder)
    }

    private fun realTopicTemplate(): MiniTemplator {
        val templatePath = listOf(
                Path.of("src/main/assets/template_theme.html"),
                Path.of("app/src/main/assets/template_theme.html")
        ).first { Files.exists(it) }
        return MiniTemplator.Builder()
                .setSkipUndefinedVars(true)
                .build(Files.newInputStream(templatePath), Charset.forName("utf-8"))
    }
}
