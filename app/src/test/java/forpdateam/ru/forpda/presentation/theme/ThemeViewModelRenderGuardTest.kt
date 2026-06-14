package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeViewModelRenderGuardTest {

    @Test
    fun shouldRetryWhenListPostsMissingDespiteHatOverlay() {
        val page = ThemePage().apply {
            topicHatPost = ThemePost().apply {
                id = 1
                number = 1
            }
            posts.add(ThemePost().apply { id = 10; number = 17971 })
            posts.add(ThemePost().apply { id = 11; number = 17972 })
            html = """
                <div class="topic_hat_fixed post_container" data-post-id="1"></div>
                <div class="posts_list">
                    <!-- theme_posts_list_start -->
                    <!-- theme_posts_list_end -->
                </div>
            """.trimIndent()
        }

        assertTrue(ThemeHtmlMetrics.shouldRetryRenderWithoutHat(page, expectedListPosts = 2))
    }

    @Test
    fun shouldRetryWhenOnlySubsetOfPostsRenderedWithCachedHat() {
        val page = ThemePage().apply {
            topicHatPost = ThemePost().apply {
                id = 1
                number = 1
            }
            repeat(15) { index ->
                posts.add(ThemePost().apply {
                    id = 1000 + index
                    number = 17971 + index
                })
            }
            html = """
                <div class="topic_hat_fixed post_container" data-post-id="1"></div>
                <div class="posts_list">
                    <!-- theme_posts_list_start -->
                    <div class="post_container" data-post-id="1000"></div>
                    <!-- theme_posts_list_end -->
                </div>
            """.trimIndent()
        }

        assertTrue(ThemeHtmlMetrics.shouldRetryRenderWithoutHat(page, expectedListPosts = 15))
    }

    @Test
    fun shouldNotRetryWhenAllPostsRendered() {
        val page = ThemePage().apply {
            repeat(3) { index ->
                posts.add(ThemePost().apply { id = 1000 + index })
            }
            html = """
                <div class="posts_list">
                    <!-- theme_posts_list_start -->
                    <div class="post_container" data-post-id="1000"></div>
                    <div class="post_container" data-post-id="1001"></div>
                    <div class="post_container" data-post-id="1002"></div>
                    <!-- theme_posts_list_end -->
                </div>
            """.trimIndent()
        }

        assertFalse(ThemeHtmlMetrics.shouldRetryRenderWithoutHat(page, expectedListPosts = 3))
    }

    @Test
    fun shouldNotRetryWhenUnderRenderedWithoutHat() {
        val page = ThemePage().apply {
            repeat(15) { index ->
                posts.add(ThemePost().apply { id = 1000 + index })
            }
            html = """
                <div class="posts_list">
                    <!-- theme_posts_list_start -->
                    <div class="post_container" data-post-id="1000"></div>
                    <div class="post_container" data-post-id="1001"></div>
                    <!-- theme_posts_list_end -->
                </div>
            """.trimIndent()
        }

        assertFalse(ThemeHtmlMetrics.shouldRetryRenderWithoutHat(page, expectedListPosts = 15))
        assertTrue(ThemeHtmlMetrics.isListPostsUnderRendered(page, page.html, expectedListPosts = 15))
    }

    @Test
    fun isListPostsUnderRendered_usesExpectedListCountNotRawPostsWhenHatFiltered() {
        val page = ThemePage().apply {
            topicHatPost = ThemePost().apply { id = 1; number = 1 }
            posts.add(ThemePost().apply { id = 1; number = 1 })
            repeat(2) { index ->
                posts.add(ThemePost().apply { id = 1000 + index })
            }
            html = """
                <div class="posts_list">
                    <!-- theme_posts_list_start -->
                    <div class="post_container" data-post-id="1000"></div>
                    <div class="post_container" data-post-id="1001"></div>
                    <!-- theme_posts_list_end -->
                </div>
            """.trimIndent()
        }

        assertFalse(ThemeHtmlMetrics.isListPostsUnderRendered(page, page.html, expectedListPosts = 2))
        assertTrue(ThemeHtmlMetrics.isListPostsUnderRendered(page, page.html, expectedListPosts = 3))
    }

    @Test
    fun shouldSuppressHybridPreload_ambiguousAllReadSessionOnly() {
        assertTrue(
                TopicUnreadOpenPolicy.shouldSuppressHybridPreload(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.AMBIGUOUS_ALL_READ,
                )
        )
        assertFalse(
                TopicUnreadOpenPolicy.shouldSuppressHybridPreload(
                        TopicUnreadOpenPolicy.TopicOpenSessionKind.READ_RESUME,
                )
        )
    }

    @Test
    fun deferredMetadataPatchEventsDoNotRequireHtmlRebuild() {
        val post = ThemePost().apply {
            id = 400
            postRating = "12"
            canPlusPostRating = true
            canMinusPostRating = true
        }
        val before = mapOf(400 to ThemeDeferredMetadataPatcher.snapshot(
                ThemePost().apply { id = 400 }
        ))
        val events = ThemeDeferredMetadataPatcher.uiEvents(before, listOf(post))
        assertEquals(1, events.size)
        assertTrue(events.single() is ThemeUiEvent.PatchPostRatingUi)
    }
}
