package forpdateam.ru.forpda.notifications.push

import android.util.Base64
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.nio.charset.Charset
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.net.ssl.SSLSocketFactory

/**
 * Клиент собственного бинарного протокола официального клиента 4PDA
 * (`ru.fourpda.client`, реверс из classes.dex 1.9.43). Единственная задача в ProPDA —
 * зарегистрировать на сервере 4PDA наш FCM-токен, чтобы сервер слал события через FCM
 * (настоящий push, пробивающий Doze). Полноценным API не является: реализованы только
 * `ah` (hello), `ml` (login+captcha), `ma` (resume по login_key), `ai` (upload push-token).
 *
 * Транспорт: TLS + HTTP-Upgrade WebSocket к `appbk.4pda.to:443` с обязательным
 * `Sec-WebSocket-Protocol: app`; внутри — собственный фрейминг (флаг RSV1 = raw-deflate с
 * 4-байтным префиксом длины и добивкой `00 00 FF FF`). Тело — текстовые документы `[...]`
 * в cp1251. Проверено живьём 27.07.2026: hello→`[1,0,"0.2.2"]`, login→member_id+login_key,
 * ai→`[rid,0]`; после регистрации приходит реальный high-priority FCM-пуш.
 *
 * Блокирующий; вызывать с IO-потока. Не потокобезопасен — один вызов на экземпляр.
 */
class AppProtocolClient(
        private val endpoint: Endpoint = Endpoint.Ws(DEFAULT_WS_HOST),
        private val connectTimeoutMs: Int = 10_000,
        private val readTimeoutMs: Int = 25_000
) : AutoCloseable {

    /**
     * Транспорт до сервера 4PDA. Их клиент поддерживает оба и переключается между ними —
     * без этого приложение не работает там, где недоступен один из путей.
     */
    sealed class Endpoint {
        /** TLS + HTTP-Upgrade на 443. Идёт через Cloudflare, поэтому доступен не везде. */
        data class Ws(val host: String) : Endpoint()
        /** Прямой TCP на «host:port» (обычно app.4pda.to:993), в обход Cloudflare. Без TLS. */
        data class Direct(val hostPort: String) : Endpoint()
    }

    private lateinit var socket: Socket
    private lateinit var output: OutputStream
    private lateinit var input: DataInputStream
    private val inflater = Inflater(true)
    private val random = SecureRandom()
    private var rid = 0

    sealed class LoginResult {
        data class Success(val memberId: Int, val loginKey: String) : LoginResult()
        data class Captcha(val imageUrl: String) : LoginResult()
        data class Failed(val status: Int) : LoginResult()
    }

    /** Открывает TLS-WS и делает hello. Бросает при сетевой ошибке. */
    fun connect() {
        when (endpoint) {
            is Endpoint.Ws -> connectWs(endpoint.host)
            is Endpoint.Direct -> connectDirect(endpoint.hostPort)
        }
        val hello = call(listOf("ah", CLIENT_VERSION, "", "", 1, 0))
        Timber.d("AppProtocol hello (%s) -> %s", endpoint, hello)
    }

    private fun connectWs(host: String) {
        val raw = Socket()
        raw.connect(InetSocketAddress(host, 443), connectTimeoutMs)
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val ssl = factory.createSocket(raw, host, 443, true)
        ssl.soTimeout = readTimeoutMs
        socket = ssl
        output = ssl.getOutputStream()
        input = DataInputStream(BufferedInputStream(ssl.getInputStream()))

        val key = Base64.encodeToString(ByteArray(16).also { random.nextBytes(it) }, Base64.NO_WRAP)
        val handshake = "GET /ws/ HTTP/1.1\r\n" +
                "Host: $host\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $key\r\n" +
                "Sec-WebSocket-Protocol: app\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n"
        output.write(handshake.toByteArray(Charsets.ISO_8859_1))
        output.flush()
        val statusLine = readHttpHeaders()
        if (!statusLine.contains("101")) throw java.io.IOException("WS upgrade failed: $statusLine")
    }

    /** Прямой сокет: тот же фрейминг, но без TLS и без HTTP-рукопожатия. */
    private fun connectDirect(hostPort: String) {
        val idx = hostPort.lastIndexOf(':')
        val host = if (idx > 0) hostPort.substring(0, idx) else hostPort
        val port = if (idx > 0) hostPort.substring(idx + 1).toIntOrNull() ?: 993 else 993
        val raw = Socket()
        raw.connect(InetSocketAddress(host, port), connectTimeoutMs)
        raw.soTimeout = readTimeoutMs
        socket = raw
        output = raw.getOutputStream()
        input = DataInputStream(BufferedInputStream(raw.getInputStream()))
    }

    /** Логин по паролю. Вернёт Captcha(url) если сервер требует капчу — тогда повторить с [captcha]. */
    fun login(login: String, password: String, hidden: Boolean = false, captcha: Int = 0): LoginResult {
        val r = call(listOf("ml", login, password, if (hidden) 1 else 0, captcha))
        val status = (r.getOrNull(1) as? Int) ?: return LoginResult.Failed(-1)
        return when (status) {
            0 -> {
                val memberId = r.getOrNull(2) as? Int ?: return LoginResult.Failed(-2)
                val loginKey = r.getOrNull(3) as? String ?: return LoginResult.Failed(-3)
                LoginResult.Success(memberId, loginKey)
            }
            4 -> LoginResult.Captcha((r.getOrNull(2) as? String).orEmpty())
            else -> LoginResult.Failed(status)
        }
    }

    /** Восстановление сессии по сохранённому login_key (без капчи). true = успех. */
    fun resume(memberId: Int, loginKey: String): Boolean {
        val r = call(listOf("ma", memberId, loginKey))
        return (r.getOrNull(1) as? Int) == 0
    }

    /**
     * Регистрация push-токена на сервере 4PDA.
     * @param bitmask семейства событий: б0 QMS, б1 QMS-системные, б2 избранное, б3 важные темы, б4 упоминания.
     * @param provider 0 = Google FCM, 1 = Huawei HCM.
     */
    fun registerToken(token: String, bitmask: Int, provider: Int = 0): Boolean {
        val r = call(listOf("ai", token, bitmask, provider))
        return (r.getOrNull(1) as? Int) == 0
    }

    /**
     * Подписка на поток событий этого аккаунта. Опкод `ea` и тело `"u<memberId>"` взяты один
     * в один из офиц. клиента (класс `v.m`: `super(z ? 24933 : 25701)`, тело `"u" + id`).
     *
     * ⚠️ Кормится ТОЛЬКО авторизованная подписка: сервер отвечает `0` и без [resume], но событий
     * в такой канал не шлёт (замер 30.07.2026). Поэтому звать строго после успешного `ma`.
     * @return true, если сервер принял подписку.
     */
    fun subscribeEvents(memberId: Int): Boolean =
            (call(listOf("ea", "u$memberId")).getOrNull(1) as? Int) == 0

    /** Отписка (`ed`) — симметрична [subscribeEvents]. */
    fun unsubscribeEvents(memberId: Int): Boolean =
            (call(listOf("ed", "u$memberId")).getOrNull(1) as? Int) == 0

    /**
     * Читает следующий документ, присланный сервером (ответ или событие). Блокирует до кадра
     * либо до [readTimeoutMs]; ping/pong обслуживаются внутри.
     */
    fun readNextDoc(): List<Any?> = readMessage()

    /**
     * Ping. Обязателен для событийного канала: сервер закрывает соединение ровно через 60с
     * тишины (замер: `EOFException` секунда в секунду), а событий может не быть часами.
     */
    fun ping() = writeFrame(ByteArray(0), compress = false, opcode = OPCODE_PING)

    override fun close() {
        runCatching { socket.close() }
    }

    // region protocol
    private fun nextRid(): Int = ++rid

    private fun call(args: List<Any>): List<Any?> {
        val id = nextRid()
        writeFrame(encodeDoc(listOf(id) + args))
        while (true) {
            val doc = readMessage()
            if (doc.isNotEmpty() && doc[0] == id) return doc
            // Незапрошенные фреймы (события) игнорируем — нас интересует ответ на наш rid.
        }
    }

    private fun readHttpHeaders(): String {
        val sb = StringBuilder()
        var last4 = 0
        while (true) {
            val b = input.read()
            if (b == -1) throw EOFException("EOF during WS handshake")
            sb.append(b.toChar())
            last4 = (last4 shl 8) or b
            if (last4 and 0xFFFFFFFF.toInt() == 0x0D0A0D0A) break
        }
        return sb.toString().substringBefore("\r\n")
    }

    private fun writeFrame(payload: ByteArray, compress: Boolean = true, opcode: Int = OPCODE_TEXT) {
        val body: ByteArray
        val len: Int
        if (compress) {
            val def = Deflater(6, true)
            def.setInput(payload); def.finish()
            val out = ByteArrayOutputStream(payload.size + 64)
            val buf = ByteArray(4096)
            while (!def.finished()) out.write(buf, 0, def.deflate(buf))
            def.end()
            body = out.toByteArray()
            len = body.size + 4
        } else {
            body = payload; len = payload.size
        }
        val header = ByteArrayOutputStream(14)
        header.write(0x80 or (opcode and 0x0F) or (if (compress) 0x40 else 0)) // FIN + opcode + RSV1
        when {
            len <= 125 -> header.write(len)
            len <= 0xFFFF -> { header.write(126); header.write((len shr 8) and 0xFF); header.write(len and 0xFF) }
            else -> {
                header.write(127)
                for (i in 7 downTo 0) header.write(((len.toLong() shr (8 * i)) and 0xFF).toInt())
            }
        }
        if (compress) {
            // 4-байтный little-endian префикс = длина несжатого (как у офиц. клиента).
            header.write(payload.size and 0xFF)
            header.write((payload.size shr 8) and 0xFF)
            header.write((payload.size shr 16) and 0xFF)
            header.write((payload.size shr 24) and 0xFF)
        }
        synchronized(output) {
            output.write(header.toByteArray())
            output.write(body)
            output.flush()
        }
    }

    /** Читает один кадр верхнего уровня, отвечает на ping, возвращает распакованный текст. */
    private fun readMessage(): List<Any?> {
        while (true) {
            val b0 = input.readUnsignedByte()
            val opcode = b0 and 0x0F
            val rsv1 = b0 and 0x40 != 0
            var len = input.readUnsignedByte() and 0x7F
            var length: Long = len.toLong()
            if (len == 126) {
                length = ((input.readUnsignedByte() shl 8) or input.readUnsignedByte()).toLong()
            } else if (len == 127) {
                length = 0
                repeat(8) { length = (length shl 8) or input.readUnsignedByte().toLong() }
            }
            val data = ByteArray(length.toInt())
            input.readFully(data)
            when (opcode) {
                // Ping обязан получить именно PONG: раньше сюда уходил text-фрейм, и сервер
                // пытался разобрать его как документ (десинхронизация протокола).
                OPCODE_PING -> writeFrame(data, compress = false, opcode = OPCODE_PONG)
                OPCODE_PONG -> Unit
                OPCODE_CLOSE -> throw EOFException("server close")
                else -> {
                    val bytes = if (rsv1 && length > 4) inflate(data) else data
                    return decodeDoc(bytes)
                }
            }
        }
    }

    /**
     * Persistent-инфлейт (context takeover), как в офиц. клиенте: инфлейтер НЕ сбрасывается между
     * сообщениями, окно словаря переносится. После полезной нагрузки скармливаем sync-flush
     * `00 00 FF FF`, чтобы выровнять поток к следующему сообщению.
     */
    private fun inflate(data: ByteArray): ByteArray {
        val origLen = ((data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8) or
                ((data[2].toInt() and 0xFF) shl 16) or ((data[3].toInt() and 0xFF) shl 24))
                .coerceIn(0, 8 * 1024 * 1024)
        inflater.setInput(data, 4, data.size - 4)
        val out = ByteArray(origLen)
        var off = 0
        while (off < out.size) {
            val n = inflater.inflate(out, off, out.size - off)
            if (n == 0) break
            off += n
        }
        inflater.setInput(SYNC_FLUSH)
        runCatching { inflater.inflate(ByteArray(16)) } // realign, обычно 0 байт
        return if (off == out.size) out else out.copyOf(off)
    }
    // endregion

    // region document codec (cp1251 text "[ ... ]")
    private fun encodeDoc(items: List<Any?>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write('['.code)
        items.forEachIndexed { i, it ->
            if (i != 0) out.write(','.code)
            when (it) {
                is List<*> -> out.write(encodeDoc(it))
                is Int -> out.write(it.toString().toByteArray(Charsets.US_ASCII))
                is Long -> out.write(it.toString().toByteArray(Charsets.US_ASCII))
                is String -> {
                    out.write('"'.code)
                    for (ch in it) {
                        when {
                            ch == '\\' -> { out.write('\\'.code); out.write('\\'.code) }
                            ch == '"' -> { out.write('\\'.code); out.write('"'.code) }
                            ch.code in 0x20..0x7F -> out.write(ch.code)
                            else -> out.write(ch.toString().toByteArray(CP1251))
                        }
                    }
                    out.write('"'.code)
                }
                else -> throw IllegalArgumentException("bad doc value $it")
            }
        }
        out.write(']'.code)
        return out.toByteArray()
    }

    private fun decodeDoc(bytes: ByteArray): List<Any?> {
        val pos = intArrayOf(0)
        return parseArray(bytes, pos)
    }

    private fun parseArray(b: ByteArray, pos: IntArray): List<Any?> {
        // ожидаем '['
        while (pos[0] < b.size && b[pos[0]].toInt() == ' '.code) pos[0]++
        if (pos[0] >= b.size || b[pos[0]].toInt() != '['.code) return emptyList()
        pos[0]++
        val res = ArrayList<Any?>()
        while (true) {
            while (pos[0] < b.size && b[pos[0]].toInt() == ' '.code) pos[0]++
            if (pos[0] >= b.size) return res
            val c = b[pos[0]].toInt() and 0xFF
            when {
                c == ']'.code -> { pos[0]++; return res }
                c == '['.code -> res.add(parseArray(b, pos))
                c == '-'.code || c in '0'.code..'9'.code -> res.add(parseInt(b, pos))
                c == '"'.code -> res.add(parseString(b, pos))
                else -> pos[0]++
            }
            while (pos[0] < b.size && b[pos[0]].toInt() == ' '.code) pos[0]++
            if (pos[0] < b.size && b[pos[0]].toInt() == ']'.code) { pos[0]++; return res }
            if (pos[0] < b.size && b[pos[0]].toInt() == ','.code) pos[0]++
        }
    }

    private fun parseInt(b: ByteArray, pos: IntArray): Int {
        val start = pos[0]
        if (b[pos[0]].toInt() == '-'.code) pos[0]++
        while (pos[0] < b.size && (b[pos[0]].toInt() and 0xFF) in '0'.code..'9'.code) pos[0]++
        return String(b, start, pos[0] - start, Charsets.US_ASCII).toIntOrNull() ?: 0
    }

    private fun parseString(b: ByteArray, pos: IntArray): String {
        pos[0]++ // skip opening quote
        val out = ByteArrayOutputStream()
        while (pos[0] < b.size) {
            val c = b[pos[0]].toInt() and 0xFF
            if (c == '\\'.code) {
                val e = b[pos[0] + 1].toInt() and 0xFF
                when (e) {
                    'n'.code -> out.write('\n'.code)
                    'r'.code -> out.write('\r'.code)
                    't'.code -> out.write('\t'.code)
                    else -> out.write(e)
                }
                pos[0] += 2
                continue
            }
            if (c == '"'.code) { pos[0]++; break }
            out.write(c)
            pos[0]++
        }
        return String(out.toByteArray(), CP1251)
    }
    // endregion

    companion object {
        const val DEFAULT_WS_HOST = "appbk.4pda.to"
        private const val CLIENT_VERSION = "1.9.43"
        private val CP1251: Charset = Charset.forName("windows-1251")
        private val SYNC_FLUSH = byteArrayOf(0, 0, 0xFF.toByte(), 0xFF.toByte())
        private const val OPCODE_TEXT = 0x1
        private const val OPCODE_CLOSE = 0x8
        private const val OPCODE_PING = 0x9
        private const val OPCODE_PONG = 0xA

        private const val PROVISION_GIST =
                "https://gist.githubusercontent.com/aigilea/152b043823de7cfeacd06f348b78ec25/raw/provision.json"

        const val DEFAULT_DIRECT_ENDPOINT = "app.4pda.to:993"

        /** Динамический хост WSS из provision-конфига (ключ "w"); при сбое — [DEFAULT_WS_HOST]. */
        fun resolveWsHost(): String = provision().first

        private fun provision(): Pair<String, String> = runCatching {
            val conn = (URL(PROVISION_GIST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
            }
            conn.inputStream.use { stream ->
                val json = JSONObject(stream.readBytes().toString(Charsets.UTF_8))
                val ws = json.optString("w", DEFAULT_WS_HOST).ifBlank { DEFAULT_WS_HOST }
                val direct = json.optString("d", DEFAULT_DIRECT_ENDPOINT).ifBlank { DEFAULT_DIRECT_ENDPOINT }
                ws to direct
            }
        }.getOrDefault(DEFAULT_WS_HOST to DEFAULT_DIRECT_ENDPOINT)

        /**
         * Подключается, перебирая доступные транспорты, и возвращает готовый клиент.
         *
         * Порядок намеренно такой: сначала TLS (пароль при логине не идёт открытым текстом),
         * при неудаче — прямой сокет. Прямой путь обязателен, потому что WSS-хост живёт за
         * Cloudflare и у части провайдеров (особенно в РФ) недоступен — именно поэтому
         * официальный клиент тоже держит оба пути.
         *
         * @throws java.io.IOException если не удалось ни одним способом; текст содержит причины.
         */
        fun connectAny(): AppProtocolClient {
            val (wsHost, directEndpoint) = provision()
            val attempts = listOf(Endpoint.Ws(wsHost), Endpoint.Direct(directEndpoint))
            val problems = StringBuilder()
            for (endpoint in attempts) {
                val client = AppProtocolClient(endpoint)
                try {
                    client.connect()
                    Timber.i("AppProtocol connected via %s", endpoint)
                    return client
                } catch (t: Throwable) {
                    runCatching { client.close() }
                    Timber.w(t, "AppProtocol connect failed via %s", endpoint)
                    val label = when (endpoint) {
                        is Endpoint.Ws -> endpoint.host
                        is Endpoint.Direct -> endpoint.hostPort
                    }
                    problems.append(label).append(": ")
                            .append(t.javaClass.simpleName)
                            .append(t.message?.let { " ($it)" } ?: "")
                            .append("; ")
                }
            }
            throw java.io.IOException(problems.toString().trimEnd(' ', ';'))
        }
    }
}
