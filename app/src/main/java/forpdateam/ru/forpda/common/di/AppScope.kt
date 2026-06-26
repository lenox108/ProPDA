package forpdateam.ru.forpda.common.di

import javax.inject.Qualifier

/**
 * Qualifier for the process-wide application [kotlinx.coroutines.CoroutineScope]
 * used by use-cases and repositories that need to survive UI lifecycle.
 *
 * The scope is built with a [kotlinx.coroutines.SupervisorJob] so a single failed
 * child does not cancel siblings. It is provided by [AppCoroutineModule] as a
 * `@Singleton` and cancelled in [forpdateam.ru.forpda.App.onTerminate] hooks
 * (best-effort — Android processes are killed by the OS, so cancellation is
 * only meaningful for instrumentation tests and onConfigurationChanged paths).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
