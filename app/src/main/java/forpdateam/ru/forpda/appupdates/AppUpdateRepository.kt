package forpdateam.ru.forpda.appupdates

import android.util.Log
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.client.GoogleCaptchaException
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.client.OnlyShowException
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.NetworkRequest
import forpdateam.ru.forpda.model.data.remote.api.NetworkResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class AppUpdateRepository @Inject constructor(
    private val webClient: IWebClient,
    private val preferences: AppUpdatePreferences,
    private val parser: AppUpdateParser
) {

    sealed class CheckResult {
        data class UpdateAvailable(
            val version: SemanticVersion,
            val topicUrl: String,
            val description: String?,
            val downloads: List<AppUpdateParser.DownloadLink> = emptyList()
        ) : CheckResult()

        data class UpToDate(val latestVersion: SemanticVersion?) : CheckResult()
    }

    class CheckException(
        val reason: FailureReason,
        message: String,
        cause: Throwable? = null
    ) : IOException(message, cause)

    enum class FailureReason {
        Network,
        RateLimited,
        Forbidden,
        NotFound,
        Captcha,
        Parse,
        Server,
        Unknown
    }

    suspend fun check(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        manual: Boolean = false
    ): CheckResult = withContext(Dispatchers.IO) {
        logInfo(manual, "start manual=%s current=%s", manual, currentVersionName)
        val currentVersion = SemanticVersion.parse(currentVersionName) ?: SemanticVersion(0, 0, 0)
        val candidate = loadBestCandidate(verbose = manual)
        preferences.setLastCheckTime(System.currentTimeMillis())
        preferences.setLastFoundVersion(candidate?.version?.toString())

        val result = if (candidate != null && candidate.version > currentVersion) {
            CheckResult.UpdateAvailable(
                version = candidate.version,
                topicUrl = AppUpdateParser.TOPIC_URL,
                description = candidate.description,
                downloads = candidate.downloads
            )
        } else {
            CheckResult.UpToDate(candidate?.version)
        }
        logInfo(
            manual,
            "result manual=%s type=%s latest=%s current=%s",
            manual,
            result::class.java.simpleName,
            candidate?.version,
            currentVersion
        )
        result
    }

    fun shouldNotify(version: SemanticVersion): Boolean {
        return preferences.getLastNotifiedVersion() != version.toString()
    }

    fun markNotified(version: SemanticVersion) {
        preferences.setLastNotifiedVersion(version.toString())
    }

    /**
     * Из списка APK в шапке темы выбирает наиболее подходящий под текущий flavor.
     * Если ни один не подходит — берёт первый, иначе null.
     */
    fun pickPreferredDownload(
        downloads: List<AppUpdateParser.DownloadLink>,
        flavor: String = BuildConfig.FLAVOR
    ): AppUpdateParser.DownloadLink? {
        if (downloads.isEmpty()) return null
        val scored = downloads.map { link ->
            link to flavorScore(link.fileName, flavor)
        }
        val minScore = scored.minOf { it.second }
        val best = scored.firstOrNull { it.second == minScore }?.first
        return best ?: downloads.first()
    }

    private fun flavorScore(fileName: String, flavor: String): Int {
        val name = fileName.lowercase()
        val markers = when (flavor) {
            "store" -> listOf("store", "stable")
            "parallel" -> listOf("parallel", "stable")
            "beta" -> listOf("beta")
            "dev" -> listOf("dev", "debug")
            else -> emptyList()
        }
        return markers.indexOfFirst { name.contains(it) }.let { if (it < 0) Int.MAX_VALUE else it }
    }

    private fun loadBestCandidate(verbose: Boolean): AppUpdateParser.Candidate? {
        val best = loadCandidate(
            AppUpdateParser.HEADER_POST_URL,
            "header",
            throwOnFailure = true,
            verbose = verbose
        ) ?: throw CheckException(FailureReason.Parse, "No update versions found in update topic header post")
        logInfo(
            verbose,
            "selected latest=%s sourceUrl=%s postId=%s st=%s confidence=%s",
            best.version,
            best.url,
            best.postId,
            best.sourceSt,
            best.confidence
        )
        return best
    }

    private fun loadCandidate(
        url: String,
        source: String,
        throwOnFailure: Boolean = false,
        verbose: Boolean
    ): AppUpdateParser.Candidate? {
        val response = loadPage(url, source, throwOnFailure, verbose) ?: return null
        return parseCandidate(response.body, response.redirect.ifBlank { url }, source, verbose)
    }

    private fun loadPage(
        url: String,
        source: String,
        throwOnFailure: Boolean = false,
        verbose: Boolean
    ): NetworkResponse? {
        return try {
            logInfo(verbose, "request source=%s url=%s", source, url)
            webClient.requestWithoutMobileCookie(NetworkRequest.Builder().url(url).build())
                .also { response ->
                    logInfo(
                        verbose,
                        "response source=%s code=%d redirect=%s length=%d title=%s",
                        source,
                        response.code,
                        response.redirect.ifBlank { response.url },
                        response.body.length,
                        response.body.titleClue()
                    )
                    if (response.code == 404) {
                        throw CheckException(FailureReason.NotFound, "Update topic is unavailable")
                    }
                }
        } catch (error: Throwable) {
            val failure = error.toCheckException(source, url)
            logWarn(failure, "failed source=%s url=%s reason=%s message=%s", source, url, failure.reason, failure.message)
            if (throwOnFailure) {
                throw failure
            }
            null
        }
    }

    private fun parseCandidate(html: String, sourceUrl: String, source: String, verbose: Boolean): AppUpdateParser.Candidate? {
        return try {
            parser.findBestCandidate(
                html,
                sourceUrl,
                allowedPostIds = setOf(AppUpdateParser.HEADER_POST_ID)
            )?.also { candidate ->
                logInfo(
                    verbose,
                    "candidate source=%s version=%s postId=%s st=%s url=%s confidence=%d",
                    source,
                    candidate.version,
                    candidate.postId,
                    candidate.sourceSt,
                    candidate.url,
                    candidate.confidence
                )
            } ?: run {
                logWarn(null, "no_candidate source=%s url=%s length=%d title=%s", source, sourceUrl, html.length, html.titleClue())
                null
            }
        } catch (error: Throwable) {
            throw CheckException(
                FailureReason.Parse,
                "Failed to parse update page source=$source title=${html.titleClue()} length=${html.length}",
                error
            )
        }
    }

    private fun Throwable.toCheckException(source: String, url: String): CheckException {
        if (this is CheckException) return this
        val reason = when (this) {
            is OkHttpResponseException -> when (code) {
                403 -> FailureReason.Forbidden
                404 -> FailureReason.NotFound
                429 -> FailureReason.RateLimited
                in 500..599 -> FailureReason.Server
                else -> FailureReason.Unknown
            }
            is GoogleCaptchaException -> FailureReason.Captcha
            is OnlyShowException -> FailureReason.Forbidden
            is IOException -> FailureReason.Network
            else -> FailureReason.Unknown
        }
        return CheckException(reason, "Update check failed source=$source url=$url: ${message.orEmpty()}", this)
    }

    private fun String.titleClue(): String {
        return Regex("""(?is)<title[^>]*>(.*?)</title>""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(120)
            ?: "-"
    }

    private fun logInfo(enabled: Boolean, message: String, vararg args: Any?) {
        if (!enabled) return
        val formatted = message.formatLogArgs(*args)
        Log.i(LOG_TAG, formatted)
        Timber.tag(LOG_TAG).i(formatted)
    }

    private fun logWarn(error: Throwable?, message: String, vararg args: Any?) {
        val formatted = message.formatLogArgs(*args)
        Log.w(LOG_TAG, formatted, error)
        if (error != null) {
            Timber.tag(LOG_TAG).w(error, formatted)
        } else {
            Timber.tag(LOG_TAG).w(formatted)
        }
    }

    private fun String.formatLogArgs(vararg args: Any?): String {
        return if (args.isEmpty()) this else String.format(this, *args)
    }

    companion object {
        const val LOG_TAG = "AppUpdateCheck"
    }
}
