package forpdateam.ru.forpda.presentation.theme

import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.interactors.theme.ThemeUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import timber.log.Timber

/**
 * Обработчик реального времени для событий темы.
 * Отвечает за обработку WebSocket-уведомлений о новых постах, прочтениях и упоминаниях.
 */
class ThemeRealtimeEventsHandler(
    private val themeUseCase: ThemeUseCase,
    private val uiEvents: MutableSharedFlow<ThemeUiEvent>,
    private val isPageLoaded: () -> Boolean,
    private val getId: () -> Int
) {

    /**
     * Обрабатывает уведомление о событии в теме.
     * Проверяет, что событие от WebSocket, страница загружена,
     * sourceId совпадает с текущей темой и событие не от текущего пользователя.
     */
    fun handleEvent(event: TabNotification) {
        if (BuildConfig.DEBUG) {
            Timber.d("handleEvent ws=${event.isWebSocket} source=${event.source} type=${event.type}")
        }
        if (!event.isWebSocket)
            return
        if (!isPageLoaded())
            return
        if (BuildConfig.DEBUG) {
            Timber.d("handleEvent sourceId=${event.event.sourceId} selfId=${getId()}")
        }
        if (event.event.sourceId != getId())
            return
        if (event.event.userId == themeUseCase.authUserId())
            return

        if (event.source == NotificationEvent.Source.THEME) {
            when (event.type) {
                NotificationEvent.Type.NEW -> uiEvents.tryEmit(ThemeUiEvent.OnEventNew(event))
                NotificationEvent.Type.READ -> uiEvents.tryEmit(ThemeUiEvent.OnEventRead(event))
                NotificationEvent.Type.MENTION -> {
                }
                else -> {
                }
            }
        }
    }
}
