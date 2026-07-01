package forpdateam.ru.forpda.appupdates

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun check_updateAvailableFromGithubRelease() = runTest {
        val candidate = Candidate(
            version = SemanticVersion(3, 0, 0),
            url = "https://github.com/lenox108/ProPDA/releases/tag/v3.0.0",
            description = "release notes",
            downloads = listOf(
                DownloadLink("https://x/ProPDA-3.0.0.apk", "ProPDA-3.0.0.apk", 123L)
            )
        )
        val repository = newRepository(FakeGithubReleaseSource(candidate))

        val result = repository.check(currentVersionName = "2.9.9", manual = true)

        assertTrue(result is AppUpdateRepository.CheckResult.UpdateAvailable)
        val update = result as AppUpdateRepository.CheckResult.UpdateAvailable
        assertEquals(SemanticVersion(3, 0, 0), update.version)
        assertEquals(candidate.url, update.topicUrl)
        assertEquals(1, update.downloads.size)
        assertEquals("release notes", update.description)
    }

    @Test
    fun check_upToDateWhenGithubVersionNotNewer() = runTest {
        val candidate = Candidate(
            version = SemanticVersion(3, 0, 0),
            url = "https://github.com/lenox108/ProPDA/releases/tag/v3.0.0",
            description = null
        )
        val repository = newRepository(FakeGithubReleaseSource(candidate))

        val result = repository.check(currentVersionName = "3.0.0", manual = true)

        assertTrue(result is AppUpdateRepository.CheckResult.UpToDate)
        assertEquals(
            SemanticVersion(3, 0, 0),
            (result as AppUpdateRepository.CheckResult.UpToDate).latestVersion
        )
    }

    @Test
    fun pickPreferredDownload_emptyListReturnsNull() {
        assertNull(newRepository().pickPreferredDownload(emptyList(), flavor = "parallel"))
    }

    @Test
    fun pickPreferredDownload_picksMatchingFlavor() {
        val downloads = listOf(
            DownloadLink("https://x/ProPDA-2.9.3-stableRelease.apk", "ProPDA-2.9.3-stableRelease.apk"),
            DownloadLink("https://x/ProPDA-2.9.3-parallel.apk", "ProPDA-2.9.3-parallel.apk")
        )
        val chosen = newRepository().pickPreferredDownload(downloads, flavor = "parallel")
        assertEquals("ProPDA-2.9.3-parallel.apk", chosen?.fileName)
    }

    @Test
    fun pickPreferredDownload_fallsBackToFirstWhenNoMatch() {
        val downloads = listOf(
            DownloadLink("https://x/ProPDA-2.9.3-stableRelease.apk", "ProPDA-2.9.3-stableRelease.apk"),
            DownloadLink("https://x/ProPDA-2.9.3-beta.apk", "ProPDA-2.9.3-beta.apk")
        )
        // store: "store" нет в именах, "stable" — в первом.
        val chosen = newRepository().pickPreferredDownload(downloads, flavor = "store")
        assertEquals("ProPDA-2.9.3-stableRelease.apk", chosen?.fileName)
    }

    @Test
    fun pickPreferredDownload_singleLinkReturnsThatLink() {
        val only = DownloadLink("https://x/ProPDA-2.9.3.apk", "ProPDA-2.9.3.apk")
        val chosen = newRepository().pickPreferredDownload(listOf(only), flavor = "dev")
        assertEquals(only.url, chosen?.url)
    }

    private fun newRepository(
        source: GithubReleaseSource = FakeGithubReleaseSource(null)
    ): AppUpdateRepository = AppUpdateRepository(
        preferences = AppUpdatePreferences(
            RuntimeEnvironment.getApplication().getSharedPreferences("app-update-pref-test", Context.MODE_PRIVATE)
        ),
        githubSource = source
    )

    private class FakeGithubReleaseSource(
        private val candidate: Candidate?
    ) : GithubReleaseSource() {
        override fun fetchLatestRelease(): Candidate? = candidate
    }
}
