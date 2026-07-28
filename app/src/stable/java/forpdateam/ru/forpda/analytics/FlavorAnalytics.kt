package forpdateam.ru.forpda.analytics

import android.app.Application
import kotlinx.coroutines.CoroutineScope

/**
 * Канал stable (сайдлоад через 4pda) — единственный в проекте: аналитика ОТКЛЮЧЕНА.
 *
 * Никаких аналитических SDK в зависимостях нет. [Analytics] остаётся с no-op репортером —
 * все вызовы [Analytics.reportError] из `main` тихо игнорируются.
 */
object FlavorAnalytics {
    @Suppress("UNUSED_PARAMETER")
    fun setup(app: Application, scope: CoroutineScope) {
        // Намеренно пусто: в этом канале аналитики нет.
    }
}
