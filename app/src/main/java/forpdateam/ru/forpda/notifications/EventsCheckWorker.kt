package forpdateam.ru.forpda.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Периодический фоновый опрос уведомлений, когда realtime-WebSocket недоступен
 * (приложение закрыто или в фоне).
 *
 * Источники:
 *  - `act=inspector&CODE=qms` — новые сообщения QMS;
 *  - `act=inspector&CODE=fav` — новые сообщения в избранных темах;
 *  - `act=mentions` — упоминания. Отдельный источник, потому что inspector отдаёт события
 *    только с типом NEW: тип MENTION рождается исключительно в WebSocket-канале, и без
 *    этого опроса упоминания в фоне не приходили вовсе.
 *
 * Фильтрация: главный тумблер, тумблеры каналов, per-topic mute и «только важные».
 */
@HiltWorker
class EventsCheckWorker @AssistedInject constructor(
        @Assisted private val appContext: Context,
        @Assisted params: WorkerParameters,
        private val eventsApi: NotificationEventsApi,
        private val prefs: NotificationPreferencesHolder,
        private val eventsRepository: EventsRepository,
        private val mentionsRepository: MentionsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (eventsRepository.isForegroundRealtimeActive()) {
            if (BuildConfig.DEBUG) {
                Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: foreground realtime active")
            }
            Timber.d("EventsCheckWorker: foreground realtime active, skip")
            return@withContext Result.success()
        }
        // Планировщик снимает эту работу, как только push перестают быть нужны, но отмена
        // может и не доехать (гонка, убитый процесс, старая запись в базе WorkManager).
        // Тогда пробуждение всё равно случится — пусть оно хотя бы не ходит в сеть.
        if (!prefs.wantsPushNotifications()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: no push families enabled")
            Timber.d("EventsCheckWorker: push disabled, skip")
            return@withContext Result.success()
        }
        if (!prefs.getBgCheckEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: background preference disabled")
            Timber.d("EventsCheckWorker: bgCheck disabled, skip")
            return@withContext Result.success()
        }

        runCatching { checkSource(NotificationEvent.Source.QMS) }
                .onFailure { Timber.e(it, "EventsCheckWorker QMS failed") }
        runCatching { checkSource(NotificationEvent.Source.THEME) }
                .onFailure { Timber.e(it, "EventsCheckWorker THEME failed") }
        runCatching { checkMentions() }
                .onFailure { Timber.e(it, "EventsCheckWorker mentions failed") }

        Result.success()
    }

    private fun checkSource(source: NotificationEvent.Source) {
        val channelEnabled = when (source) {
            NotificationEvent.Source.QMS -> prefs.getQmsEnabled()
            NotificationEvent.Source.THEME -> prefs.getFavEnabled()
            else -> false
        }
        if (!channelEnabled) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${source.name} background check: category preference disabled")
            return
        }

        val saved = when (source) {
            NotificationEvent.Source.QMS -> prefs.getDataQmsEvents()
            NotificationEvent.Source.THEME -> prefs.getDataFavoritesEvents()
            else -> emptySet()
        }
        val savedResponse = saved.joinToString("\n")
        val savedEvents = when (source) {
            NotificationEvent.Source.QMS -> eventsApi.getQmsEvents(savedResponse)
            NotificationEvent.Source.THEME -> eventsApi.getFavoritesEvents(savedResponse)
            else -> return
        }

        val current = when (source) {
            NotificationEvent.Source.QMS -> eventsApi.getQmsEvents()
            NotificationEvent.Source.THEME -> eventsApi.getFavoritesEvents()
            else -> return
        }

        // Найти новые события по timeStamp ДО того, как перезапишем снимок: иначе падение
        // между сохранением и публикацией потеряло бы события навсегда.
        val newEvents = current.filter { loaded ->
            val sameSaved = savedEvents.firstOrNull { it.sourceId == loaded.sourceId }
            sameSaved == null || loaded.timeStamp > sameSaved.timeStamp
        }

        val mutedIds: Set<Int> = if (source == NotificationEvent.Source.THEME) prefs.getMutedTopics() else emptySet()
        val onlyImportant = source == NotificationEvent.Source.THEME && prefs.getFavOnlyImportant()
        val finalEvents = newEvents
                .filterNot { it.sourceId in mutedIds }
                .filter { !onlyImportant || it.isImportant }

        for (event in finalEvents) {
            NotificationPublisher.publish(appContext, prefs, event)
        }

        val savedSet = current.map { it.sourceEventText.orEmpty() }.toSet()
        when (source) {
            NotificationEvent.Source.QMS -> prefs.setDataQmsEvents(savedSet)
            NotificationEvent.Source.THEME -> prefs.setDataFavoritesEvents(savedSet)
            else -> Unit
        }
    }

    private suspend fun checkMentions() {
        if (!prefs.getMentionsEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip mentions background check: category preference disabled")
            return
        }

        val items = mentionsRepository.getMentions(FIRST_PAGE_ST).items
        val decision = BackgroundMentionPolicy.decide(
                items = items,
                alreadyNotifiedKeys = prefs.getNotifiedMentionKeys(),
                seeded = prefs.getMentionKeysSeeded(),
                mutedTopicIds = prefs.getMutedTopics()
        )

        for (item in decision.toNotify) {
            val event = MentionNotificationMapper.toNotificationEvent(item) ?: continue
            NotificationPublisher.publish(
                    appContext,
                    prefs,
                    event,
                    intentUrlOverride = MentionNotificationMapper.intentUrl(item, event)
            )
        }

        prefs.setNotifiedMentionKeys(decision.keysToPersist)
        if (decision.markSeeded) {
            prefs.setMentionKeysSeeded(true)
            if (BuildConfig.DEBUG) {
                Log.i(NOTIFICATIONS_LOG_TAG, "Seeded ${decision.keysToPersist.size} mention keys without notifying")
            }
        }
    }

    companion object {
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        /** act=mentions пагинируется через &st=<offset>; 0 — первая (самая свежая) страница. */
        private const val FIRST_PAGE_ST = 0
        const val UNIQUE_NAME = "events_check_periodic"
    }
}
