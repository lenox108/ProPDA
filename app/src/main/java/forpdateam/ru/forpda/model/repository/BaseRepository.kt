package forpdateam.ru.forpda.model.repository

import forpdateam.ru.forpda.model.SchedulersProvider
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.Flowable
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

open class BaseRepository(
        private val schedulers: SchedulersProvider
) {

    /** Таймаут для сетевых Single (по умолчанию 30s). */
    fun <T> Single<T>.withNetworkTimeout(seconds: Long = 30): Single<T> =
            this.timeout(seconds, TimeUnit.SECONDS, schedulers.io())

    /**
     * Retry с экспоненциальным backoff для временных сетевых ошибок.
     * Не используем для действий с побочными эффектами (send/submit), только для safe GET/LOAD.
     */
    fun <T> Single<T>.withNetworkRetry(
            maxAttempts: Int = 3,
            initialDelayMs: Long = 500
    ): Single<T> = this.retryWhen { errors ->
        errors
            .zipWith(Flowable.range(1, maxAttempts)) { e: Throwable, attempt: Int -> e to attempt }
            .flatMap { (e, attempt) ->
                val transient = e is IOException ||
                        e is SocketTimeoutException ||
                        e is TimeoutException
                if (!transient || attempt >= maxAttempts) {
                    Flowable.error<Long>(e)
                } else {
                    val delay = initialDelayMs * (1L shl (attempt - 1))
                    Flowable.timer(delay, TimeUnit.MILLISECONDS, schedulers.io())
                }
            }
    }

    fun <T> Single<T>.runInIoToUi(): Single<T> = this
            .subscribeOn(schedulers.io())
            .observeOn(schedulers.ui())

    fun <T> Observable<T>.runInIoToUi(): Observable<T> = this
            .subscribeOn(schedulers.io())
            .observeOn(schedulers.ui())

    fun Completable.runInIoToUi(): Completable = this
            .subscribeOn(schedulers.io())
            .observeOn(schedulers.ui())
}
