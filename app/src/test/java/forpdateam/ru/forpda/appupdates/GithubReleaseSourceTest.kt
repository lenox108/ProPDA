package forpdateam.ru.forpda.appupdates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric — чтобы был настоящий org.json (в обычных unit-тестах android.jar
// застаблен returnDefaultValues и JSONObject не парсит).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GithubReleaseSourceTest {

    private val source = GithubReleaseSource()

    @Test
    fun parsesVersionAssetsAndUrl() {
        val json = """
            {
              "tag_name": "v3.0.0",
              "html_url": "https://github.com/lenox108/ProPDA/releases/tag/v3.0.0",
              "body": "release notes",
              "draft": false,
              "assets": [
                {"name": "ProPDA-3.0.0.apk", "browser_download_url": "https://x/ProPDA-3.0.0.apk", "size": 12345},
                {"name": "source.zip", "browser_download_url": "https://x/source.zip", "size": 999}
              ]
            }
        """.trimIndent()

        val candidate = source.parseRelease(json)!!

        assertEquals(SemanticVersion(3, 0, 0), candidate.version)
        assertEquals("https://github.com/lenox108/ProPDA/releases/tag/v3.0.0", candidate.url)
        assertEquals("release notes", candidate.description)
        // Только .apk попадает в downloads, .zip игнорируется.
        assertEquals(1, candidate.downloads.size)
        assertEquals("ProPDA-3.0.0.apk", candidate.downloads.first().fileName)
        assertEquals(12345L, candidate.downloads.first().sizeBytes)
    }

    @Test
    fun draftReleaseReturnsNull() {
        val json = """{"tag_name":"v9.9.9","draft":true}"""
        assertNull(source.parseRelease(json))
    }

    @Test
    fun tagWithoutVPrefixParses() {
        val json = """{"tag_name":"3.1.2","html_url":"https://github.com/x/y/releases/tag/3.1.2"}"""
        assertEquals(SemanticVersion(3, 1, 2), source.parseRelease(json)?.version)
    }
}
