package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.entity.remote.theme.ThemePage
import forpdateam.ru.forpda.entity.remote.theme.ThemePost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeHtmlMetricsTest {

    @Test
    fun countListPostContainers_ignoresTopicHatOverlay() {
        val html = """
            <div class="topic_hat_fixed post_container top_hat_overlay_host" data-post-id="1"></div>
            <div class="posts_list">
                <!-- theme_posts_list_start -->
                <div class="post_container" data-post-id="10"></div>
                <div class="post_container" data-post-id="11"></div>
                <!-- theme_posts_list_end -->
            </div>
        """.trimIndent()

        assertEquals(2, ThemeHtmlMetrics.countListPostContainers(html))
    }

    @Test
    fun countListPostContainers_ignoresInlineTopicHatEntry() {
        val html = """
            <div class="posts_list">
                <!-- theme_posts_list_start -->
                <div class="topic_hat_entry post_container" data-post-id="1"></div>
                <div class="post_container" data-post-id="10"></div>
                <div class="post_container" data-post-id="11"></div>
                <!-- theme_posts_list_end -->
            </div>
        """.trimIndent()

        assertEquals(2, ThemeHtmlMetrics.countListPostContainers(html))
    }

    @Test
    fun isListPostsUnderRendered_detectsMissingPosts() {
        val page = ThemePage().apply {
            posts.add(ThemePost().apply { id = 10 })
            posts.add(ThemePost().apply { id = 11 })
        }
        val html = """
            <div class="posts_list">
                <!-- theme_posts_list_start -->
                <div class="post_container" data-post-id="10"></div>
                <!-- theme_posts_list_end -->
            </div>
        """.trimIndent()

        assertTrue(ThemeHtmlMetrics.isListPostsUnderRendered(page, html))
    }

    @Test
    fun isListPostsUnderRendered_falseWhenAllPostsPresent() {
        val page = ThemePage().apply {
            posts.add(ThemePost().apply { id = 10 })
            posts.add(ThemePost().apply { id = 11 })
        }
        val html = """
            <div class="posts_list">
                <!-- theme_posts_list_start -->
                <div class="post_container" data-post-id="10"></div>
                <div class="post_container" data-post-id="11"></div>
                <!-- theme_posts_list_end -->
            </div>
        """.trimIndent()

        assertFalse(ThemeHtmlMetrics.isListPostsUnderRendered(page, html))
    }
}
