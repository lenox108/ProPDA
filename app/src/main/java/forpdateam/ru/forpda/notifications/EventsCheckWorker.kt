package forpdateam.ru.forpda.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.common.AuthState
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import forpdateam.ru.forpda.model.repository.mentions.MentionsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
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
        private val hatWatcher: forpdateam.ru.forpda.notifications.hatwatch.HatVersionWatcher,
        private val mentionsRepository: MentionsRepository,
        private val webClient: IWebClient,
        private val authHolder: AuthHolder
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Атомарная защита от одновременного прохода двух контуров (periodic + alarm имеют разные
        // unique-work имена и МОГУТ выполниться параллельно в одном процессе; дедуп по lastCheckAt
        // сам по себе не атомарен — оба воркера прочитали бы старое значение, P1 code review).
        // Оба живут в одном процессе, поэтому process-level Mutex сериализует их: второй просто
        // выходит, пока первый держит проверку, — без двойной сети и гонки снапшота.
        if (!workLock.tryLock()) {
            Timber.d("EventsCheckWorker: another run in progress, skip")
            NotifDiagLog.log(applicationContext, "worker: skip (another run in progress)")
            return@withContext Result.success()
        }
        try {
            runGuarded()
        } finally {
            workLock.unlock()
        }
    }

    private suspend fun runGuarded(): Result = withContext(Dispatchers.IO) {
        // Отметка «нас вообще запускали» — пишется ДО любых скипов: самодиагностика в настройках
        // по ней отличает «система не запускает воркер» от «воркер запускается, но выходит пустым».
        prefs.setLastWorkerRunAt(System.currentTimeMillis())

        // Скипаем ТОЛЬКО в foreground при живом сокете: там процесс активен, пинги OkHttp идут,
        // и «connected» надёжно означает доставку. В ФОНЕ «connected» может врать — тихий обрыв
        // сети в Doze или заморозка процесса оставляют сокет-зомби, который числится живым, но
        // ничего не доставляет (полевой лог: «skip (websocket connected, ui=false)» на закрытом
        // приложении). Поэтому в фоне проверку НЕ пропускаем: она дёшева (дедуп last_check_at),
        // служит страховкой поверх сокета, а снапшот-дедуп не даст дубля, если сокет всё-таки
        // доставил событие сам. Прыжок на Main заодно прогоняет отложенные lifecycle-колбэки.
        val uiForeground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (uiForeground && eventsRepository.isWebSocketConnected()) {
            if (BuildConfig.DEBUG) {
                Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: foreground + websocket connected")
            }
            Timber.d("EventsCheckWorker: foreground realtime alive, skip")
            NotifDiagLog.log(applicationContext, "worker: skip (foreground websocket)")
            return@withContext Result.success()
        }
        // Планировщик снимает эту работу, как только push перестают быть нужны, но отмена
        // может и не доехать (гонка, убитый процесс, старая запись в базе WorkManager).
        // Тогда пробуждение всё равно случится — пусть оно хотя бы не ходит в сеть.
        if (!prefs.wantsPushNotifications()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: no push families enabled")
            Timber.d("EventsCheckWorker: push disabled, skip")
            NotifDiagLog.log(applicationContext, "worker: skip (push disabled)")
            return@withContext Result.success()
        }
        if (!prefs.getBgCheckEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip background check: background preference disabled")
            Timber.d("EventsCheckWorker: bgCheck disabled, skip")
            NotifDiagLog.log(applicationContext, "worker: skip (bg disabled)")
            return@withContext Result.success()
        }

        // В холодном фоновом процессе auth-куки гидрируются из EncryptedSharedPreferences
        // асинхронно, а loadForRequest ждёт их максимум 500 мс — на медленных устройствах
        // опрос уходил «гостем»: пустые события + затёртый снапшот. Ждём гидрацию явно;
        // сеть уже гарантирована констрейнтом, ожидание ничего не будит.
        val hydrated = webClient.awaitAuthCookiesHydrated(COOKIE_HYDRATION_TIMEOUT_MS)
        if (!hydrated) {
            Timber.w("EventsCheckWorker: cookie hydration timed out, retry later")
            NotifDiagLog.log(applicationContext, "worker: cookie hydration timeout -> retry")
            return@withContext Result.retry()
        }
        if (authHolder.get().state != AuthState.AUTH) {
            // Обратный split-brain (audit HIGH): если хранилище куки в fallback-режиме, SKIP,
            // скорее всего, ЛОЖНЫЙ — KeyStore не открылся, а куки лежат в encrypted. Форсим
            // перечитывание (retry encrypted upgrade) и перепроверяем.
            if (webClient.isSecureCookieStoreFallback()) {
                NotifDiagLog.log(applicationContext, "worker: not authorized + secure fallback -> reinit")
                webClient.reinitAuthCookies()
            }
            if (authHolder.get().state != AuthState.AUTH) {
                // Всё ещё гость. Если хранилище ВСЁ ЕЩЁ в fallback — просим WorkManager повторить:
                // следующий процесс/попытка может открыть encrypted. Снапшоты НЕ трогаем.
                if (webClient.isSecureCookieStoreFallback() && runAttemptCount < MAX_NETWORK_RETRY_ATTEMPTS) {
                    NotifDiagLog.log(applicationContext, "worker: still fallback -> retry")
                    return@withContext Result.retry()
                }
                Timber.d("EventsCheckWorker: not authorized, skip")
                NotifDiagLog.log(applicationContext, "worker: skip (not authorized)")
                return@withContext Result.success()
            }
        }

        // Дедуп двух триггеров (periodic WorkManager + точный будильник): реальный сетевой
        // проход делает максимум один из них за полуинтервал — иначе двойные пробуждения
        // удвоили бы сетевые запросы, а событий чаще не становится. Интервал — эффективный:
        // «Реже проверять ночью» растягивает его до часа в 00:00–07:00.
        val now = System.currentTimeMillis()
        val hourOfDay = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val effectiveIntervalMin = prefs.getEffectiveBgIntervalMin(hourOfDay)
        val minGapMs = effectiveIntervalMin * 60_000L / 2
        val prevCheckAt = prefs.getLastCheckAt()
        val sinceLastCheck = now - prevCheckAt
        if (sinceLastCheck in 0 until minGapMs) {
            Timber.d("EventsCheckWorker: checked ${sinceLastCheck / 1000}s ago, skip")
            NotifDiagLog.log(applicationContext, "worker: skip (checked ${sinceLastCheck / 1000}s ago)")
            return@withContext Result.success()
        }
        // Слот резервируем сразу (иначе параллельный триггер задвоил бы сеть), но при полном
        // сетевом фейле вернём назад — см. ниже.
        prefs.setLastCheckAt(now)

        NotifDiagLog.log(applicationContext, "worker: run start")
        // Retry при сетевом сбое ЛЮБОГО источника, а не только когда упали все (P1 code review:
        // раньше успех QMS «прятал» падение упоминаний — их events ждали бы полного интервала).
        // Источник, бросивший ошибку, снапшот НЕ двигает (checkSource бросает до saveSnapshot),
        // поэтому на retry успешные источники просто пере-детектят пустоту, а упавший — доберёт.
        var anyNetworkError = false
        fun trackFailure(tag: String, e: Throwable) {
            Timber.e(e, "EventsCheckWorker $tag failed")
            NotifDiagLog.log(applicationContext, "$tag: error ${e.javaClass.simpleName}")
            if (e is java.io.IOException) anyNetworkError = true
        }
        runCatching { checkSource(NotificationEvent.Source.QMS) }
                .onFailure { trackFailure("QMS", it) }
        runCatching { checkSource(NotificationEvent.Source.THEME) }
                .onFailure { trackFailure("THEME", it) }
        runCatching { checkHatVersions() }
                .onSuccess { hadNetError -> if (hadNetError) anyNetworkError = true }
                .onFailure { trackFailure("HAT", it) }
        runCatching { checkMentions() }
                .onFailure { trackFailure("MENTIONS", it) }

        // Самовосстановление alarm-цепи: перевзвод живёт в ресивере будильника, но force-stop
        // снимает будильники, и цепь мертва до следующего запуска приложения. Каждый запуск
        // периодического воркера перевзводит будильник заново — пока жив хоть один контур,
        // второй восстанавливается сам. Дёшево: AlarmManager.set — локальный вызов.
        if (prefs.getBgCheckEnabled() && prefs.wantsPushNotifications()) {
            EventsCheckAlarmScheduler.schedule(applicationContext, effectiveIntervalMin)
        }

        if (anyNetworkError) {
            // Возвращаем слот дедупа, чтобы retry реально перезапустил проверку, а не упёрся в
            // «checked Ns ago». Успевшие источники уже сохранили снапшот — на retry они пере-
            // детектят пустоту без дублей; упавший доберёт свои события.
            prefs.setLastCheckAt(prevCheckAt)
            if (runAttemptCount < MAX_NETWORK_RETRY_ATTEMPTS) {
                NotifDiagLog.log(applicationContext, "worker: network error -> retry (attempt=${runAttemptCount + 1})")
                return@withContext Result.retry()
            }
            NotifDiagLog.log(applicationContext, "worker: network error, giving up until next window")
        }

        Result.success()
    }

    /**
     * Обход watch-list «Следить за новыми версиями»: для каждой темы детектор грузит шапку и
     * пушит, если появился новый apk. В фоне это единственный источник сигнала о новой версии
     * (инспектор избранного смену шапки не сообщает).
     */
    /** @return true, если хотя бы одна проверка шапки упала СЕТЕВОЙ ошибкой (для участия в retry). */
    private fun checkHatVersions(): Boolean {
        if (!prefs.getHatEnabled()) return false
        val topics = prefs.getHatWatchTopics()
        if (topics.isEmpty()) return false
        var networkError = false
        for (topicId in topics) {
            runCatching { hatWatcher.check(topicId) }
                    .onFailure { e ->
                        Timber.e(e, "EventsCheckWorker hat check $topicId failed")
                        if (e is java.io.IOException) networkError = true
                    }
        }
        return networkError
    }

    /** @return true, если реально сходили в сеть (для A-фикса «retry при сетевом фейле»). */
    private fun checkSource(source: NotificationEvent.Source): Boolean {
        val channelEnabled = when (source) {
            NotificationEvent.Source.QMS -> prefs.getQmsEnabled()
            NotificationEvent.Source.THEME -> prefs.getFavEnabled()
            else -> false
        }
        if (!channelEnabled) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip ${source.name} background check: category preference disabled")
            return false
        }

        // Доставка заблокирована системой (нет POST_NOTIFICATIONS / уведомления или канал выключены
        // в Android)? Тогда НЕ ходим в сеть и, главное, НЕ двигаем снапшот — иначе событие «съелось»
        // бы: publish вернул бы null, а база сравнения уехала, и после разблокировки уведомление
        // уже не показалось бы (P1 code review). Снапшот остаётся прежним → после разблокировки
        // события пере-детектятся относительно старой базы.
        val channelId = when (source) {
            NotificationEvent.Source.QMS -> NotificationsService.CHANNEL_QMS_ID
            NotificationEvent.Source.THEME -> NotificationsService.CHANNEL_FAV_ID
            else -> null
        }
        if (channelId != null && !NotificationPublisher.canDeliver(appContext, channelId)) {
            NotifDiagLog.log(appContext, "${source.name}: skip (delivery blocked by system), snapshot kept")
            return false
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
            else -> return false
        }

        val current = when (source) {
            NotificationEvent.Source.QMS -> eventsApi.getQmsEvents()
            NotificationEvent.Source.THEME -> eventsApi.getFavoritesEvents()
            else -> return false
        }

        // Seed первого прохода (как у упоминаний): на пустом снапшоте нельзя отличить «новое с
        // прошлой проверки» от «просто накопленная непрочитанка» — без базы всё сочтётся новым и
        // вывалится пачкой (полевой лог THEME new=28 published=25). Первый проход ТОЛЬКО запоминает
        // базу, не уведомляя. Флаг, а не «saved.isEmpty()»: пустой снапшот бывает и легитимно
        // (в прошлый раз непрочитанного не было — тогда одно новое событие ДОЛЖНО уведомить).
        val seeded = when (source) {
            NotificationEvent.Source.QMS -> prefs.getQmsEventsSeeded()
            NotificationEvent.Source.THEME -> prefs.getFavEventsSeeded()
            else -> true
        }
        if (!seeded) {
            fun markSeeded() = when (source) {
                NotificationEvent.Source.QMS -> prefs.setQmsEventsSeeded(true)
                NotificationEvent.Source.THEME -> prefs.setFavEventsSeeded(true)
                else -> Unit
            }
            // Миграция (code-review P2): если старый снапшот УЖЕ есть (обновление с версии до
            // seed-флагов), он — валидная база. НЕ re-seed'им (иначе события с последней проверки
            // старой версии молча потерялись бы) — помечаем seeded и идём в обычное сравнение.
            if (saved.isNotEmpty()) {
                markSeeded()
                NotifDiagLog.log(appContext, "${source.name}: seed-flag migrated (existing snapshot kept)")
                // не return — падаем в нормальный путь сравнения ниже
            } else {
                // Реально первый проход (свежая установка/очистка) — только эталон, без пуша.
                saveSnapshot(source, current)
                markSeeded()
                NotifDiagLog.log(appContext, "${source.name}: seeded ${current.size} without notifying")
                return true
            }
        }

        // Найти новые события по timeStamp ДО того, как перезапишем снимок: иначе падение
        // между сохранением и публикацией потеряло бы события навсегда.
        // Равный timeStamp при выросшем msgCount — тоже новое: секундная гранулярность
        // инспектора иначе молча теряла бы второе сообщение, пришедшее в ту же секунду.
        val newEvents = current.filter { loaded ->
            val sameSaved = savedEvents.firstOrNull { it.sourceId == loaded.sourceId }
            sameSaved == null || loaded.timeStamp > sameSaved.timeStamp ||
                    (loaded.timeStamp == sameSaved.timeStamp && loaded.msgCount > sameSaved.msgCount)
        }

        val mutedIds: Set<Int> = if (source == NotificationEvent.Source.THEME) prefs.getMutedTopics() else emptySet()
        val onlyImportant = source == NotificationEvent.Source.THEME && prefs.getFavOnlyImportant()
        val afterMute = newEvents.filterNot { it.sourceId in mutedIds }
        val mutedCount = newEvents.size - afterMute.size
        val finalEvents = afterMute.filter { !onlyImportant || it.isImportant }
        val notImportantCount = afterMute.size - finalEvents.size

        // Тема прямо сейчас на экране (foreground с мёртвым сокетом — воркер работает и там):
        // пуш о том, что пользователь читает в этот момент, — шум.
        val toPublish = finalEvents.filterNot {
            source == NotificationEvent.Source.THEME && eventsRepository.isTopicOnScreen(it.sourceId)
        }
        val onScreenSkipped = finalEvents.size - toPublish.size
        // Стопкой при большой пачке (как foreground-путь): иначе долгое отсутствие = десятки
        // отдельных уведомлений в шторке (полевой лог: 25 разом). Порог зеркалит STACKED_MAX.
        // Отслеживаем ФАКТ публикации (publish/publishStacked → null, если система заблокировала
        // доставку в окне между canDeliver и notify): если событие НЕ показалось — снапшот не
        // двигаем, оно переиграет (полностью закрывает P1 из code-review, а не только предпроверкой).
        var publishBlocked = false
        if (toPublish.size > STACKED_MAX) {
            if (NotificationPublisher.publishStacked(appContext, prefs, toPublish) == null) publishBlocked = true
        } else {
            for (event in toPublish) {
                if (NotificationPublisher.publish(appContext, prefs, event) == null) publishBlocked = true
            }
        }
        // Разбивка фильтров: published=0 при new>0 иначе не объясняет себя. Теперь видно, что
        // именно съело события — заглушённые темы (muted) или «Только закреплённые» (notImportant).
        val filterDetail = buildString {
            if (mutedCount > 0) append(" muted=$mutedCount")
            if (notImportantCount > 0) append(" notImportant=$notImportantCount")
            if (onScreenSkipped > 0) append(" onScreen=$onScreenSkipped")
            if (toPublish.size > STACKED_MAX) append(" stacked")
        }
        NotifDiagLog.log(appContext, "${source.name}: total=${current.size} new=${newEvents.size} published=${toPublish.size}$filterDetail")

        // Страховка от затирания базы сравнения: пустой ответ при непустом снапшоте пишем,
        // только если авторизация всё ещё жива (ответ мог разлогинить — saveFromResponse
        // переключает AuthState по member_id=deleted). Иначе после re-login воркер увидел бы
        // «всё новое» и высыпал дубли, а до него — молчал бы.
        if (current.isEmpty() && saved.isNotEmpty() && authHolder.get().state != AuthState.AUTH) {
            Timber.w("EventsCheckWorker: empty ${source.name} response while deauthorized, keep snapshot")
            NotifDiagLog.log(appContext, "${source.name}: deauthorized mid-run, snapshot kept")
            return true
        }
        // Публикация была заблокирована системой в момент показа — снапшот НЕ двигаем, чтобы
        // событие пере-детектилось и показалось, когда доставка разблокируется.
        if (publishBlocked) {
            NotifDiagLog.log(appContext, "${source.name}: publish blocked mid-run, snapshot kept")
            return true
        }
        saveSnapshot(source, current)
        return true
    }

    private fun saveSnapshot(source: NotificationEvent.Source, current: List<NotificationEvent>) {
        val savedSet = current.map { it.sourceEventText.orEmpty() }.toSet()
        when (source) {
            NotificationEvent.Source.QMS -> prefs.setDataQmsEvents(savedSet)
            NotificationEvent.Source.THEME -> prefs.setDataFavoritesEvents(savedSet)
            else -> Unit
        }
    }

    /** @return true, если реально сходили в сеть (для A-фикса «retry при сетевом фейле»). */
    private suspend fun checkMentions(): Boolean {
        if (!prefs.getMentionsEnabled()) {
            if (BuildConfig.DEBUG) Log.i(NOTIFICATIONS_LOG_TAG, "Skip mentions background check: category preference disabled")
            return false
        }
        // Доставка заблокирована системой → не двигаем персист notifiedMentionKeys (P1 code review):
        // иначе упоминание отметилось бы «уже уведомлённым» и после разблокировки не показалось.
        if (!NotificationPublisher.canDeliver(appContext, NotificationsService.CHANNEL_MENTION_ID)) {
            NotifDiagLog.log(appContext, "MENTIONS: skip (delivery blocked by system), keys kept")
            return false
        }

        val items = mentionsRepository.getMentions(FIRST_PAGE_ST).items
        val decision = BackgroundMentionPolicy.decide(
                items = items,
                alreadyNotifiedKeys = prefs.getNotifiedMentionKeys(),
                seeded = prefs.getMentionKeysSeeded(),
                mutedTopicIds = prefs.getMutedTopics()
        )

        var publishBlocked = false
        for (item in decision.toNotify) {
            val event = MentionNotificationMapper.toNotificationEvent(item) ?: continue
            // Упоминание в теме, открытой на экране прямо сейчас, — ОСОЗНАННЫЙ фильтр (юзер видит),
            // ключ можно двигать. А publish==null — это блокировка доставки: тогда ключи не двигаем.
            if (event.fromTheme() && eventsRepository.isTopicOnScreen(event.sourceId)) {
                NotifDiagLog.log(appContext, "MENTIONS: skip topic on screen (${event.sourceId})")
                continue
            }
            val published = NotificationPublisher.publish(
                    appContext,
                    prefs,
                    event,
                    intentUrlOverride = MentionNotificationMapper.intentUrl(item, event)
            )
            if (published == null) publishBlocked = true
        }

        // Ключ помечаем «уведомлённым» только по факту публикации (code-review P1): при блокировке
        // системой в окне между canDeliver и notify упоминание переиграет, а не потеряется.
        if (publishBlocked) {
            NotifDiagLog.log(appContext, "MENTIONS: publish blocked mid-run, keys kept")
            return true
        }
        prefs.setNotifiedMentionKeys(decision.keysToPersist)
        if (decision.markSeeded) {
            prefs.setMentionKeysSeeded(true)
            if (BuildConfig.DEBUG) {
                Log.i(NOTIFICATIONS_LOG_TAG, "Seeded ${decision.keysToPersist.size} mention keys without notifying")
            }
        }
        return true
    }

    companion object {
        private const val NOTIFICATIONS_LOG_TAG = "Notifications"
        /** act=mentions пагинируется через &st=<offset>; 0 — первая (самая свежая) страница. */
        private const val FIRST_PAGE_ST = 0
        /** Щедрый потолок ожидания гидрации куки: Keystore+AES на холодном старте медленных устройств. */
        private const val COOKIE_HYDRATION_TIMEOUT_MS = 10_000L
        /** Сколько раз повторяем цикл при полном сетевом фейле, прежде чем сдаться до следующего окна. */
        private const val MAX_NETWORK_RETRY_ATTEMPTS = 2
        /** Больше этого числа новых событий за раз — публикуем стопкой, а не по одному. Зеркалит foreground. */
        private const val STACKED_MAX = 4
        /**
         * Process-level замок: periodic и alarm-воркеры живут в одном процессе, и Mutex сериализует
         * их проход, делая дедуп по lastCheckAt атомарным (P1 code review). tryLock → второй просто
         * выходит, не дожидаясь первого.
         */
        private val workLock = Mutex()
        const val UNIQUE_NAME = "events_check_periodic"
    }
}
