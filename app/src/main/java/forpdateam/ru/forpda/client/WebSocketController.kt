package forpdateam.ru.forpda.client

import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.model.data.remote.IWebClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Random

class WebSocketController(
        private val webClient: IWebClient,
        private val listener: Listener
) {

    private val lock = Any()
    private val webSockets = mutableListOf<WebSocketState>()
    private var currentId = NO_ID

    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            val shouldNotify = synchronized(lock) {
                if (BuildConfig.DEBUG) Timber.d("WSListener onOpen; current=$currentId")
                markCurrentLocked(webSocket)
            }
            if (shouldNotify) {
                listener.onConnected()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val shouldNotify = synchronized(lock) {
                if (BuildConfig.DEBUG) Timber.d("WSListener onMessage: hasPayload=${text.isNotEmpty()}; current=$currentId")
                markCurrentLocked(webSocket)
            }
            if (shouldNotify) {
                listener.onMessage(text)
            }
        }

        /**
         * Сервер прислал close-фрейм. OkHttp вызовет [onClosed] только если мы сами закроем
         * сокет в ответ — иначе цикл чтения просто завершится, а состояние навсегда останется
         * `connected = true`, и [isConnected] будет врать, блокируя переподключение.
         */
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (BuildConfig.DEBUG) Timber.d("WSListener onClosing: code=$code")
            webSocket.close(NORMAL_CLOSURE, null)
            handleTermination(webSocket, ClosedException(code, reason))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (BuildConfig.DEBUG) Timber.d("WSListener onClosed: code=$code")
            handleTermination(webSocket, ClosedException(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (BuildConfig.DEBUG) Timber.d("WSListener onFailure: code=${response?.code}")
            handleTermination(webSocket, t, response)
        }
    }

    /**
     * Помечает сокет живым и отвечает, актуален ли он.
     *
     * Отдельный метод, потому что наивное `current == event` возвращало `true`, когда оба
     * равны null: сокет уже снят с учёта (обрыв, disconnectAll), currentId == NO_ID — и
     * сообщение с мёртвого соединения всё равно доходило до слушателя. Так после ухода
     * приложения в фон долетали «призрачные» события.
     */
    private fun markCurrentLocked(webSocket: WebSocket): Boolean {
        val eventWebSocket = getByWebSocketLocked(webSocket) ?: return false
        eventWebSocket.connected = true
        return eventWebSocket.id == currentId
    }

    /**
     * Единая точка снятия сокета с учёта: убирает состояние из списка и уведомляет слушателя
     * ровно один раз, только если умер текущий сокет.
     */
    private fun handleTermination(webSocket: WebSocket, t: Throwable, response: Response? = null) {
        val shouldNotify = synchronized(lock) {
            val eventWebSocket = getByWebSocketLocked(webSocket) ?: return
            val currentWebSocket = getByIdLocked(currentId)
            eventWebSocket.connected = false
            webSockets.remove(eventWebSocket)
            val wasCurrent = currentWebSocket == eventWebSocket
            if (wasCurrent) currentId = NO_ID
            wasCurrent
        }
        if (shouldNotify) {
            listener.onDisconnected(t, response)
        }
    }

    fun connect() {
        // Снимаем «осиротевшие» сокеты (например, тот, чей close-фрейм мы только что обработали),
        // иначе список растёт, а мёртвые соединения продолжают держать ресурсы.
        disconnectAll()
        val newId = (1000..16384).random()
        val newWebSocket = webClient.createWebSocketConnection(webSocketListener)
        val newWebSocketState = WebSocketState(newId, newWebSocket, true)
        synchronized(lock) {
            currentId = newId
            webSockets.add(newWebSocketState)
        }
    }

    fun send(message: String) {
        val webSocket = synchronized(lock) {
            getByIdLocked(currentId)?.webSocket
        }
        webSocket?.send(message)
    }

    fun disconnectAll() {
        val sockets = synchronized(lock) {
            val activeSockets = webSockets.onEach { it.connected = false }.map { it.webSocket }
            webSockets.clear()
            currentId = NO_ID
            activeSockets
        }
        sockets.forEach {
            it.cancel()
        }
    }

    fun isConnected(): Boolean {
        return synchronized(lock) {
            val currentWebSocket = getByIdLocked(currentId)
            (currentId != NO_ID && currentWebSocket?.connected ?: false).also {
                if (BuildConfig.DEBUG) Timber.d("isConnected $currentId, $currentWebSocket ... $it")
            }
        }
    }

    fun getCurrentId() = synchronized(lock) { currentId }

    private fun getByIdLocked(id: Int): WebSocketState? = webSockets.firstOrNull { it.id == id }

    private fun getByWebSocketLocked(webSocket: WebSocket): WebSocketState? = webSockets.firstOrNull { it.webSocket == webSocket }

    private fun IntRange.random() = Random().nextInt((endInclusive + 1) - start) + start

    /** Штатное закрытие соединения сервером — для слушателя это такой же обрыв, как и сбой. */
    class ClosedException(val code: Int, val closeReason: String) :
            java.io.IOException("WebSocket closed by peer: code=$code reason=$closeReason")

    companion object {
        const val NO_ID = -1
        private const val NORMAL_CLOSURE = 1000
        private const val LOG_TAG = "WebSocketController"
    }

    open class Listener {
        open fun onConnected() {}
        open fun onDisconnected(throwable: Throwable, response: Response?) {}
        open fun onMessage(text: String?) {}
    }

    private class WebSocketState(
            var id: Int,
            var webSocket: WebSocket,
            var connected: Boolean = false
    ) {
        override fun toString(): String {
            return "WebSocketState[$id, $connected]"
        }
    }
}