package forpdateam.ru.forpda.appupdates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppUpdateParserTest {

    private val parser = AppUpdateParser()

    @Test
    fun downloadSection_usesMaxVersionFromVersionRows() {
        val html = """
            <div>
            Скачать:<br>
            Версия: 2.7.9 Исправления и добавления (Lenox30).<br>
            Версия: 2.7.8 Исправления и добавления (Lenox30).<br>
            Версия: 2.7.7 Исправления и добавления (Lenox30).<br>
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(SemanticVersion(2, 7, 9), candidate?.version)
        assertFalse(candidate!!.version > SemanticVersion.parse("2.8.0")!!)
        assertTrue(candidate.version > SemanticVersion.parse("2.7.8")!!)
    }

    @Test
    fun newVersionPost_hasHighConfidenceAndFindpostUrl() {
        val html = """
            <a id="entry143200001"></a>
            Тип: Новая версия<br>
            Версия: 2.7.9<br>
            Краткое описание: Исправления и добавления<br>
            Прикрепленные файлы
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.LAST_KNOWN_PAGE_URL)

        assertEquals(SemanticVersion(2, 7, 9), candidate?.version)
        assertEquals("${AppUpdateParser.TOPIC_URL}&view=findpost&p=143200001", candidate?.url)
        assertEquals("Исправления и добавления", candidate?.description)
        assertFalse(candidate!!.version > SemanticVersion.parse("2.8.0")!!)
        assertTrue(candidate.version > SemanticVersion.parse("2.7.8")!!)
    }

    @Test
    fun latestPageUrls_returnsLastFourPagesFromForumPagination() {
        val html = """
            <script>
                pages = parseInt(45);
                url = parseInt(st*20);
            </script>
            <div class="pagination"><span>46</span></div>
        """.trimIndent()

        val urls = parser.findLatestPageUrls(html)

        assertEquals(
            listOf(
                "${AppUpdateParser.TOPIC_URL}&st=840",
                "${AppUpdateParser.TOPIC_URL}&st=860",
                "${AppUpdateParser.TOPIC_URL}&st=880",
                "${AppUpdateParser.TOPIC_URL}&st=900"
            ),
            urls
        )
    }

    @Test
    fun latestPageUrls_deduplicatesShortTopics() {
        val html = """
            <a href="index.php?showtopic=1121483&amp;st=0">1</a>
            <a href="index.php?showtopic=1121483&amp;st=20">2</a>
        """.trimIndent()

        val urls = parser.findLatestPageUrls(html)

        assertEquals(
            listOf(
                "${AppUpdateParser.TOPIC_URL}&st=0",
                "${AppUpdateParser.TOPIC_URL}&st=20"
            ),
            urls
        )
    }

    @Test
    fun pageWithSeveralReleasePosts_prefersHighestVersionAndPostUrl() {
        val html = """
            <div class="post" id="entry143200001">
                Тип: Новая версия<br>
                Версия: 2.8.1<br>
            </div>
            <div class="post" id="entry143200099">
                Тип: Новая версия<br>
                Версия: 2.8.2<br>
                Краткое описание: Исправления
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, "${AppUpdateParser.TOPIC_URL}&st=900")

        assertEquals(SemanticVersion(2, 8, 2), candidate?.version)
        assertEquals("${AppUpdateParser.TOPIC_URL}&view=findpost&p=143200099", candidate?.url)
        assertTrue(candidate!!.version > SemanticVersion.parse("2.8.1")!!)
    }

    @Test
    fun sameVersion_prefersNewestPost() {
        val html = """
            <div class="post" id="entry143200001">
                Тип: Новая версия<br>
                Версия: 2.8.2<br>
            </div>
            <div class="post" id="entry143200099">
                Тип: Новая версия<br>
                Версия: 2.8.2<br>
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, "${AppUpdateParser.TOPIC_URL}&st=900")

        assertEquals(SemanticVersion(2, 8, 2), candidate?.version)
        assertEquals("${AppUpdateParser.TOPIC_URL}&view=findpost&p=143200099", candidate?.url)
    }

    @Test
    fun plainPercentText_doesNotBreakParsing() {
        val html = """
            <div class="post" id="entry143200001">
                Тип: Новая версия<br>
                Версия: 2.8.2<br>
                Краткое описание: исправлено 100% падений
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.LAST_KNOWN_PAGE_URL)

        assertEquals(SemanticVersion(2, 8, 2), candidate?.version)
        assertEquals("исправлено 100% падений", candidate?.description)
    }

    @Test
    fun headerUpdateDate_isNotParsedAsAppVersion() {
        val html = """
            <div>
                ProPDA<br>
                Обновлено 17.05.2026<br>
                Скачать:<br>
                Версия: 2.8.1 Исправления и добавления (Lenox30).<br>
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(SemanticVersion(2, 8, 1), candidate?.version)
    }

    @Test
    fun versionLabelWithDate_isIgnoredInFavorOfAppVersion() {
        val html = """
            <div>
                ProPDA<br>
                Скачать:<br>
                Версия: 17.05.2026<br>
                Версия: 2.8.1 Исправления и добавления (Lenox30).<br>
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(SemanticVersion(2, 8, 1), candidate?.version)
    }

    @Test
    fun allowedHeaderPost_ignoresNewerVersionInLaterPost() {
        val html = """
            <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                ProPDA<br>
                Скачать:<br>
                Версия: 2.8.2 Исправления и добавления (Lenox30).<br>
            </div>
            <div class="post" id="entry143200099">
                Тип: Новая версия<br>
                Версия: 2.9.0<br>
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(
            html,
            AppUpdateParser.HEADER_POST_URL,
            allowedPostIds = setOf(AppUpdateParser.HEADER_POST_ID)
        )

        assertEquals(SemanticVersion(2, 8, 2), candidate?.version)
        assertEquals("${AppUpdateParser.TOPIC_URL}&view=findpost&p=${AppUpdateParser.HEADER_POST_ID}", candidate?.url)
    }

    @Test
    fun allowedHeaderPost_ignoresDateInLaterPost() {
        val html = """
            <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                ProPDA<br>
                Скачать:<br>
                Версия: 2.8.2 Исправления и добавления (Lenox30).<br>
            </div>
            <div class="post" id="entry143200099">
                Обновлено 17.05.2026<br>
                Версия: 17.05.2026<br>
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(
            html,
            AppUpdateParser.HEADER_POST_URL,
            allowedPostIds = setOf(AppUpdateParser.HEADER_POST_ID)
        )

        assertEquals(SemanticVersion(2, 8, 2), candidate?.version)
    }

    @Test
    fun updateDateWithoutVersion_isIgnored() {
        val html = """
            <div>
                ProPDA<br>
                Дата обновления: 17.05.2026<br>
                Последнее обновление темы
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(null, candidate)
    }

    @Test
    fun semanticCompare_handlesTwoDigitMinor() {
        assertTrue(SemanticVersion.parse("2.10.0")!! > SemanticVersion.parse("2.9.9")!!)
    }

    @Test
    fun downloadSection_extractsApkLinkFromHeader() {
        val html = """
            <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                ProPDA<br>
                Скачать:<br>
                <a class="attach_block" href="https://4pda.to/forum/dl/post/143179849/ProPDA-2.9.3-stableRelease.apk">
                    <span class="icon"></span>
                    <span class="title">ProPDA-2.9.3-stableRelease.apk</span>
                    <span class="desc">6,19 МБ, скачиваний: 124</span>
                </a>
                <br>
                Версия: 2.9.3 Исправления и добавления (Lenox30).
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(SemanticVersion(2, 9, 3), candidate?.version)
        assertEquals(1, candidate?.downloads?.size)
        val link = candidate?.downloads?.first()
        assertEquals(
            "https://4pda.to/forum/dl/post/143179849/ProPDA-2.9.3-stableRelease.apk",
            link?.url
        )
        assertEquals("ProPDA-2.9.3-stableRelease.apk", link?.fileName)
        // 6,19 МБ = (6.19 * 1024 * 1024).toLong() = 6_490_685 байт
        assertEquals(6_490_685L, link?.sizeBytes)
    }

    @Test
    fun downloadSection_extractsMultipleApkLinks() {
        val html = """
            <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                Скачать:<br>
                <a href="https://4pda.to/forum/dl/post/143179849/ProPDA-2.9.3-stableRelease.apk">
                    <span class="title">ProPDA-2.9.3-stableRelease.apk</span>
                    <span class="desc">6,19 МБ</span>
                </a>
                <a href="https://4pda.to/forum/dl/post/143179849/ProPDA-2.9.3-parallel.apk">
                    <span class="title">ProPDA-2.9.3-parallel.apk</span>
                    <span class="desc">6,18 МБ</span>
                </a>
                Версия: 2.9.3
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(2, candidate?.downloads?.size)
        val names = candidate?.downloads?.map { it.fileName }
        assertTrue(names?.contains("ProPDA-2.9.3-stableRelease.apk") == true)
        assertTrue(names?.contains("ProPDA-2.9.3-parallel.apk") == true)
    }

    @Test
    fun downloadSection_returnsEmptyDownloadsWhenNoApkLinks() {
        val html = """
            <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                Скачать:<br>
                Версия: 2.9.3 Исправления и добавления.
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals(SemanticVersion(2, 9, 3), candidate?.version)
        assertTrue(candidate?.downloads?.isEmpty() == true)
    }

    @Test
    fun downloadSection_decodesPercentEncodedFileName() {
        val html = """
            <div class="post" id="entry${AppUpdateParser.HEADER_POST_ID}">
                Скачать:<br>
                <a href="https://4pda.to/forum/dl/post/143179849/ProPDA%20v2.9.3.apk">
                    <span class="title">ProPDA v2.9.3.apk</span>
                </a>
                Версия: 2.9.3
            </div>
        """.trimIndent()

        val candidate = parser.findBestCandidate(html, AppUpdateParser.HEADER_POST_URL)

        assertEquals("ProPDA v2.9.3.apk", candidate?.downloads?.firstOrNull()?.fileName)
    }
}
