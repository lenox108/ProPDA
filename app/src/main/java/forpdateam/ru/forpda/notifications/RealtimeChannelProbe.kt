package forpdateam.ru.forpda.notifications

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Диагностика «Мгновенного канала» одним тапом: DNS → TCP :993 → raw-WS ping/pong → вердикт.
 * Отвечает на полевой вопрос «почему не соединяется: провайдер, VPN, Private DNS или сам хост?»
 * без ручной возни с браузером.
 *
 * ВАЖНО: сервер app.4pda.to:993 — НЕстандартный WebSocket (голый TCP, без TLS и без
 * HTTP-рукопожатия; см. [forpdateam.ru.forpda.client.RawWebSocket]). Поэтому проба не делает
 * wss-хендшейк (он там не пройдёт никогда и ничего не измеряет), а шлёт masked ping-фрейм и
 * ждёт pong `8a 00` — ровно то, что делает боевой транспорт.
 *
 * Свой лёгкий OkHttpClient для HTTPS-контролей: без кук, интерсепторов и кэша приложения —
 * проба должна мерить сеть, а не наше окружение. Вызывать на IO-потоке.
 */
object RealtimeChannelProbe {

    private const val HOST = "app.4pda.to"
    private const val WS_PORT = 993
    private const val TIMEOUT_S = 6L

    data class ProbeReport(val lines: List<String>, val verdict: String)

    private fun client(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    /** Сырой TCP-connect: open / refused (порт не слушает) / timeout (сеть режет). */
    private fun rawPortState(port: Int): String = Socket().use { s ->
        runCatching { s.connect(InetSocketAddress(HOST, port), (TIMEOUT_S * 1000).toInt()) }
                .fold(
                        onSuccess = { "open" },
                        onFailure = { e ->
                            when {
                                e is java.net.SocketTimeoutException -> "timeout (сеть режет)"
                                e is java.net.ConnectException &&
                                        e.message?.contains("refused", true) == true -> "refused (не слушает)"
                                else -> "fail ${e.javaClass.simpleName}"
                            }
                        }
                )
    }

    /**
     * Боевой сценарий транспорта в миниатюре: TCP → masked ping-фрейм → ждём pong (0x8A).
     * null = канал жив; иначе — строка с причиной сбоя.
     */
    private fun rawWsPingPong(): String? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(HOST, WS_PORT), (TIMEOUT_S * 1000).toInt())
            s.soTimeout = (TIMEOUT_S * 1000).toInt()
            val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
            // FIN+ping, MASK+len 0, mask key — пустой masked ping-фрейм.
            s.getOutputStream().apply {
                write(byteArrayOf(0x89.toByte(), 0x80.toByte()) + mask)
                flush()
            }
            val input = s.getInputStream()
            val b0 = input.read()
            if (b0 == -1) return@runCatching "сервер закрыл соединение без ответа"
            val opcode = b0 and 0x0F
            if (opcode != 0xA) return@runCatching "неожиданный ответ (opcode=0x${opcode.toString(16)})"
            null // pong получен — канал жив
        }
    }.getOrElse { e -> "${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}" }

    fun run(): ProbeReport {
        val lines = mutableListOf<String>()

        // 1. DNS: не резолвится → Private DNS/блокировщик/резолвер сети.
        val dnsOk = runCatching { InetAddress.getAllByName(HOST) }
                .fold(
                        onSuccess = { addrs ->
                            lines += "DNS: ok (${addrs.joinToString { it.hostAddress ?: "?" }})"
                            true
                        },
                        onFailure = { e ->
                            lines += "DNS: FAIL ${e.javaClass.simpleName}"
                            false
                        }
                )

        // 1b. Сырой TCP до порта 993: показывает, слушает ли сервер и режет ли сеть уже TCP.
        if (dnsOk) {
            lines += "TCP :$WS_PORT → ${rawPortState(WS_PORT)}"
        }

        // 2. Raw-WS ping/pong — собственно живость мгновенного канала.
        var wsOk = false
        if (dnsOk) {
            val err = rawWsPingPong()
            wsOk = err == null
            lines += if (wsOk) "Канал :$WS_PORT (ping/pong): ok" else "Канал :$WS_PORT: FAIL $err"
        } else {
            lines += "Канал :$WS_PORT: пропущено (нет DNS)"
        }

        val probeClient = client()

        // 3. Контроль: базовый сайт (виден ли форум вообще из этой сети).
        val siteOk = runCatching {
            probeClient.newCall(Request.Builder().url("https://4pda.to/").build())
                    .execute().use { it.code }
        }.fold(
                onSuccess = { code ->
                    lines += "HTTPS 4pda.to: ok (HTTP $code)"
                    true
                },
                onFailure = { e ->
                    lines += "HTTPS 4pda.to: FAIL ${e.javaClass.simpleName}"
                    false
                }
        )

        val portLine = lines.firstOrNull { it.startsWith("TCP :$WS_PORT") }.orEmpty()
        val portOpen = portLine.contains("open")
        val portTimeout = portLine.contains("timeout")
        val verdict = when {
            wsOk -> "Мгновенный канал работает из этой сети."
            !dnsOk && siteOk -> "DNS не резолвит $HOST, хотя 4pda.to доступен: похоже на Private DNS или блокировщик рекламы — отключите и повторите."
            !dnsOk -> "DNS не работает в этой сети."
            portOpen -> "Порт $WS_PORT открыт, но сервер не отвечает на ping мгновенного канала: вероятно, сбой на стороне 4PDA или вмешательство сети. Уведомления идут через резервную проверку."
            portTimeout -> "Порт $WS_PORT (мгновенный канал) не отвечает из этой сети — трафик режет провайдер/VPN. На основном сайте это не сказывается. Уведомления идут через резервную проверку."
            siteOk -> "Узел $HOST недоступен из этой сети (4pda.to при этом открывается): блокировка на пути."
            else -> "Сеть недоступна или форум блокируется целиком."
        }
        return ProbeReport(lines, verdict)
    }
}
