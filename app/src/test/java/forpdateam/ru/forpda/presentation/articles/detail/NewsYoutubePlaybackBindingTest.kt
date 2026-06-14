package forpdateam.ru.forpda.presentation.articles.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NewsYoutubePlaybackBindingTest {

    @Test
    fun `news js plays youtube inline before external browser`() {
        val newsJs = newsJsFile().readText()

        assertTrue(newsJs.contains("function extractYoutubeVideoIdFromUrl(url)"))
        assertTrue(newsJs.contains("INews.playVideoInArticle(youtubeId)"))
        assertTrue(newsJs.contains("if (this.closest(\".news-video-card\"))"))
        val playIndex = newsJs.indexOf("INews.playVideoInArticle(youtubeId)")
        val externalIndex = newsJs.indexOf("INews.openExternalBrowser(resolved)", playIndex)
        assertTrue(playIndex >= 0)
        assertTrue(externalIndex > playIndex)
    }

    @Test
    fun `article content embeds youtube in webview before link handler`() {
        val fragment = articleContentFragmentFile().readText()

        assertTrue(fragment.contains("private fun embedYoutubeVideoInArticle(videoId: String)"))
        assertTrue(fragment.contains("YouTubeUrl.extractVideoId(url)?.let { videoId ->"))
        assertTrue(fragment.contains("embedYoutubeVideoInArticle(videoId)"))
        assertTrue(fragment.contains("YouTubeLauncher.openApp"))
    }

    private fun newsJsFile(): File =
            File("src/main/assets/forpda/scripts/modules/news.js").takeIf { it.isFile }
                    ?: File("app/src/main/assets/forpda/scripts/modules/news.js")

    private fun articleContentFragmentFile(): File =
            File("src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
                    .takeIf { it.isFile }
                    ?: File("app/src/main/java/forpdateam/ru/forpda/ui/fragments/news/details/ArticleContentFragment.kt")
}
