package forpdateam.ru.forpda.presentation.theme

import android.content.Context
import biz.source_code.miniTemplator.MiniTemplator
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.theme.Poll
import forpdateam.ru.forpda.entity.remote.theme.PollQuestion
import forpdateam.ru.forpda.entity.remote.theme.PollQuestionItem
import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import forpdateam.ru.forpda.model.preferences.ForumBlacklistedUser
import forpdateam.ru.forpda.model.preferences.MainPreferencesHolder
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.ui.TemplateManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.Charset

class ThemeTemplateTest {

    private lateinit var context: Context
    private lateinit var templateManager: TemplateManager
    private lateinit var authHolder: AuthHolder
    private lateinit var mainPreferencesHolder: MainPreferencesHolder
    private lateinit var topicPreferencesHolder: TopicPreferencesHolder
    private lateinit var themeTemplate: ThemeTemplate

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        templateManager = mockk(relaxed = true)
        authHolder = mockk(relaxed = true)
        mainPreferencesHolder = mockk(relaxed = true)
        topicPreferencesHolder = mockk(relaxed = true)

        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { testTemplate() }
        every { templateManager.getThemeType() } returns "light"
        every { templateManager.getThemeOverridesCss() } returns ""
        every { mainPreferencesHolder.getTopicScrollMode() } returns forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.CLASSIC
        every { mainPreferencesHolder.getTopicPostDensity() } returns forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMFORTABLE
        every { mainPreferencesHolder.getTopicToolbarBehavior() } returns forpdateam.ru.forpda.common.Preferences.Main.TopicToolbarBehavior.PINNED
        every { topicPreferencesHolder.getShowAvatars() } returns false
        every { topicPreferencesHolder.getCircleAvatars() } returns false
        every { topicPreferencesHolder.getHatOpened() } returns false
        every { topicPreferencesHolder.getForumBlacklist() } returns emptyList()
        every { context.getString(R.string.hat) } returns "Шапка"
        every { context.getString(R.string.res_s_group) } returns "Группа"
        every { context.getString(R.string.poll_all_votes_count) } returns "Всего голосов"
        every { context.getString(R.string.poll_vote_btn) } returns "Голосовать"
        every { context.getString(R.string.poll_results_btn) } returns "Результаты"
        every { context.getString(R.string.poll_show_btn) } returns "Пункты опроса"
        every { context.getString(R.string.forum_blacklist_post_hidden) } returns "Сообщение скрыто"
        every { context.getString(R.string.forum_blacklist_posts_hidden) } returns "%d сообщений скрыто"

        themeTemplate = ThemeTemplate(context, templateManager, authHolder, mainPreferencesHolder, topicPreferencesHolder)
    }

    @Test
    fun `own post hides rating buttons`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)

        val html = themeTemplate.mapString(pageWithPost(userId = 42, postRating = "+5"))

        assertTrue(html.contains("class=\"post_rating\""))
        assertFalse(html.contains("class=\"btn rep_up\""))
        assertFalse(html.contains("class=\"btn rep_down\""))
    }

    @Test
    fun `non-own post with known rating shows rating buttons`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)

        val html = themeTemplate.mapString(pageWithPost(userId = 7, postRating = "0"))

        assertTrue(html.contains("class=\"btn rep_up\""))
        assertTrue(html.contains("class=\"btn rep_down\""))
    }

    @Test
    fun `non-own post without rating metadata still shows rating buttons when quote is allowed`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)

        val html = themeTemplate.mapString(pageWithPost(userId = 7, postRating = null).apply {
            canQuote = true
        })

        assertTrue(html.contains("class=\"btn rep_up\""))
        assertTrue(html.contains("class=\"btn rep_down\""))
    }

    @Test
    fun `non-own post without rating metadata hides rating buttons when quote is unavailable`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)

        val html = themeTemplate.mapString(pageWithPost(userId = 7, postRating = null).apply {
            canQuote = false
        })

        assertFalse(html.contains("class=\"btn rep_up\""))
        assertFalse(html.contains("class=\"btn rep_down\""))
    }

    @Test
    fun `unknown current user id keeps rating buttons visible`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.AUTH)

        val html = themeTemplate.mapString(pageWithPost(userId = AuthData.NO_ID, postRating = "0"))

        assertTrue(html.contains("class=\"btn rep_up\""))
        assertTrue(html.contains("class=\"btn rep_down\""))
    }

    @Test
    fun `blacklisted forum post renders compact placeholder without post content`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(ForumBlacklistedUser(7, "Blocked"))

        val html = themeTemplate.mapString(pageWithPost(userId = 7, postRating = "0").apply {
            posts.first().apply {
                id = 100
                nick = "Blocked"
                date = "22.05.2026, 12:00"
                body = "Hidden body"
            }
        })

        assertTrue(html.contains("name=\"entry100\""))
        assertTrue(html.contains("class=\"post_container blacklisted_post\""))
        assertTrue(html.contains("blacklisted_post_placeholder"))
        assertTrue(html.contains("Сообщение скрыто"))
        assertTrue(html.contains("toggleBlacklistedPost('100'"))
        assertTrue(html.contains("class=\"blacklisted_post_content\" aria-hidden=\"true\" hidden"))
        assertTrue(html.contains("Hidden body"))
        val hiddenContentIndex = html.indexOf("class=\"blacklisted_post_content\"")
        val hiddenBodyIndex = html.indexOf("Hidden body")
        val nickIndex = html.indexOf(">Blocked<")
        assertTrue(hiddenContentIndex > 0)
        assertTrue(hiddenBodyIndex > hiddenContentIndex)
        assertTrue(nickIndex > hiddenContentIndex)
        assertFalse(nickIndex in 0 until hiddenContentIndex)
    }

    @Test
    fun `render signature changes when forum blacklist changes`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { topicPreferencesHolder.getForumBlacklist() } returns emptyList()
        val initial = themeTemplate.renderSignature()

        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(ForumBlacklistedUser(7, "Blocked"))
        val withBlacklist = themeTemplate.renderSignature()

        assertFalse(initial == withBlacklist)
    }

    @Test
    fun `topic hat uses first post rating value without first post buttons`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=0"
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                body = "Hat body"
                number = 1
            }
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                postRating = "0"
                canPlusPostRating = true
                canMinusPostRating = true
                nick = "Tester"
                body = "Hat body"
                number = 1
            })
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                postRating = "0"
                nick = "Second"
                body = "Second body"
                number = 2
            })
        })

        val topHatHtml = html.topHatHtml()
        assertTrue(topHatHtml, topHatHtml.contains("<b>0</b>"))
        assertFalse(topHatHtml.contains("class=\"btn rep_up aec\""))
        assertFalse(topHatHtml.contains("class=\"btn rep_down aec\""))
    }

    @Test
    fun `topic hat uses resolved first post voted rating state`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=0"
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                body = "Hat body"
                number = 1
            }
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                postRating = "+5"
                nick = "Tester"
                body = "Hat body"
                number = 1
            })
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                postRating = "0"
                canPlusPostRating = true
                canMinusPostRating = true
                nick = "Second"
                body = "Second body"
                number = 2
            })
        })

        val topHatHtml = html.topHatHtml()
        assertTrue(topHatHtml.contains("<b>+5</b>"))
        assertFalse(topHatHtml.contains("class=\"btn rep_up aec\""))
        assertFalse(topHatHtml.contains("class=\"btn rep_down aec\""))
        assertFalse(html.contains("class=\"topic_hat_entry\""))
    }

    @Test
    fun `last page with cached hat renders all posts in hybrid mode`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { mainPreferencesHolder.getTopicScrollMode() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.HYBRID

        val posts = (0 until 15).map { index ->
            ThemePost().apply {
                id = 143_734_000 + index
                userId = 7 + index
                nick = "User$index"
                body = "Body $index"
                number = 17_971 + index
            }
        }
        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=17970"
            pagination.current = 1199
            pagination.all = 1199
            pagination.perPage = 15
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 1
                nick = "Hat"
                body = "Hat body"
                number = 1
            }
            this.posts.addAll(posts)
        })

        assertEquals(1, html.countOccurrences("theme_page_container"))
        assertEquals(15, ThemeHtmlMetrics.countListPostContainers(html))
        assertEquals(15, themeTemplate.expectedListPostCount(ThemePage().apply {
            id = 10
            pagination.current = 1199
            topicHatPost = ThemePost().apply { id = 100; number = 1 }
            this.posts.addAll(posts)
        }))
    }

    @Test
    fun `hybrid merge wraps each loaded page in theme_page_container`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { mainPreferencesHolder.getTopicScrollMode() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.HYBRID

        fun page(pageNumber: Int, postId: Int) = ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=${(pageNumber - 1) * 15}"
            pagination.current = pageNumber
            pagination.all = 3
            pagination.perPage = 15
            posts.add(ThemePost().apply {
                id = postId
                userId = 7
                nick = "User$postId"
                body = "Body $postId"
                number = postId
            })
        }

        val base = page(2, 200)
        val merged = themeTemplate.mapHybridPages(base, listOf(page(1, 100), page(2, 200)))

        assertEquals(2, merged.html!!.countOccurrences("theme_page_container"))
        assertTrue(merged.html!!.contains("data-page-number=\"1\""))
        assertTrue(merged.html!!.contains("data-page-number=\"2\""))
        assertFalse(merged.html!!.contains("class=\"post_container blacklisted_post\" data-post-id"))
    }

    @Test
    fun `blacklisted posts before visible post render compact placeholder not empty anchor`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(ForumBlacklistedUser(7, "Blocked"))

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                nick = "Blocked"
                body = "Hidden"
                number = 1
            })
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                nick = "Visible"
                body = "Shown"
                number = 2
            })
        })

        assertTrue(html.contains("blacklisted_post_placeholder"))
        assertTrue(html.contains("toggleBlacklistedPost('100'"))
        assertTrue(html.contains("class=\"blacklisted_post_content\" aria-hidden=\"true\" hidden"))
        assertTrue(html.contains("name=\"entry101\""))
    }

    @Test
    fun `consecutive blacklisted posts keep separate anchors with hidden content`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(ForumBlacklistedUser(7, "Blocked"))

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            posts.addAll((1..3).map { index ->
                ThemePost().apply {
                    id = 100 + index
                    userId = 7
                    nick = "Blocked"
                    body = "Hidden $index"
                    number = index
                }
            })
        })

        assertEquals(3, html.countOccurrences("class=\"post_container blacklisted_post\""))
        assertEquals(3, html.countOccurrences("class=\"blacklisted_post_content\" aria-hidden=\"true\" hidden"))
        assertTrue(html.contains("name=\"entry101\""))
        assertTrue(html.contains("name=\"entry102\""))
        assertTrue(html.contains("name=\"entry103\""))
    }

    @Test
    fun `interleaved blacklisted and visible posts preserve publication order in html`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(
                ForumBlacklistedUser(7, "BlockedA"),
                ForumBlacklistedUser(9, "BlockedB"),
        )

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            posts.add(ThemePost().apply {
                id = 200
                userId = 8
                nick = "Visible1"
                body = "Visible body 1"
                number = 10
            })
            posts.add(ThemePost().apply {
                id = 201
                userId = 7
                nick = "BlockedA"
                body = "Hidden body 1"
                number = 11
            })
            posts.add(ThemePost().apply {
                id = 202
                userId = 8
                nick = "Visible2"
                body = "Visible body 2"
                number = 12
            })
            posts.add(ThemePost().apply {
                id = 203
                userId = 9
                nick = "BlockedB"
                body = "Hidden body 2"
                number = 13
            })
            posts.add(ThemePost().apply {
                id = 204
                userId = 7
                nick = "BlockedA"
                body = "Hidden body 3"
                number = 14
            })
            posts.add(ThemePost().apply {
                id = 205
                userId = 8
                nick = "Namca"
                body = "Visible body 3"
                number = 15
            })
        })

        assertPostsListOrder(html, 200, 201, 202, 203, 204, 205)
        val postsInner = extractPostsListInner(html)
        assertTrue(postsInner.indexOf("data-post-id=\"201\"") < postsInner.indexOf("Hidden body 1"))
        assertTrue(postsInner.indexOf("data-post-id=\"203\"") < postsInner.indexOf("Hidden body 2"))
        assertTrue(postsInner.indexOf("Visible body 2") < postsInner.indexOf("data-post-id=\"203\""))
        assertTrue(postsInner.indexOf("Hidden body 3") < postsInner.indexOf("data-post-id=\"205\""))
    }

    @Test
    fun `hybrid posts fragment keeps interleaved blacklisted stub order`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { mainPreferencesHolder.getTopicScrollMode() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.HYBRID
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(
                ForumBlacklistedUser(7, "Blocked"),
        )

        val page = ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=24520"
            pagination.current = 1227
            pagination.all = 1227
            pagination.perPage = 20
            posts.add(ThemePost().apply {
                id = 300
                userId = 8
                nick = "Visible"
                body = "Above"
                number = 24522
            })
            posts.add(ThemePost().apply {
                id = 301
                userId = 7
                nick = "Blocked"
                body = "Hidden middle"
                number = 24523
            })
            posts.add(ThemePost().apply {
                id = 302
                userId = 8
                nick = "Namca"
                body = "Below"
                number = 24524
            })
        }

        val fragment = themeTemplate.mapPostsFragment(page)
        assertPostsListOrder(fragment, 300, 301, 302)
        assertTrue(fragment.contains("class=\"post_container blacklisted_post\" data-post-id=\"301\""))
    }

    @Test
    fun `hybrid merge dedupes duplicate blacklisted post across page boundaries`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { mainPreferencesHolder.getTopicScrollMode() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicScrollMode.HYBRID
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(ForumBlacklistedUser(7, "Blocked"))

        fun blacklistedPost(postId: Int, number: Int) = ThemePost().apply {
            id = postId
            userId = 7
            nick = "Blocked"
            body = "Hidden $postId"
            this.number = number
        }

        fun visiblePost(postId: Int, number: Int) = ThemePost().apply {
            id = postId
            userId = 8
            nick = "Visible$postId"
            body = "Body $postId"
            this.number = number
        }

        fun page(pageNumber: Int, vararg posts: ThemePost) = ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=${(pageNumber - 1) * 20}"
            pagination.current = pageNumber
            pagination.all = 2
            pagination.perPage = 20
            this.posts.addAll(posts)
        }

        val pageOne = page(
                1,
                visiblePost(100, 2),
                blacklistedPost(200, 3),
                visiblePost(101, 4),
        )
        val pageTwo = page(
                2,
                blacklistedPost(200, 21),
                blacklistedPost(201, 22),
                visiblePost(102, 23),
        )

        val merged = themeTemplate.mapHybridPages(pageTwo, listOf(pageOne, pageTwo))
        val mergedHtml = merged.html.orEmpty()

        assertEquals(1, mergedHtml.countOccurrences("class=\"post_container blacklisted_post\" data-post-id=\"200\""))
        assertEquals(1, mergedHtml.countOccurrences("class=\"post_container blacklisted_post\" data-post-id=\"201\""))
        assertTrue(mergedHtml.indexOf("data-page-number=\"1\"") < mergedHtml.indexOf("data-page-number=\"2\""))
        assertPostsListOrder(mergedHtml, 100, 200, 101, 201, 102)
    }

    @Test
    fun `duplicate post ids in page list render single blacklisted stub`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { topicPreferencesHolder.getForumBlacklist() } returns listOf(ForumBlacklistedUser(7, "Blocked"))

        val duplicatePost = ThemePost().apply {
            id = 100
            userId = 7
            nick = "Blocked"
            body = "Hidden once"
            number = 1
        }
        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            posts.add(duplicatePost)
            repeat(6) { posts.add(duplicatePost) }
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                nick = "Visible"
                body = "Shown"
                number = 2
            })
        })

        assertEquals(1, html.countOccurrences("class=\"post_container blacklisted_post\" data-post-id=\"100\""))
        assertEquals(1, html.countOccurrences("name=\"entry100\""))
        assertTrue(html.contains("name=\"entry101\""))
    }

    @Test
    fun `topic hat uses cached first page rating without buttons`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        themeTemplate.mapString(ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=0"
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                body = "Hat body"
                number = 1
            }
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                postRating = "+3"
                canPlusPostRating = true
                canMinusPostRating = true
                nick = "Tester"
                body = "Hat body"
                number = 1
            })
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                nick = "Second"
                body = "Second body"
                number = 2
            })
        })

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=20"
            pagination.current = 2
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                body = "Hat body"
                number = 1
            }
            posts.add(ThemePost().apply {
                id = 120
                userId = 8
                nick = "Page two"
                body = "Page two body"
                number = 21
            })
        })

        val topHatHtml = html.topHatHtml()
        assertTrue(topHatHtml.contains("<b>+3</b>"))
        assertFalse(topHatHtml.contains("class=\"btn rep_up aec\""))
        assertFalse(topHatHtml.contains("class=\"btn rep_down aec\""))
    }

    @Test
    fun `real topic template emits top hat rating inside action row without buttons`() {
        val templatePath = Path.of("src/main/assets/template_theme.html")
        val template = MiniTemplator.Builder()
                .setSkipUndefinedVars(true)
                .build(Files.newInputStream(templatePath), Charset.forName("utf-8"))
        template.setVariableOpt("post_id", "100")
        template.setVariableOpt("top_hat_state_class", "open")
        template.setVariableOpt("post_rating", "+3")
        template.setVariableOpt("post_rating_state", "")
        template.setVariableOpt("post_rating_hidden_class", "")
        template.addBlockOpt("top_hat_reply_quote_row")
        template.addBlockOpt("top_hat")

        val html = template.generateOutput()
        val topHatHtml = html.substringAfter("class=\"topic_hat_fixed").substringBefore("class=\"posts_list\"")

        assertTrue(topHatHtml.contains("<div class=\"post_actions_row\">"))
        assertTrue(topHatHtml.contains("<span class=\"post_rating"))
        assertTrue(topHatHtml.contains("<b>+3</b>"))
        assertFalse(topHatHtml.contains("<a class=\"btn rep_up aec\""))
        assertFalse(topHatHtml.contains("<a class=\"btn rep_down aec\""))
    }

    @Test
    fun `topic header initial state is rendered before first layout`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val overlayHtml = themeTemplate.mapString(pageWithTopHat(isHatOpen = true))
        val inlineExpandedHtml = themeTemplate.mapString(pageWithTopHat(isHatOpen = false).apply {
            isInlineHatOpen = true
        })
        val collapsedHtml = themeTemplate.mapString(pageWithTopHat(isHatOpen = false))

        assertTrue(overlayHtml.contains("class=\"topic_hat_fixed post_container top_hat_overlay_host  open\""))
        assertTrue(overlayHtml.contains("<div class=\"hat_content open\">"))
        assertTrue(inlineExpandedHtml.contains("class=\"topic_hat_entry post_container top_hat_entry open"))
        assertTrue(inlineExpandedHtml.contains("<div class=\"hat_content inline_hat_content open\">"))
        assertTrue(collapsedHtml.contains("class=\"topic_hat_fixed post_container top_hat_overlay_host  close\""))
        assertTrue(collapsedHtml.contains("class=\"topic_hat_entry post_container top_hat_entry close"))
        assertTrue(collapsedHtml.contains("<div class=\"hat_content inline_hat_content close\">"))
    }

    @Test
    fun `first page renders topic header only as top block before posts`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val overlayOpenHtml = themeTemplate.mapString(pageWithTopHat(isHatOpen = true).apply {
            isInlineHatOpen = true
        })
        val inlineOnlyHtml = themeTemplate.mapString(pageWithTopHat(isHatOpen = false).apply {
            isInlineHatOpen = true
        })

        assertEquals(1, overlayOpenHtml.countOccurrences("class=\"topic_hat_fixed post_container top_hat_overlay_host"))
        assertEquals(0, overlayOpenHtml.countOccurrences("class=\"topic_hat_entry post_container"))
        assertEquals(1, overlayOpenHtml.countOccurrences("Hat body"))
        assertFalse(overlayOpenHtml.substringAfter("class=\"posts_list\"").contains("class=\"topic_hat_fixed post_container"))

        assertEquals(1, inlineOnlyHtml.countOccurrences("class=\"topic_hat_entry post_container"))
        assertEquals(1, inlineOnlyHtml.countOccurrences("name=\"entry100\""))
        assertTrue(inlineOnlyHtml.indexOf("class=\"topic_hat_entry post_container") > inlineOnlyHtml.indexOf("class=\"posts_list\""))
        assertTrue(inlineOnlyHtml.indexOf("Reply body") > inlineOnlyHtml.indexOf("class=\"posts_list\""))
    }

    @Test
    fun `topic header overlay renders content without title chrome`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(pageWithTopHat(isHatOpen = true))
        val overlayHtml = html
                .substringAfter("class=\"topic_hat_fixed post_container top_hat_overlay_host")
                .substringBefore("<div class=\"posts_list\"")

        assertTrue(overlayHtml.contains("Hat body"))
        assertFalse(overlayHtml.contains("class=\"btn hat_button"))
        assertFalse(overlayHtml.contains("<span>Шапка</span>"))
    }

    @Test
    fun `real topic template renders post count in current post header`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                userPostCount = 19342
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Body"
                number = 1
            })
        })

        val postHeaderHtml = html.substringAfter("class=\"post_container").substringBefore("<div class=\"post_body")
        assertTrue(postHeaderHtml, postHeaderHtml.contains("class=\"inf user_post_count\""))
        assertTrue(postHeaderHtml, postHeaderHtml.contains("aria-label=\"Сообщений: 19342\""))
        assertTrue(postHeaderHtml, postHeaderHtml.contains("<span>19342</span>"))
        assertFalse(postHeaderHtml.contains(">постов<"))
        assertFalse(postHeaderHtml.contains(">сообщений<"))
    }

    @Test
    fun `real topic template renders post count in visible topic hat entry header`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=0"
            pagination.current = 1
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 7
                nick = "Vedmak08"
                group = "Постоянный"
                reputation = "55"
                date = "22.05.2026, 12:00"
                body = "Hat"
                number = 1
            }
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                userPostCount = 19342
                nick = "Vedmak08"
                group = "Постоянный"
                reputation = "55"
                date = "22.05.2026, 12:00"
                body = "Hat"
                number = 1
            })
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                nick = "Reader"
                group = "Members"
                date = "22.05.2026, 12:10"
                body = "Body"
                number = 2
            })
        })

        val hatEntryHeaderHtml = html
                .substringAfter("class=\"topic_hat_fixed post_container")
                .substringBefore("<div class=\"post_body")
        assertTrue(hatEntryHeaderHtml, hatEntryHeaderHtml.contains("class=\"inf user_post_count\""))
        assertTrue(hatEntryHeaderHtml, hatEntryHeaderHtml.contains("aria-label=\"Сообщений: 19342\""))
        assertTrue(hatEntryHeaderHtml, hatEntryHeaderHtml.contains("<span>19342</span>"))
        assertFalse(hatEntryHeaderHtml.contains(">постов<"))
        assertFalse(hatEntryHeaderHtml.contains(">сообщений<"))
        assertFalse(hatEntryHeaderHtml.contains("${"$"}{user_post_count}"))
    }

    @Test
    fun `real topic template omits post count block when value is absent`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Body"
                number = 1
            })
        })

        val postHeaderHtml = html.substringAfter("class=\"post_container").substringBefore("<div class=\"post_body")
        assertFalse(postHeaderHtml.contains("class=\"inf user_post_count\""))
        assertFalse(html.contains("${"$"}{user_post_count}"))
    }

    @Test
    fun `real topic template omits post count block when value is zero`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                userPostCount = 0
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Body"
                number = 1
            })
        })

        val postHeaderHtml = html.substringAfter("class=\"post_container").substringBefore("<div class=\"post_body")
        assertFalse(postHeaderHtml.contains("class=\"inf user_post_count\""))
        assertFalse(postHeaderHtml.contains("Сообщений: 0"))
    }

    @Test
    fun `real topic template renders positive post count only`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                userPostCount = 41
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Body"
                number = 1
            })
        })

        val postHeaderHtml = html.substringAfter("class=\"post_container").substringBefore("<div class=\"post_body")
        assertTrue(postHeaderHtml, postHeaderHtml.contains("class=\"inf user_post_count\""))
        assertTrue(postHeaderHtml, postHeaderHtml.contains("aria-label=\"Сообщений: 41\""))
        assertTrue(postHeaderHtml, postHeaderHtml.contains("<span>41</span>"))
    }

    @Test
    fun `real topic template preserves spoiler image attributes and order`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val body = """
            <div class="post-block spoil close">
                <div class="block-title">Скриншоты</div>
                <div class="block-body">
                    <a href="https://4pda.to/forum/dl/post/1/full-one.png">
                        <img class="attach linked-image" src="https://s.4pda.to/forum/uploads/post-1/thumb-one.png" data-src="https://s.4pda.to/forum/uploads/post-1/thumb-one.png" data-preview="https://4pda.to/forum/dl/post/1/full-one.png" data-attach-id="1" width="320" height="180" alt="Прикрепленное изображение">
                    </a>
                    <img class="linked-image" data-src="//s.4pda.to/forum/uploads/post-1/thumb-two.jpg" data-preview="/forum/dl/post/1/full-two.jpg" data-attach-id="2" width="640" height="360" alt="Прикрепленное изображение">
                </div>
            </div>
        """.trimIndent()

        val html = themeTemplate.mapString(ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                this.body = body
                number = 1
            })
        })
        val firstIndex = html.indexOf("data-attach-id=\"1\"")
        val secondIndex = html.indexOf("data-attach-id=\"2\"")

        assertTrue(html.contains("class=\"post-block spoil close\""))
        assertTrue(html.contains("src=\"https://s.4pda.to/forum/uploads/post-1/thumb-one.png\""))
        assertTrue(html.contains("data-src=\"https://s.4pda.to/forum/uploads/post-1/thumb-one.png\""))
        assertTrue(html.contains("data-preview=\"https://4pda.to/forum/dl/post/1/full-one.png\""))
        assertTrue(html.contains("data-src=\"//s.4pda.to/forum/uploads/post-1/thumb-two.jpg\""))
        assertTrue(html.contains("data-preview=\"/forum/dl/post/1/full-two.jpg\""))
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex)
    }

    @Test
    fun `theme image pattern accepts webp attachments with query`() {
        val url = "https://4pda.to/forum/dl/post/12345/photo.webp?download=1"
        val matcher = forpdateam.ru.forpda.model.data.remote.api.theme.ThemeApi.attachImagesPattern.matcher(url)

        assertTrue(matcher.find())
        assertEquals("4pda.to/forum/dl/post/12345/photo.webp?download=1", matcher.group(1))
    }

    @Test
    fun `voteable poll renders enabled options and results target`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            isPollOpen = true
            poll = Poll().apply {
                title = "Опрос"
                formAction = "https://4pda.to/forum/index.php?showtopic=42"
                formMethod = "post"
                resultsUrl = "https://4pda.to/forum/index.php?showtopic=42&mode=show"
                showResultsButton = true
                voteButton = true
                questions.add(PollQuestion().apply {
                    title = "Выбор"
                    questionItems.add(PollQuestionItem().apply {
                        type = "radio"
                        name = "poll_vote[1]"
                        value = "answer_1"
                        title = "Первый"
                    })
                })
            }
        })

        assertTrue(html.contains("name=\"poll_vote[1]\""))
        assertTrue(html.contains("value=\"answer_1\""))
        assertTrue(html.contains("type=\"radio\""))
        assertContainsPollResultsUrl(html, "https://4pda.to/forum/index.php?showtopic=42&mode=show")
        assertFalse(html.contains("name=\"poll_vote[1]\" value=\"answer_1\" disabled"))
        assertFalse(html.contains("readonly"))
    }

    @Test
    fun `poll with no parsed buttons still renders results fallback`() {
        every { authHolder.get() } returns AuthData(userId = 42, state = AuthState.AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 1050118
            url = "https://4pda.to/forum/index.php?showtopic=1050118&st=0"
            isPollOpen = true
            poll = Poll().apply {
                title = "Опрос"
                questions.add(PollQuestion().apply {
                    title = "Выбор"
                    questionItems.add(PollQuestionItem().apply {
                        type = "radio"
                        name = "poll_vote[1]"
                        value = "answer_1"
                        title = "Первый"
                    })
                })
            }
        })

        assertTrue(html.contains("name=\"poll_vote[1]\""))
        assertTrue(html.contains("<button type=\"submit\""))
        assertContainsPollResultsUrl(html, "https://4pda.to/forum/index.php?showtopic=1050118&st=0&mode=show")
        assertFalse(html.contains("name=\"poll_vote[1]\" value=\"answer_1\" disabled"))
        assertFalse(html.contains("readonly"))
    }

    @Test
    fun `compact density renders density_compact body class`() {
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { mainPreferencesHolder.getTopicPostDensity() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.COMPACT
        every { mainPreferencesHolder.getTopicToolbarBehavior() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL

        val html = themeTemplate.mapString(pageWithPost(userId = 7, postRating = "0"))

        assertTrue(html.contains("density_compact"))
        assertTrue(html.contains("topChromePadding = 0"))
    }

    @Test
    fun `super compact density renders density_super_compact body class`() {
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }
        every { mainPreferencesHolder.getTopicPostDensity() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicPostDensity.SUPER_COMPACT
        every { mainPreferencesHolder.getTopicToolbarBehavior() } returns
                forpdateam.ru.forpda.common.Preferences.Main.TopicToolbarBehavior.HIDE_ON_SCROLL

        val html = themeTemplate.mapString(pageWithPost(userId = 7, postRating = "0"))

        assertTrue(html.contains("density_super_compact"))
        assertTrue(html.contains("topChromePadding = 0"))
    }

    @Test
    fun `readonly result poll still renders results fallback`() {
        every { authHolder.get() } returns AuthData(userId = AuthData.NO_ID, state = AuthState.NO_AUTH)
        every { templateManager.getTemplate(TemplateManager.TEMPLATE_THEME) } answers { realTopicTemplate() }

        val html = themeTemplate.mapString(ThemePage().apply {
            id = 1050118
            url = "https://4pda.to/forum/index.php?showtopic=1050118&st=0"
            isPollOpen = true
            poll = Poll().apply {
                title = "Опрос"
                isResult = true
                questions.add(PollQuestion().apply {
                    title = "Выбор"
                    questionItems.add(PollQuestionItem().apply {
                        title = "Первый"
                        votes = 10
                        percent = 100f
                    })
                })
            }
        })

        assertTrue(html.contains("question_item_votes") || html.contains("num_votes") || html.contains("<span>10</span>"))
        assertFalse(html.contains("readonly"))
    }

    private fun pageWithPost(userId: Int, postRating: String?): ThemePage {
        return ThemePage().apply {
            posts.add(ThemePost().apply {
                id = 100
                this.userId = userId
                this.postRating = postRating
                nick = "Tester"
                body = "Body"
                number = 1
            })
        }
    }

    private fun pageWithTopHat(isHatOpen: Boolean): ThemePage {
        return ThemePage().apply {
            id = 10
            url = "https://4pda.to/forum/index.php?showtopic=10&st=0"
            pagination.current = 1
            this.isHatOpen = isHatOpen
            topicHatPost = ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Hat body"
                number = 1
            }
            posts.add(ThemePost().apply {
                id = 100
                userId = 7
                nick = "Tester"
                group = "Members"
                date = "22.05.2026, 12:00"
                body = "Hat body"
                number = 1
            })
            posts.add(ThemePost().apply {
                id = 101
                userId = 8
                nick = "Reply"
                group = "Members"
                date = "22.05.2026, 12:01"
                body = "Reply body"
                number = 2
            })
        }
    }

    private fun testTemplate(): MiniTemplator {
        val html = """
            <span class="strings">${"$"}{res_s_hat}</span>
            <span class="strings">${"$"}{res_s_group}</span>
            <span class="strings">${"$"}{res_s_poll_all_votes_count}</span>
            <span class="strings">${"$"}{res_s_poll_show_btn}</span>
            <!-- ${"$"}BeginBlock top_hat -->
            <div class="topic_hat_fixed" data-post-id="${"$"}{post_id}">
                <!-- ${"$"}BeginBlock top_hat_reply_quote_row -->
                <div class="post_actions_row">
                    <!-- ${"$"}BeginBlock top_hat_rep_up_block -->
                    <a class="btn rep_up aec" data-post-id="${"$"}{post_id}">+</a>
                    <!-- ${"$"}EndBlock top_hat_rep_up_block -->
                    <span class="post_rating"><b>${"$"}{post_rating}</b></span>
                    <!-- ${"$"}BeginBlock top_hat_rep_down_block -->
                    <a class="btn rep_down aec" data-post-id="${"$"}{post_id}">-</a>
                    <!-- ${"$"}EndBlock top_hat_rep_down_block -->
                </div>
                <!-- ${"$"}EndBlock top_hat_reply_quote_row -->
            </div>
            <!-- ${"$"}EndBlock top_hat -->
            <!-- ${"$"}BeginBlock top_hat_entry -->
            <div class="topic_hat_entry" data-post-id="${"$"}{post_id}">
                <!-- ${"$"}BeginBlock top_hat_entry_reply_quote_row -->
                <div class="post_actions_row">
                    <!-- ${"$"}BeginBlock top_hat_entry_rep_up_block -->
                    <a class="btn rep_up aec" data-post-id="${"$"}{post_id}">+</a>
                    <!-- ${"$"}EndBlock top_hat_entry_rep_up_block -->
                    <span class="post_rating"><b>${"$"}{post_rating}</b></span>
                    <!-- ${"$"}BeginBlock top_hat_entry_rep_down_block -->
                    <a class="btn rep_down aec" data-post-id="${"$"}{post_id}">-</a>
                    <!-- ${"$"}EndBlock top_hat_entry_rep_down_block -->
                </div>
                <!-- ${"$"}EndBlock top_hat_entry_reply_quote_row -->
            </div>
            <!-- ${"$"}EndBlock top_hat_entry -->
            <!-- ${"$"}BeginBlock post -->
            <div name="entry${"$"}{post_id}" class="post_container ${"$"}{blacklisted_post_class}" data-post-id="${"$"}{post_id}" data-user-id="${"$"}{user_id}">
                <!-- ${"$"}BeginBlock blacklisted_stub_open -->
                <button type="button" class="blacklisted_post_placeholder aec" onclick="toggleBlacklistedPost('${"$"}{post_id}', this); return false;" role="button" aria-expanded="false" aria-label="${"$"}{res_s_forum_blacklist_post_hidden}"><span class="blacklisted_post_placeholder_text">${"$"}{res_s_forum_blacklist_post_hidden}</span></button>
                <div class="blacklisted_post_content" aria-hidden="true" hidden>
                <!-- ${"$"}EndBlock blacklisted_stub_open -->
                <!-- ${"$"}BeginBlock blacklisted_post_body -->
                <div class="post_body">${"$"}{body}</div>
                <!-- ${"$"}EndBlock blacklisted_post_body -->
                <!-- ${"$"}BeginBlock blacklisted_post_footer -->
                <!-- ${"$"}BeginBlock blacklisted_reply_quote_row -->
                <div class="post_actions_row">
                    <!-- ${"$"}BeginBlock blacklisted_rep_up_block -->
                    <a class="btn rep_up" data-post-id="${"$"}{post_id}">+</a>
                    <!-- ${"$"}EndBlock blacklisted_rep_up_block -->
                    <span class="post_rating"><b>${"$"}{post_rating}</b></span>
                    <!-- ${"$"}BeginBlock blacklisted_rep_down_block -->
                    <a class="btn rep_down" data-post-id="${"$"}{post_id}">-</a>
                    <!-- ${"$"}EndBlock blacklisted_rep_down_block -->
                </div>
                <!-- ${"$"}EndBlock blacklisted_reply_quote_row -->
                <!-- ${"$"}EndBlock blacklisted_post_footer -->
                <!-- ${"$"}BeginBlock visible_post_body -->
                <div class="post_body">${"$"}{body}</div>
                <!-- ${"$"}EndBlock visible_post_body -->
                <!-- ${"$"}BeginBlock visible_post_footer -->
                <!-- ${"$"}BeginBlock reply_quote_row -->
                <div class="post_actions_row">
                    <!-- ${"$"}BeginBlock rep_up_block -->
                    <a class="btn rep_up" data-post-id="${"$"}{post_id}">+</a>
                    <!-- ${"$"}EndBlock rep_up_block -->
                    <span class="post_rating"><b>${"$"}{post_rating}</b></span>
                    <!-- ${"$"}BeginBlock rep_down_block -->
                    <a class="btn rep_down" data-post-id="${"$"}{post_id}">-</a>
                    <!-- ${"$"}EndBlock rep_down_block -->
                </div>
                <!-- ${"$"}EndBlock reply_quote_row -->
                <!-- ${"$"}EndBlock visible_post_footer -->
                <!-- ${"$"}BeginBlock blacklisted_stub_close -->
                </div>
                <!-- ${"$"}EndBlock blacklisted_stub_close -->
            </div>
            <!-- ${"$"}EndBlock post -->
            <!-- ${"$"}BeginBlock poll_overlay_block -->
            <form action="${"$"}{poll_form_action}" method="${"$"}{poll_form_method}" onsubmit="return submitThemePoll(this)">
                <!-- ${"$"}BeginBlock poll_overlay_question_block -->
                <!-- ${"$"}BeginBlock poll_overlay_default_item -->
                <label class="item ${"$"}{poll_type}">
                    <input type="${"$"}{question_item_type}" name="${"$"}{question_item_name}" value="${"$"}{question_item_value}" ${"$"}{question_item_disabled}>
                    <span>${"$"}{question_item_title}</span>
                </label>
                <!-- ${"$"}EndBlock poll_overlay_default_item -->
                <!-- ${"$"}EndBlock poll_overlay_question_block -->
                <!-- ${"$"}BeginBlock poll_overlay_buttons -->
                <!-- ${"$"}BeginBlock poll_overlay_vote_button -->
                <button type="submit">${"$"}{res_s_poll_vote_btn}</button>
                <!-- ${"$"}EndBlock poll_overlay_vote_button -->
                <!-- ${"$"}BeginBlock poll_overlay_show_results_button -->
                <a onclick="IThemePresenter.showPollResults('${"$"}{poll_results_url}')">${"$"}{res_s_poll_results_btn}</a>
                <!-- ${"$"}EndBlock poll_overlay_show_results_button -->
                <!-- ${"$"}EndBlock poll_overlay_buttons -->
            </form>
            <!-- ${"$"}EndBlock poll_overlay_block -->
        """.trimIndent()
        return MiniTemplator.Builder().build(
                ByteArrayInputStream(html.toByteArray(Charset.forName("utf-8"))),
                Charset.forName("utf-8")
        )
    }

    private fun realTopicTemplate(): MiniTemplator {
        val templatePath = Path.of("src/main/assets/template_theme.html")
        return MiniTemplator.Builder()
                .setSkipUndefinedVars(true)
                .build(Files.newInputStream(templatePath), Charset.forName("utf-8"))
    }

    private fun String.countOccurrences(value: String): Int {
        var count = 0
        var index = indexOf(value)
        while (index >= 0) {
            count++
            index = indexOf(value, index + value.length)
        }
        return count
    }

    private fun extractPostsListInner(html: String): String {
        val startMarker = "<!-- theme_posts_list_start -->"
        val endMarker = "<!-- theme_posts_list_end -->"
        val start = html.indexOf(startMarker).takeIf { it >= 0 }?.plus(startMarker.length) ?: return html
        val end = html.indexOf(endMarker, start).takeIf { it >= 0 } ?: return html.substring(start)
        return html.substring(start, end)
    }

    private fun orderedPostIdsInPostsList(html: String): List<Int> {
        val pattern = Regex(
                """<div\b(?=[^>]*\bclass="[^"]*\bpost_container\b)(?![^>]*\btopic_hat_entry\b)(?![^>]*\btop_hat_entry\b)[^>]*\bdata-post-id="(\d+)"[^>]*>""",
                RegexOption.IGNORE_CASE,
        )
        return pattern.findAll(extractPostsListInner(html)).map { it.groupValues[1].toInt() }.toList()
    }

    private fun assertPostsListOrder(html: String, vararg expectedIds: Int) {
        assertEquals(expectedIds.toList(), orderedPostIdsInPostsList(html))
    }

    private fun String.topHatHtml(): String =
            substringAfter("class=\"topic_hat_fixed")
                    .substringBefore("class=\"posts_list\"")

    private fun assertContainsPollResultsUrl(html: String, url: String) {
        val encodedUrl = url.replace("&", "&amp;")
        assertTrue(
                html.contains("showPollResults('$url')") ||
                        html.contains("showPollResults('$encodedUrl')")
        )
    }
}
