package forpdateam.ru.forpda.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides the process-wide application [CoroutineScope] used for fire-and-
 * forget work that should outlive any single UI surface. The scope uses a
 * [SupervisorJob] (so a failing child does not cancel siblings) and the
 * default [Dispatchers.Default] for CPU work — callers can override per-call
 * with `withContext(Dispatchers.IO)` as needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppCoroutineModule {
    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
