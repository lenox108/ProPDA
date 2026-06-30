package forpdateam.ru.forpda.appupdates

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Источник обновлений — последний релиз на GitHub (вместо шапки темы 4pda,
 * которая иногда парсилась с ошибками). Использует публичный GitHub Releases
 * API; версия берётся из тега релиза, APK — из его ассетов.
 *
 * Класс open, а сетевой [fetchLatestRelease] и чистый [parseRelease] разделены,
 * чтобы их можно было подменять/тестировать по отдельности.
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
     * @return [AppUpdateParser.Candidate] последнего релиза, либо null если
     *   релизов ещё нет (404) или последний релиз — черновик.
     * @throws AppUpdateRepository.CheckException при сетевых/HTTP/парс-ошибках.
     */
    open fun fetchLatestRelease(): AppUpdateParser.Candidate? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
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
                            "GitHub API rate limited (code=${response.code})"
                        )
                    !response.isSuccessful ->
                        throw AppUpdateRepository.CheckException(
                            if (response.code in 500..599) AppUpdateRepository.FailureReason.Server
                            else AppUpdateRepository.FailureReason.Unknown,
                            "GitHub API returned code=${response.code}"
                        )
                    else -> text
                }
            }
        } catch (e: AppUpdateRepository.CheckException) {
            throw e
        } catch (e: IOException) {
            throw AppUpdateRepository.CheckException(
                AppUpdateRepository.FailureReason.Network,
                "GitHub API network error: ${e.message}",
                e
            )
        }

        return parseRelease(responseBody)
    }

    /**
     * Чистый разбор JSON ответа GitHub `releases/latest`. Без сети — тестируемо.
     */
    fun parseRelease(json: String): AppUpdateParser.Candidate? {
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

        val downloads = mutableListOf<AppUpdateParser.DownloadLink>()
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank() && name.endsWith(".apk", ignoreCase = true)) {
                    val size = asset.optLong("size").takeIf { it > 0 }
                    downloads += AppUpdateParser.DownloadLink(url = url, fileName = name, sizeBytes = size)
                }
            }
        }

        return AppUpdateParser.Candidate(
            version = version,
            url = releaseUrl,
            postId = null,
            confidence = 100,
            description = description,
            sourceSt = null,
            downloads = downloads
        )
    }

    companion object {
        const val OWNER = "lenox108"
        const val REPO = "ProPDA"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/$OWNER/$REPO/releases/latest"
        private const val USER_AGENT = "ProPDA-AppUpdateChecker"
    }
}
