package forpdateam.ru.forpda.appupdates

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник обновлений — последний релиз на GitHub (вместо шапки темы 4pda,
 * которая иногда парсилась с ошибками).
 *
 * Использует Atom-фид релизов (`github.com/OWNER/REPO/releases.atom`), а НЕ
 * `api.github.com`: последний для неавторизованных запросов лимитирован 60/час
 * НА IP, и за CGNAT оператора все пользователи приложения делят этот лимит →
 * массовые 403 «rate limited». Atom-фид отдаётся с github.com (CDN) и такому
 * жёсткому лимиту не подвержен. Плата: в фиде НЕТ ссылок на APK-ассеты — версия
 * берётся из тега (title записи), а обновление ведёт на тему 4pda (см.
 * AppUpdateRepository.TOPIC_URL / кнопку «Открыть»), без встроенного скачивания.
 *
 * Класс open, а сетевой [fetchLatestRelease] и чистые [parseAtom] / [parseRelease]
 * разделены, чтобы их можно было подменять/тестировать по отдельности.
 */
@Singleton
open class GithubReleaseSource @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @return [Candidate] последнего релиза, либо null если релизов ещё нет
     *   (404) или последний релиз — черновик.
     * @throws AppUpdateRepository.CheckException при сетевых/HTTP/парс-ошибках.
     */
    open fun fetchLatestRelease(): Candidate? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_ATOM_URL)
            .header("Accept", "application/atom+xml")
            .header("User-Agent", USER_AGENT)
            .build()

        val responseBody: String = try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> return null
                    response.code == 403 || response.code == 429 ->
                        throw AppUpdateRepository.CheckException(
                            AppUpdateRepository.FailureReason.RateLimited,
                            "GitHub releases feed rate limited (code=${response.code})"
                        )
                    !response.isSuccessful ->
                        throw AppUpdateRepository.CheckException(
                            if (response.code in 500..599) AppUpdateRepository.FailureReason.Server
                            else AppUpdateRepository.FailureReason.Unknown,
                            "GitHub releases feed returned code=${response.code}"
                        )
                    else -> text
                }
            }
        } catch (e: AppUpdateRepository.CheckException) {
            throw e
        } catch (e: IOException) {
            throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Network,
                "GitHub releases feed network error: ${e.message}",
                e
            )
        }

        return parseAtom(responseBody)
    }

    /**
     * Чистый разбор Atom-фида релизов GitHub (`releases.atom`). Без сети —
     * тестируемо. Берёт ПЕРВУЮ запись `<entry>` (самый свежий релиз): версию из
     * `<title>` (тег), ссылку из `<link rel="alternate">` (страница релиза),
     * описание из `<content>`. APK-ассетов в фиде нет — [Candidate.downloads]
     * всегда пуст.
     *
     * @return [Candidate] последнего релиза, либо null если записей ещё нет.
     */
    fun parseAtom(xml: String): Candidate? {
        val parser = try {
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
                .newPullParser()
                .apply { setInput(StringReader(xml)) }
        } catch (e: Exception) {
            throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Parse,
                "Failed to init Atom parser",
                e
            )
        }

        var inEntry = false
        var title: String? = null
        var link: String? = null
        var content: String? = null
        try {
            var event = parser.eventType
            loop@ while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> inEntry = true
                        "title" -> if (inEntry && title == null) title = parser.nextText().trim()
                        "link" -> if (inEntry && link == null) {
                            val rel = parser.getAttributeValue(null, "rel")
                            if (rel == null || rel == "alternate") {
                                link = parser.getAttributeValue(null, "href")
                            }
                        }
                        "content" -> if (inEntry && content == null) content = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) break@loop
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Parse,
                "Failed to parse Atom releases feed",
                e
            )
        }

        val tag = title?.trim().orEmpty()
        if (tag.isBlank()) return null

        // SemanticVersion.parse ищет X.Y.Z в строке, поэтому "v3.0.0" разбирается как есть.
        val version = SemanticVersion.parse(tag)
            ?: throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Parse,
                "Atom release title is not a semantic version: '$tag'"
            )

        return Candidate(
            version = version,
            url = link?.takeIf { it.isNotBlank() } ?: RELEASES_PAGE_URL,
            description = content?.takeIf { it.isNotBlank() },
            downloads = emptyList()
        )
    }

    /**
     * Чистый разбор JSON ответа GitHub `releases/latest`. Без сети — тестируемо.
     */
    fun parseRelease(json: String): Candidate? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Parse,
                "Failed to parse GitHub release JSON",
                e
            )
        }
        if (root.optBoolean("draft", false)) return null

        // SemanticVersion.parse ищет X.Y.Z в строке, поэтому "v3.0.0" разбирается как есть.
        val tag = root.optString("tag_name").trim()
        val version = SemanticVersion.parse(tag)
            ?: throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Parse,
                "GitHub release tag is not a semantic version: '$tag'"
            )

        val releaseUrl = root.optString("html_url").ifBlank { RELEASES_PAGE_URL }
        val description = root.optString("body").takeIf { it.isNotBlank() }

        val downloads = mutableListOf<DownloadLink>()
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank() && name.endsWith(".apk", ignoreCase = true)) {
                    val size = asset.optLong("size").takeIf { it > 0 }
                    downloads += DownloadLink(url = url, fileName = name, sizeBytes = size)
                }
            }
        }

        return Candidate(
            version = version,
            url = releaseUrl,
            description = description,
            downloads = downloads
        )
    }

    companion object {
        const val OWNER = "lenox108"
        const val REPO = "ProPDA"
        // Atom-фид релизов — основной источник (не лимитируется как api.github.com).
        const val LATEST_RELEASE_ATOM_URL = "https://github.com/$OWNER/$REPO/releases.atom"
        // JSON API оставлен для parseRelease-тестов и возможного авторизованного пути.
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/$OWNER/$REPO/releases/latest"
        private const val USER_AGENT = "ProPDA-AppUpdateChecker"
    }
}
