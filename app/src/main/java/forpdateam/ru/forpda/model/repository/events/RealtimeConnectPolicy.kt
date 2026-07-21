package forpdateam.ru.forpda.model.repository.events

/**
 * The single rule for opening the realtime events WebSocket.
 *
 * It used to live scattered across [EventsRepository]: `setForegroundRealtimeEnabled()` refused to go
 * realtime while notifications were off («Никогда не поднимаем WS, если уведомления выключены» — the
 * 45-second pings keep the radio awake), but `start()` — the only place that actually calls
 * `WebSocketController.connect()` — checked just the foreground flag. Since `stop()` never clears that
 * flag, turning notifications off in the foreground (which stops the socket via `NotificationsService`)
 * left it set, and the next network or auth change re-opened the socket with notifications disabled.
 *
 * Keeping the condition here, as one pure function, means the invariant is stated once and can be
 * tested without an Application, an OkHttp client or a live socket.
 */
object RealtimeConnectPolicy {

    /**
     * @param foregroundRealtime the app is in the foreground and wants realtime events
     * @param networkAvailable   the device reports a usable network
     * @param authorized         the user is logged in (an anonymous socket receives nothing)
     * @param wantsPushNotifications the master notification toggle AND at least one event family
     *        (see `NotificationDataStore.wantsPushNotificationsSync`); realtime exists to feed
     *        notifications, so with none of them wanted the socket is normally pure battery cost.
     * @param realtimeScreenActive a foreground screen (an open QMS chat) explicitly needs a live
     *        socket for message delivery — this overrides [wantsPushNotifications]: a user reading a
     *        dialog must get new messages instantly even with notifications turned off. Bounded: the
     *        idle timer / [releaseRealtimeForScreen] close the socket again once no such screen remains.
     */
    fun shouldOpenWebSocket(
            foregroundRealtime: Boolean,
            networkAvailable: Boolean,
            authorized: Boolean,
            wantsPushNotifications: Boolean,
            realtimeScreenActive: Boolean = false,
    ): Boolean = foregroundRealtime && networkAvailable && authorized &&
            (wantsPushNotifications || realtimeScreenActive)
}
