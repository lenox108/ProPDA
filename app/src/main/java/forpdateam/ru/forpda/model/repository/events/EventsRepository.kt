package forpdateam.ru.forpda.model.repository.events

import android.content.Context
import androidx.collection.ArraySet
import android.util.Log
import forpdateam.ru.forpda.App
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.client.WebSocketController
import forpdateam.ru.forpda.entity.app.TabNotification
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.NetworkStateProvider
import forpdateam.ru.forpda.model.SchedulersProvider
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi
import forpdateam.ru.forpda.model.preferences.NotificationPreferencesHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asCoroutineDispatcher
import okhttp3.Response
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeoutException

class EventsRepository(
        @Suppress("UNUSED_PARAMETER") private val context: Context,
        private val webClient: IWebClient,
        private val eventsApi: NotificationEventsApi,
        private val schedulers: SchedulersProvider,
        private val networkStateProvider: NetworkStateProvider,
        private val authHolder: AuthHolder,
        private val notificationPreferencesHolder: NotificationPreferencesHolder
) {
    companion object {
        private const val LOG_TAG = "EventsRepository"
        private const val STACKED_MAX = 4
    }

    private var timerPeriod = (10 * 1000).toLong()

    private val repoJob = SupervisorJob()
    private val repoScope = CoroutineScope(repoJob + Dispatchers.Main.immediate)
    private val ioDispatcher = schedulers.io().asCoroutineDispatcher()

    private val pendingEvents = mapOf<NotificationEvent.Source, MutableMap<Int, NotificationEvent>>(
            NotificationEvent.Source.QMS to mutableMapOf(),
            NotificationEvent.Source.THEME to mutableMapOf(),
            NotificationEvent.Source.SITE to mutableMapOf()
    )

    private var checkTimer: Timer? = null
    private val timerRunnable = {
        for (source in pendingEvents.keys) {
            handlePendingEvents(source)
        }
    }

    private val eventsHistory = mutableMapOf<Int, NotificationEvent>()


    private val notifyEventFlow = MutableSharedFlow<NotificationEvent>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val notifyStackFlow = MutableSharedFlow<List<NotificationEvent>>(
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val cancelEventFlow = MutableSharedFlow<NotificationEvent>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val notifyTabFlow = MutableSharedFlow<TabNotification>(
            extraBufferCapacity = 256,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val controllerListener: WebSocketController.Listener = object : WebSocketController.Listener() {
        override fun onConnected() {
            Log.d(LOG_TAG, "WSContr onConnected ${webSocketController.getCurrentId()},  ${webSocketController.isConnected()}")
            webSocketController.send("""[${webSocketController.getCurrentId()}, "sv"]""")
            webSocketController.send("""[0, "ea", "u${authHolder.get().userId}"]""")
        }

        override fun onMessage(text: String?) {
            Log.d(LOG_TAG, "WSContr onMessage ${webSocketController.getCurrentId()}, ${webSocketController.isConnected()}, $text")
            try {
                eventsApi.parseWebSocketEvent(text)?.also {
                    if (it.type != NotificationEvent.Type.HAT_EDITED) {
                        handleWebSocketEvent(it)
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        override fun onDisconnected(throwable: Throwable, response: Response?) {
            Log.d(LOG_TAG, "WSContr onDisconnected ${webSocketController.getCurrentId()}, ${webSocketController.isConnected()}, ${throwable.message}, $response")
            if (response != null) {
                Log.d(LOG_TAG, "WSContr onDisconnected: code=${response.code}")
                if (response.code == 403) {
                    App.get().notifyForbidden(true)
                }
            }

            throwable.printStackTrace()
            if (throwable is SocketTimeoutException || throwable is TimeoutException) {
                Log.d(LOG_TAG, "start onFailure")
                start(true)
            }
        }
    }

    private val webSocketController = WebSocketController(webClient, controllerListener)

    init {
        repoScope.launch {
            var lastNet = networkStateProvider.getState()
            networkStateProvider.observeState().collect { s ->
                if (s == lastNet) return@collect
                lastNet = s
                if (s) {
                    Log.d(LOG_TAG, "start networkStateProvider.observeState")
                    start(true)
                }
            }
        }
        repoScope.launch {
            var lastAuth = authHolder.get().isAuth()
            authHolder.observe().collect { auth ->
                val now = auth.isAuth()
                if (now == lastAuth) return@collect
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "authHolder.observe state=${auth.state}")
                }
                lastAuth = now
                if (now) {
                    if (webSocketController.isConnected()) {
                        stop()
                    }
                    Log.d(LOG_TAG, "start authHolder.observe")
                    start(true)
                } else {
                    stop()
                }
            }
        }
        repoScope.launch {
            var lastTimerStamp = System.currentTimeMillis()
            while (isActive) {
                delay(60_000L)
                Log.d(LOG_TAG, "start timer (${(System.currentTimeMillis() - lastTimerStamp) / 1000}), ${webSocketController.isConnected()}")
                lastTimerStamp = System.currentTimeMillis()
                if (!webSocketController.isConnected()) {
                    stop()
                    start(false)
                }
            }
        }
        timerPeriod = notificationPreferencesHolder.getMainLimit()
    }

    fun observeEvents(): Flow<NotificationEvent> = notifyEventFlow.asSharedFlow()

    fun observeEventsStack(): Flow<List<NotificationEvent>> = notifyStackFlow.asSharedFlow()

    fun observeCancel(): Flow<NotificationEvent> = cancelEventFlow.asSharedFlow()

    fun observeEventsTab(): Flow<TabNotification> = notifyTabFlow.asSharedFlow()

    fun onDestroy() {
        repoJob.cancel()
    }

    fun setTimerPeriod(period: Long) {
        timerPeriod = period
        resetTimer()
    }

    fun externalStart(checkEvents: Boolean) {
        Log.e(LOG_TAG, "start externalStart")
        start(checkEvents)
    }

    fun updateEvents(source: NotificationEvent.Source) {
        hardHandleEvent(source)
    }

    private fun start(checkEvents: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Start: net=${networkStateProvider.getState()} ws=${webSocketController.isConnected()} check=$checkEvents id=${webSocketController.getCurrentId()}")
        }
        if (networkStateProvider.getState() && authHolder.get().isAuth()) {
            if (!webSocketController.isConnected()) {
                webSocketController.connect()
            }

            if (checkEvents) {
                hardHandleEvent(NotificationEvent.Source.THEME)
                hardHandleEvent(NotificationEvent.Source.QMS)
            }
            if (BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "timerPeriod=$timerPeriod")
            }
            resetTimer()
        }
    }

    private fun stop() {
        Log.d(LOG_TAG, "stop")
        cancelTimer()
        webSocketController.disconnectAll()
    }

    private fun resetTimer() {
        cancelTimer()
        checkTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    timerRunnable.invoke()
                }
            }, 0, timerPeriod)
        }
    }

    private fun cancelTimer() {
        checkTimer?.apply {
            cancel()
            purge()
        }
        checkTimer = null
    }

    private fun sendNotification(event: NotificationEvent) {
        Log.e("events_lalala", "send notification rep " + event.sourceEventText + " : " + event.source + " : " + event.sourceTitle + " : " + event.userNick)
        if (event.userId == authHolder.get().userId) {
            return
        }
        eventsHistory[event.notifyId()] = event
        if (!checkNotify(event, event.source)) {
            return
        }
        notifyEventFlow.tryEmit(event)
    }

    private fun sendNotifications(events: List<NotificationEvent>, tSource: NotificationEvent.Source) {
        if (events.isEmpty()) {
            return
        }
        if (events.size <= STACKED_MAX) {
            for (event in events) {
                sendNotification(event)
            }
            return
        }
        if (!checkNotify(null, tSource)) {
            return
        }
        notifyStackFlow.tryEmit(events)
    }

    private fun notifyTabs(event: TabNotification) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "notifyTabs")
        }
        notifyTabFlow.tryEmit(event)
    }

    private fun checkNotify(event: NotificationEvent?, source: NotificationEvent.Source): Boolean {
        if (!notificationPreferencesHolder.getMainEnabled()) {
            return false
        }
        if (NotificationEvent.fromQms(source)) {
            if (!notificationPreferencesHolder.getQmsEnabled()) {
                return false
            }
        } else if (NotificationEvent.fromTheme(source)) {
            if (event != null && event.isMention) {
                if (!notificationPreferencesHolder.getMentionsEnabled()) {
                    return false
                }
            } else {
                if (!notificationPreferencesHolder.getFavEnabled()) {
                    return false
                }
            }
        }
        return true
    }

    private fun checkOldEvent(event: NotificationEvent) {
        var oldEvent = eventsHistory[event.notifyId(NotificationEvent.Type.NEW)]
        var delete = false
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "checkOldEvent old=$oldEvent new=$event")
        }

        if (event.fromTheme()) {
            //Убираем уведомления избранного
            if (oldEvent != null && event.messageId >= oldEvent.messageId) {
                cancelEventFlow.tryEmit(oldEvent)
                delete = true
            }

            //Убираем уведомление упоминаний
            oldEvent = eventsHistory[event.notifyId(NotificationEvent.Type.MENTION)]
            if (oldEvent != null) {
                cancelEventFlow.tryEmit(oldEvent)
                delete = true
            }
        } else if (event.fromQms()) {

            //Убираем уведомление кумыса
            if (oldEvent != null) {
                cancelEventFlow.tryEmit(oldEvent)
                delete = true
            }
        }

        if (delete || oldEvent == null) {
            notifyTabs(TabNotification(
                    event.source,
                    event.type,
                    event,
                    true
            ))
        }
        if (delete) {
            eventsHistory.remove(event.notifyId(NotificationEvent.Type.NEW))
        }
    }

    private fun checkOldEvents(loadedEvents: List<NotificationEvent>, source: NotificationEvent.Source) {
        val oldEvents = eventsHistory.filter { it.value.source == source }.map { it.value }

        for (oldEvent in oldEvents) {
            var exist = false
            for (loadedEvent in loadedEvents) {
                if (oldEvent.sourceId == loadedEvent.sourceId) {
                    exist = true
                    break
                }
            }
            if (!exist) {
                cancelEventFlow.tryEmit(oldEvent)
                eventsHistory.remove(oldEvent.notifyId(NotificationEvent.Type.NEW))
                notifyTabs(TabNotification(
                        oldEvent.source,
                        NotificationEvent.Type.READ,
                        oldEvent,
                        true
                ))
            }
        }
    }

    private fun handleWebSocketEvent(event: NotificationEvent) {
        if (event.isRead) {
            checkOldEvent(event)
            return
        }
        eventsHistory[event.notifyId()] = event
        notifyTabs(TabNotification(
                event.source,
                event.type,
                event,
                true
        ))
        handleEvent(listOf(event), event.source)
    }


    private fun handleEvent(events: List<NotificationEvent>, source: NotificationEvent.Source) {
        val pending = pendingEvents[source]
        if (pending != null) {
            for (event in events) {
                pending[event.sourceId] = event
            }
        }
    }

    private fun hardHandleEvent(source: NotificationEvent.Source) {
        hardHandleEvent(emptyList(), source)
    }

    private fun hardHandleEvent(events: List<NotificationEvent>, source: NotificationEvent.Source) {
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "hardHandleEvent size=${events.size} source=$source")
        }
        if (NotificationEvent.fromSite(source)) {
            if (notificationPreferencesHolder.getMentionsEnabled()) {
                for (event in events) {
                    sendNotification(event)
                }
            }
            return
        }
        if (!NotificationEvent.fromQms(source) && !NotificationEvent.fromTheme(source)) {
            return
        }

        repoScope.launch(ioDispatcher) {
            val loadedEvents = runCatching {
                if (NotificationEvent.fromQms(source)) {
                    eventsApi.qmsEvents
                } else {
                    eventsApi.favoritesEvents
                }
            }.getOrElse { e ->
                e.printStackTrace()
                emptyList()
            }

            val savedEvents = getSavedEvents(source)
            saveEvents(loadedEvents, source)
            val newEvents = compareEvents(savedEvents, loadedEvents, events, source)
            val stackedNewEvents = newEvents.toMutableList()

            checkOldEvents(loadedEvents, source)

            for (event in events) {
                for (newEvent in newEvents) {
                    if (newEvent.sourceId == event.sourceId) {
                        stackedNewEvents.remove(newEvent)
                        newEvent.type = event.type
                        newEvent.messageId = event.messageId

                        notifyTabs(TabNotification(
                                newEvent.source,
                                newEvent.type,
                                newEvent,
                                false,
                                loadedEvents.toList(),
                                newEvents.toList()
                        ))

                        sendNotification(newEvent)
                    } else if (event.isMention && !notificationPreferencesHolder.getFavEnabled()) {
                        stackedNewEvents.remove(newEvent)
                    }
                }
            }

            sendNotifications(stackedNewEvents, source)
        }
    }


    private fun handlePendingEvents(source: NotificationEvent.Source) {
        val pending = pendingEvents[source]
        if (pending != null && pending.isNotEmpty()) {
            hardHandleEvent(pending.map { it.value }, source)
            pending.clear()
        }
    }


    private fun getSavedEvents(source: NotificationEvent.Source): List<NotificationEvent> {
        val savedEvents: Set<String> = when {
            NotificationEvent.fromQms(source) -> notificationPreferencesHolder.getDataQmsEvents()
            NotificationEvent.fromTheme(source) -> notificationPreferencesHolder.getDataFavoritesEvents()
            else -> return emptyList()
        }

        val responseBuilder = StringBuilder()
        for (saved in savedEvents) {
            responseBuilder.append(saved).append('\n')
        }
        val response = responseBuilder.toString()

        if (NotificationEvent.fromQms(source)) {
            return eventsApi.getQmsEvents(response)
        } else if (NotificationEvent.fromTheme(source)) {
            return eventsApi.getFavoritesEvents(response)
        }
        return emptyList()
    }

    private fun saveEvents(loadedEvents: List<NotificationEvent>, source: NotificationEvent.Source) {
        val savedEvents = androidx.collection.ArraySet<String>()
        for (event in loadedEvents) {
            savedEvents.add(event.sourceEventText.orEmpty())
        }
        if (NotificationEvent.fromQms(source)) {
            notificationPreferencesHolder.setDataQmsEvents(savedEvents)
        } else if (NotificationEvent.fromTheme(source)) {
            notificationPreferencesHolder.setDataFavoritesEvents(savedEvents)
        }
    }

    private fun compareEvents(
            savedEvents: List<NotificationEvent>,
            loadedEvents: List<NotificationEvent>,
            events: List<NotificationEvent>,
            source: NotificationEvent.Source
    ): List<NotificationEvent> {
        val newEvents = mutableListOf<NotificationEvent>()

        for (loaded in loadedEvents) {
            var isNew = true
            for (saved in savedEvents) {
                if (loaded.sourceId == saved.sourceId && loaded.timeStamp <= saved.timeStamp) {
                    isNew = false
                    break
                }
            }

            if (isNew) {
                newEvents.add(loaded)
            }
        }

        if (NotificationEvent.fromTheme(source) && notificationPreferencesHolder.getFavOnlyImportant()) {
            val toRemove = mutableListOf<NotificationEvent>()
            for (newEvent in newEvents) {
                var remove = false
                for (event in events) {
                    if (!event.isMention && !newEvent.isImportant) {
                        remove = true
                        break
                    }
                }
                if (!newEvent.isImportant) {
                    remove = true
                }
                if (remove) {
                    toRemove.add(newEvent)
                }
            }
            for (removeEvent in toRemove) {
                newEvents.remove(removeEvent)
            }
            toRemove.clear()
        }

        return newEvents
    }


}