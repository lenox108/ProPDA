package forpdateam.ru.forpda.appupdates

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: AppUpdateRepository,
    private val preferences: AppUpdatePreferences,
    private val notifier: AppUpdateNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!preferences.isCheckEnabled()) return@withContext Result.success()

        runCatching { repository.check() }
            .onSuccess { result ->
                if (result is AppUpdateRepository.CheckResult.UpdateAvailable && repository.shouldNotify(result.version)) {
                    notifier.showUpdate(result)
                    repository.markNotified(result.version)
                }
            }
            .onFailure { error ->
                Timber.e(error, "App update check failed")
                return@withContext Result.retry()
            }
        Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "app_update_check_periodic"
    }
}
