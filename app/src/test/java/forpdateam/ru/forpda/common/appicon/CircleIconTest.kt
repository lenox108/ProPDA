package forpdateam.ru.forpda.common.appicon

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.appupdates.Candidate
import forpdateam.ru.forpda.appupdates.GithubReleaseSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CircleIconTest {

    private val installed = BuildConfig.VERSION_NAME

    @Test
    fun resolve_usesOwnReleaseWhenAssetIsThere() = runTest {
        val source = FakeSource(existing = setOf(assetUrl(installed, installed)))
        val resolved = CircleIcon(source).resolve(context(), "glass_4")

        assertFalse(resolved.isUpgrade)
        assertEquals(installed, resolved.version)
        assertEquals("ProPDA-$installed-circle-glass_4.apk", resolved.fileName)
    }

    /** Варианты выложены не в каждый релиз — берём самый свежий, где они есть. */
    @Test
    fun resolve_fallsBackToNewestReleaseThatHasVariant() = runTest {
        val source = FakeSource(
                existing = setOf(assetUrl("9.9.0", "9.9.0")),
                tags = listOf("v9.9.1", "v9.9.0", "v1.0.0"),
        )
        val resolved = CircleIcon(source).resolve(context(), "glass_4")

        assertTrue(resolved.isUpgrade)
        assertEquals("9.9.0", resolved.version)
        assertEquals("ProPDA-9.9.0-circle-glass_4.apk", resolved.fileName)
    }

    /** Релиз старше установленного не подходит: Android запрещает downgrade. */
    @Test(expected = CircleIcon.AssetMissingException::class)
    fun resolve_ignoresOlderReleasesAndFails() = runTest {
        val source = FakeSource(
                existing = setOf(assetUrl("1.0.0", "1.0.0")),
                tags = listOf("v1.0.0"),
        )
        CircleIcon(source).resolve(context(), "glass_4")
    }

    @Test
    fun assetName_bakedVariantIsTheBaseApk() {
        assertEquals("ProPDA-3.0.0.apk", CircleIcon.assetName(CircleIcon.BAKED_ID, "3.0.0"))
        assertEquals("ProPDA-3.0.0-circle-puzzle.apk", CircleIcon.assetName("puzzle", "3.0.0"))
    }

    @Test
    fun currentVariant_readsInstalledManifestIcon() {
        val app = RuntimeEnvironment.getApplication()
        val original = app.applicationInfo.icon
        app.applicationInfo.icon = R.mipmap.ic_launcher_puzzle
        try {
            assertEquals("puzzle", CircleIcon.currentVariant(app).id)
        } finally {
            app.applicationInfo.icon = original
        }
    }

    @Test
    fun parseAtomTags_readsTagsNewestFirst() {
        val xml = """
            <feed>
              <entry><link rel="alternate" href="https://github.com/o/r/releases/tag/v3.3.2"/></entry>
              <entry><link rel="alternate" href="https://github.com/o/r/releases/tag/v3.3.1"/></entry>
            </feed>
        """.trimIndent()
        assertEquals(listOf("v3.3.2", "v3.3.1"), GithubReleaseSource().parseAtomTags(xml))
    }

    private fun context() = RuntimeEnvironment.getApplication()

    private fun assetUrl(tagVersion: String, fileVersion: String) =
            "https://github.com/${GithubReleaseSource.OWNER}/${GithubReleaseSource.REPO}" +
                    "/releases/download/v$tagVersion/ProPDA-$fileVersion-circle-glass_4.apk"

    private class FakeSource(
            private val existing: Set<String> = emptySet(),
            private val tags: List<String> = emptyList(),
    ) : GithubReleaseSource() {
        override fun fetchLatestRelease(): Candidate? = null
        override fun assetExists(url: String): Boolean = url in existing
        override fun fetchReleaseTags(): List<String> = tags
    }
}
