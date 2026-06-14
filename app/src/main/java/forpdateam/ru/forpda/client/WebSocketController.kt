package forpdateam.ru.forpda.client

import timber.log.Timber
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.entity.remote.events.NotificationEvent
import forpdateam.ru.forpda.model.data.remote.IWebClient
import forpdateam.ru.forpda.model.repository.events.EventsRepository
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.lang.Exception
import java.net.SocketTimeoutException
import java.util.*
import java.util.concurrent.TimeoutException

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
                val eventWebSocket = getByWebSocketLocked(webSocket)
                val currentWebSocket = getByIdLocked(currentId)
                if (BuildConfig.DEBUG) Timber.d("WSListener onOpen; ${eventWebSocket?.id}, ${currentWebSocket?.id}")
                eventWebSocket?.connected = true
                currentWebSocket == eventWebSocket
            }
            if (shouldNotify) {
                listener.onConnected()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val shouldNotify = synchronized(lock) {
                val eventWebSocket = getByWebSocketLocked(webSocket)
                val currentWebSocket = getByIdLocked(currentId)
                if (BuildConfig.DEBUG) Timber.d("WSListener onMessage: hasPayload=${text.isNotEmpty()}; ${eventWebSocket?.id}, ${currentWebSocket?.id}")
                eventWebSocket?.connected = true
                currentWebSocket == eventWebSocket
            }
            if (shouldNotify) {
                listener.onMessage(text)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val shouldNotify = synchronized(lock) {
                val eventWebSocket = getByWebSocketLocked(webSocket)
                val currentWebSocket = getByIdLocked(currentId)
                if (BuildConfig.DEBUG) Timber.d("WSListener onFailure: code=${response?.code}; ${eventWebSocket?.id}, ${currentWebSocket?.id}")
                eventWebSocket?.connected = false
                eventWebSocket?.also {
                    try {
                        webSockets.remove(eventWebSocket)
                    } catch (ex: Exception) {
                        Timber.e(ex, "WebSocket remove error")
                    }
                }
                currentWebSocket == eventWebSocket
            }
            if (shouldNotify) {
                listener.onDisconnected(t, response)
            }
        }
    }

    fun connect() {
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

    companion object {
        const val NO_ID = -1
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