package forpdateam.ru.forpda.client

import forpdateam.ru.forpda.model.data.remote.IWebClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * До фикса `onClosed` был пуст, а `onClosing` не переопределялся вовсе. OkHttp при штатном
 * close-фрейме сервера вызывает только `onClosing` (если мы не закрываем сокет в ответ),
 * поэтому состояние навсегда оставалось `connected = true`: [WebSocketController.isConnected]
 * врал, `EventsRepository.start()` не переподключался, и realtime-уведомления молча умирали
 * до перезапуска приложения.
 */
class WebSocketControllerCloseTest {

    private class RecordingListener : WebSocketController.Listener() {
        var disconnects = 0
        var lastThrowable: Throwable? = null

        override fun onDisconnected(throwable: Throwable, response: Response?) {
            disconnects++
            lastThrowable = throwable
        }
    }

    /** Контроллер после connect(), плюс сокет и слушатель, которые он реально использует. */
    private class Fixture {
        val socket: WebSocket = mockk(relaxed = true)
        val listener = RecordingListener()
        val controller: WebSocketController
        val wsListener: WebSocketListener

        init {
            val captured = slot<WebSocketListener>()
            val webClient = mockk<IWebClient>(relaxed = true)
            every { webClient.createWebSocketConnection(capture(captured)) } returns socket
            controller = WebSocketController(webClient, listener)
            controller.connect()
            wsListener = captured.captured
        }
    }

    @Test
    fun connect_reportsConnected() {
        assertTrue(Fixture().controller.isConnected())
    }

    @Test
    fun onClosing_marksDisconnectedAndNotifiesListener() {
        val f = Fixture()

        f.wsListener.onClosing(f.socket, 1000, "bye")

        assertFalse("после close-фрейма сокет не считается живым", f.controller.isConnected())
        assertEquals("обрыв обязан дойти до слушателя, иначе не будет реконнекта", 1, f.listener.disconnects)
        assertTrue(f.listener.lastThrowable is WebSocketController.ClosedException)
    }

    @Test
    fun onClosedAfterOnClosing_doesNotNotifyTwice() {
        val f = Fixture()

        f.wsListener.onClosing(f.socket, 1000, "bye")
        f.wsListener.onClosed(f.socket, 1000, "bye")

        assertEquals("повторное уведомление вызвало бы двойной реконнект", 1, f.listener.disconnects)
        assertFalse(f.controller.isConnected())
    }

    @Test
    fun onFailure_marksDisconnectedAndNotifiesListener() {
        val f = Fixture()

        f.wsListener.onFailure(f.socket, java.io.IOException("connection abort"), null)

        assertFalse(f.controller.isConnected())
        assertEquals(1, f.listener.disconnects)
    }

    @Test
    fun terminationOfStaleSocket_doesNotNotify() {
        val f = Fixture()
        val stale: WebSocket = mockk(relaxed = true)

        f.wsListener.onFailure(stale, java.io.IOException("stale"), null)

        assertTrue("текущий сокет жив, состояние трогать нельзя", f.controller.isConnected())
        assertEquals(0, f.listener.disconnects)
    }
}
