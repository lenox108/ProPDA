package forpdateam.ru.forpda.model.repository.checker

import android.util.Log
import forpdateam.ru.forpda.entity.remote.checker.UpdateData
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.remote.api.checker.CheckerApi
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CHECKER_REPO_TAG = "ForPDA.CheckerRepo"

/**
 * Created by radiationx on 28.01.18.
 */
class CheckerRepository(
        private val schedulers: SchedulersProvider,
        private val checkerApi: CheckerApi,
        private val patternProvider: IPatternProvider
) {

    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()
    private val mutex = Mutex()

    @Volatile
    private var cached: UpdateData? = null

    suspend fun checkUpdate(force: Boolean = false): UpdateData = withContext(ioDispatcher) {
        mutex.withLock {
            val fetched = if (!force && cached != null) {
                cached!!
            } else {
                checkerApi.checkUpdate()
            }
            Log.e(CHECKER_REPO_TAG, "check version on updater ${fetched.patternsVersion} > ${patternProvider.getCurrentVersion()}")
            if (fetched.patternsVersion > patternProvider.getCurrentVersion()) {
                val patterns = checkerApi.loadPatterns()
                patternProvider.update(patterns)
            }
            cached = fetched
            fetched
        }
    }
}
