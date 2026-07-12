package forpdateam.ru.forpda.model.interactors.favorites

import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.faviorites.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Держит бейдж непрочитанного избранного живым на весь процесс, даже когда экран «Избранное» закрыт.
 *
 * Зеркалит [forpdateam.ru.forpda.model.interactors.qms.QmsInteractor]. Без этой подписки счётчик
 * `favorites` пересчитывался только внутри FavoritesViewModel (пока открыт экран), а шапка форума
 * счётчик непрочитанного избранного не отдаёт — поэтому циферка на «Полном меню» и в нижней
 * навигации не появлялась, пока пользователь не зайдёт в само избранное.
 */
class FavoritesInteractor(
        private val favoritesRepository: FavoritesRepository,
        private val eventsRepository: EventsRepository
) {

    private val eventsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var eventsJob: Job? = null

    fun subscribeEvents() {
        if (eventsJob?.isActive == true) return
        eventsJob = eventsScope.launch {
            eventsRepository.observeEventsTab().collect { event ->
                // Как и в QMS: исключение при пересчёте (например, сбой Room) не должно отменять
                // подписку на весь процесс — иначе бейдж замрёт до перезапуска приложения.
                runCatching { favoritesRepository.handleEvent(event) }
                        .onFailure { Timber.e(it, "Favorites counters: handleEvent failed") }
            }
        }
    }

    /**
     * Первичный посев счётчика из кэша + inspector при старте/возврате в приложение — чтобы циферка
     * была верной сразу, не дожидаясь ни WS-события, ни захода в избранное.
     */
    fun seedCounter() {
        eventsScope.launch {
            runCatching { favoritesRepository.seedFavoritesCounter() }
                    .onFailure { Timber.e(it, "Favorites counters: seed failed") }
        }
    }
}
