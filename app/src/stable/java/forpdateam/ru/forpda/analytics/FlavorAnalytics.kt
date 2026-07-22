package forpdateam.ru.forpda.analytics

import android.app.Application
import kotlinx.coroutines.CoroutineScope

/**
 * stable/parallel канал (сайдлоад через 4pda): аналитика ОТКЛЮЧЕНА.
 *
 * AppMetrica здесь отсутствует на classpath (см. `storeImplementation` в build.gradle),
 * поэтому библиотека не пакуется в APK. [Analytics] остаётся с no-op репортером —
 * все вызовы [Analytics.reportError] из `main` тихо игнорируются.
 */
object FlavorAnalytics {
    @Suppress("UNUSED_PARAMETER")
    fun setup(app: Application, scope: CoroutineScope) {
        // Намеренно пусто: в этом канале аналитики нет.
    }
}
