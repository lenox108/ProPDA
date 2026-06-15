package forpdateam.ru.forpda.appupdates

import android.content.Context
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateRepositoryTest {

    @Test
    fun check_usesOnlyHeaderPostAndDoesNotRequestLatestPages() = runTest {
        val webClient = HeaderOnlyWebClient(
            headerHtml = """
                <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                    ProPDA<br>
                    Скачать:<br>
                    Версия: 2.8.2 Исправления и добавления (Lenox30).<br>
                </div>
                <div class="pagination">
                    <a href="index.php?showtopic=${AppUpdateParser.TOPIC_ID}&amp;st=840">43</a>
                    <a href="index.php?showtopic=${AppUpdateParser.TOPIC_ID}&amp;st=860">44</a>
                    <a href="index.php?showtopic=${AppUpdateParser.TOPIC_ID}&amp;st=880">45</a>
                </div>
                <div class="post" id="entry143200099">
                    Тип: Новая версия<br>
                    Версия: 2.9.0<br>
                </div>
            """.trimIndent()
        )
        val repository = AppUpdateRepository(
            webClient = webClient,
            preferences = AppUpdatePreferences(
                RuntimeEnvironment.getApplication().getSharedPreferences("app-update-test", Context.MODE_PRIVATE)
            ),
            parser = AppUpdateParser()
        )

        val result = repository.check(currentVersionName = "2.8.1", manual = true)

        assertTrue(result is AppUpdateRepository.CheckResult.UpdateAvailable)
        assertEquals(SemanticVersion(2, 8, 2), (result as AppUpdateRepository.CheckResult.UpdateAvailable).version)
        assertEquals(AppUpdateParser.TOPIC_URL, result.topicUrl)
        assertEquals(listOf(AppUpdateParser.HEADER_POST_URL), webClient.requestedUrls)
        assertFalse(webClient.requestedUrls.any { it.contains("&st=") })
        assertFalse(webClient.requestedUrls.contains(AppUpdateParser.TOPIC_URL))
    }

    @Test
    fun check_topicUrlIsAlwaysTopicFirstPage() = runTest {
        val webClient = HeaderOnlyWebClient(
            headerHtml = """
                <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                    Скачать:<br>
                    Версия: 2.9.0<br>
                </div>
            """.trimIndent()
        )
        val repository = AppUpdateRepository(
            webClient = webClient,
            preferences = AppUpdatePreferences(
                RuntimeEnvironment.getApplication().getSharedPreferences("app-update-test-2", Context.MODE_PRIVATE)
            ),
            parser = AppUpdateParser()
        )

        val result = repository.check(currentVersionName = "2.8.0", manual = true)

        assertTrue(result is AppUpdateRepository.CheckResult.UpdateAvailable)
        val update = result as AppUpdateRepository.CheckResult.UpdateAvailable
        assertEquals(AppUpdateParser.TOPIC_URL, update.topicUrl)
        assertFalse(update.topicUrl.contains("view=findpost"))
        assertFalse(update.topicUrl.contains("&st="))
    }

    @Test
    fun pickPreferredDownload_emptyListReturnsNull() {
        val repository = newRepository()
        assertNull(repository.pickPreferredDownload(emptyList(), flavor = "parallel"))
    }

    @Test
    fun pickPreferredDownload_picksMatchingFlavor() {
        val repository = newRepository()
        val downloads = listOf(
            AppUpdateParser.DownloadLink("https://x/ProPDA-2.9.3-stableRelease.apk", "ProPDA-2.9.3-stableRelease.apk"),
            AppUpdateParser.DownloadLink("https://x/ProPDA-2.9.3-parallel.apk", "ProPDA-2.9.3-parallel.apk")
        )
        val chosen = repository.pickPreferredDownload(downloads, flavor = "parallel")
        assertEquals("ProPDA-2.9.3-parallel.apk", chosen?.fileName)
    }

    @Test
    fun pickPreferredDownload_fallsBackToFirstWhenNoMatch() {
        val repository = newRepository()
        val downloads = listOf(
            AppUpdateParser.DownloadLink("https://x/ProPDA-2.9.3-stableRelease.apk", "ProPDA-2.9.3-stableRelease.apk"),
            AppUpdateParser.DownloadLink("https://x/ProPDA-2.9.3-beta.apk", "ProPDA-2.9.3-beta.apk")
        )
        val chosen = repository.pickPreferredDownload(downloads, flavor = "store")
        // store: "store" не в именах, "stable" — в первом.
        assertEquals("ProPDA-2.9.3-stableRelease.apk", chosen?.fileName)
    }

    @Test
    fun pickPreferredDownload_singleLinkReturnsThatLink() {
        val repository = newRepository()
        val only = AppUpdateParser.DownloadLink("https://x/ProPDA-2.9.3.apk", "ProPDA-2.9.3.apk")
        val chosen = repository.pickPreferredDownload(listOf(only), flavor = "dev")
        assertEquals(only.url, chosen?.url)
    }

    private fun newRepository(): AppUpdateRepository = AppUpdateRepository(
        webClient = HeaderOnlyWebClient(headerHtml = ""),
        preferences = AppUpdatePreferences(
            RuntimeEnvironment.getApplication().getSharedPreferences("app-update-pref-test", Context.MODE_PRIVATE)
        ),
        parser = AppUpdateParser()
    )

    private class HeaderOnlyWebClient(
        private val headerHtml: String
    ) : IWebClient {
        val requestedUrls = mutableListOf<String>()

        override fun get(url: String): NetworkResponse = response(url)

        override fun request(request: NetworkRequest): NetworkResponse = response(request.url)

        override fun request(request: NetworkRequest, progressListener: IWebClient.ProgressListener?): NetworkResponse =
            response(request.url)

        override fun requestWithoutMobileCookie(request: NetworkRequest): NetworkResponse = response(request.url)

        override fun getAuthKey(): String = ""

        override fun getClientCookies(): Map<String, Cookie> = emptyMap()

        override fun clearCookies() = Unit

        override fun createWebSocketConnection(webSocketListener: WebSocketListener): WebSocket {
            throw UnsupportedOperationException("Not needed for AppUpdateRepository tests")
        }

        private fun response(url: String): NetworkResponse {
            requestedUrls += url
            if (url != AppUpdateParser.HEADER_POST_URL) {
                throw AssertionError("Unexpected update check URL: $url")
            }
            return NetworkResponse(
                url = url,
                code = 200,
                redirect = url,
                body = headerHtml
            )
        }
    }
}
