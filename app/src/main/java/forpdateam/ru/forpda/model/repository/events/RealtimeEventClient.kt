package forpdateam.ru.forpda.model.repository.events

import forpdateam.ru.forpda.notifications.push.AppProtocolClient
import forpdateam.ru.forpda.notifications.push.PushSessionStore
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Живой канал событий 4PDA: подписка `ea` на app-протоколе поверх АВТОРИЗОВАННОЙ сессии.
 *
 * Почему не старый realtime-сокет. ProPDA с самого форка слал подписку `[0,"ea","u<id>"]`
 * текстом в легаси-протокол на `app.4pda.to:993`. Сервер её принимает (`[0,0]`) — и не кормит
 * НИКОГДА: замер 30.07.2026 — часы тишины при доказанно приходящих сообщениях; более того,
 * тот же `0` возвращается на заведомо мусорную подписку (`q<id>`, голый id, вообще без
 * аргумента). То есть текстовый `ea` — заглушка, а не канал.
 *
 * Настоящий канал вскрыт разбором офиц. клиента (`ru.fourpda.client`, класс `v`): опкоды —
 * 2 байта LE ASCII, `ea`=24933 (подписка, тело `"u<memberId>"`), `ed`=25701 (отписка),
 * `ev`=30309 — ВХОДЯЩЕЕ событие. Диспетчер офиц. клиента: `doc[0]==30309 && doc[1]==0` →
 * событие с телом `["<тип><id>", код, messageId]`. Формат совпадает с тем, что уже разбирает
 * [forpdateam.ru.forpda.model.data.remote.api.events.NotificationEventsApi.parseWebSocketEvent],
 * поэтому событие мы просто пересобираем в его текстовый вид и отдаём тому же парсеру — одна
 * точка разбора на оба канала.
 *
 * Два условия, без которых канал молчит (оба проверены живьём):
 *  - протокол ДОЛЖЕН быть бинарным (текстовый `ea` не кормится);
 *  - сессия ДОЛЖНА быть авторизована (`ma` по `login_key`): без неё подписка тоже принимается
 *    со статусом 0 и не кормится.
 *
 * Сессия — та же, что заводится для push ([PushSessionStore]), поэтому канал доступен и там,
 * где нет сервисов Google: на Huawei и прошивках без GMS это единственный способ получать
 * события мгновенно.
 */
class RealtimeEventClient(
        private val session: PushSessionStore,
        private val onEventDoc: (String) -> Unit,
        private val onConnectedChanged: (Boolean) -> Unit = {},
) {

    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var client: AppProtocolClient? = null
    @Volatile private var connected = false

    fun isConnected(): Boolean = connected

    /** Есть ли чем авторизоваться: без сессии канал бессмысленен. */
    fun isUsable(): Boolean = runCatching { session.hasSession() }.getOrDefault(false)

    fun start() {
        if (!isUsable()) {
            Timber.d("RealtimeEventClient: сессии нет, канал недоступен")
            return
        }
        if (!running.compareAndSet(false, true)) return
        worker = thread(name = "realtime-events", isDaemon = true) { runLoop() }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        // Закрываем сокет из другого потока: читающий поток висит в readNextDoc и проснётся
        // исключением — это единственный способ прервать блокирующее чтение.
        runCatching { client?.close() }
        client = null
        worker?.interrupt()
        worker = null
        setConnected(false)
    }

    private fun runLoop() {
        var attempt = 0
        while (running.get()) {
            val memberId = session.memberId
            val loginKey = session.loginKey
            if (memberId == 0 || loginKey.isNullOrEmpty()) {
                Timber.d("RealtimeEventClient: сессия пропала, останавливаемся")
                break
            }
            try {
                AppProtocolClient.connectAny().use { c ->
                    client = c
                    if (!c.resume(memberId, loginKey)) {
                        // login_key протух — дальше сессию чинит push-регистрация, а мы молча
                        // уходим: без авторизации канал всё равно не кормится.
                        Timber.w("RealtimeEventClient: resume отклонён, канал остановлен")
                        running.set(false)
                        return@use
                    }
                    if (!c.subscribeEvents(memberId)) {
                        Timber.w("RealtimeEventClient: подписка отклонена")
                        return@use
                    }
                    attempt = 0
                    setConnected(true)
                    Timber.i("RealtimeEventClient: канал открыт, подписка принята")
                    readUntilFailure(c)
                }
            } catch (t: Throwable) {
                if (!running.get()) break
                Timber.d("RealtimeEventClient: обрыв (%s)", t.javaClass.simpleName)
            } finally {
                client = null
                setConnected(false)
            }
            if (!running.get()) break
            // Бэкофф: 5с → 10с → 20с → 40с, потолок минута.
            val delayMs = (RECONNECT_BASE_MS shl attempt.coerceAtMost(3)).coerceAtMost(RECONNECT_MAX_MS)
            attempt++
            runCatching { Thread.sleep(delayMs) }.onFailure { return }
        }
        setConnected(false)
    }

    private fun readUntilFailure(c: AppProtocolClient) {
        var lastPing = System.currentTimeMillis()
        while (running.get()) {
            // Сервер закрывает соединение ровно через 60с тишины, а событий может не быть
            // часами — поэтому пингуем сами, не дожидаясь его ping'а.
            if (System.currentTimeMillis() - lastPing > PING_INTERVAL_MS) {
                lastPing = System.currentTimeMillis()
                c.ping()
            }
            val doc = try {
                c.readNextDoc()
            } catch (e: java.net.SocketTimeoutException) {
                continue // тишина в пределах read-timeout — нормальная работа канала
            }
            val text = eventDocToLegacyText(doc) ?: continue
            runCatching { onEventDoc(text) }
                    .onFailure { Timber.e(it, "RealtimeEventClient: обработка события упала") }
        }
    }

    private fun setConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        runCatching { onConnectedChanged(value) }
    }

    companion object {
        /** Опкод входящего события (`ev` = 2 байта LE ASCII). */
        const val EVENT_OPCODE = 30309
        private const val PING_INTERVAL_MS = 20_000L
        private const val RECONNECT_BASE_MS = 5_000L
        private const val RECONNECT_MAX_MS = 60_000L

        /**
         * Событие app-протокола → текстовый вид легаси-канала, который уже умеет разбирать
         * `NotificationEventsApi.parseWebSocketEvent`: `[a,b,"<тип><id>",код,messageId]`.
         * Возвращает null, если документ событием не является (обычный ответ на запрос).
         */
        fun eventDocToLegacyText(doc: List<Any?>): String? {
            if (doc.size < 5) return null
            if ((doc[0] as? Int) != EVENT_OPCODE) return null
            if ((doc[1] as? Int) != 0) return null
            val source = doc[2] as? String ?: return null
            val code = doc[3] as? Int ?: return null
            val messageId = doc[4] as? Int ?: return null
            return "[0,0,\"$source\",$code,$messageId]"
        }
    }
}
