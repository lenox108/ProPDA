package forpdateam.ru.forpda.appupdates

import android.util.Log
import forpdateam.ru.forpda.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

class AppUpdateRepository @Inject constructor(
    private val preferences: AppUpdatePreferences,
    private val githubSource: GithubReleaseSource
) {

    sealed class CheckResult {
        data class UpdateAvailable(
            val version: SemanticVersion,
            val topicUrl: String,
            val description: String?,
            val downloads: List<DownloadLink> = emptyList()
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
                // «Открыть» ведёт на тему обсуждения на 4pda;
                // «Скачать» — прямые ссылки на APK из GitHub-релиза (downloads).
                topicUrl = TOPIC_URL,
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
     * Из ассетов релиза выбирает наиболее подходящий APK под текущий flavor.
     * GitHub-релизы обычно содержат один APK — тогда возвращается он.
     */
    fun pickPreferredDownload(
        downloads: List<DownloadLink>,
        flavor: String = BuildConfig.FLAVOR
    ): DownloadLink? {
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

    private fun loadBestCandidate(verbose: Boolean): Candidate? {
        val best = try {
            githubSource.fetchLatestRelease()
                ?: throw CheckException(FailureReason.NotFound, "No published GitHub release found")
        } catch (e: CheckException) {
            // Фид временно недоступен (лимит/сеть/сервер) — отдаём последнюю
            // известную версию из кэша, чтобы проверка не «падала» ошибкой, а
            // показывала актуальное на момент прошлой успешной проверки состояние.
            // downloads в кэше нет (Atom их не даёт) → обновление ведёт на тему 4pda.
            val cached = cachedCandidate()
            if (cached != null &&
                (e.reason == FailureReason.RateLimited ||
                    e.reason == FailureReason.Network ||
                    e.reason == FailureReason.Server)
            ) {
                logInfo(verbose, "feed failed (%s); serving cached version=%s", e.reason, cached.version)
                cached
            } else {
                throw e
            }
        }
        logInfo(
            verbose,
            "selected latest=%s url=%s downloads=%d",
            best.version,
            best.url,
            best.downloads.size
        )
        return best
    }

    /** Последняя успешно найденная версия из prefs, как [Candidate] без ассетов. */
    private fun cachedCandidate(): Candidate? {
        val version = preferences.getLastFoundVersion()?.let { SemanticVersion.parse(it) } ?: return null
        return Candidate(
            version = version,
            url = GithubReleaseSource.RELEASES_PAGE_URL,
            description = null,
            downloads = emptyList()
        )
    }

    private fun logInfo(enabled: Boolean, message: String, vararg args: Any?) {
        if (!enabled) return
        val formatted = message.formatLogArgs(*args)
        Log.i(LOG_TAG, formatted)
        Timber.tag(LOG_TAG).i(formatted)
    }

    private fun String.formatLogArgs(vararg args: Any?): String {
        return if (args.isEmpty()) this else String.format(this, *args)
    }

    companion object {
        const val LOG_TAG = "AppUpdateCheck"

        // Тема обсуждения приложения на 4pda — цель кнопки «Открыть».
        const val TOPIC_ID = 1121483
        const val TOPIC_URL = "https://4pda.to/forum/index.php?showtopic=$TOPIC_ID"
    }
}
