package forpdateam.ru.forpda.model.data.remote.api.theme

import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.Preferences
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.theme.ThemeTemplate
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.Charset
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeParserUserPostCountTest {

    @Test
    fun `parses post count from author details after first line`() {
        val postHtml = """
            <div class="post_header">
                <span class="post_user_info">
                    <strong>Members</strong><br>
                    <span style="color:#777">Регистрация: 01.01.2020</span><br>
                    Сообщений: <b>1 234</b><br>
                    Реп: (<a href="/forum/index.php?showuser=42"><span data-member-rep="42">55</span></a>)
                </span>
                <span class="post_action"></span>
            </div>
        """.trimIndent()

        assertEquals(1234, parseUserPostCount(postHtml))
    }

    @Test
    fun `parses post count from data member posts attribute`() {
        val postHtml = """
            <div class="post_header">
                <a href="index.php?showuser=42" data-member-posts="987">Member</a>
                <span class="post_user_info">
                    <strong>Members</strong><br>
                    Реп: (<a><span data-member-rep="42">55</span></a>)
                </span>
            </div>
            <span class="post_action"></span>
        """.trimIndent()

        assertEquals(987, parseUserPostCount(postHtml))
    }

    @Test
    fun `parses post count when label and number are split by html`() {
        val postHtml = """
            <span class="post_user_info">
                <strong>Постоянный</strong><br>
                Сообщений:<br><span class="desc">19 342</span><br>
                Реп: (<a><span data-member-rep="42">55</span></a>)
            </span>
            <span class="post_action"></span>
        """.trimIndent()

        assertEquals(19342, parseUserPostCount(postHtml))
    }

    @Test
    fun `parses post count from desktop postdetails block with nbsp`() {
        val postHtml = """
            <span class="post_user_info">
                <strong>Members</strong><br>
            </span>
            <span class="postdetails">
                <center>
                    Сообщений:&nbsp;3794<br>
                </center>
            </span>
            <span class="post_action"></span>
        """.trimIndent()

        assertEquals(3794, parseUserPostCount(postHtml))
    }

    @Test
    fun `returns null when source has no post count`() {
        val postHtml = """
            <span class="post_user_info">
                <strong>Members</strong><br>
                Реп: (<a><span data-member-rep="42">55</span></a>)
            </span>
            <span class="post_action"></span>
        """.trimIndent()

        assertNull(parseUserPostCount(postHtml))
    }

    @Test
    fun `returns null when explicit post count is zero`() {
        val postHtml = """
            <span class="post_user_info">
                <strong>Members</strong><br>
                Сообщений: 0<br>
                Реп: (<a><span data-member-rep="42">55</span></a>)
            </span>
            <span class="post_action"></span>
        """.trimIndent()

        assertNull(parseUserPostCount(postHtml))
    }

    @Test
    fun `returns null when post count marker has malformed empty value`() {
        val postHtml = """
            <span class="post_user_info">
                <strong>Members</strong><br>
                Сообщений: <b></b><br>
                Реп: (<a><span data-member-rep="42">55</span></a>)
            </span>
            <span class="post_action"></span>
        """.trimIndent()

        assertNull(parseUserPostCount(postHtml))
    }

    @Test
    fun `does not parse bare pipe number without explicit post count marker`() {
        val postHtml = """
            <span class="post_user_info">
                <strong>Members</strong><br>
                Регистрация: 01.01.2020 | 0<br>
                Реп: (<a><span data-member-rep="42">55</span></a>)
            </span>
            <span class="post_action"></span>
        """.trimIndent()

        assertNull(parseUserPostCount(postHtml))
    }

    @Test
    fun `post id count map extracts post count from full page block`() {
        val html = """
            <a name="entry100"></a>
            <div class="post_header_container"><div class="post_header" data-test-post="100">
                <span class="post_date">22.05.2026, 12:00 |<a>#1</a></span>
                <a data-av="avatar.jpg">Level13</a>
                <a href="index.php?showuser=7"> </a>
                <font color="#33aa33">Постоянный</font>
                <span class="post_user_info">
                    <strong>Постоянный</strong><br>
                    Регистрация: 01.01.2020<br>
                    Сообщений: 19 342<br>
                    Реп: (<a><span data-member-rep="41">41</span></a>)
                </span>
                <a class="win_minus">-</a> (<a><span data-member-rep="41">41</span></a>)<a class="win_add">+</a><br>
                <span class="post_action"><a href="report">r</a> <a href="edit_post">e</a> <a href="delete">d</a> <a href="pasteQ">q</a></span>
                <div class="post_body">Body</div></div>
            <div class="topic_foot_nav"></div>
        """.trimIndent()

        assertEquals(19342, parseUserPostCountsByPostId(html)[100])
    }

    @Test
    fun `post id count map extracts count from id entry anchor`() {
        val html = """
            <div class="post" id="entry143230576">
                <div class="post_header_container"><div class="post_header">
                    <span class="post_date">22.05.2026, 12:00 |<a>#1</a></span>
                    <font color="#33aa33">Постоянный</font>
                    <a data-av="avatar.jpg">Level13</a>
                    <a href="index.php?showuser=7"> </a>
                    <span class="post_user_info">
                        <strong>Постоянный</strong><br>
                        Сообщений: 19 342<br>
                    </span>
                    (<a><span data-member-rep="41">41</span></a>)<br>
                    <span class="post_action"><a href="report">r</a> <a href="edit_post">e</a> <a href="delete">d</a> <a href="pasteQ">q</a></span>
                    <div class="post_body">Body</div>
                </div></div>
            </div>
            <div class="post" id="entry143230577">
                <div class="post_header_container"><div class="post_header">
                    <span class="post_user_info"><strong>Members</strong><br>Сообщений: 41<br></span>
                    <span class="post_action"></span><div class="post_body">Body</div>
                </div></div>
            </div>
        """.trimIndent()

        val counts = parseUserPostCountsByPostId(html)
        assertEquals(19342, counts[143230576])
        assertEquals(41, counts[143230577])
    }

    @Test
    fun `post id count map extracts desktop postdetails count with nbsp`() {
        val html = """
            <a name="entry100"></a>
            <div class="post_header_container"><div class="post_header">
                <span class="post_date">22.05.2026, 12:00 | <a>#1</a></span>
                <font color="#3a8f3a">Постоянный</font>
                <a data-av="avatar.jpg">Lenox30</a>
                <a href="index.php?showuser=7"> </a>
                <span class="post_user_info"><strong>Постоянный</strong><br></span>
                <span class="postdetails"><center>Сообщений:&nbsp;3794</center></span>
                (<a><span data-member-rep="7">55</span></a>)<br>
                <span class="post_action"></span>
                <div class="post_body">Body</div>
            </div></div>
            <div class="topic_foot_nav"></div>
        """.trimIndent()

        assertEquals(3794, parseUserPostCountsByPostId(html)[100])
    }

    @Test
    fun `production parser assigns post count on main topic parse flow`() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = topicPageHtml(twoPosts = false),
                argUrl = "https://4pda.to/forum/index.php?showtopic=10"
        )

        assertEquals(1, page.posts.size)
        assertEquals(19342, page.posts.first().userPostCount)
    }

    @Test
    fun `production parser to template renders compact post count in visible header`() {
        val page = ThemeParser(loadProductionPatterns()).parsePage(
                response = topicPageHtml(twoPosts = true),
                argUrl = "https://4pda.to/forum/index.php?showtopic=10"
        ).apply {
            topicHatPost = posts.first()
        }
        val html = productionThemeTemplate().mapString(page)
        val visibleHatHeader = html
                .substringAfter("class=\"topic_hat_entry post_container")
                .substringBefore("<div class=\"post_body")

        assertEquals(19342, page.posts.first().userPostCount)
        assertTrue(visibleHatHeader.contains("class=\"inf user_post_count\""))
        assertTrue(visibleHatHeader.contains("<span>19342</span>"))
        assertTrue(visibleHatHeader.contains("aria-label=\"Сообщений: 19342\""))
        assertFalse(visibleHatHeader.contains(">постов<"))
        assertFalse(visibleHatHeader.contains(">сообщений<"))
        assertFalse(visibleHatHeader.contains("${"$"}{user_post_count}"))
    }

    private fun parseUserPostCount(postHtml: String): Int? {
        val parser = ThemeParser(StubPatternProvider)
        val method = ThemeParser::class.java.getDeclaredMethod("parseUserPostCount", String::class.java)
        method.isAccessible = true
        return method.invoke(parser, postHtml) as Int?
    }

    private object StubPatternProvider : IPatternProvider {
        override fun getCurrentVersion(): Int = 0
        override fun getPattern(scope: String, key: String): Pattern = Pattern.compile("")
        override fun update(jsonString: String) = Unit
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

    private fun topicPageHtml(twoPosts: Boolean): String {
        val secondPost = if (!twoPosts) "" else """<a name="entry101"></a><div class="post_header_container"><div class="post_header"><span class="post_date">22.05.2026, 12:10 | <a>#2</a></span><font color="#777777">Members</font><a data-av="">Reader</a><x><a href="index.php?showuser=8"> </a><span class="post_user_info"><strong>Members</strong><br>Сообщений: 41<br></span> (<a><span data-member-rep="8">4</span></a>)<br><span class="post_action"><a href="report">r</a> <a href="edit_post">e</a> <a href="delete">d</a> <a href="pasteQ">q</a></span><div class="post_body">Body</div></div>"""
        return """<html><body><script>var marker = "ipb_input_f:1, ipb_input_t:10,";</script><div class="topic_title_post">Тема<br></div><a name="entry100"></a><div class="post_header_container"><div class="post_header"><span class="post_date">22.05.2026, 12:00 | <a>#1</a></span><font color="#3a8f3a">Постоянный</font><a data-av="avatar.jpg">Vedmak08</a><x><a href="index.php?showuser=7"> </a><span class="post_user_info"><strong>Постоянный</strong><br>Сообщений: 19 342<br></span> (<a><span data-member-rep="7">55</span></a>)<br><span class="post_action"><a href="report">r</a> <a href="edit_post">e</a> <a href="delete">d</a> <a href="pasteQ">q</a></span><div class="post_body">Hat body</div></div>$secondPost<div class="topic_foot_nav"></div></body></html>"""
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

    @Suppress("UNCHECKED_CAST")
    private fun parseUserPostCountsByPostId(pageHtml: String): Map<Int, Int> {
        val parser = ThemeParser(StubPatternProvider)
        val method = ThemeParser::class.java.getDeclaredMethod("parseUserPostCountsByPostId", String::class.java)
        method.isAccessible = true
        return method.invoke(parser, pageHtml) as Map<Int, Int>
    }
}
