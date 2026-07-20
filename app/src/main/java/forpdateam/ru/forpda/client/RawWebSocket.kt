package forpdateam.ru.forpda.client

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * WebSocket-транспорт БЕЗ HTTP-рукопожатия и БЕЗ TLS — под нестандартный realtime-сервер 4PDA
 * на app.4pda.to:993.
 *
 * Почему он существует: сервер :993 не делает HTTP upgrade (`GET /ws/` → в ответ прилетают
 * WS close-фреймы 1003/1009) и не говорит по TLS (ClientHello → 4 байта мусора, «no peer
 * certificate»; trust-all тоже падает). Он ожидает СРАЗУ сырые WebSocket-фреймы поверх голого
 * TCP: masked ping → валидный pong `8a00`, masked text → текстовый ответ `[0,2]` (проверено
 * 20.07.2026 с двух сетей). Официальный клиент ru.fourpda.client носит собственный кодек
 * фреймов в classes.dex именно поэтому. Стандартный OkHttp (как и любая RFC 6455-библиотека)
 * начинает с upgrade/TLS и потому не подключится к :993 никогда — это и была причина
 * «SSLHandshakeException у всех, на любой сети, с VPN и без».
 *
 * Реализует [okhttp3.WebSocket] и дёргает обычный [WebSocketListener], поэтому
 * [WebSocketController] и вся логика выше (circuit breaker, reconnect) не знают о подмене.
 *
 * Приватность: cookies по этому каналу НЕ ходят — авторизация делается сообщением
 * `[0, "ea", "u<userId>"]` уже внутри протокола (см. EventsRepository).
 */
class RawWebSocket(
        private val host: String,
        private val port: Int,
        private val originalRequest: Request,
        private val listener: WebSocketListener,
        private val connectTimeoutMs: Int = 30_000,
        private val pingIntervalMs: Long = 60_000L
) : WebSocket {

    private val writeLock = Any()
    private val random = SecureRandom()
    private val terminated = AtomicBoolean(false)

    // send() зовут с главного потока (EventsRepository.onConnected → Main.immediate), а
    // блокирующая запись в сокет там запрещена (StrictMode/ANR). OkHttp решает это внутренним
    // writer-потоком — повторяем: send() только ставит фрейм в очередь. Однопоточность
    // executor'а заодно гарантирует порядок фреймов.
    private val writeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "RawWS-write-$port").apply { isDaemon = true }
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var open = false
    @Volatile private var pingThread: Thread? = null

    /** Запускает подключение в фоновом потоке; коллбеки listener'а придут оттуда же. */
    fun connect() {
        thread(name = "RawWS-$port", isDaemon = true) { runSocket() }
    }

    private fun runSocket() {
        try {
            // Тег трафика: без него каждый connect сыплет UntaggedSocketViolation в StrictMode.
            android.net.TrafficStats.setThreadStatsTag(TRAFFIC_STATS_TAG)
            val s = Socket()
            // socket присваиваем ДО connect: cancel() из другого потока закроет сокет и
            // прервёт даже зависшую попытку подключения.
            socket = s
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.tcpNoDelay = true
            // Пассивный детектор мёртвого соединения: мы шлём ping каждые pingIntervalMs,
            // сервер отвечает pong — если 2 интервала подряд ни байта, сокет мёртв.
            s.soTimeout = (pingIntervalMs * 2).toInt().coerceAtLeast(30_000)
            output = s.getOutputStream()
            val input = s.getInputStream()
            open = true
            listener.onOpen(this, syntheticResponse())
            startPinger()
            readLoop(input)
        } catch (t: Throwable) {
            failOnce(t)
        }
    }

    private fun readLoop(input: InputStream) {
        // Аккумулятор фрагментированного сообщения (opcode 0 = continuation).
        var messageOpcode = 0
        val messageBuf = ByteArrayOutputStream()
        while (true) {
            val b0 = input.readUByteOrThrow()
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0F
            val b1 = input.readUByteOrThrow()
            val masked = b1 and 0x80 != 0
            var length = (b1 and 0x7F).toLong()
            if (length == 126L) {
                val ext = input.readExact(2)
                length = ((ext[0].toLong() and 0xFF) shl 8) or (ext[1].toLong() and 0xFF)
            } else if (length == 127L) {
                val ext = input.readExact(8)
                length = 0L
                for (b in ext) length = (length shl 8) or (b.toLong() and 0xFF)
            }
            if (length > MAX_MESSAGE_BYTES) throw IOException("inbound frame too big: $length")
            val maskKey = if (masked) input.readExact(4) else null
            val payload = input.readExact(length.toInt())
            if (maskKey != null) {
                for (i in payload.indices) {
                    payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                }
            }
            when (opcode) {
                OPCODE_PING -> runCatching { sendFrame(OPCODE_PONG, payload) }
                OPCODE_PONG -> Unit // liveness: сам факт чтения сбросил soTimeout
                OPCODE_CLOSE -> {
                    handleServerClose(payload)
                    return
                }
                OPCODE_TEXT, OPCODE_BINARY, OPCODE_CONTINUATION -> {
                    if (opcode != OPCODE_CONTINUATION) messageOpcode = opcode
                    messageBuf.write(payload)
                    if (messageBuf.size() > MAX_MESSAGE_BYTES) throw IOException("inbound message too big")
                    if (fin) {
                        val bytes = messageBuf.toByteArray()
                        messageBuf.reset()
                        if (messageOpcode == OPCODE_TEXT) {
                            listener.onMessage(this, String(bytes, Charsets.UTF_8))
                        } else {
                            listener.onMessage(this, bytes.toByteString())
                        }
                    }
                }
                else -> throw IOException("unknown opcode: $opcode")
            }
        }
    }

    private fun handleServerClose(payload: ByteArray) {
        val code = if (payload.size >= 2) {
            ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        } else {
            1005 // RFC 6455: close без кода
        }
        val reason = if (payload.size > 2) String(payload, 2, payload.size - 2, Charsets.UTF_8) else ""
        open = false
        runCatching { sendFrame(OPCODE_CLOSE, payload) } // echo close по RFC
        if (terminated.compareAndSet(false, true)) {
            runCatching { listener.onClosing(this, code, reason) }
        }
        runCatching { socket?.close() }
    }

    private fun startPinger() {
        pingThread = thread(name = "RawWS-ping-$port", isDaemon = true) {
            try {
                while (open && !terminated.get()) {
                    Thread.sleep(pingIntervalMs)
                    if (!open || terminated.get()) break
                    sendFrame(OPCODE_PING, EMPTY)
                }
            } catch (_: InterruptedException) {
                // штатная остановка из failOnce/close
            } catch (t: Throwable) {
                failOnce(t)
            }
        }
    }

    /** Единая точка аварийного завершения: коллбек onFailure строго один раз. */
    private fun failOnce(t: Throwable) {
        open = false
        runCatching { socket?.close() }
        runCatching { writeExecutor.shutdown() }
        pingThread?.takeIf { it !== Thread.currentThread() }?.interrupt()
        if (terminated.compareAndSet(false, true)) {
            runCatching { listener.onFailure(this, t, null) }
        }
    }

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        val out = output ?: throw IOException("raw ws not connected")
        val mask = ByteArray(4).also { random.nextBytes(it) }
        val buf = ByteArrayOutputStream(payload.size + 14)
        buf.write(0x80 or opcode) // FIN всегда: наши сообщения маленькие, не фрагментируем
        when {
            payload.size < 126 -> buf.write(0x80 or payload.size)
            payload.size < 65536 -> {
                buf.write(0x80 or 126)
                buf.write(payload.size shr 8)
                buf.write(payload.size and 0xFF)
            }
            else -> {
                buf.write(0x80 or 127)
                for (i in 7 downTo 0) buf.write(((payload.size.toLong() shr (8 * i)) and 0xFF).toInt())
            }
        }
        buf.write(mask)
        for (i in payload.indices) buf.write(payload[i].toInt() xor mask[i % 4].toInt())
        val bytes = buf.toByteArray()
        synchronized(writeLock) {
            out.write(bytes)
            out.flush()
        }
    }

    private fun syntheticResponse(): Response = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(101)
            .message("Switching Protocols (raw, no handshake)")
            .build()

    private fun InputStream.readUByteOrThrow(): Int =
            read().also { if (it == -1) throw EOFException("raw ws: socket EOF") }

    private fun InputStream.readExact(n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(buf, off, n - off)
            if (r == -1) throw EOFException("raw ws: socket EOF mid-frame")
            off += r
        }
        return buf
    }

    // region okhttp3.WebSocket
    override fun request(): Request = originalRequest

    override fun queueSize(): Long = 0L

    override fun send(text: String): Boolean =
            enqueueFrame(OPCODE_TEXT, text.toByteArray(Charsets.UTF_8))

    override fun send(bytes: ByteString): Boolean =
            enqueueFrame(OPCODE_BINARY, bytes.toByteArray())

    /** Неблокирующая отправка в духе OkHttp: true = поставлено в очередь writer-потока. */
    private fun enqueueFrame(opcode: Int, payload: ByteArray): Boolean {
        if (!open) return false
        return runCatching {
            writeExecutor.execute {
                try {
                    sendFrame(opcode, payload)
                } catch (t: Throwable) {
                    failOnce(t)
                }
            }
        }.isSuccess // RejectedExecutionException после shutdown → false
    }

    override fun close(code: Int, reason: String?): Boolean {
        if (!open) return false
        open = false
        val reasonBytes = reason?.toByteArray(Charsets.UTF_8) ?: EMPTY
        val payload = ByteArray(2 + reasonBytes.size)
        payload[0] = (code shr 8).toByte()
        payload[1] = (code and 0xFF).toByte()
        reasonBytes.copyInto(payload, 2)
        runCatching { sendFrame(OPCODE_CLOSE, payload) }
        // Без лингера: единственный вызывающий — контроллер в ответ на серверный close,
        // где терминация уже обработана. Просто рвём сокет.
        runCatching { socket?.close() }
        runCatching { writeExecutor.shutdown() }
        pingThread?.interrupt()
        return true
    }

    override fun cancel() {
        open = false
        // Закрытие сокета будит readLoop/зависший connect → failOnce(SocketException);
        // контроллер к этому моменту уже снял сокет с учёта, коллбек уйдёт в пустоту.
        runCatching { socket?.close() }
        runCatching { writeExecutor.shutdown() }
        pingThread?.interrupt()
    }
    // endregion

    companion object {
        private val EMPTY = ByteArray(0)
        private const val MAX_MESSAGE_BYTES = 4L * 1024 * 1024
        private const val TRAFFIC_STATS_TAG = 0x4FDA

        private const val OPCODE_CONTINUATION = 0x0
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_BINARY = 0x2
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA
    }
}
