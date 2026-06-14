package forpdateam.ru.forpda.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArticleLinkResolverTest {

    @Test
    fun resolveForNavigation_relativePostId_usesSiteRoot() {
        assertEquals(
                "https://4pda.to/index.php?p=12345",
                ArticleLinkResolver.resolveForNavigation("index.php?p=12345")
        )
    }

    @Test
    fun resolveForNavigation_rootAbsolutePath_unchangedHost() {
        assertEquals(
                "https://4pda.to/stat/go?u=https%3A%2F%2Fexample.com",
                ArticleLinkResolver.resolveForNavigation("/stat/go?u=https%3A%2F%2Fexample.com")
        )
    }

    @Test
    fun normalizeMisplacedForumPrefix_fixesArticlePostUnderForum() {
        assertEquals(
                "https://4pda.to/index.php?p=999",
                ArticleLinkResolver.normalizeMisplacedForumPrefix(
                        "https://4pda.to/forum/index.php?p=999"
                )
        )
    }

    @Test
    fun normalizeMisplacedForumPrefix_keepsForumTopicLinks() {
        val topicUrl = "https://4pda.to/forum/index.php?showtopic=12345"
        assertEquals(topicUrl, ArticleLinkResolver.normalizeMisplacedForumPrefix(topicUrl))
    }

    @Test
    fun normalizeMisplacedForumPrefix_keepsForumFindPostLinks() {
        val findPostUrl = "https://4pda.to/forum/index.php?showtopic=99&view=findpost&p=42"
        assertEquals(findPostUrl, ArticleLinkResolver.normalizeMisplacedForumPrefix(findPostUrl))
    }

    @Test
    fun resolveForNavigation_themeWebViewArticlePost_underForumBase() {
        assertEquals(
                "https://4pda.to/index.php?p=12345",
                ArticleLinkResolver.resolveForNavigation(
                        "https://4pda.to/forum/index.php?p=12345"
                )
        )
    }

    @Test
    fun resolveForNavigation_themeWebViewPagesRedirect_underForumBase() {
        assertEquals(
                "https://4pda.to/pages/go?u=https%3A%2F%2Fexample.com",
                ArticleLinkResolver.resolveForNavigation(
                        "https://4pda.to/forum/pages/go?u=https%3A%2F%2Fexample.com"
                )
        )
    }

    @Test
    fun normalizeMisplacedForumPrefix_fixesSoftwareSectionPath() {
        assertEquals(
                "https://4pda.to/software/tag/fast-installer/",
                ArticleLinkResolver.normalizeMisplacedForumPrefix(
                        "https://4pda.to/forum/software/tag/fast-installer/"
                )
        )
    }

    @Test
    fun resolveForNavigation_blocksUnsafeSchemes() {
        assertNull(ArticleLinkResolver.resolveForNavigation("javascript:alert(1)"))
        assertNull(ArticleLinkResolver.resolveForNavigation("#"))
    }
}
